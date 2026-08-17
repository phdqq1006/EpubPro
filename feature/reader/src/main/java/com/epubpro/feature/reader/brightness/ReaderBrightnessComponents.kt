package com.epubpro.feature.reader.brightness

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.EXTRA_DIM_THRESHOLD

/**
 * Tìm kiếm [Activity] từ [Context] thông qua chuỗi phân giải [ContextWrapper].
 *
 * @receiver [Context] hiện tại của ứng dụng.
 * @return [Activity] tương ứng nếu tồn tại, ngược lại trả về `null`.
 */
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * Quản lý can thiệp độ sáng đèn nền phần cứng trên Activity Window và an toàn vòng đời (Lifecycle).
 *
 * - Effect ổn định theo [window] và [lifecycleOwner], không bị recreate/dispose theo từng frame thay đổi độ sáng.
 * - Sử dụng [rememberUpdatedState] và [SideEffect] để cập nhật độ sáng tức thì khi đang ở trạng thái `RESUMED`.
 * - Khi Composable bị hủy (người dùng thoát khỏi màn hình đọc): trả `screenBrightness` về mặc định hệ thống.
 * - Khi ứng dụng chuyển sang nền (`ON_PAUSE`): tạm thời trả về độ sáng hệ thống.
 * - Khi ứng dụng quay lại (`ON_RESUME`): áp dụng lại độ sáng đọc sách mới nhất.
 *
 * @param activity [Activity] hiện tại sở hữu Window cần điều chỉnh.
 * @param hardwareBrightness Mức độ sáng đèn nền phần cứng (từ 0.01f đến 1.0f).
 */
@Composable
fun BrightnessWindowEffect(
    activity: Activity?,
    hardwareBrightness: Float
) {
    val window = activity?.window ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBrightness by rememberUpdatedState(hardwareBrightness)

    SideEffect {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val params = window.attributes
            if (params.screenBrightness != hardwareBrightness) {
                params.screenBrightness = hardwareBrightness
                window.attributes = params
            }
        }
    }

    DisposableEffect(window, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val params = window.attributes
                    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = params
                }
                Lifecycle.Event.ON_RESUME -> {
                    val params = window.attributes
                    params.screenBrightness = currentBrightness
                    window.attributes = params
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val params = window.attributes
            params.screenBrightness = currentBrightness
            window.attributes = params
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params
        }
    }
}

/**
 * Cảm biến nhận diện cử chỉ vuốt dọc ở mép trái màn hình để điều chỉnh độ sáng nhanh chóng.
 *
 * @param onBrightnessDelta Callback phát ra độ chênh lệch độ sáng (delta) trong dải 0.0f..1.0f tương ứng với khoảng cách vuốt.
 * @param onDragEnd Callback được gọi khi kết thúc cử chỉ vuốt để tiến hành lưu cấu hình.
 * @param modifier [Modifier] tùy chỉnh bố cục cho dải cảm ứng.
 */
@Composable
fun BrightnessEdgeSensor(
    onBrightnessDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnBrightnessDelta by rememberUpdatedState(onBrightnessDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Vuốt lên (dragAmount < 0) -> tăng sáng; Vuốt xuống (dragAmount > 0) -> giảm sáng
                        val screenHeight = size.height.toFloat().takeIf { it > 0f } ?: 1000f
                        val delta = -dragAmount / screenHeight
                        currentOnBrightnessDelta(delta)
                    }
                )
            }
    )
}

/**
 * Bảng thông báo nổi (HUD) hiển thị phần trăm độ sáng và chế độ siêu tối khi người dùng thao tác vuốt.
 *
 * @param visible Trạng thái hiển thị của bảng HUD.
 * @param brightness Mức độ sáng hiện tại (từ 0.0f đến 1.0f).
 * @param modifier [Modifier] tùy biến vị cục hiển thị của HUD.
 */
@Composable
fun BrightnessHud(
    visible: Boolean,
    brightness: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtAlpha(0.88f),
            tonalElevation = 8.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                val isExtraDim = brightness < EXTRA_DIM_THRESHOLD
                val icon = when {
                    isExtraDim -> Icons.Default.Nightlight
                    brightness < 0.6f -> Icons.Default.BrightnessMedium
                    else -> Icons.Default.BrightnessHigh
                }
                val iconTint = if (isExtraDim) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.reader_brightness),
                    tint = iconTint,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val percent = (brightness.coerceIn(0.0f, 1.0f) * 100).toInt()
                Text(
                    text = stringResource(R.string.reader_brightness_hud_format, percent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isExtraDim) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reader_brightness_extra_dim_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lớp phủ màu đen mờ (Extra Dim Overlay) phía trên giao diện để làm tối màn hình vượt mức tối thiểu phần cứng.
 *
 * @param alpha Độ mờ của lớp phủ đen (từ 0.0f đến 0.75f). Khi bằng 0.0f, không dựng layout.
 * @param modifier [Modifier] tùy chỉnh bố cục của lớp phủ.
 */
@Composable
fun ExtraDimOverlay(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha.coerceIn(0.0f, 1.0f)))
        )
    }
}

/**
 * Hàm mở rộng tính toán màu bề mặt với alpha linh hoạt cho MaterialTheme.
 */
@Composable
private fun androidx.compose.material3.ColorScheme.surfaceColorAtAlpha(alpha: Float): Color {
    return surface.copy(alpha = alpha)
}
