package tv.a8c.broker

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BrokerRoutes")

/** Renders the phone-facing "return to your TV" page (HTML, defaults to 200). */
private suspend fun ApplicationCall.respondDonePage(
    message: String,
    ok: Boolean,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(donePage(message, ok), ContentType.Text.Html, status)
}

/**
 * The four broker endpoints from the spec, plus a /healthz.
 *
 * The TV is never part of the OAuth exchange: the session id (carried as OAuth
 * `state`) is the only thing correlating "this phone's login" with "that polling
 * TV". The poll secret ensures only the TV that created the session can collect
 * its token.
 */
fun Route.brokerRoutes(
    config: Config,
    store: SessionStore,
    wpcom: WpComClient,
    now: () -> Long = { System.currentTimeMillis() },
) {
    get("/healthz") {
        call.respondText("ok")
    }

    // 1. TV starts a pairing session.
    post("/session") {
        val session = store.create(now())
        log.info("session created id={}…", session.id.take(8))
        call.respond(
            CreateSessionResponse(
                sessionId = session.id,
                pollSecret = session.pollSecret,
                qrUrl = "${config.publicBaseUrl}/pair/${session.id}",
                ttl = config.sessionTtlSeconds,
            ),
        )
    }

    // 2. Phone scans the QR and lands here → redirect into WP.com's OAuth.
    get("/pair/{id}") {
        val id = call.parameters["id"].orEmpty()
        val session = store.get(id, now())
        if (session == null) {
            call.respondText(
                "This pairing link has expired. Return to your TV and try again.",
                status = HttpStatusCode.Gone,
            )
            return@get
        }
        call.respondRedirect(wpcom.authorizeUrl(state = id))
    }

    // 3. WP.com redirects back here with code + state; broker exchanges the code.
    get("/callback") {
        val params = call.request.queryParameters
        val state = params["state"].orEmpty()
        val code = params["code"]
        val oauthError = params["error"]

        val session = store.get(state, now())
        if (session == null) {
            call.respondDonePage(
                "Pairing session not found or expired. Start over from your TV.",
                ok = false,
                status = HttpStatusCode.BadRequest,
            )
            return@get
        }

        if (oauthError != null) {
            session.fail("oauth_denied")
            log.info("session id={}… denied at WP.com ({})", session.id.take(8), oauthError)
            call.respondDonePage("Login was cancelled. Return to your TV to try again.", ok = false)
            return@get
        }

        if (code.isNullOrBlank()) {
            call.respondDonePage("Missing authorization code.", ok = false, status = HttpStatusCode.BadRequest)
            return@get
        }

        try {
            val token = wpcom.exchangeCode(code)
            session.authorize(token.accessToken)
            log.info("session id={}… authorized", session.id.take(8))
            call.respondDonePage("You're paired. Return to your TV — it'll continue automatically.", ok = true)
        } catch (e: Exception) {
            session.fail("token_exchange_failed")
            log.error("token exchange failed for id={}…: {}", session.id.take(8), e.message)
            call.respondDonePage(
                "Couldn't complete login. Return to your TV and try again.",
                ok = false,
                status = HttpStatusCode.BadGateway,
            )
        }
    }

    // 4. TV polls here. Requires the poll secret. Token is returned once, then deleted.
    get("/session/{id}") {
        val id = call.parameters["id"].orEmpty()
        val presentedSecret = call.request.headers["X-Poll-Secret"]
            ?: call.request.queryParameters["poll_secret"]

        val session = store.get(id, now())
        if (session == null) {
            call.respond(HttpStatusCode.Gone, ErrorResponse("expired", "Session not found or expired."))
            return@get
        }
        if (presentedSecret == null || presentedSecret != session.pollSecret) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", "Invalid or missing poll secret."))
            return@get
        }

        when (session.status) {
            SessionStatus.PENDING ->
                call.respond(SessionStatusResponse(status = "pending"))

            SessionStatus.ERROR -> {
                store.remove(id)
                call.respond(SessionStatusResponse(status = "error", error = session.errorMessage ?: "error"))
            }

            SessionStatus.AUTHORIZED -> {
                val token = session.accessToken
                store.remove(id) // single-use rendezvous — collect once, then it's gone
                call.respond(SessionStatusResponse(status = "authorized", accessToken = token))
            }
        }
    }
}
