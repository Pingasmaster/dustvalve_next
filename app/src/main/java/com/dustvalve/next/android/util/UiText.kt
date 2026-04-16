package com.dustvalve.next.android.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    class StringResource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText()

    class PluralsResource(
        @param:PluralsRes val resId: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(resId, *args.toTypedArray())
        is PluralsResource -> pluralStringResource(resId, count, *args.toTypedArray())
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(resId, *args.toTypedArray())
        is PluralsResource -> context.resources.getQuantityString(resId, count, *args.toTypedArray())
    }
}
