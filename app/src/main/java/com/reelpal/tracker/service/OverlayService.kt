package com.reelpal.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.reelpal.tracker.MainActivity
import com.reelpal.tracker.util.CounterBus
import com.reelpal.tracker.util.MonitoredApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Draws a small floating counter bubble on top of whichever monitored app
 * is currently in the foreground. Requires SYSTEM_ALERT_WINDOW ("display
 * over other apps"), granted via Settings.ACTION_MANAGE_OVERLAY_PERMISSION.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: TextView? = null
    private var collectJob: Job? = null
    private var currentPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                currentPackage = intent.getStringExtra(EXTRA_PACKAGE)
                showBubble()
            }
            ACTION_HIDE -> hideBubble()
        }
        return START_STICKY
    }

    private fun showBubble() {
        if (bubbleView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        val view = TextView(this).apply {
            setBackgroundColor(0xCC000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(28, 14, 28, 14)
            textSize = 13f
            text = "Reels: 0"
        }
        windowManager?.addView(view, params)
        bubbleView = view

        collectJob = CoroutineScope(Dispatchers.Main).launch {
            CounterBus.counts.collect { map ->
                val pkg = currentPackage ?: return@collect
                val count = map[pkg] ?: 0
                val label = MonitoredApps.byPackageName(pkg)?.displayName ?: pkg
                bubbleView?.text = "$label: $count"
            }
        }
    }

    private fun hideBubble() {
        collectJob?.cancel()
        bubbleView?.let { runCatching { windowManager?.removeView(it) } }
        bubbleView = null
    }

    override fun onDestroy() {
        hideBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "reelpal_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "ReelPal monitoring", NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ReelPal is tracking your reel scrolls")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SHOW = "com.reelpal.tracker.SHOW"
        const val ACTION_HIDE = "com.reelpal.tracker.HIDE"
        const val EXTRA_PACKAGE = "extra_package"
        private const val NOTIFICATION_ID = 42
    }
}
