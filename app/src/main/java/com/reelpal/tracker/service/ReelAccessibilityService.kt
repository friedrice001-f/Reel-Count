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
 * happening inside Instagram/YouTube/Snapchat/TikTok's own screens — that
 * visibility is exactly what the Accessibility permission grants, which is
 * why Android requires the user to enable it manually in Settings rather
 * than via a normal runtime prompt.
 *
 * Detection heuristic: a "reel" is counted when we see a large vertical
 * scroll (TYPE_VIEW_SCROLLED with a big deltaY) inside a monitored app,
 * debounced so one physical swipe doesn't fire multiple counts. This is
 * intentionally app-version-independent so it keeps working across app
 * updates, at the cost of also picking up other big vertical scrolls in
 * the same app (e.g. Instagram's main feed, not just Reels). To narrow
 * this to the reels feed specifically, inspect event.source's
 * viewIdResourceName / className against MonitoredApps.feedContainerIdHints
 * for the app version you're targeting, and add a filter below.
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
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handlePossibleReelScroll(pkg, event)
        }
    }

    private fun handlePossibleReelScroll(pkg: String, event: AccessibilityEvent) {
        val deltaY = kotlin.math.abs(event.scrollDeltaY)
        val isLargeVerticalScroll = deltaY > MIN_SCROLL_DELTA_PX
        val now = SystemClock.elapsedRealtime()

        if (isLargeVerticalScroll && now - lastCountedAt > debounceMs) {
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
        private const val MIN_SCROLL_DELTA_PX = 400
    }
}
