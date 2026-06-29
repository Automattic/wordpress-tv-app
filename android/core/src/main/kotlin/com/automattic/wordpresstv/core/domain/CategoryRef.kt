package com.automattic.wordpresstv.core.domain

/**
 * Reference to a category within a source. Used by the (currently stubbed)
 * category APIs on `ContentRepository`.
 */
data class CategoryRef(
    val id: String,
    val name: String,
    val slug: String,
)
