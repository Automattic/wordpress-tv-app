package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.serialization.json.Json

/**
 * [ContentRepository] backed by the WP.com REST API (v1.1).
 *
 * For a public source (wordpress.tv, `auth == NONE`) it sends no token. For a
 * private source (a8c.tv, `auth == WPCOM_OAUTH`) it asks the injected
 * [AuthTokenProvider] for the user's token and sets `Authorization: Bearer`. The
 * implementation is otherwise common Kotlin; only [PlatformHttpClient] is
 * provided by Android/tvOS.
 */
class WpComContentRepository(
    private val pageSize: Int = 24,
    private val authProvider: AuthTokenProvider? = null,
) : ContentRepository {

    private val client = PlatformHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Swift cannot conveniently implement a suspending Kotlin provider, so the
     * tvOS adapter sets this before each call. Android keeps using
     * [authProvider].
     */
    var accessTokenOverride: String? = null

    override suspend fun listLatest(source: ContentSource, page: Int): List<Video> =
        listPosts(source, page)

    suspend fun listLatest(source: ContentSource, page: Int, accessToken: String?): List<Video> =
        listPosts(source, page, accessToken = accessToken)

    override suspend fun resolvePlayback(source: ContentSource, video: Video): PlaybackAsset =
        resolvePlayback(source, video, tokenFor(source))

    suspend fun resolvePlayback(source: ContentSource, video: Video, accessToken: String?): PlaybackAsset {
        val url = apiUrl("videos", video.videoGuid)
        val dto = decode<VideoInfoDto>(getBody(url, tokenFor(source, accessToken)))

        val asset = Mapping.playbackAsset(
            info = dto,
            fallbackTitle = video.title,
            preferProgressive = source.needsPlaybackToken,
        ) ?: throw RepositoryException.NotPlayable

        if (!source.needsPlaybackToken) return asset
        val playbackToken = video.playbackToken ?: return asset
        return asset.copy(url = appendQuery(asset.url, "metadata_token", playbackToken))
    }

    override suspend fun posterUrl(source: ContentSource, video: Video): String? =
        posterUrl(source, video, tokenFor(source))

    suspend fun posterUrl(source: ContentSource, video: Video, accessToken: String?): String? {
        if (!source.needsPlaybackToken) return video.posterUrl

        val token = tokenFor(source, accessToken) ?: return video.posterUrl
        val url = apiUrl("videos", video.videoGuid)
        return try {
            val info = decode<VideoInfoDto>(getBody(url, token))
            val poster = info.poster ?: return null
            val playbackToken = video.playbackToken ?: return poster
            appendQuery(poster, "metadata_token", playbackToken)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listByCategory(source: ContentSource, category: CategoryRef, page: Int): List<Video> =
        listPosts(source, page, extraQuery = mapOf("category" to category.slug))

    suspend fun listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        accessToken: String?,
    ): List<Video> =
        listPosts(source, page, extraQuery = mapOf("category" to category.slug), accessToken = accessToken)

    override suspend fun search(source: ContentSource, query: String, page: Int): List<Video> =
        search(source, query, page, tokenFor(source))

    suspend fun search(source: ContentSource, query: String, page: Int, accessToken: String?): List<Video> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return listPosts(
            source,
            page,
            orderByDate = false,
            extraQuery = mapOf("search" to trimmed),
            accessToken = accessToken,
        )
    }

    override suspend fun listCategories(source: ContentSource) =
        throw RepositoryException.NotImplemented

    private suspend fun listPosts(
        source: ContentSource,
        page: Int,
        orderByDate: Boolean = true,
        extraQuery: Map<String, String> = emptyMap(),
        accessToken: String? = null,
    ): List<Video> {
        val query = mutableMapOf(
            "number" to pageSize.toString(),
            "page" to maxOf(1, page).toString(),
        )
        if (orderByDate) query["order_by"] = "date"
        query.putAll(extraQuery)

        val url = apiUrl("sites", source.wpcomSite, "posts", query = query)
        val dto = decode<PostsResponseDto>(getBody(url, tokenFor(source, accessToken)))
        return Mapping.videos(dto, source.id)
    }

    private suspend fun tokenFor(source: ContentSource, explicitToken: String? = null): String? {
        if (source.auth == ContentSource.Auth.NONE) return null
        return explicitToken ?: accessTokenOverride ?: authProvider?.accessToken(source)
    }

    private suspend fun getBody(url: String, token: String?): String {
        val headers = if (token == null) emptyMap() else mapOf("Authorization" to "Bearer $token")
        val response = client.get(url, headers)
        val code = response.statusCode
        if (code == 401 || code == 403) throw RepositoryException.Unauthorized
        if (code !in 200..299) throw RepositoryException.Http(code)
        return response.body
    }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (_: Exception) {
            throw RepositoryException.DecodingFailed
        }

    private fun appendQuery(url: String, name: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator${name.urlEncoded()}=${value.urlEncoded()}"
    }

    private fun apiUrl(vararg pathSegments: String, query: Map<String, String> = emptyMap()): String {
        val path = pathSegments.joinToString("/") { it.urlEncoded() }
        val base = "$API_BASE/$path"
        if (query.isEmpty()) return base
        val queryString = query.entries.joinToString("&") { (name, value) ->
            "${name.urlEncoded()}=${value.urlEncoded()}"
        }
        return "$base?$queryString"
    }

    private fun String.urlEncoded(): String =
        encodeToByteArray().joinToString(separator = "") { byte ->
            val value = byte.toInt() and 0xff
            val char = value.toChar()
            if (
                char in 'A'..'Z' ||
                char in 'a'..'z' ||
                char in '0'..'9' ||
                char == '-' ||
                char == '.' ||
                char == '_' ||
                char == '~'
            ) {
                char.toString()
            } else {
                "%${value.toString(16).uppercase().padStart(2, '0')}"
            }
        }

    private companion object {
        const val API_BASE = "https://public-api.wordpress.com/rest/v1.1"
    }
}
