package com.epubpro.core.reader.tts.bubble

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * Renders the TTS overlay and translates gestures into callbacks. Playback and visibility policy
 * remain owned by TtsService; this controller never starts a service or mutates preferences.
 */
class TtsBubbleOverlayController(
    context: Context,
    private val callbacks: TtsBubbleOverlayCallbacks,
    initialPosition: TtsBubblePosition = TtsBubblePosition.Default
) : ComponentCallbacks, AutoCloseable {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = appContext.resources.displayMetrics.density
    private val renderState = MutableStateFlow(
        BubbleRenderState(TtsBubbleState.DISABLED, TtsBubbleUiModel())
    )
    private val hideTargetActive = MutableStateFlow(false)

    private var currentState = TtsBubbleState.DISABLED
    private var currentModel = TtsBubbleUiModel()
    private var storedPosition = initialPosition.sanitized()
    private var currentCoordinates = TtsBubbleCoordinates(0, 0)
    private var dragging = false
    private var overlayFailureLatched = false
    @Volatile
    private var releaseRequested = false

    private var bubbleView: ComposeView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var bubbleLifecycleOwner: BubbleOverlayLifecycleOwner? = null

    private var hideTargetView: ComposeView? = null
    private var hideTargetLayoutParams: WindowManager.LayoutParams? = null
    private var hideTargetLifecycleOwner: BubbleOverlayLifecycleOwner? = null

    init {
        appContext.registerComponentCallbacks(this)
    }

    fun update(state: TtsBubbleState, model: TtsBubbleUiModel) {
        runOnMain {
            updateInternal(state, model)
        }
    }

    /** Applies a newly loaded/saved edge position without taking preference ownership. */
    fun onPreferencesChanged(position: TtsBubblePosition) {
        runOnMain {
            storedPosition = position.sanitized()
            overlayFailureLatched = false
            if (currentState.isVisible && !dragging) {
                applyLayoutForState(currentState)
            }
        }
    }

    fun release() {
        if (releaseRequested) return
        releaseRequested = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseOnMain()
        } else {
            mainHandler.post(::releaseOnMain)
        }
    }

    override fun close() = release()

    override fun onConfigurationChanged(newConfig: Configuration) {
        runOnMain {
            dragging = false
            removeHideTarget()
            if (currentState.isVisible) {
                applyLayoutForState(currentState)
            }
        }
    }

    override fun onLowMemory() = Unit

    private fun updateInternal(state: TtsBubbleState, model: TtsBubbleUiModel) {
        if (releaseRequested) return
        val previousState = currentState
        currentState = state
        currentModel = model
        renderState.value = BubbleRenderState(state = state, model = model)

        if (!state.isVisible) {
            overlayFailureLatched = false
            dragging = false
            removeHideTarget()
            detachBubble()
            return
        }
        if (overlayFailureLatched) return

        if (bubbleView == null) {
            attachBubble()
        } else if (previousState != state) {
            dragging = false
            removeHideTarget()
            applyLayoutForState(state)
        }
    }

    private fun attachBubble() {
        if (!Settings.canDrawOverlays(appContext)) {
            handleOverlayFailure(SecurityException("Overlay permission is not granted"))
            return
        }

        val owner = BubbleOverlayLifecycleOwner()
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by renderState.collectAsState()
                TtsBubbleTheme {
                    TtsAudioBubble(
                        state = state.state,
                        model = state.model,
                        onCommand = callbacks.onCommand,
                        onExpansionChangeRequested = callbacks.onExpansionChangeRequested,
                        dragCallbacks = TtsBubbleDragCallbacks(
                            onStart = ::startDrag,
                            onDragBy = ::dragBy,
                            onEnd = { finishDrag(allowTemporaryHide = true) },
                            onCancel = { finishDrag(allowTemporaryHide = false) }
                        )
                    )
                }
            }
            setOnTouchListener { _, event ->
                if (
                    event.action == MotionEvent.ACTION_OUTSIDE &&
                    currentState == TtsBubbleState.EXPANDED
                ) {
                    callbacks.onExpansionChangeRequested(false)
                }
                false
            }
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (
                    currentState == TtsBubbleState.EXPANDED &&
                    bottom > 0 &&
                    bottom != oldBottom
                ) {
                    applyExpandedLayout(measuredHeightPx = bottom)
                }
            }
        }
        val params = createBubbleLayoutParams(currentState)
        bubbleView = view
        bubbleLayoutParams = params
        bubbleLifecycleOwner = owner

        try {
            windowManager.addView(view, params)
            owner.onAttached()
        } catch (error: RuntimeException) {
            bubbleView = null
            bubbleLayoutParams = null
            bubbleLifecycleOwner = null
            runCatching { windowManager.removeViewImmediate(view) }
            runCatching { owner.onDetached() }
            runCatching { view.disposeComposition() }
            handleOverlayFailure(error)
        }
    }

    private fun createBubbleLayoutParams(state: TtsBubbleState): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            if (state == TtsBubbleState.EXPANDED) expandedWidthPx() else bubbleSizePx,
            if (state == TtsBubbleState.EXPANDED) {
                WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                bubbleSizePx
            },
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flagsFor(state),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = WINDOW_TITLE
        }
        val coordinates = if (state == TtsBubbleState.EXPANDED) {
            expandedCoordinates(estimatedExpandedHeightPx)
        } else {
            collapsedCoordinates()
        }
        currentCoordinates = coordinates
        params.x = coordinates.x
        params.y = coordinates.y
        return params
    }

    private fun applyLayoutForState(state: TtsBubbleState) {
        val params = bubbleLayoutParams ?: return
        if (state == TtsBubbleState.EXPANDED) {
            params.width = expandedWidthPx()
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = flagsFor(state)
            val height = bubbleView?.height?.takeIf { it > 0 } ?: estimatedExpandedHeightPx
            val coordinates = expandedCoordinates(height)
            currentCoordinates = coordinates
            params.x = coordinates.x
            params.y = coordinates.y
        } else {
            params.width = bubbleSizePx
            params.height = bubbleSizePx
            params.flags = flagsFor(state)
            val coordinates = collapsedCoordinates()
            currentCoordinates = coordinates
            params.x = coordinates.x
            params.y = coordinates.y
        }
        updateBubbleLayout(params)
    }

    private fun applyExpandedLayout(measuredHeightPx: Int) {
        val params = bubbleLayoutParams ?: return
        if (currentState != TtsBubbleState.EXPANDED || dragging) return
        val coordinates = expandedCoordinates(measuredHeightPx)
        if (params.x == coordinates.x && params.y == coordinates.y) return
        currentCoordinates = coordinates
        params.x = coordinates.x
        params.y = coordinates.y
        updateBubbleLayout(params)
    }

    private fun updateBubbleLayout(params: WindowManager.LayoutParams) {
        val view = bubbleView ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (error: RuntimeException) {
            detachBubble()
            handleOverlayFailure(error)
        }
    }

    private fun startDrag() {
        if (currentState != TtsBubbleState.COLLAPSED || dragging) return
        dragging = true
        showHideTarget()
    }

    private fun dragBy(deltaX: Float, deltaY: Float) {
        if (!dragging || currentState != TtsBubbleState.COLLAPSED) return
        val next = TtsBubbleCoordinates(
            x = currentCoordinates.x + deltaX.roundToInt(),
            y = currentCoordinates.y + deltaY.roundToInt()
        )
        currentCoordinates = TtsBubblePositionCalculator.clampCoordinates(
            coordinates = next,
            viewport = currentViewport(),
            bubbleSize = collapsedBubbleSize,
            edgeMarginPx = edgeMarginPx
        )
        bubbleLayoutParams?.let { params ->
            params.x = currentCoordinates.x
            params.y = currentCoordinates.y
            updateBubbleLayout(params)
        }
        updateHideTargetActive()
    }

    private fun finishDrag(allowTemporaryHide: Boolean) {
        if (!dragging) return
        dragging = false
        val shouldHide = allowTemporaryHide && hideTargetActive.value
        removeHideTarget()
        if (shouldHide) {
            callbacks.onTemporarilyHideRequested()
            return
        }

        val placement = TtsBubblePositionCalculator.snap(
            coordinates = currentCoordinates,
            viewport = currentViewport(),
            bubbleSize = collapsedBubbleSize,
            edgeMarginPx = edgeMarginPx
        )
        currentCoordinates = placement.coordinates
        storedPosition = placement.position
        bubbleLayoutParams?.let { params ->
            params.x = placement.coordinates.x
            params.y = placement.coordinates.y
            updateBubbleLayout(params)
        }
        callbacks.onPositionChanged(placement.position)
    }

    private fun showHideTarget() {
        if (hideTargetView != null) return
        val owner = BubbleOverlayLifecycleOwner()
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val active by hideTargetActive.collectAsState()
                TtsBubbleTheme {
                    TtsBubbleHideTarget(active = active)
                }
            }
        }
        val zone = hideTargetRect()
        val params = WindowManager.LayoutParams(
            hideTargetSizePx,
            hideTargetSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = zone.left
            y = zone.top
            title = HIDE_TARGET_WINDOW_TITLE
        }
        hideTargetView = view
        hideTargetLayoutParams = params
        hideTargetLifecycleOwner = owner
        hideTargetActive.value = false
        try {
            windowManager.addView(view, params)
            owner.onAttached()
        } catch (error: RuntimeException) {
            hideTargetView = null
            hideTargetLayoutParams = null
            hideTargetLifecycleOwner = null
            runCatching { windowManager.removeViewImmediate(view) }
            runCatching { owner.onDetached() }
            runCatching { view.disposeComposition() }
            runCatching { callbacks.onOverlayUnavailable(error) }
        }
    }

    private fun updateHideTargetActive() {
        if (hideTargetView == null) return
        hideTargetActive.value = TtsBubblePositionCalculator.isInsideHideZone(
            coordinates = currentCoordinates,
            bubbleSize = collapsedBubbleSize,
            hideZone = hideTargetRect()
        )
    }

    private fun removeHideTarget() {
        hideTargetActive.value = false
        val view = hideTargetView ?: return
        val owner = hideTargetLifecycleOwner
        hideTargetView = null
        hideTargetLayoutParams = null
        hideTargetLifecycleOwner = null
        runCatching { owner?.onDetached() }
        runCatching { view.disposeComposition() }
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun detachBubble() {
        val view = bubbleView ?: return
        val owner = bubbleLifecycleOwner
        bubbleView = null
        bubbleLayoutParams = null
        bubbleLifecycleOwner = null
        runCatching { owner?.onDetached() }
        runCatching { view.disposeComposition() }
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun handleOverlayFailure(error: Throwable) {
        overlayFailureLatched = true
        runCatching { callbacks.onOverlayUnavailable(error) }
    }

    private fun releaseOnMain() {
        dragging = false
        removeHideTarget()
        detachBubble()
        runCatching { appContext.unregisterComponentCallbacks(this) }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun collapsedCoordinates(): TtsBubbleCoordinates =
        TtsBubblePositionCalculator.coordinatesFor(
            position = storedPosition,
            viewport = currentViewport(),
            bubbleSize = collapsedBubbleSize,
            edgeMarginPx = edgeMarginPx
        )

    private fun expandedCoordinates(heightPx: Int): TtsBubbleCoordinates =
        TtsBubblePositionCalculator.coordinatesFor(
            position = storedPosition,
            viewport = currentViewport(),
            bubbleSize = TtsBubbleSize(expandedWidthPx(), heightPx.coerceAtLeast(1)),
            edgeMarginPx = expandedMarginPx
        )

    private fun hideTargetRect(): TtsBubbleRect {
        val viewport = currentViewport()
        val left = ((viewport.widthPx - hideTargetSizePx) / 2)
            .coerceAtLeast(viewport.insets.left)
        val top = (viewport.heightPx - viewport.insets.bottom - hideTargetBottomMarginPx -
            hideTargetSizePx).coerceAtLeast(viewport.insets.top)
        return TtsBubbleRect(
            left = left,
            top = top,
            right = left + hideTargetSizePx,
            bottom = top + hideTargetSizePx
        )
    }

    @Suppress("DEPRECATION")
    private fun currentViewport(): TtsBubbleViewport {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return TtsBubbleViewport(
                widthPx = metrics.bounds.width(),
                heightPx = metrics.bounds.height(),
                insets = TtsBubbleInsets(
                    left = insets.left,
                    top = insets.top,
                    right = insets.right,
                    bottom = insets.bottom
                )
            )
        }

        val realSize = Point()
        val usableSize = Point()
        windowManager.defaultDisplay.getRealSize(realSize)
        windowManager.defaultDisplay.getSize(usableSize)
        val statusBarHeight = systemDimensionPx("status_bar_height")
        val rightInset = (realSize.x - usableSize.x).coerceAtLeast(0)
        val bottomInset = (realSize.y - usableSize.y - statusBarHeight).coerceAtLeast(0)
        return TtsBubbleViewport(
            widthPx = realSize.x,
            heightPx = realSize.y,
            insets = TtsBubbleInsets(
                top = statusBarHeight,
                right = rightInset,
                bottom = bottomInset
            )
        )
    }

    private fun expandedWidthPx(): Int {
        val viewport = currentViewport()
        val available = (
            viewport.widthPx - viewport.insets.left - viewport.insets.right -
                (expandedMarginPx * 2)
            ).coerceAtLeast(1)
        return minOf(dpToPx(320), available)
    }

    private fun flagsFor(state: TtsBubbleState): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (state == TtsBubbleState.EXPANDED) {
            flags = flags or WindowManager.LayoutParams.FLAG_SECURE
        }
        return flags
    }

    private fun systemDimensionPx(name: String): Int {
        val resourceId = appContext.resources.getIdentifier(name, "dimen", "android")
        return if (resourceId == 0) 0 else appContext.resources.getDimensionPixelSize(resourceId)
    }

    private fun dpToPx(dp: Int): Int = (dp * density).roundToInt()

    private fun runOnMain(action: () -> Unit) {
        if (releaseRequested) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post {
                if (!releaseRequested) action()
            }
        }
    }

    private val collapsedBubbleSize: TtsBubbleSize
        get() = TtsBubbleSize(bubbleSizePx, bubbleSizePx)

    private val bubbleSizePx: Int
        get() = dpToPx(64)

    private val edgeMarginPx: Int
        get() = dpToPx(8)

    private val expandedMarginPx: Int
        get() = dpToPx(12)

    private val estimatedExpandedHeightPx: Int
        get() = dpToPx(246)

    private val hideTargetSizePx: Int
        get() = dpToPx(80)

    private val hideTargetBottomMarginPx: Int
        get() = dpToPx(20)

    private val TtsBubbleState.isVisible: Boolean
        get() = this == TtsBubbleState.COLLAPSED || this == TtsBubbleState.EXPANDED

    private fun TtsBubblePosition.sanitized(): TtsBubblePosition = copy(
        normalizedY = normalizedY.takeIf(Float::isFinite)?.coerceIn(0f, 1f)
            ?: TtsBubblePosition.DEFAULT_NORMALIZED_Y
    )

    private data class BubbleRenderState(
        val state: TtsBubbleState,
        val model: TtsBubbleUiModel
    )

    private companion object {
        const val WINDOW_TITLE = "EpubPro TTS audio bubble"
        const val HIDE_TARGET_WINDOW_TITLE = "EpubPro TTS bubble hide target"
    }
}
