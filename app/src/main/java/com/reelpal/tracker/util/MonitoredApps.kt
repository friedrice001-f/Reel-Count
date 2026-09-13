package com.reelpal.tracker.util

/**
 * One entry per app we watch. [feedContainerIdHints] are resource-id
 * fragments that (as of writing) tend to appear on the reels/shorts feed
 * container in each app. They are NOT reliable long-term — Instagram,
 * YouTube and Snapchat all change/obfuscate view IDs across releases.
 *
 * The detector in ReelAccessibilityService currently uses a
 * version-independent fallback (large vertical scroll while the app is
 * foreground) rather than relying on these hints, so the app keeps working
 * even when a hint goes stale. See README for how to tighten detection
 * using these hints once you've inspected the current view hierarchy
 * yourself (Android Studio's Layout Inspector, run against each app).
 */
data class MonitoredApp(
    val packageName: String,
    val displayName: String,
    val feedContainerIdHints: List<String> = emptyList()
)

object MonitoredApps {
    val ALL = listOf(
        MonitoredApp(
            packageName = "com.instagram.android",
            displayName = "Instagram Reels",
            feedContainerIdHints = listOf("clips_viewer", "reel_viewer_view_pager")
        ),
        MonitoredApp(
            packageName = "com.google.android.youtube",
            displayName = "YouTube Shorts",
            feedContainerIdHints = listOf("reel_recycler", "shorts_player")
        ),
        MonitoredApp(
            packageName = "com.snapchat.android",
            displayName = "Snapchat Spotlight",
            feedContainerIdHints = listOf("spotlight", "discover_feed")
        ),
        MonitoredApp(
            packageName = "com.zhiliaoapp.musically",
            displayName = "TikTok",
            feedContainerIdHints = listOf("recycler_view", "fragment_container")
        )
    )

    fun byPackageName(pkg: String): MonitoredApp? = ALL.find { it.packageName == pkg }
    fun packageNames(): Set<String> = ALL.map { it.packageName }.toSet()
}
