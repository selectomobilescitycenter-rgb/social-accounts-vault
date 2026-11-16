# Social Accounts Vault — Android App

A native Android app for keeping your Facebook, Instagram and TikTok account
details — plus any website and any other app — in one place, on your own
device. Bold, colourful cards per email; tap a card and a panel slides up
showing every entry on that email, its country (with flag), its Facebook
Pages, and a LOGIN / Open button that opens the platform's login page, the
website, or the installed app.

**Privacy first:** all data is stored only in a local Room database on the
device. The app asks for **no network permission** (the browser handles
logins) and sends nothing anywhere. It starts empty; you add your own
accounts inside the app. **PIN lock:** on first launch you set a 4-digit
PIN; the vault asks for it on every start and when it returns from the
background (fingerprint unlock is offered when the device supports it).
The PIN is stored only as a SHA-256 hash — **if you forget the PIN there
is no recovery, by design.**

- Package: `com.socialvault.app`
- Language: Kotlin + Jetpack Compose + Room
- Min SDK 26 (Android 8.0), target SDK 34 (Android 14)
- License: MIT (see `LICENSE`)

## Built APK (ready to install)

A debug APK was built and verified on 2026-11-16 (v1.5):

- **File:** `app/build/outputs/apk/debug/app-debug.apk`
- **Size:** 20,320,457 bytes (~19.4 MB)
- **SHA-256:** `0c588637cc3ab7c555b2fd7248ae12087a8b8d459e53d5698420babb06cb1a68`
- **Package:** `com.socialvault.app`, version 1.5 (versionCode 6)
- **SDK:** minSdk 26, targetSdk 34, compileSdk 34
- **Signed:** with the standard Android debug key (`apksigner verify` passes)
- **Permissions:** only `USE_BIOMETRIC` (a normal, auto-granted permission
  needed for fingerprint unlock) — no internet permission, no storage
  permission. The only other entry is the internal
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- **Verified with:** `aapt2 dump badging` (label "Social Accounts Vault",
  launchable activity `com.socialvault.app.MainActivity`) and `apksigner verify`

To install it, copy the APK to your phone and tap it (see "Install the APK
on your phone" below). To rebuild it after changing the code, run
`./gradlew assembleDebug` — the fresh APK replaces the one above.

**Upgrading from v1:** install the v2 APK over the v1 app (same package,
same database name). A proper Room migration (v1 → v2) carries your v1
accounts into v2 — nothing is lost.

**What's new in v1.3 — "View IDs & Copy" fix:** the detail panel used to
open only by tapping the card body, with nothing on the card saying so,
while the only visible button ("OPEN →") opened the login page in the
browser — so it looked like IDs could not be viewed or copied at all.
Now every email card has a clear **"👁 View IDs & Copy Password"** button
plus the hint "Card par tap karein — IDs dekhein, password copy karein",
and the external button is relabelled to say it opens the login page in
the browser (e.g. "🌐 Login to Facebook in Browser →"). In the panel,
every entry now has labelled **"📋 Copy Email"** and **"📋 Copy Password"**
buttons (email copy was missing), and the panel header has a **📋 Copy**
button for the email address. No database change in v1.3 — install it
over v1.2 and all accounts survive.

**What's new in v1.4 — home-screen layout fix:** on the user's phone the
email card sat below the fold, invisible, and the stat tiles looked like
boxes but did nothing when tapped. Now all 8 stats are compact, tappable
chips in ONE horizontally scrollable row (with the hint "👆 Kisi bhi dabbe
par tap karein — list khul jayegi"): tapping Emails / Total IDs / FB Pages
opens the list — or the single email's panel directly when there is only
one email; Facebook / Instagram / TikTok filter to Social, Websites and
Apps filter to their kind and scroll the list into view. The backup
warning is one short line, the top buttons (Dark / Backup / Restore / PIN)
share the width equally with single-line labels (Restore no longer wraps
to "Resto re"), and the whole home screen is now ONE LazyColumn — header
plus email cards as list items — so the page always scrolls and the first
email card with its "👁 View IDs & Copy Password" button is reachable
even on a tall phone where the old plain Column starved the inner list of
height (list got ~0dp, nothing scrolled at all). No database change in
v1.4 — install it over v1.3 and all accounts survive.

**What's new in v1.5 — saved Apps/Websites "disappearing" fix:** tapping
a stat chip (e.g. "Facebook") filtered the list to Social only, and that
filter never cleared — so freshly saved Website/App entries were saved
correctly but stayed hidden from the list while the stat chips still
counted them. Now saving any entry clears the filter and the search and
shows a "Saved: <name>" toast, so what you saved is visible at once;
tapping the same stat chip again clears the filter; and while a filter is
on, a banner above the list says so in plain words ("🔎 Filter: Social —
sirf Social dikh raha hai") with a ✕ Clear button. The duplicate-password
warning now says "Don't save" instead of "Cancel", and tells you "Not
saved" if you dismiss it, so an entry is never silently thrown away. The
filter logic lives in `ui/Filtering.kt` as a pure function, covered by
`FilterTest` (7 tests) and `RepositoryTest` (5 tests) — run with
`./gradlew testDebugUnitTest`. No database change in v1.5 — install it
over v1.4 and all accounts survive.

## Features (v2)

- Email accounts as bold, colourful cards (Facebook blue, Instagram
  pink/purple gradient, TikTok black; websites blue, apps purple)
- Colour-coded stat tiles: Emails, Total IDs, Facebook, Instagram, TikTok,
  Facebook Pages, Websites, Apps
- Tap a card → bottom-sheet panel with every entry on that email:
  platform/website/app name, username, password (show/hide + copy), flag +
  country badge, Facebook Pages (name + followers), and an action button
  (Login / Open Website / Open App)
- **Three entry types:** Social (Facebook / Instagram / TikTok), Website
  (site name + URL — LOGIN opens the site in the browser), App (app name +
  optional package name — opens the installed app when known, otherwise a
  Play Store search). The Add/Edit form adapts to the chosen type.
- **PIN lock + fingerprint:** 4-digit PIN set on first launch, unlock screen
  on every cold start and on return from background; fingerprint unlock via
  BiometricPrompt when the device supports it; "Change PIN" in the top bar.
  No PIN recovery by design.
- **Password generator:** dice button in the form generates a strong
  16-character password (length 12 / 16 / 20 selectable)
- **Password strength meter:** Weak / Medium / Strong with a coloured bar
- **2FA codes:** optional TOTP secret per entry; the panel shows the live
  6-digit code with a 30-second countdown and a copy button (implemented
  in-app, RFC 6238 — no network)
- **Duplicate password warning:** saving a password already used on another
  entry warns you ("Ye password pehle se … par use ho raha hai") and lets
  you save anyway
- **Categories:** Personal / Business / Shop / Other (chip on each entry)
- **Notes, recovery email and recovery phone** per entry
- **Filter + sort:** All / Social / Websites / Apps chips; search matches
  site/app names, URLs, notes and categories too; sort A–Z or Newest
- **Backup / Restore:** export all data to a human-readable JSON file (or
  restore one) through the system file picker
  - ⚠️ **Warning:** the backup file contains all your passwords in readable
    form — keep it private and never share it. The app shows this warning
    next to the Backup/Restore buttons.
  - Google Drive auto-backup is NOT included: it needs a Google account and
    OAuth sign-in, which is out of scope for this offline vault.
- **Dark mode:** sun/moon toggle in the top bar, persisted
- Add Account form: email, type (Social / Website / App), platform,
  username, country (dropdown with Pakistan, USA, UK, UAE, Canada,
  Australia, India, Bangladesh, Germany, France, Turkey, Saudi Arabia,
  Malaysia, Indonesia, or free text), password, Facebook Pages (one per
  line: `Page name - followers`)
- Edit and delete (two-step confirm), search by email / username / country
  / site / app / notes / category

## Build it

### In Android Studio (easiest)

1. Open this folder in Android Studio (Hedgehog or newer).
2. Let Gradle sync (it downloads the Android Gradle Plugin, Kotlin and
   Compose libraries on first run).
3. Run the app on a device or emulator, or use **Build → Build App Bundle(s)
   / APK(s) → Build APK(s)** for a debug APK.

### From the command line

Requirements: JDK 17, Android SDK with platform 34 and build-tools 34.0.0.

```sh
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

For a release build:

```sh
./gradlew assembleRelease
```

(The release APK is unsigned by default. To publish it, sign it with your
own keystore — see Android's "Sign your app" documentation. Never commit a
keystore or its passwords to source control.)

## Install the APK on your phone

1. Copy `app-debug.apk` to your phone (USB, or any file transfer).
2. On the phone, tap the APK file. If Android asks, allow "Install unknown
   apps" for the app you open it with (Files, Chrome, etc.).
3. Open **Social Accounts Vault**. On first launch, set your 4-digit PIN
   (remember it — there is no recovery). Then add your accounts with **＋ Add**.
   If you had the v1 app installed, install v2 over it — your accounts are
   kept.

## Submitting to F-Droid

This project is MIT-licensed and built to be F-Droid friendly (no
proprietary dependencies, no tracking, no network permission).

- `fastlane/metadata/android/en-US/` holds the store listing texts
  (title, short description, full description).
- `metadata/com.socialvault.app.yml` is a skeleton of the F-Droid
  package metadata for this app.

To submit, publish this source to a public Git repository (GitHub/GitLab),
then open a merge request against F-Droid's `fdroiddata` repository adding
the metadata file (or use F-Droid's submission form). F-Droid builds the
app from source on its own servers — you do not upload an APK.
