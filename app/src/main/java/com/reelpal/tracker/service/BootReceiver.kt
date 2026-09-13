package com.reelpal.tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The AccessibilityService restarts automatically once the user has
 * enabled it in Settings — Android manages that lifecycle, not us. This
 * receiver exists as a hook for any future post-boot setup you might want
 * (e.g. re-showing an onboarding nudge if a permission got revoked).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty for now.
    }
}
