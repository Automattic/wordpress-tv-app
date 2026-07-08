package com.automattic.wordpresstv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.core.Sources
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentLanguage
import com.automattic.wordpresstv.ui.theme.BrandBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun SettingsScreen(
    repository: ContentRepository,
    languageSelection: ContentLanguageSelection,
    onLanguageSelectionChange: (ContentLanguageSelection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    val currentRepository by rememberUpdatedState(repository)
    var languages by remember { mutableStateOf<List<ContentLanguage>>(emptyList()) }
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    val scope = rememberCoroutineScope()

    suspend fun loadLanguages() {
        state = LoadState.Loading
        runCatching { currentRepository.listLanguages(Sources.wordpressTV) }
            .onSuccess {
                languages = it
                state = LoadState.Loaded
            }
            .onFailure {
                languages = emptyList()
                state = LoadState.Failed
            }
    }

    LaunchedEffect(Unit) {
        loadLanguages()
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.done))
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.width(420.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.content_language),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = languageSelection.summary(
                            languages = languages,
                            allLabel = stringResource(R.string.all_languages),
                        ),
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    )
                }

                LanguageList(
                    state = state,
                    languages = languages,
                    languageSelection = languageSelection,
                    onAllLanguages = { onLanguageSelectionChange(ContentLanguageSelection.All) },
                    onToggleLanguage = { onLanguageSelectionChange(languageSelection.toggled(it)) },
                    onRetry = { scope.launch { loadLanguages() } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class LoadState {
    Loading,
    Loaded,
    Failed,
}

@Composable
private fun LanguageList(
    state: LoadState,
    languages: List<ContentLanguage>,
    languageSelection: ContentLanguageSelection,
    onAllLanguages: () -> Unit,
    onToggleLanguage: (ContentLanguage) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocus = remember { FocusRequester() }

    LaunchedEffect(state) {
        if (state == LoadState.Loaded) {
            repeat(10) {
                if (runCatching { firstRowFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(30)
            }
        }
    }

    when (state) {
        LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }

        LoadState.Failed -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = stringResource(R.string.languages_load_error),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 20.sp,
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        LoadState.Loaded -> {
            if (languages.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_languages_available),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 20.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        LanguageRow(
                            title = stringResource(R.string.all_languages),
                            selected = languageSelection.ids.isEmpty(),
                            onClick = onAllLanguages,
                            modifier = Modifier.focusRequester(firstRowFocus),
                        )
                    }
                    items(languages, key = { it.id }) { language ->
                        LanguageRow(
                            title = language.name,
                            selected = languageSelection.contains(language),
                            onClick = { onToggleLanguage(language) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 70.dp).padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SelectionIndicator(selected = selected, modifier = Modifier.size(28.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        if (selected) {
            drawCircle(BrandBlue)
            drawLine(
                Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.52f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.68f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.68f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.34f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(
                Color.White.copy(alpha = 0.42f),
                style = Stroke(width = stroke),
            )
        }
    }
}
