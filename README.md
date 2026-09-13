# ReelPal

An Android app that counts how many reels/shorts you scroll inside
Instagram, YouTube, Snapchat, and TikTok, and shows a live floating
counter while you're in those apps.

## What it uses and why

| Permission | Why | How it's granted |
|---|---|---|
| Accessibility Service | The only way for an app to observe what's happening *inside* another app's screen (scroll events, which screen is open) | User enables manually in **Settings → Accessibility** — Android does not allow this to be granted via a normal in-app prompt |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draws the floating live-count bubble on top of Instagram/YouTube/etc. | User grants via **Settings → Apps → Special access → Display over other apps** |
| Usage Access | Lets the app know which app is currently in the foreground | User grants via **Settings → Apps → Special access → Usage access** |
| Foreground Service + Notification | Keeps the overlay/monitor alive without Android killing the process; the persistent notification is required by Android, not optional | Runtime notification permission prompt (Android 13+) |

The app walks the user through granting all of these from the main
screen — it can't do it for them (that's intentional on Android's part).

## How detection works (and its limits)

Instagram, YouTube, and Snapchat don't expose an official "user is
watching Reels/Shorts" signal, and their view hierarchies are partly
obfuscated and change with every app update. So `ReelAccessibilityService`
uses a pragmatic heuristic:

> A large vertical scroll (`TYPE_VIEW_SCROLLED` with big `scrollDeltaY`)
> inside a monitored app, debounced to ~1 count per swipe, is counted as
> "one reel."

This is deliberately **app-version-independent**, so it won't break the
next time Instagram ships an update — but it also means it can't
perfectly distinguish "scrolling Reels" from "scrolling the main feed"
within the same app. `MonitoredApps.kt` includes `feedContainerIdHints` —
resource-id fragments that were true for some versions of each app at
time of writing — as a starting point if you want to narrow detection
further. To do that properly:

1. Open the target app on a test device.
2. Use Android Studio's **Layout Inspector** while the Accessibility
   service is running to see the actual `viewIdResourceName`/`className`
   of the reels feed container on the device's current app version.
3. Add a check in `handlePossibleReelScroll()` in
   `ReelAccessibilityService.kt` that only counts scrolls whose
   `event.source` matches that container.

Expect to revisit this occasionally — it's the same maintenance burden
every third-party accessibility-based app (ad blockers, password
autofillers, etc.) has to deal with.

## Building it

1. Open this folder in Android Studio (Hedgehog/2023.1+ recommended).
2. Let Gradle sync — it'll pull Compose, Room, and KSP from Google/Maven
   automatically.
3. Run on a physical device or emulator with Google Play (Instagram/
   YouTube/Snapchat need Play Services to install).
4. On first launch, follow the three permission prompts, then open
   Instagram/YouTube and scroll a Reel/Short to see the counter update.

## A note on responsible use

Accessibility + overlay + background-monitoring is real power — it's the
same permission combination stalkerware uses to spy on someone without
their knowledge. This project is built for **monitoring your own device**
(the classic "how much am I actually scrolling" digital-wellbeing use
case). If you intend to put it on a device you don't personally use,
make sure the person using that device knows it's there and agrees to it.

## Known gaps / things to add next

- No historical charts yet — `ReelRepository.observeLastNDays()` already
  returns the data; a simple bar chart on the dashboard is the natural
  next step.
- No daily reel-count goal/limit + notification when exceeded.
- No per-app detection tuning UI (currently requires editing
  `MonitoredApps.kt` and rebuilding).
- Play Store review for accessibility-based apps not used for actual
  accessibility is strict; this is realistically a personal-use / sideload
  app unless you build a strong in-app justification flow for review.
