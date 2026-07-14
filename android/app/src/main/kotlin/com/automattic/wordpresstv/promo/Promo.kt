package com.automattic.wordpresstv.promo

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The "Code for the People" documentary promo. WordPress.tv can't host the film
 * directly, so we feature it on Home and send viewers to YouTube — opening a
 * YouTube app when the device has one, and otherwise letting them scan the QR to
 * watch on their phone. This is static, curated content (like [catalog.Catalog]);
 * it never touches the repository or data layer.
 */
object CodeForThePeople {
    const val VIDEO_ID = "8lQijrTaaGg"
    const val TITLE = "Code for the People"
    const val TAGLINE = "The human story of the open web"
    const val CREDIT = "Directed by Bao Nguyen"
    const val BLURB =
        "The open web is arguably the world's most vital invisible utility — and it's under " +
            "siege. Code for the People is a documentary short on the past, present, and contested " +
            "future of the internet: what it's for, who gets to own it, and what it takes to keep " +
            "it free. The web belongs to all of us."

    /** Encoded in the QR and opened on a phone — the short link resolves into the app. */
    const val SHARE_URL = "https://youtu.be/$VIDEO_ID"

    /** The canonical watch URL used for the on-device app hand-off. */
    const val WATCH_URL = "https://www.youtube.com/watch?v=$VIDEO_ID"

    /** Shown under the QR as human-readable text. */
    const val SHARE_LABEL = "youtu.be/$VIDEO_ID"
}

/** The YouTube apps we'd rather hand off to, most-preferred (TV) first. */
private val YOUTUBE_PACKAGES = listOf(
    "com.google.android.youtube.tv", // Android TV / Google TV YouTube
    "com.google.android.youtube", // phone/tablet YouTube (present on some devices)
)

private fun watchIntent(pkg: String? = null): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(CodeForThePeople.WATCH_URL)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) setPackage(pkg)
    }

/**
 * Open the film in a YouTube app, trying the known TV/phone packages in order and
 * launching the first that's installed. Deliberately targets a concrete package
 * (never a generic `ACTION_VIEW`): a generic intent would pop the system chooser
 * and its own "no app can do this" toast when nothing handles it. Returns false
 * when no known YouTube app is present, so the caller can show our own message.
 * Relies on the `<queries>` entry in the manifest on Android 11+.
 */
fun openInYouTube(context: Context): Boolean {
    val pm = context.packageManager
    val pkg = YOUTUBE_PACKAGES.firstOrNull { watchIntent(it).resolveActivity(pm) != null } ?: return false
    return runCatching { context.startActivity(watchIntent(pkg)) }.isSuccess
}
