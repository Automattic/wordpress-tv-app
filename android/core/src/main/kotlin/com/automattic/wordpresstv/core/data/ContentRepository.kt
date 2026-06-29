package com.automattic.wordpresstv.core.data

import com.automattic.wordpresstv.core.domain.CategoryRef
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.PlaybackAsset
import com.automattic.wordpresstv.core.domain.Video

/**
 * The seam between the data layer (`:core`) and the UI (`:app`).
 *
 * It declares the **full** content contract; the scaffold implements three
 * methods ([listLatest], [resolvePlayback], [posterUrl]) and stubs the rest with
 * [RepositoryException.NotImplemented] until their slices land. The app codes
 * against this interface, never against a concrete implementation.
 */
interface ContentRepository {
    /** Newest videos first. [page] is 1-based. */
    suspend fun listLatest(source: ContentSource, page: Int): List<Video>

    /** Resolve a ready-to-play asset (absolute URL + metadata) for [video]. */
    suspend fun resolvePlayback(source: ContentSource, video: Video): PlaybackAsset

    /**
     * A ready-to-load poster URL for [video]. For a private source this appends
     * the VideoPress `metadata_token` (the poster host gates on it just like
     * playback); for a public source it's simply [Video.posterUrl]. Best-effort:
     * returns `null` if a private poster can't be resolved.
     */
    suspend fun posterUrl(source: ContentSource, video: Video): String?

    // Declared, not yet implemented (later slices).

    suspend fun listCategories(source: ContentSource): List<CategoryRef>
    suspend fun listByCategory(source: ContentSource, category: CategoryRef, page: Int): List<Video>
    suspend fun search(source: ContentSource, query: String, page: Int): List<Video>
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
