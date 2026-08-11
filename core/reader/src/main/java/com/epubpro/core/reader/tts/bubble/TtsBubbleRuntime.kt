package com.epubpro.core.reader.tts.bubble

import android.content.Context
import com.epubpro.core.storage.TtsBubblePreferences
import com.epubpro.core.storage.TtsBubblePreferencesManager
import com.epubpro.core.storage.TtsBubbleSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns the non-playback lifecycle of the overlay. TtsService remains the command/state owner and
 * supplies an immutable [TtsBubbleUiModel].
 */
internal class TtsBubbleRuntime(
    context: Context,
    scope: CoroutineScope,
    private val preferencesManager: TtsBubblePreferencesManager,
    onCommand: (TtsBubbleCommand) -> Unit,
    private val onAvailabilityChanged: (Boolean) -> Unit,
    private val onOverlayUnavailable: (Throwable) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val appVisibilityTracker = AppVisibilityTracker()
    private val deviceLockTracker = DeviceLockTracker(appContext)
    private val overlayPermissionTracker = TtsOverlayPermissionTracker(appContext)
    private var expansionRequested = false
    private var overlayUnavailable = false
    private var currentModel = TtsBubbleUiModel()
    private val availabilityObserver =
        TtsBubbleAvailabilityObserver(overlayPermissionTracker.granted.value)

    private val overlayController = TtsBubbleOverlayController(
        context = appContext,
        initialPosition = preferencesManager.getPreferences().toPosition(),
        callbacks = TtsBubbleOverlayCallbacks(
            onCommand = onCommand,
            onExpansionChangeRequested = { expanded ->
                expansionRequested = expanded
                render()
            },
            onTemporarilyHideRequested = {
                expansionRequested = false
                preferencesManager.setHiddenForCurrentSession(true)
                render()
            },
            onPositionChanged = { position ->
                preferencesManager.savePosition(
                    side = when (position.edge) {
                        TtsBubbleHorizontalEdge.LEFT -> TtsBubbleSide.LEFT
                        TtsBubbleHorizontalEdge.RIGHT -> TtsBubbleSide.RIGHT
                    },
                    normalizedY = position.normalizedY
                )
            },
            onOverlayUnavailable = { error ->
                overlayUnavailable = true
                expansionRequested = false
                render()
                onOverlayUnavailable(error)
            }
        )
    )

    private val environmentJob: Job = scope.launch {
        combine(
            preferencesManager.preferencesFlow,
            appVisibilityTracker.appVisible,
            deviceLockTracker.deviceLocked,
            overlayPermissionTracker.granted
        ) { preferences, _, _, _ -> preferences }
            .collect { preferences ->
                overlayController.onPreferencesChanged(preferences.toPosition())
                if (!preferences.enabled) {
                    expansionRequested = false
                    overlayUnavailable = false
                }
                render(preferences)
                val permissionGranted = overlayPermissionTracker.granted.value
                if (availabilityObserver.update(permissionGranted)) {
                    onAvailabilityChanged(permissionGranted)
                }
            }
    }

    fun updateModel(model: TtsBubbleUiModel) {
        currentModel = model
        render()
    }

    fun refreshEnvironment() {
        deviceLockTracker.refresh()
        overlayPermissionTracker.refresh()
        if (overlayPermissionTracker.granted.value) {
            overlayUnavailable = false
        }
        render()
    }

    fun isBubbleAvailable(): Boolean {
        val preferences = preferencesManager.getPreferences()
        return preferences.enabled &&
            overlayPermissionTracker.granted.value &&
            !overlayUnavailable
    }

    override fun close() {
        environmentJob.cancel()
        overlayController.release()
        appVisibilityTracker.release()
        deviceLockTracker.release()
        overlayPermissionTracker.close()
    }

    private fun render(
        preferences: TtsBubblePreferences = preferencesManager.getPreferences()
    ) {
        val permissionGranted = overlayPermissionTracker.granted.value && !overlayUnavailable
        val state = TtsBubbleReducer.reduce(
            TtsBubbleEnvironment(
                enabled = preferences.enabled,
                overlayPermissionGranted = permissionGranted,
                appVisible = appVisibilityTracker.appVisible.value,
                deviceLocked = deviceLockTracker.deviceLocked.value,
                hiddenForCurrentSession = preferences.hiddenForCurrentSession,
                expansionRequested = expansionRequested
            )
        )
        overlayController.update(state = state, model = currentModel)
    }

    private fun TtsBubblePreferences.toPosition(): TtsBubblePosition = TtsBubblePosition(
        edge = when (side) {
            TtsBubbleSide.LEFT -> TtsBubbleHorizontalEdge.LEFT
            TtsBubbleSide.RIGHT -> TtsBubbleHorizontalEdge.RIGHT
        },
        normalizedY = normalizedY
    )
}
