package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [ContentRepository] backed by the WP.com REST API (v1.1).
 *
 * For a public source (wordpress.tv, `auth == NONE`) it sends no token. For a
 * private source (a8c.tv, `auth == WPCOM_OAUTH`) it asks the injected
 * [AuthTokenProvider] for the user's token and sets `Authorization: Bearer`. When
 * the source `needsPlaybackToken`, the poster and stream URLs get a VideoPress
 * `metadata_token` appended — reusing the per-video token WP.com mints into the
 * post's private embed ([Video.playbackToken]). A 401/403 surfaces as
 * [RepositoryException.Unauthorized] so the UI can re-pair.
 */
class WpComContentRepository(
    private val client: OkHttpClient = OkHttpClient(),
    private val pageSize: Int = 24,
    private val authProvider: AuthTokenProvider? = null,
) : ContentRepository {

    private val apiBase = "https://public-api.wordpress.com/rest/v1.1".toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    // --- Implemented ---

    override suspend fun listLatest(source: ContentSource, page: Int): List<Video> {
        val url = apiBase.newBuilder()
            .addPathSegments("sites/${source.wpcomSite}/posts")
            .addQueryParameter("number", pageSize.toString())
            .addQueryParameter("page", maxOf(1, page).toString())
            .addQueryParameter("order_by", "date")
            .build()
        val dto = decode<PostsResponseDto>(getBody(url.toString(), tokenFor(source)))
        return Mapping.videos(dto, source.id)
    }

    override suspend fun resolvePlayback(source: ContentSource, video: Video): PlaybackAsset {
        val token = tokenFor(source)
        val url = apiBase.newBuilder().addPathSegments("videos/${video.videoGuid}").build()
        val dto = decode<VideoInfoDto>(getBody(url.toString(), token))

        // Private VideoPress (a8c.tv) plays the progressive `original` MP4 with a
        // metadata token appended; public videos (wordpress.tv) keep HLS.
        val asset = Mapping.playbackAsset(
            info = dto,
            fallbackTitle = video.title,
            preferProgressive = source.needsPlaybackToken,
        ) ?: throw RepositoryException.NotPlayable

        if (!source.needsPlaybackToken) return asset
        // Reuse the token WP.com minted into the post's embed (carried on the
        // Video). A post without a token is a public video; play it bare rather
        // than treating the absence as an auth failure (a needless re-pair).
        val playbackToken = video.playbackToken ?: return asset
        return asset.copy(url = appendQuery(asset.url, "metadata_token", playbackToken))
    }

    override suspend fun posterUrl(source: ContentSource, video: Video): String? {
        // Public sources (wordpress.tv): the posts list already carried a poster
        // from the video's attachment thumbnails — serve it openly.
        if (!source.needsPlaybackToken) return video.posterUrl

        // Private VideoPress (a8c.tv): posts carry no attachment, so resolve the
        // real poster from the video-info endpoint and append the embed's
        // metadata token. Best-effort: null (placeholder) on any failure.
        val token = tokenFor(source) ?: return video.posterUrl
        val url = apiBase.newBuilder().addPathSegments("videos/${video.videoGuid}").build()
        return try {
            val info = decode<VideoInfoDto>(getBody(url.toString(), token))
            val poster = info.poster ?: return null
            val playbackToken = video.playbackToken ?: return poster
            appendQuery(poster, "metadata_token", playbackToken)
        } catch (_: Exception) {
            null
        }
    }

    // --- Stubbed (later slices) ---

    override suspend fun listCategories(source: ContentSource) =
        throw RepositoryException.NotImplemented

    override suspend fun listByCategory(source: ContentSource, category: CategoryRef, page: Int) =
        throw RepositoryException.NotImplemented

    override suspend fun search(source: ContentSource, query: String, page: Int) =
        throw RepositoryException.NotImplemented

    // --- Auth helpers ---

    private suspend fun tokenFor(source: ContentSource): String? {
        if (source.auth == ContentSource.Auth.NONE) return null
        return authProvider?.accessToken(source)
    }

    // --- Transport ---

    private suspend fun getBody(url: String, token: String?): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            if (token != null) builder.header("Authorization", "Bearer $token")
            client.newCall(builder.build()).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) throw RepositoryException.Unauthorized
                if (code !in 200..299) throw RepositoryException.Http(code)
                response.body?.string() ?: throw RepositoryException.InvalidResponse
            }
        }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (_: Exception) {
            throw RepositoryException.DecodingFailed
        }

    /** Append a single query item to [url], preserving any existing ones. */
    private fun appendQuery(url: String, name: String, value: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        return httpUrl.newBuilder().addQueryParameter(name, value).build().toString()
    }
}
