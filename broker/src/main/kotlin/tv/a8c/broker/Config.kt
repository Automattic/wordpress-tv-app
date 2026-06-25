package tv.a8c.broker

/**
 * All broker configuration, read from the environment once at startup.
 *
 * Required: WPCOM_CLIENT_ID, WPCOM_CLIENT_SECRET.
 * Everything else has a sensible default (the WP.com production endpoints,
 * a 5-minute session TTL, port 8080, blog 14140874 = a8c.tv).
 */
data class Config(
    val port: Int,
    val publicBaseUrl: String,
    /** Identity client (phase 1: `scope=auth` + `/me` + `/internal/automattician`).
     *  This is the app every user — including non-a12s / App Review — sees. */
    val clientId: String,
    val clientSecret: String,
    /** SEPARATE client for phase 2 (a8c.tv content). Using a different app means
     *  phase 2 isn't a second grant for the same `(client, user)` as phase 1 —
     *  that collision is what made WP.com's `/oauth2/token` return an empty 500.
     *  MUST be set in production: it falls back to the identity client only so the
     *  service still boots before the second app's secrets are configured, but
     *  that fallback reproduces the 500. */
    val a8cClientId: String,
    val a8cClientSecret: String,
    val redirectUri: String,
    val blogId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    /** WP.com REST base, for `/me` and `/internal/automattician`. */
    val apiBaseUrl: String,
    /** Phase 1 scope: identity only, no site access. Lets us call `/me` and
     *  `/internal/automattician` without ever touching a8c.tv. */
    val authScope: String,
    /** Phase 2 scope: what the a8c.tv token the TV keeps may do — read posts and
     *  videos, confined to a8c.tv by the `blog` param. Scopes are SPACE-
     *  separated (`posts videos`); a comma (`posts,videos`) is rejected by
     *  /authorize as an invalid scope. */
    val a8cScope: String,
    val sessionTtlSeconds: Long,
) {
    companion object {
        /**
         * The single source of truth for the target site. a8c.tv is the only content
         * source in v1 (hardcoded per spec). WP.com's OAuth `blog` param needs the site
         * DOMAIN, not the numeric id (blog_id 14140874 == a8ctv.wordpress.com).
         */
        const val A8C_TV_BLOG = "a8ctv.wordpress.com"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun opt(key: String, default: String): String =
                env[key]?.takeIf { it.isNotBlank() } ?: default

            fun req(key: String): String =
                env[key]?.takeIf { it.isNotBlank() }
                    ?: error("Missing required env var: $key")

            val publicBaseUrl = opt("PUBLIC_BASE_URL", "http://localhost:8080").trimEnd('/')
            val clientId = req("WPCOM_CLIENT_ID")
            val clientSecret = req("WPCOM_CLIENT_SECRET")

            return Config(
                port = opt("PORT", "8080").toInt(),
                publicBaseUrl = publicBaseUrl,
                clientId = clientId,
                clientSecret = clientSecret,
                a8cClientId = opt("WPCOM_A8C_CLIENT_ID", clientId),
                a8cClientSecret = opt("WPCOM_A8C_CLIENT_SECRET", clientSecret),
                redirectUri = opt("REDIRECT_URI", "$publicBaseUrl/callback"),
                blogId = A8C_TV_BLOG,
                authorizeUrl = opt("WPCOM_AUTHORIZE_URL", "https://public-api.wordpress.com/oauth2/authorize"),
                tokenUrl = opt("WPCOM_TOKEN_URL", "https://public-api.wordpress.com/oauth2/token"),
                apiBaseUrl = opt("WPCOM_API_BASE", "https://public-api.wordpress.com/rest/v1.1").trimEnd('/'),
                authScope = opt("WPCOM_AUTH_SCOPE", "auth"),
                a8cScope = opt("WPCOM_A8C_SCOPE", "posts videos"),
                sessionTtlSeconds = opt("SESSION_TTL_SECONDS", "300").toLong(),
            )
        }
    }
}
