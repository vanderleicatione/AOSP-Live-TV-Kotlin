package com.android.tv.ui.hideable

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
import androidx.annotation.UiThread
import java.lang.ref.WeakReference

/**
 * Blendet nach einer Zeit automatisch aus – außer bei Screenreader (Touch-Erkundung). Die TCL hat immer
 * Bedienungshilfe-Dienste an (tvQuickActions, Limite), daher reicht isEnabled nicht.
 */
@UiThread
class AutoHideScheduler internal constructor(
    runnable: Runnable,
    private val accessibilityManager: AccessibilityManager,
    looper: Looper,
) : AccessibilityStateChangeListener {

    constructor(context: Context, runnable: Runnable) :
        this(runnable, context.getSystemService(AccessibilityManager::class.java), Looper.getMainLooper())

    // Starke Referenz hier, der Handler hält nur eine schwache.
    private val runnable = runnable
    private val weakRunnable = WeakReference(runnable)
    private var allowAutoHide = !accessibilityManager.isTouchExplorationEnabled
    private val handler = Handler(looper) { msg ->
        if (msg.what == MSG_HIDE && allowAutoHide) weakRunnable.get()?.run()
        true
    }

    fun cancel() = handler.removeMessages(MSG_HIDE)

    fun schedule(delayMs: Long) {
        cancel()
        if (allowAutoHide) handler.sendEmptyMessageDelayed(MSG_HIDE, delayMs)
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        allowAutoHide = !accessibilityManager.isTouchExplorationEnabled
    }

    val isScheduled: Boolean get() = handler.hasMessages(MSG_HIDE)

    companion object {
        private const val MSG_HIDE = 1
    }
}
