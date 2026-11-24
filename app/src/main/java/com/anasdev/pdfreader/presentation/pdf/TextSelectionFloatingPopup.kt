package com.anasdev.pdfreader.presentation.pdf

import android.graphics.RectF
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anasdev.pdfreader.R
import com.github.barteksc.pdfviewer.selection.PdfTextSelectionManager
import kotlin.math.roundToInt

/**
 * Unified pages for the anchored floating text-selection menu.
 */
enum class SelectionMenuPage {
    MAIN,
    COLORS,
    MORE
}

/**
 * State representing active text selection in PDFView.
 */
data class TextSelectionState(
    val selectedText: String,
    val bounds: RectF
)

/**
 * Anchored floating text-selection popup with seamless in-place page animations:
 * - MAIN: Highlight swatch button, Copy, Translate, Overflow (⋮).
 * - COLORS: Return arrow, 4 color swatches, subtle 1dp divider, refined delete/trash icon.
 * - MORE: Return arrow, Select All, Share.
 *
 * All three pages share the EXACT same (x, y) anchor and animate with AnimatedContent + animateContentSize.
 */
@Composable
fun TextSelectionFloatingPopup(
    selectedText: String,
    selectionBounds: RectF,
    screenWidth: Float,
    density: Float,
    topBarHeightPx: Float,
    onApplyHighlight: (colorArgb: Int) -> Unit,
    onCopy: () -> Unit,
    onTranslate: () -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteOrClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(SelectionMenuPage.MAIN) }

    // Dynamic sizing estimates for clamp bounds
    val estimatedPillWidthPx = 220f * density
    val estimatedPillHeightPx = 48f * density

    // Center horizontally above selection, clamped to screen margins
    val selCenterX = (selectionBounds.left + selectionBounds.right) / 2f
    val targetX = (selCenterX - (estimatedPillWidthPx / 2f))
        .coerceIn(16f * density, (screenWidth - estimatedPillWidthPx - 16f * density).coerceAtLeast(16f * density))

    // Position vertically above the selected text; fallback to below if clipped by topBar
    val marginPx = 12f * density
    val aboveY = selectionBounds.top - estimatedPillHeightPx - marginPx
    val minTopY = topBarHeightPx + (8f * density)
    val targetY = if (aboveY >= minTopY) {
        aboveY
    } else {
        selectionBounds.bottom + marginPx
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { width -> width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "SelectionMenuPageTransition"
        ) { page ->
            when (page) {
                SelectionMenuPage.MAIN -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Highlight swatch button (opens COLORS in-place)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClickLabel = stringResource(R.string.highlight)
                                ) {
                                    currentPage = SelectionMenuPage.COLORS
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(PdfTextSelectionManager.COLOR_YELLOW))
                                    .border(1.dp, Color(0x33000000), CircleShape)
                            )
                        }

                        // Copy
                        TextButton(
                            onClick = onCopy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.copy),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Translate
                        IconButton(
                            onClick = onTranslate,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_translate),
                                contentDescription = stringResource(R.string.translate),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Overflow (opens MORE in-place)
                        IconButton(
                            onClick = { currentPage = SelectionMenuPage.MORE },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                SelectionMenuPage.COLORS -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Return arrow back to MAIN without clearing active selection
                        IconButton(
                            onClick = { currentPage = SelectionMenuPage.MAIN },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 4 Color swatches (22.dp - 24.dp)
                        val colors = listOf(
                            PdfTextSelectionManager.COLOR_YELLOW,
                            PdfTextSelectionManager.COLOR_GREEN,
                            PdfTextSelectionManager.COLOR_PINK,
                            PdfTextSelectionManager.COLOR_BLUE
                        )
                        for (colorArgb in colors) {
                            Box(
                                modifier = Modifier
                                    .size(23.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorArgb))
                                    .border(1.dp, Color(0x33000000), CircleShape)
                                    .clickable {
                                        onApplyHighlight(colorArgb)
                                    }
                            )
                        }

                        // Subtle 1.dp divider
                        Box(
                            modifier = Modifier
                                .size(width = 1.dp, height = 20.dp)
                                .background(Color.White.copy(alpha = 0.14f))
                        )

                        // Refined trash icon (18.dp - 20.dp inside a clean 32.dp - 36.dp touch target)
                        IconButton(
                            onClick = onDeleteOrClear,
                            modifier = Modifier
                                .size(34.dp)
                                .padding(horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete_annotation),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                SelectionMenuPage.MORE -> {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Return arrow back to MAIN
                        IconButton(
                            onClick = { currentPage = SelectionMenuPage.MAIN },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Select All
                        TextButton(
                            onClick = onSelectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.select_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Share
                        TextButton(
                            onClick = onShare,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.share),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
