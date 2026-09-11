package com.margin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/**
 * The iOS sheet: a large rounded top edge, a grabber, a centred title with Cancel on the left
 * and the confirming action on the right, and the grouped background underneath so forms
 * inside read as inset grouped lists. A dimming layer sits behind, because a sheet interrupts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarginSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    leadingText: String? = "Cancel",
    onLeading: (() -> Unit)? = onDismiss,
    trailingText: String? = null,
    onTrailing: (() -> Unit)? = null,
    trailingEnabled: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MarginTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = MarginShape.sheet,
        containerColor = colors.groupedBackground,
        contentColor = colors.label,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = { Grabber() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Space.s),
        ) {
            if (leadingText != null && onLeading != null) {
                TextAction(
                    text = leadingText,
                    onClick = onLeading,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            if (title != null) {
                Text(
                    text = title,
                    style = AppleType.headline,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 88.dp),
                )
            }
            if (trailingText != null && onTrailing != null) {
                TextAction(
                    text = trailingText,
                    onClick = onTrailing,
                    emphasized = true,
                    enabled = trailingEnabled,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            content()
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
fun Grabber() {
    Box(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 5.dp)
            .clip(MarginShape.capsule)
            .background(MarginTheme.colors.tertiaryLabel),
    )
}
