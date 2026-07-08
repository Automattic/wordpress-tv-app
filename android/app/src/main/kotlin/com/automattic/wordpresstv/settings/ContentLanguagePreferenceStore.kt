package com.automattic.wordpresstv.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.automattic.wordpresstv.core.domain.ContentLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ContentLanguageSelection(val ids: List<Long>) {
    companion object {
        val All = ContentLanguageSelection(emptyList())

        fun fromRaw(raw: String?): ContentLanguageSelection {
            if (raw.isNullOrBlank() || raw == "all") return All
            return ContentLanguageSelection(raw.split(",").mapNotNull { it.toLongOrNull() }.distinct())
        }
    }

    init {
        require(ids.distinct() == ids) { "Content language IDs must be unique." }
    }

    val rawValue: String
        get() = ids.joinToString(",")

    fun contains(language: ContentLanguage): Boolean =
        ids.contains(language.id)

    fun toggled(language: ContentLanguage): ContentLanguageSelection =
        if (contains(language)) {
            ContentLanguageSelection(ids.filterNot { it == language.id })
        } else {
            ContentLanguageSelection(ids + language.id)
        }

    fun summary(languages: List<ContentLanguage>, allLabel: String): String {
        if (ids.isEmpty()) return allLabel
        val names = ids.map { id -> languages.firstOrNull { it.id == id }?.name ?: "#$id" }
        if (names.size <= 3) return names.joinToString(", ")
        return "${names.take(3).joinToString(", ")} +${names.size - 3}"
    }
}

private val Context.contentLanguageDataStore by preferencesDataStore(name = "content_language")

class ContentLanguagePreferenceStore(context: Context) {
    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("content_language_term_ids")

    val selection: Flow<ContentLanguageSelection> =
        appContext.contentLanguageDataStore.data.map { prefs ->
            ContentLanguageSelection.fromRaw(prefs[key])
        }

    suspend fun write(selection: ContentLanguageSelection) {
        appContext.contentLanguageDataStore.edit { prefs ->
            if (selection.ids.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = selection.rawValue
            }
        }
    }
}
