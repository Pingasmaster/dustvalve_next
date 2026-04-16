package com.dustvalve.next.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun Context.shareUrl(url: String, title: String? = null) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        if (title != null) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
    }
}

fun Context.openInBrowser(url: String) {
    val viewIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(viewIntent)
    } catch (_: ActivityNotFoundException) {
    }
}
