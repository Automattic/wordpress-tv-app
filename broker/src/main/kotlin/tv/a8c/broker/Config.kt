package tv.a8c.broker

data class Config(
    val port: Int,
    val publicBaseUrl: String,
    val clientId: String,
    val clientSecret: String,
    /** Separate app from the identity client: a second auth_code grant for the same
     *  `(client, user)` makes WP.com's `/oauth2/token` return an empty 500. Required,
     *  with no fallback to the identity client (that just reproduced the 500). */
    val a8cClientId: String,
    val a8cClientSecret: String,
    val redirectUri: String,
    val blogId: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val apiBaseUrl: String,
    val authScope: String,
    val a8cScope: String,
) {
    companion object {
        const val A8C_TV_BLOG = "a8ctv.wordpress.com"
        const val CALLBACK_PATH = "/callback"

        /** Session lifetime: short-lived and single-use (deleted on collect). */
        const val SESSION_TTL_SECONDS = 300L

        const val AUTHORIZE_URL = "https://public-api.wordpress.com/oauth2/authorize"
        const val TOKEN_URL = "https://public-api.wordpress.com/oauth2/token"
        const val API_BASE_URL = "https://public-api.wordpress.com/rest/v1.1"

        const val AUTH_SCOPE = "auth"
        const val A8C_SCOPE = "posts videos"

        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun opt(key: String, default: String): String =
                env[key]?.takeIf { it.isNotBlank() } ?: default

            fun req(key: String): String =
                env[key]?.takeIf { it.isNotBlank() }
                    ?: error("Missing required env var: $key")

            val publicBaseUrl = req("PUBLIC_BASE_URL").trimEnd('/')
            return Config(
                port = opt("PORT", "8080").toInt(),
                publicBaseUrl = publicBaseUrl,
                clientId = req("WPCOM_CLIENT_ID"),
                clientSecret = req("WPCOM_CLIENT_SECRET"),
                a8cClientId = req("WPCOM_A8C_CLIENT_ID"),
                a8cClientSecret = req("WPCOM_A8C_CLIENT_SECRET"),
                redirectUri = "$publicBaseUrl$CALLBACK_PATH",
                blogId = A8C_TV_BLOG,
                authorizeUrl = AUTHORIZE_URL,
                tokenUrl = TOKEN_URL,
                apiBaseUrl = API_BASE_URL,
                authScope = AUTH_SCOPE,
                a8cScope = A8C_SCOPE,
            )
        }
    }
}
