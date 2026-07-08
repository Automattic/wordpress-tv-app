package com.automattic.wordpresstv.core.domain

/**
 * A language term from WordPress.tv's public `language` taxonomy.
 *
 * The term ID is the value used by wp/v2 filtering. The name is API-owned UI
 * text, often including both English and native labels, e.g. `Polish/Polski`.
 */
data class ContentLanguage(
    val id: Long,
    val name: String,
    val slug: String,
)
