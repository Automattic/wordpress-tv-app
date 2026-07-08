package com.automattic.wordpresstv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.automattic.wordpresstv.auth.AuthManager
import com.automattic.wordpresstv.auth.BrokerClient
import com.automattic.wordpresstv.auth.SessionStore
import com.automattic.wordpresstv.continuewatching.WatchProgressStore
import com.automattic.wordpresstv.settings.ContentLanguagePreferenceStore
import com.automattic.wordpresstv.settings.ContentLanguageSelection
import com.automattic.wordpresstv.root.ContentRootScreen
import com.automattic.wordpresstv.splash.SplashScreen
import com.automattic.wordpresstv.core.data.WpComContentRepository
import kotlinx.coroutines.launch

/**
 * Composition root. `auth` owns the a8c.tv token (DataStore + broker) and feeds
 * it to the repository, which sets `Authorization: Bearer` for the private
 * source. wordpress.tv stays public/no-auth.
 *
 * The splash plays once per cold launch, then hands off to the content grid.
 */
@Composable
fun WordPressTvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val auth = remember {
        AuthManager(
            store = SessionStore(context.applicationContext),
            broker = BrokerClient(BuildConfig.BROKER_BASE_URL),
            scope = scope,
        )
    }
    val languagePreferences = remember { ContentLanguagePreferenceStore(context.applicationContext) }
    var contentLanguageSelection by remember { mutableStateOf(ContentLanguageSelection.All) }

    LaunchedEffect(languagePreferences) {
        languagePreferences.selection.collect { contentLanguageSelection = it }
    }

    fun updateContentLanguageSelection(selection: ContentLanguageSelection) {
        contentLanguageSelection = selection
        scope.launch { languagePreferences.write(selection) }
    }

    val repository = remember(auth, contentLanguageSelection.rawValue) {
        WpComContentRepository(
            authProvider = auth,
            contentLanguageTermIds = contentLanguageSelection.ids,
        )
    }
    // Local Continue Watching store (per-device resume points).
    val store = remember { WatchProgressStore(context.applicationContext) }

    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
        ContentRootScreen(
            repository = repository,
            auth = auth,
            store = store,
            contentLanguageSelection = contentLanguageSelection,
            onContentLanguageSelectionChange = ::updateContentLanguageSelection,
        )
    }
}
