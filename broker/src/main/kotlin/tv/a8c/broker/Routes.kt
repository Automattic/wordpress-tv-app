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
    //    Phase 1 is identity only (scope=auth, no blog): it never touches a8c.tv,
    //    so a non-Automattician never sees an a8c consent screen.
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
        call.respondRedirect(wpcom.authorizeUrl(state = id, scope = config.authScope, blog = null, clientId = config.clientId))
    }

    // 3. WP.com redirects back here with code + state. Two passes per session:
    //      phase 1 (identity) → for an a12s, redirect into phase 2 (a8c.tv);
    //      phase 2 (a8c.tv token) → done.
    //    `session.awaitingA8c` tells the two passes apart.
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
            // Declining the a8c.tv consent (phase 2) isn't a failure — the user is
            // already identified, so sign them in without a8c access.
            if (session.awaitingA8c) {
                session.authorize(a8cToken = null)
                log.info("session id={}… declined a8c; signed in without it", session.id.take(8))
                call.respondDonePage("You're signed in. Return to your TV — it'll continue automatically.", ok = true)
            } else {
                session.fail("oauth_denied")
                log.info("session id={}… denied at WP.com ({})", session.id.take(8), oauthError)
                call.respondDonePage("Login was cancelled. Return to your TV to try again.", ok = false)
            }
            return@get
        }

        if (code.isNullOrBlank()) {
            call.respondDonePage("Missing authorization code.", ok = false, status = HttpStatusCode.BadRequest)
            return@get
        }

        try {
            if (session.awaitingA8c) {
                // Phase 2: exchange the a8c.tv code for the narrow token the TV keeps.
                // Uses the SEPARATE a8c client so this isn't a second grant for the
                // identity client + same user (the collision that 500s).
                val token = wpcom.exchangeCode(code, config.a8cClientId, config.a8cClientSecret).accessToken
                session.authorize(a8cToken = token)
                log.info("session id={}… authorized with a8c access", session.id.take(8))
                call.respondDonePage("You're paired. Return to your TV — it'll continue automatically.", ok = true)
            } else {
                // Phase 1: identify the user (identity client), then branch on a12s membership.
                val identityToken = wpcom.exchangeCode(code, config.clientId, config.clientSecret).accessToken
                val account = wpcom.fetchAccount(identityToken)
                session.setAccount(account.displayName, account.avatarUrl)

                val isA12s = runCatching { wpcom.isAutomattician(identityToken) }
                    .getOrElse { e ->
                        // Treat an unreachable/forbidden check as "not a12s" so sign-in
                        // still works; surfaced loudly because it hides a8c from employees.
                        log.error("automattician check failed for id={}…: {}", session.id.take(8), e.message)
                        false
                    }

                if (isA12s) {
                    session.awaitA8c()
                    log.info("session id={}… is a12s; requesting a8c.tv access", session.id.take(8))
                    call.respondRedirect(wpcom.authorizeUrl(state = session.id, scope = config.a8cScope, blog = config.blogId, clientId = config.a8cClientId))
                } else {
                    session.authorize(a8cToken = null)
                    log.info("session id={}… signed in (not a12s)", session.id.take(8))
                    call.respondDonePage("You're signed in. Return to your TV — it'll continue automatically.", ok = true)
                }
            }
        } catch (e: Exception) {
            session.fail("token_exchange_failed")
            log.error("callback failed for id={}…: {}", session.id.take(8), e.message)
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
                val response = SessionStatusResponse(
                    status = "authorized",
                    account = AccountDTO(displayName = session.displayName, avatarUrl = session.avatarUrl),
                    a8cAccessToken = session.a8cAccessToken,
                )
                store.remove(id) // single-use rendezvous — collect once, then it's gone
                call.respond(response)
            }
        }
    }
}
