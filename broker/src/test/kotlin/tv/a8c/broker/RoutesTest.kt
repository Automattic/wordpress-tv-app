package tv.a8c.broker

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoutesTest {

    private fun testConfig() = Config(
        port = 8080,
        publicBaseUrl = "http://localhost:8080",
        clientId = "test-client",
        clientSecret = "test-secret",
        a8cClientId = "test-a8c-client",
        a8cClientSecret = "test-a8c-secret",
        redirectUri = "http://localhost:8080/callback",
        blogId = "14140874",
        authorizeUrl = "https://wpcom.test/oauth2/authorize",
        tokenUrl = "https://wpcom.test/oauth2/token",
        apiBaseUrl = "https://wpcom.test/rest/v1.1",
        authScope = "auth",
        a8cScope = "posts videos",
    )

    @Test
    fun `create session then poll requires the poll secret`() = testApplication {
        application { module(testConfig()) }
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val created: CreateSessionResponse = client.post("/session").body()
        assertNotNull(created.sessionId)
        assertEquals("http://localhost:8080/pair/${created.sessionId}", created.qrUrl)

        // Poll without the secret → 403.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/session/${created.sessionId}").status,
        )

        // Poll with the secret → 200 pending.
        val ok = client.get("/session/${created.sessionId}") {
            header("X-Poll-Secret", created.pollSecret)
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("pending", ok.body<SessionStatusResponse>().status)
    }

    @Test
    fun `polling an unknown session is 410 Gone`() = testApplication {
        application { module(testConfig()) }
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val response = client.get("/session/does-not-exist") {
            header("X-Poll-Secret", "whatever")
        }
        assertEquals(HttpStatusCode.Gone, response.status)
    }

    @Test
    fun `pair redirects into the WordPress_com authorize url for phase 1 identity`() = testApplication {
        application { module(testConfig()) }
        // Don't follow the redirect — we want to inspect the Location header.
        val client = createClient { followRedirects = false }

        val created: CreateSessionResponse =
            createClient { install(ClientContentNegotiation) { json() } }.post("/session").body()

        val response = client.get("/pair/${created.sessionId}")
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.headers["Location"]
        assertNotNull(location)
        assertEquals(true, location.startsWith("https://wpcom.test/oauth2/authorize?"))
        assertEquals(true, location.contains("state=${created.sessionId}"))
        // Phase 1 is identity-only: scope=auth and — crucially — NO blog, so a
        // non-Automattician never sees an a8c.tv consent screen.
        assertEquals(true, location.contains("scope=auth"))
        assertEquals(false, location.contains("blog="))
    }
}
