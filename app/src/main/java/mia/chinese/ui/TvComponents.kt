package mia.chinese.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

@Composable
fun TvAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val focusModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    Box(
        modifier = modifier
            .then(focusModifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.18f)
                },
                shape = shape
            )
            .background(
                if (focused) {
                    MaterialTheme.colors.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colors.surface
                }
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 18.dp))
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
            androidx.compose.foundation.layout.Column(content = content)
        }
    }
}

@Composable
fun TvPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    androidx.compose.foundation.layout.Column {
        Text(text = title, style = MaterialTheme.typography.h4)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun RowScope.SpacerWeight() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
}
