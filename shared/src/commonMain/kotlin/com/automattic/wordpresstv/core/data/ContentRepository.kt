package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentEvent
import com.automattic.wordpresstv.core.domain.ContentLanguage
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video

/**
 * The seam between the data layer (`:core`) and the UI (`:app`).
 *
 * It declares the **full** content contract. Everything the browse experience
 * needs is implemented ([listLatest], [listByCategory], [listByEvent],
 * [listFlagshipWordCampEvents], [listWordCampEvents], [search],
 * [resolvePlayback], [posterUrl]); [listCategories] stays stubbed with
 * [RepositoryException.NotImplemented] until a slice needs it. The app codes
 * against this interface, never against a concrete implementation.
 */
interface ContentRepository {
    /** Newest videos first. [page] is 1-based. */
    suspend fun listLatest(source: ContentSource, page: Int): List<Video>

    /** Resolve a ready-to-play asset (absolute URL + metadata) for [video]. */
    suspend fun resolvePlayback(source: ContentSource, video: Video): PlaybackAsset

    /**
     * A ready-to-load poster URL for [video]. wp/v2 post rows do not carry
     * VideoPress thumbnails, so the repository may resolve this from video-info.
     * For a private source this appends the VideoPress `metadata_token`.
     * Best-effort: returns `null` if a poster cannot be resolved.
     */
    suspend fun posterUrl(source: ContentSource, video: Video): String?

    /** Videos in [category] for [source]. [page] is 1-based. */
    suspend fun listByCategory(
        source: ContentSource,
        category: CategoryRef,
        page: Int,
        applyLanguageFilter: Boolean = true,
    ): List<Video>

    /** Latest flagship WordCamp event terms from the WordPress.tv `event` taxonomy. */
    suspend fun listFlagshipWordCampEvents(source: ContentSource): List<ContentEvent>

    /** Recent WordCamp event terms from the WordPress.tv `event` taxonomy. [page] is 1-based. */
    suspend fun listWordCampEvents(source: ContentSource, page: Int): List<ContentEvent>

    /** Videos in [event] for [source]. [page] is 1-based. */
    suspend fun listByEvent(
        source: ContentSource,
        event: ContentEvent,
        page: Int,
        applyLanguageFilter: Boolean = true,
    ): List<Video>

    /** Relevance-ranked search over [source]. [page] is 1-based; blank [query] yields nothing. */
    suspend fun search(source: ContentSource, query: String, page: Int): List<Video>

    /** Available content languages for [source]. */
    suspend fun listLanguages(source: ContentSource): List<ContentLanguage>

    // Declared, not yet implemented (later slices).

    suspend fun listCategories(source: ContentSource): List<CategoryRef>
}

/** Errors surfaced across the repository seam. */
sealed class RepositoryException(message: String? = null) : Exception(message) {
    /** A contract method this slice hasn't built yet. */
    data object NotImplemented : RepositoryException()
    /** The source returned data, but nothing playable could be resolved. */
    data object NotPlayable : RepositoryException()
    /** The token was missing, rejected, or expired (HTTP 401/403) — clear it and re-pair. */
    data object Unauthorized : RepositoryException()
    /** Could not build a valid request or asset URL. */
    data object InvalidUrl : RepositoryException()
    /** The transport returned something other than an HTTP response. */
    data object InvalidResponse : RepositoryException()
    /** Non-2xx HTTP status. */
    data class Http(val status: Int) : RepositoryException("HTTP $status")
    /** Transport succeeded but the payload wasn't the shape we expected. */
    data object DecodingFailed : RepositoryException()
}
