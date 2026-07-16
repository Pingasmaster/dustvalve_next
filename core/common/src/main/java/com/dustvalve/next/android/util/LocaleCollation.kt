package com.dustvalve.next.android.util

import java.text.Collator
import java.util.Locale

/**
 * Locale-aware string ordering for user content (track/artist/album names).
 *
 * Unlike [String.CASE_INSENSITIVE_ORDER] (raw code-point order), [Collator]
 * sorts names where users of the current locale expect them: "é" next to "e",
 * "ö" inside "o" for German, Cyrillic/Kana in their native order. SECONDARY
 * strength keeps ordering case-insensitive while still distinguishing accents.
 */
object LocaleCollation {

    /**
     * Comparator following the current default locale's collation rules.
     * Collator instances are not thread-safe, so callers get a fresh one;
     * create it once per sort, not once per comparison.
     */
    fun comparator(): Comparator<String> {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        return Comparator { a, b -> collator.compare(a, b) }
    }
}
