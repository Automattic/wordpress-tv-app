package tv.a8c.broker

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun main() {
    val config = Config.fromEnv()
    LoggerFactory.getLogger("Application").info(
        "Starting broker on :{} (publicBaseUrl={})",
        config.port, config.publicBaseUrl,
    )
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: Config) {
    val log = LoggerFactory.getLogger("Application")

    install(ContentNegotiation) {
        json(brokerJson)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on {}: {}", call.request.local.uri, cause.message)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
    }

    val store = SessionStore(Config.SESSION_TTL_SECONDS)
    val wpcom = WpComClient(config)

    routing {
        brokerRoutes(config, store, wpcom)
    }
}
