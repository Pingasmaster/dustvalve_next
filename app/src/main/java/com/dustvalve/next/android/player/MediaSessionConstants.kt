package com.dustvalve.next.android.player

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_FAVORITE = "com.dustvalve.next.ACTION_TOGGLE_FAVORITE"
    val COMMAND_TOGGLE_FAVORITE = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
}
