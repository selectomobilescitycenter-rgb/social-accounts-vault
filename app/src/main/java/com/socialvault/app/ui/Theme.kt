package com.socialvault.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.socialvault.app.data.SocialId

// Brand colours — bold and colourful, matching the web version of the vault.
val PurpleStart = Color(0xFF7B2FF7)
val PinkMid = Color(0xFFFF2E88)
val OrangeEnd = Color(0xFFFF7A18)
val BlueStart = Color(0xFF2F7BFF)
val FacebookBlue = Color(0xFF1877F2)
val FacebookBlueDark = Color(0xFF0D5BD7)
val InstagramYellow = Color(0xFFFEDA75)
val InstagramOrange = Color(0xFFFA7E1E)
val InstagramPink = Color(0xFFD62976)
val InstagramPurple = Color(0xFF962FBF)
val TikTokBlack = Color(0xFF111111)
val GreenStart = Color(0xFF00B578)
val GreenEnd = Color(0xFF00D68F)
val DarkText = Color(0xFF0B1030)

val TitleGradient: Brush
    get() = Brush.linearGradient(listOf(PurpleStart, PinkMid, OrangeEnd))

val AddButtonGradient: Brush
    get() = Brush.linearGradient(listOf(PinkMid, OrangeEnd))

val SaveButtonGradient: Brush
    get() = Brush.linearGradient(listOf(GreenStart, GreenEnd))

@Composable
fun VaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PurpleStart,
            secondary = PinkMid,
            tertiary = OrangeEnd,
            background = Color(0xFF12121A),
            surface = Color(0xFF1E1E2C),
            surfaceVariant = Color(0xFF2A2A3C),
            onBackground = Color(0xFFF2F2F7),
            onSurface = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFFB9B9C9)
        )
    } else {
        lightColorScheme(
            primary = PurpleStart,
            secondary = PinkMid,
            tertiary = OrangeEnd,
            background = Color(0xFFF4F6FF),
            surface = Color.White,
            surfaceVariant = Color(0xFFE8ECFF),
            onBackground = DarkText,
            onSurface = DarkText,
            onSurfaceVariant = Color(0xFF4A4F6B)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Card background for an email, coloured by its first entry. */
fun cardGradient(platform: String): Brush = when (platform) {
    "Facebook" -> Brush.linearGradient(listOf(FacebookBlue, FacebookBlueDark))
    "Instagram" -> Brush.linearGradient(
        listOf(InstagramYellow, InstagramOrange, InstagramPink, InstagramPurple)
    )
    else -> Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF000000)))
}

/** Card background for one entry, by kind (social keeps its platform colour). */
fun entryGradient(entry: SocialId): Brush = when (entry.kind) {
    "WEBSITE" -> Brush.linearGradient(listOf(Color(0xFF00B4DB), Color(0xFF0083B0)))
    "APP" -> Brush.linearGradient(listOf(PurpleStart, InstagramPurple))
    else -> cardGradient(entry.platform)
}

/** Distinct gradient per stat tile (index 0..7). */
fun statGradient(index: Int): Brush = when (index % 8) {
    0 -> Brush.linearGradient(listOf(PurpleStart, Color(0xFFA55BFF)))
    1 -> Brush.linearGradient(listOf(PinkMid, OrangeEnd))
    2 -> Brush.linearGradient(listOf(BlueStart, Color(0xFF00C6FF)))
    3 -> Brush.linearGradient(listOf(FacebookBlue, FacebookBlueDark))
    4 -> Brush.linearGradient(listOf(InstagramPink, InstagramPurple))
    5 -> Brush.linearGradient(listOf(GreenStart, GreenEnd))
    6 -> Brush.linearGradient(listOf(Color(0xFF00B4DB), Color(0xFF0083B0)))
    else -> Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF555555)))
}

/** Icon shown for an entry: platform icon for social, globe / phone otherwise. */
fun entryIcon(entry: SocialId): String = when (entry.kind) {
    "WEBSITE" -> "🌐"
    "APP" -> "📱"
    else -> platformIcon(entry.platform)
}

fun kindLabel(kind: String): String = when (kind) {
    "WEBSITE" -> "Website"
    "APP" -> "App"
    else -> "Social"
}

/** Platform icon (emoji) shown in a white circle on the card. */
fun platformIcon(platform: String): String = when (platform) {
    "Facebook" -> "ⓕ"
    "Instagram" -> "📸"
    else -> "🎵"
}

/** Login page opened in the user's own browser. */
fun loginUrl(platform: String): String = when (platform) {
    "Facebook" -> "https://www.facebook.com/login/"
    "Instagram" -> "https://www.instagram.com/accounts/login/"
    else -> "https://www.tiktok.com/login"
}

/** Label of the main action button for an entry. Worded so it is clear
 * this opens the destination OUTSIDE the vault (browser / installed app),
 * not the entry's details — those open with "View IDs & Copy Password". */
fun entryActionLabel(entry: SocialId): String = when (entry.kind) {
    "WEBSITE" -> "🌐 Open Website in Browser →"
    "APP" -> "📱 Open App →"
    else -> "🌐 Login to ${entry.platform} in Browser →"
}

/** Known Android package names, so an app entry can open the installed app. */
val KNOWN_APP_PACKAGES = mapOf(
    "whatsapp" to "com.whatsapp",
    "whatsapp business" to "com.whatsapp.w4b",
    "gmail" to "com.google.android.gm",
    "youtube" to "com.google.android.youtube",
    "facebook" to "com.facebook.katana",
    "instagram" to "com.instagram.android",
    "tiktok" to "com.zhiliaoapp.musically",
    "messenger" to "com.facebook.orca",
    "snapchat" to "com.snapchat.android",
    "telegram" to "org.telegram.messenger",
    "spotify" to "com.spotify.music",
    "netflix" to "com.netflix.mediaclient",
    "chrome" to "com.android.chrome",
    "daraz" to "com.daraz.android",
    "jazzcash" to "com.techlogix.mobilinkcustomer",
    "easypaisa" to "pk.com.telenor.phoenix",
    "x" to "com.twitter.android",
    "twitter" to "com.twitter.android",
    "linkedin" to "com.linkedin.android",
    "pinterest" to "com.pinterest",
    "zoom" to "us.zoom.videomeetings",
    "skype" to "com.skype.raider"
)

/** Resolve the package name for an app entry: typed package wins, else the known map. */
fun packageForApp(appName: String, typedPackage: String): String? {
    val typed = typedPackage.trim()
    if (typed.isNotEmpty()) return typed
    return KNOWN_APP_PACKAGES[appName.trim().lowercase()]
}

/**
 * Open an entry's destination. Returns true when something was opened.
 *  - SOCIAL: platform login page in the browser.
 *  - WEBSITE: the stored URL in the browser (https:// added when missing).
 *  - APP: the installed app when its package is known/installed, otherwise
 *    a Play Store search for the app name.
 */
fun openEntry(context: Context, entry: SocialId): Boolean {
    return when (entry.kind) {
        "WEBSITE" -> {
            val raw = entry.url.trim()
            if (raw.isEmpty()) return false
            val url = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw
            } else {
                "https://$raw"
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }
        "APP" -> {
            val pkg = packageForApp(entry.appName, entry.packageName)
            val launch = pkg?.let {
                context.packageManager.getLaunchIntentForPackage(it)
            }
            if (launch != null) {
                context.startActivity(launch)
            } else {
                val query = Uri.encode(entry.appName.ifBlank { entry.username })
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/search?q=$query&c=apps")
                    )
                )
            }
            true
        }
        else -> {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl(entry.platform)))
            )
            true
        }
    }
}

/** Entry categories offered in the form. */
val CATEGORIES = listOf("Personal", "Business", "Shop", "Other")

/** Countries offered in the Add Account dropdown (free text also allowed). */
val COUNTRIES = listOf(
    "Pakistan", "USA", "UK", "UAE", "Canada", "Australia",
    "India", "Bangladesh", "Germany", "France", "Turkey",
    "Saudi Arabia", "Malaysia", "Indonesia"
)

/** Flag emoji for a country name; globe when unknown. */
fun flagFor(country: String): String = when (country.trim()) {
    "Pakistan" -> "🇵🇰"
    "USA", "United States" -> "🇺🇸"
    "UK", "United Kingdom" -> "🇬🇧"
    "UAE" -> "🇦🇪"
    "Canada" -> "🇨🇦"
    "Australia" -> "🇦🇺"
    "India" -> "🇮🇳"
    "Bangladesh" -> "🇧🇩"
    "Germany" -> "🇩🇪"
    "France" -> "🇫🇷"
    "Turkey", "Türkiye" -> "🇹🇷"
    "Saudi Arabia" -> "🇸🇦"
    "Malaysia" -> "🇲🇾"
    "Indonesia" -> "🇮🇩"
    else -> "🌍"
}

/**
 * Parse the Facebook Pages textarea: one page per line,
 * "Page name - followers". Lines without a dash get "—" followers.
 */
fun parsePages(text: String): List<Pair<String, String>> =
    text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val dash = line.indexOf('-')
            if (dash > 0) {
                line.substring(0, dash).trim() to line.substring(dash + 1).trim().ifEmpty { "—" }
            } else {
                line to "—"
            }
        }
