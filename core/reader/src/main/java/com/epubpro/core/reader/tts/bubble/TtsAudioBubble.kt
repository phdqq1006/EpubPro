package com.epubpro.core.reader.tts.bubble

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.epubpro.core.designsystem.theme.EpubProTheme
import com.epubpro.core.reader.R

internal class TtsBubbleDragCallbacks(
    val onStart: () -> Unit,
    val onDragBy: (deltaX: Float, deltaY: Float) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit
)

@Composable
internal fun TtsBubbleTheme(content: @Composable () -> Unit) {
    EpubProTheme(
        darkTheme = isSystemInDarkTheme(),
        content = content
    )
}

@Composable
internal fun TtsAudioBubble(
    state: TtsBubbleState,
    model: TtsBubbleUiModel,
    onCommand: (TtsBubbleCommand) -> Unit,
    onExpansionChangeRequested: (Boolean) -> Unit,
    dragCallbacks: TtsBubbleDragCallbacks
) {
    when (state) {
        TtsBubbleState.COLLAPSED -> CollapsedBubble(
            model = model,
            onExpand = { onExpansionChangeRequested(true) },
            dragCallbacks = dragCallbacks
        )
        TtsBubbleState.EXPANDED -> ExpandedBubble(
            model = model,
            onCommand = onCommand,
            onCollapse = { onExpansionChangeRequested(false) }
        )
        TtsBubbleState.DISABLED,
        TtsBubbleState.HIDDEN -> Unit
    }
}

@Composable
private fun CollapsedBubble(
    model: TtsBubbleUiModel,
    onExpand: () -> Unit,
    dragCallbacks: TtsBubbleDragCallbacks
) {
    val bubbleDescription = stringResource(R.string.tts_bubble_open_controls)
    Surface(
        modifier = Modifier
            .size(BUBBLE_SIZE)
            .semantics {
                role = Role.Button
                contentDescription = bubbleDescription
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragCallbacks.onStart() },
                    onDragEnd = dragCallbacks.onEnd,
                    onDragCancel = dragCallbacks.onCancel
                ) { change, dragAmount ->
                    change.consume()
                    dragCallbacks.onDragBy(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onExpand),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = if (
                    model.playbackStatus == TtsBubblePlaybackStatus.IDLE ||
                    model.playbackStatus == TtsBubblePlaybackStatus.COMPLETED
                ) {
                    0f
                } else {
                    model.normalizedProgress
                }
            )
            BubbleCover(model = model, modifier = Modifier.size(52.dp))
            PlaybackStatusBadge(
                status = model.playbackStatus,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Float) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(BUBBLE_SIZE)) {
        val strokeWidth = 4.dp.toPx()
        val radiusInset = strokeWidth / 2f
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(radiusInset, radiusInset),
            size = size.copy(
                width = size.width - strokeWidth,
                height = size.height - strokeWidth
            ),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = Offset(radiusInset, radiusInset),
                size = size.copy(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth
                ),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun BubbleCover(model: TtsBubbleUiModel, modifier: Modifier = Modifier) {
    val coverDescription = stringResource(R.string.tts_bubble_book_cover)
    val cover = model.coverBitmap
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null && !cover.isRecycled) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = coverDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlaybackStatusBadge(
    status: TtsBubblePlaybackStatus,
    modifier: Modifier = Modifier
) {
    val badgeDescription = when (status) {
        TtsBubblePlaybackStatus.PLAYING -> stringResource(R.string.tts_bubble_playing)
        TtsBubblePlaybackStatus.PREPARING -> stringResource(R.string.tts_bubble_preparing)
        TtsBubblePlaybackStatus.PAUSED -> stringResource(R.string.tts_bubble_paused)
        TtsBubblePlaybackStatus.ERROR -> stringResource(R.string.tts_bubble_error)
        TtsBubblePlaybackStatus.IDLE,
        TtsBubblePlaybackStatus.COMPLETED -> stringResource(R.string.tts_bubble_idle)
    }
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.semantics { contentDescription = badgeDescription },
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                TtsBubblePlaybackStatus.PREPARING -> CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                TtsBubblePlaybackStatus.PLAYING -> Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                TtsBubblePlaybackStatus.ERROR -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                TtsBubblePlaybackStatus.IDLE,
                TtsBubblePlaybackStatus.PAUSED,
                TtsBubblePlaybackStatus.COMPLETED -> Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ExpandedBubble(
    model: TtsBubbleUiModel,
    onCommand: (TtsBubbleCommand) -> Unit,
    onCollapse: () -> Unit
) {
    val noBookTitle = stringResource(R.string.tts_bubble_no_book)
    val noSessionText = stringResource(R.string.tts_bubble_no_session)
    val preparingText = stringResource(R.string.tts_bubble_preparing)
    val displayText = when {
        model.playbackStatus == TtsBubblePlaybackStatus.ERROR && !model.errorMessage.isNullOrBlank() -> {
            model.errorMessage
        }
        model.playbackStatus == TtsBubblePlaybackStatus.PREPARING && model.currentText.isBlank() -> {
            preparingText
        }
        model.currentText.isNotBlank() -> model.currentText
        else -> noSessionText
    }
    Card(
        modifier = Modifier
            .widthIn(max = EXPANDED_MAX_WIDTH)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BubbleCover(model = model, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.bookTitle.ifBlank { noBookTitle },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.playbackStatus == TtsBubblePlaybackStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = stringResource(R.string.tts_bubble_collapse)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (model.playbackStatus == TtsBubblePlaybackStatus.PREPARING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { model.normalizedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BubbleControlButton(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.tts_bubble_previous),
                    enabled = model.hasPlaybackSnapshot,
                    onClick = { onCommand(TtsBubbleCommand.Previous) }
                )
                BubbleControlButton(
                    imageVector = if (
                        model.playbackStatus == TtsBubblePlaybackStatus.PLAYING ||
                        model.playbackStatus == TtsBubblePlaybackStatus.PREPARING
                    ) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (
                        model.playbackStatus == TtsBubblePlaybackStatus.PLAYING ||
                        model.playbackStatus == TtsBubblePlaybackStatus.PREPARING
                    ) {
                        stringResource(R.string.tts_bubble_pause)
                    } else {
                        stringResource(R.string.tts_bubble_play)
                    },
                    enabled = model.hasPlaybackSnapshot,
                    onClick = { onCommand(TtsBubbleCommand.TogglePlayPause) }
                )
                BubbleControlButton(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.tts_bubble_next),
                    enabled = model.hasPlaybackSnapshot,
                    onClick = { onCommand(TtsBubbleCommand.Next) }
                )
                BubbleControlButton(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.tts_bubble_stop),
                    enabled = model.hasPlaybackSnapshot,
                    onClick = { onCommand(TtsBubbleCommand.Stop) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
                onClick = { onCommand(TtsBubbleCommand.OpenBook) },
                enabled = model.canOpenBook,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.tts_bubble_open_book))
            }
        }
    }
}

@Composable
private fun BubbleControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
internal fun TtsBubbleHideTarget(active: Boolean) {
    val hideTargetDescription = stringResource(R.string.tts_bubble_hide_target)
    val backgroundColor = if (active) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
    }
    val foregroundColor = if (active) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .size(HIDE_TARGET_SIZE)
            .semantics { contentDescription = hideTargetDescription },
        shape = CircleShape,
        color = backgroundColor,
        shadowElevation = 10.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = foregroundColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpandedBubblePreview() {
    TtsBubbleTheme {
        TtsAudioBubble(
            state = TtsBubbleState.EXPANDED,
            model = TtsBubbleUiModel(
                playbackStatus = TtsBubblePlaybackStatus.PLAYING,
                bookTitle = "Đấu La Đại Lục",
                currentText = "Đường Gia Tam Thiếu",
                progress = 0.42f,
                hasPlaybackSnapshot = true,
                canOpenBook = true
            ),
            onCommand = {},
            onExpansionChangeRequested = {},
            dragCallbacks = TtsBubbleDragCallbacks({}, { _, _ -> }, {}, {})
        )
    }
}

internal val BUBBLE_SIZE = 64.dp
internal val HIDE_TARGET_SIZE = 80.dp
private val EXPANDED_MAX_WIDTH = 320.dp
