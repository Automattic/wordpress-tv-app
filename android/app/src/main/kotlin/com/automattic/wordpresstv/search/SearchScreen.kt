package com.automattic.wordpresstv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.wordpresstv.R
import com.automattic.wordpresstv.core.data.ContentRepository
import com.automattic.wordpresstv.core.domain.ContentSource
import com.automattic.wordpresstv.core.domain.Video
import com.automattic.wordpresstv.feed.VideoGrid
import com.automattic.wordpresstv.ui.theme.BrandBlue
import kotlinx.coroutines.delay
import androidx.tv.material3.Text

/**
 * Search over the current source. A focusable field brings up the leanback
 * keyboard; submitting runs the query and rebuilds the results grid (recreating
 * the grid per submitted term forces a fresh load). Mirrors the Apple
 * `SearchScreen`.
 */
@Composable
fun SearchScreen(
    repository: ContentRepository,
    source: ContentSource,
    onPlay: (Video, ContentSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { submitted = query.trim() },
        )

        Box(Modifier.weight(1f).fillMaxSize()) {
            if (submitted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.search_prompt),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 22.sp,
                    )
                }
            } else {
                // key() on the term forces a fresh VideoGrid (and load) per submission.
                androidx.compose.runtime.key(submitted) {
                    VideoGrid(
                        repository = repository,
                        source = source,
                        query = com.automattic.wordpresstv.feed.VideoQuery.Search(submitted),
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    // Focus the field on entry so the D-pad can reach it and Center opens the IME.
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 56.dp, vertical = 24.dp)
            .background(Color.White.copy(alpha = 0.1f), CircleShape)
            .border(2.dp, if (focused) BrandBlue else Color.Transparent, CircleShape)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SearchIcon(Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.search_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/** A dependency-free magnifying-glass glyph, shared by the field and the nav tab. */
@Composable
fun SearchIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val r = size.minDimension * 0.32f
        val center = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(Color.White, radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawLine(
            Color.White,
            start = androidx.compose.ui.geometry.Offset(center.x + r * 0.72f, center.y + r * 0.72f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
