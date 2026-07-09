package com.automattic.wordpresstv.core.domain

/**
 * An event term from WordPress.tv's public `event` taxonomy.
 *
 * The term ID is the value used by wp/v2 filtering. [videoCount] is the number
 * of published videos currently attached to the event term.
 */
data class ContentEvent(
    val id: Long,
    val name: String,
    val slug: String,
    val videoCount: Int,
)
