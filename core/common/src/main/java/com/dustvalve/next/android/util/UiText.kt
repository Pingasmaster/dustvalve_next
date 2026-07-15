package com.dustvalve.next.android.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    class StringResource(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()

    class PluralsResource(@param:PluralsRes val resId: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText()

    companion object {
        /**
         * [value] when present (typically an exception message), otherwise the
         * localized [fallback] resource.
         */
        fun orResource(value: String?, @StringRes fallback: Int): UiText = value?.let { DynamicString(it) } ?: StringResource(fallback)
    }

    // Format args may themselves be UiText (e.g. a localized fallback noun
    // inserted into a localized pattern); those are resolved against the same
    // locale before the array is handed to the platform formatter.

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(resId, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
        is PluralsResource -> pluralStringResource(resId, count, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value

        is StringResource -> context.getString(resId, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())

        // slack-lints' ArgInFormattedQuantityStringRes asks for a Slack-internal
        // LocalizationUtils.getFormattedCount() helper that doesn't exist here.
        // The standard Android plurals pattern (count as both quantity selector
        // and first format arg) is correct and uses platform plural rules.
        is PluralsResource ->
            @Suppress("ArgInFormattedQuantityStringRes")
            context.resources.getQuantityString(resId, count, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
    }
}
