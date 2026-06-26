package tv.a8c.broker

import kotlin.test.Test
import kotlin.test.assertTrue

class WpComClientTest {

    private fun config() = Config(
        port = 8080,
        publicBaseUrl = "http://localhost:8080",
        clientId = "142411",
        clientSecret = "secret",
        a8cClientId = "999999",
        a8cClientSecret = "a8c-secret",
        redirectUri = "http://localhost:8080/callback",
        blogId = "a8ctv.wordpress.com",
        authorizeUrl = "https://public-api.wordpress.com/oauth2/authorize",
        tokenUrl = "https://public-api.wordpress.com/oauth2/token",
        apiBaseUrl = "https://public-api.wordpress.com/rest/v1.1",
        authScope = "auth",
        a8cScope = "posts videos",
    )

    @Test
    fun `phase 1 authorize is identity-only - scope=auth, no blog, identity client`() {
        val url = WpComClient(config()).authorizeUrl(state = "abc", scope = "auth", blog = null, clientId = "142411")
        assertTrue(url.contains("scope=auth"), url)
        assertTrue(!url.contains("blog="), url)
        assertTrue(url.contains("client_id=142411"), url)
    }

    @Test
    fun `phase 2 authorize carries the blog, space-separated scope, and the SEPARATE a8c client`() {
        val url = WpComClient(config()).authorizeUrl(state = "abc", scope = "posts videos", blog = "a8ctv.wordpress.com", clientId = "999999")
        assertTrue(url.contains("blog=a8ctv.wordpress.com"), url)
        // Space-separated scope, percent- or plus-encoded — never a comma, which
        // WP.com rejects as an invalid scope.
        assertTrue(url.contains("scope=posts%20videos") || url.contains("scope=posts+videos"), url)
        assertTrue(!url.contains("posts,videos") && !url.contains("posts%2Cvideos"), url)
        // Different client from phase 1 — that's the whole fix.
        assertTrue(url.contains("client_id=999999"), url)
    }
}
