package com.reelpal.tracker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.reelpal.tracker.data.ReelRepository
import com.reelpal.tracker.util.CounterBus
import com.reelpal.tracker.util.MonitoredApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Core detection engine. This is the only component that can see what's
 * happening inside Instagram/YouTube/Snapchat/TikTok's own screens - that
 * visibility is exactly what the Accessibility permission grants, which is
 * why Android requires the user to enable it manually in Settings rather
 * than via a normal runtime prompt.
 *
 * Detection heuristic: a "reel" is counted on either a scroll event
 * (TYPE_VIEW_SCROLLED) or a content-change event (TYPE_WINDOW_CONTENT_CHANGED)
 * inside a monitored app, debounced so one physical swipe doesn't fire
 * multiple counts. Different apps expose swipe gestures differently -
 * Instagram tends to fire real scroll events, while YouTube Shorts often
 * only fires content-change events for its swipe transitions. Watching both
 * event types keeps this working across apps without per-app-version
 * tuning, at the cost of also picking up other UI changes in the same app.
 * To narrow this to the reels feed specifically, inspect event.source's
 * viewIdResourceName / className against MonitoredApps.feedContainerIdHints
 * for the app version you're targeting, and add a filter in
 * handlePossibleReelScroll.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: ReelRepository

    private var lastCountedAt = 0L
    private val debounceMs = 350L

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = ReelRepository.getInstance(applicationContext)
        Log.i(TAG, "ReelPal accessibility service connected")

        // Keep the in-memory CounterBus (used by the overlay bubble) in sync
        // with whatever is actually persisted, including on service restart.
        scope.launch {
            repository.observeToday().collect { rows ->
                rows.forEach { CounterBus.setCount(it.packageName, it.count) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in MonitoredApps.packageNames()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> startOverlayIfEnabled(pkg)
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handlePossibleReelScroll(pkg, event)
        }
    }

    private fun handlePossibleReelScroll(pkg: String, event: AccessibilityEvent) {
        val now = SystemClock.elapsedRealtime()

        // Debounced so one physical swipe = one count, regardless of how
        // many raw events it generates.
        if (now - lastCountedAt > debounceMs) {
            lastCountedAt = now
            scope.launch {
                if (!repository.isAppMonitored(pkg)) return@launch
                repository.incrementCount(pkg)
            }
        }
    }

    private fun startOverlayIfEnabled(pkg: String) {
        if (!repository.isAppMonitored(pkg)) return
        if (!repository.isOverlayEnabled()) return
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
            putExtra(OverlayService.EXTRA_PACKAGE, pkg)
        }
        startService(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    companion object {
        private const val TAG = "ReelAccessibility"
    }
}
