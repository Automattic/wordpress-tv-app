package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentLanguage
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * [ContentRepository] backed by the WP.com REST API.
 *
 * Posts use wp/v2 (`/wp/v2/sites/{site}/posts`) because that endpoint supports
 * filtering by custom taxonomy term IDs, including WordPress.tv's `language`
 * and `event` taxonomies. Playback and posters still use the v1.1 video-info
 * endpoint because wp/v2 posts do not include VideoPress attachment metadata.
 *
 * [contentLanguageTermIds] are WordPress.tv `language` taxonomy term IDs from
 * the app's explicit content-language setting. Browse feeds apply that language
 * filter for public WordPress.tv content. Search and collection drill-ins ignore
 * it, and authenticated a8c.tv content does not use the public language
 * taxonomy.
 */
class WpComContentRepository(
    private val pageSize: Int = 24,
    private val authProvider: AuthTokenProvider? = null,
    private val contentLanguageTermIds: List<Long> = emptyList(),
) : ContentRepository {

    private val client = PlatformHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val termCacheLock = Mutex()
    private val languageCache = mutableMapOf<String, List<ContentLanguage>>()
    private val eventCache = mutableMapOf<String, List<ContentEvent>>()
    private val categoryIdCache = mutableMapOf<String, Long>()

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
        val url = apiUrl(VIDEOS_API_BASE, "videos", video.videoGuid)
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
        val token = tokenFor(source, accessToken)
        val url = apiUrl(VIDEOS_API_BASE, "videos", video.videoGuid)
        val info = try {
            decode<VideoInfoDto>(getBody(url, token))
        } catch (_: Exception) {
            return null
        }

        val poster = info.poster ?: return null
        if (!source.needsPlaybackToken) return poster
        val playbackToken = video.playbackToken ?: return poster
        return appendQuery(poster, "metadata_token", playbackToken)
    }

    override suspend fun listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        applyLanguageFilter: Boolean,
    ): List<Video> =
        listByCategory(source, category, page, applyLanguageFilter = applyLanguageFilter, accessToken = null)

    suspend fun listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        applyLanguageFilter: Boolean,
        accessToken: String?,
    ): List<Video> {
        val id = categoryId(source, category.slug, accessToken) ?: return emptyList()
        return listPosts(
            source = source,
            page = page,
            categoryIds = listOf(id),
            applyLanguageFilter = applyLanguageFilter,
            accessToken = accessToken,
        )
    }

    override suspend fun listWordCampEvents(source: ContentSource, limit: Int): List<ContentEvent> =
        listWordCampEvents(source, limit, accessToken = null)

    suspend fun listWordCampEvents(source: ContentSource, limit: Int, accessToken: String?): List<ContentEvent> {
        if (limit <= 0) return emptyList()
        if (source.auth != ContentSource.Auth.NONE) return emptyList()
        termCacheLock.withLock {
            eventCache[source.id]?.let { return it.take(limit) }
            val events = wordCampEventsFromTerms(
                fetchRecentEventTerms(source, accessToken, limit),
            )
            eventCache[source.id] = events
            return events.take(limit)
        }
    }

    override suspend fun listByEvent(
        source: ContentSource,
        event: ContentEvent,
        page: Int,
        applyLanguageFilter: Boolean,
    ): List<Video> =
        listByEvent(source, event, page, applyLanguageFilter = applyLanguageFilter, accessToken = null)

    suspend fun listByEvent(
        source: ContentSource,
        event: ContentEvent,
        page: Int,
        applyLanguageFilter: Boolean,
        accessToken: String?,
    ): List<Video> =
        listPosts(
            source = source,
            page = page,
            eventIds = listOf(event.id),
            applyLanguageFilter = applyLanguageFilter,
            accessToken = accessToken,
        )

    override suspend fun search(source: ContentSource, query: String, page: Int): List<Video> =
        search(source, query, page, accessToken = null)

    suspend fun search(source: ContentSource, query: String, page: Int, accessToken: String?): List<Video> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return listPosts(
            source = source,
            page = page,
            search = trimmed,
            applyLanguageFilter = false,
            accessToken = accessToken,
        )
    }

    override suspend fun listCategories(source: ContentSource) =
        throw RepositoryException.NotImplemented

    override suspend fun listLanguages(source: ContentSource): List<ContentLanguage> =
        listLanguages(source, accessToken = null)

    suspend fun listLanguages(source: ContentSource, accessToken: String?): List<ContentLanguage> {
        if (source.auth != ContentSource.Auth.NONE) return emptyList()
        termCacheLock.withLock {
            languageCache[source.id]?.let { return it }
            val languages = contentLanguagesFromTerms(fetchTerms(source, "language", accessToken = accessToken))
            languageCache[source.id] = languages
            return languages
        }
    }

    private suspend fun listPosts(
        source: ContentSource,
        page: Int,
        categoryIds: List<Long> = emptyList(),
        eventIds: List<Long> = emptyList(),
        search: String? = null,
        applyLanguageFilter: Boolean = true,
        accessToken: String? = null,
    ): List<Video> {
        val query = mutableListOf(
            "per_page" to pageSize.toString(),
            "page" to maxOf(1, page).toString(),
            "_fields" to "id,status,title,excerpt,content",
        )
        categoryIds.forEach { query += "categories[]" to it.toString() }
        eventIds.forEach { query += "event[]" to it.toString() }
        if (applyLanguageFilter) {
            languageIds(source).forEach { query += "language[]" to it.toString() }
        }
        search?.let { query += "search" to it }

        val url = apiUrl(POSTS_API_BASE, "sites", source.wpcomSite, "posts", query = query)
        val posts = decode<List<PostDto>>(getBody(url, tokenFor(source, accessToken)))
        return Mapping.videos(posts, source.id)
    }

    private fun languageIds(source: ContentSource): List<Long> {
        if (contentLanguageTermIds.isEmpty()) return emptyList()
        if (source.auth != ContentSource.Auth.NONE) return emptyList()
        return contentLanguageTermIds.distinct()
    }

    private suspend fun categoryId(source: ContentSource, slug: String, accessToken: String?): Long? {
        val key = "${source.wpcomSite}:$slug"
        termCacheLock.withLock {
            categoryIdCache[key]?.let { return it }
            val terms = fetchTerms(source, "categories", slug = slug, accessToken = accessToken)
            val id = (terms.firstOrNull { it.slug == slug } ?: terms.firstOrNull())?.id
            if (id != null) categoryIdCache[key] = id
            return id
        }
    }

    private suspend fun fetchRecentEventTerms(
        source: ContentSource,
        accessToken: String?,
        targetEventCount: Int,
    ): List<TermDto> {
        val terms = mutableListOf<TermDto>()
        var page = 1
        while (page <= MAX_EVENT_TERM_PAGES) {
            val pageTerms = fetchTerms(
                source = source,
                taxonomy = "event",
                orderBy = "id",
                order = "desc",
                page = page,
                accessToken = accessToken,
            )
            if (pageTerms.isEmpty()) break
            terms += pageTerms
            if (wordCampEventsFromTerms(terms).size >= targetEventCount) break
            page += 1
        }
        return terms
    }

    private suspend fun fetchTerms(
        source: ContentSource,
        taxonomy: String,
        slug: String? = null,
        orderBy: String? = null,
        order: String? = null,
        perPage: Int = 100,
        page: Int = 1,
        accessToken: String? = null,
    ): List<TermDto> {
        val query = mutableListOf(
            "_fields" to "id,name,slug,count",
            "per_page" to perPage.toString(),
            "page" to maxOf(1, page).toString(),
        )
        slug?.let { query += "slug" to it }
        orderBy?.let { query += "orderby" to it }
        order?.let { query += "order" to it }
        val url = apiUrl(POSTS_API_BASE, "sites", source.wpcomSite, taxonomy, query = query)
        return try {
            decode<List<TermDto>>(getBody(url, tokenFor(source, accessToken)))
        } catch (_: Exception) {
            emptyList()
        }
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

    private fun apiUrl(
        base: String,
        vararg pathSegments: String,
        query: List<Pair<String, String>> = emptyList(),
    ): String {
        val path = pathSegments.joinToString("/") { it.urlEncoded() }
        val url = "$base/$path"
        if (query.isEmpty()) return url
        val queryString = query.joinToString("&") { (name, value) ->
            "${name.urlEncoded()}=${value.urlEncoded()}"
        }
        return "$url?$queryString"
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
        const val POSTS_API_BASE = "https://public-api.wordpress.com/wp/v2"
        const val VIDEOS_API_BASE = "https://public-api.wordpress.com/rest/v1.1"
        const val MAX_EVENT_TERM_PAGES = 5
    }
}

internal fun contentLanguagesFromTerms(terms: List<TermDto>): List<ContentLanguage> =
    terms
        .filter { it.name.isNotBlank() && it.slug.isNotBlank() }
        .distinctBy { it.id }
        .map { ContentLanguage(id = it.id, name = it.name, slug = it.slug) }
        .sortedBy { it.name.lowercase() }

private val WordCampEventSlug = Regex("""^wordcamp-.+-\d{4}$""")

internal fun wordCampEventsFromTerms(terms: List<TermDto>): List<ContentEvent> =
    terms
        .filter { term ->
            term.name.isNotBlank() &&
                WordCampEventSlug.matches(term.slug) &&
                term.count > 0
        }
        .distinctBy { it.id }
        .map { ContentEvent(id = it.id, name = it.name, slug = it.slug, videoCount = it.count) }
