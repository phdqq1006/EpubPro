package com.epubpro.core.reader.tts.bubble

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks whether any EpubPro activity is visible without retaining an Activity instance. */
class AppVisibilityTracker internal constructor(
    private val processLifecycle: Lifecycle
) : DefaultLifecycleObserver, AutoCloseable {
    constructor() : this(ProcessLifecycleOwner.get().lifecycle)
    private val mutableAppVisible = MutableStateFlow(
        processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    )
    private var observing = false

    val appVisible: StateFlow<Boolean> = mutableAppVisible.asStateFlow()

    init {
        start()
    }

    fun start() {
        if (observing) return
        observing = true
        mutableAppVisible.value = processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        processLifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        mutableAppVisible.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        mutableAppVisible.value = false
    }

    fun release() {
        if (!observing) return
        observing = false
        processLifecycle.removeObserver(this)
    }

    override fun close() = release()
}
