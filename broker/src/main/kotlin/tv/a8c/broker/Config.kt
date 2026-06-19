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
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val blogId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val scope: String,
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

            return Config(
                port = opt("PORT", "8080").toInt(),
                publicBaseUrl = publicBaseUrl,
                clientId = req("WPCOM_CLIENT_ID"),
                clientSecret = req("WPCOM_CLIENT_SECRET"),
                redirectUri = opt("REDIRECT_URI", "$publicBaseUrl/callback"),
                blogId = A8C_TV_BLOG,
                authorizeUrl = opt("WPCOM_AUTHORIZE_URL", "https://public-api.wordpress.com/oauth2/authorize"),
                tokenUrl = opt("WPCOM_TOKEN_URL", "https://public-api.wordpress.com/oauth2/token"),
                scope = opt("WPCOM_SCOPE", ""),
                sessionTtlSeconds = opt("SESSION_TTL_SECONDS", "300").toLong(),
            )
        }
    }
}
