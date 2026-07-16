package com.dustvalve.next.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LocaleCollationTest {

    private var originalLocale: Locale = Locale.getDefault()

    @Before fun remember() {
        originalLocale = Locale.getDefault()
    }

    @After fun restore() {
        Locale.setDefault(originalLocale)
    }

    @Test fun `accented names sort next to their base letter, not after z`() {
        Locale.setDefault(Locale.US)
        val sorted = listOf("Zebra", "Édith Piaf", "Adele", "Björk").sortedWith(LocaleCollation.comparator())
        // Code-point order would put É and ö after Z; collation must not.
        assertThat(sorted).containsExactly("Adele", "Björk", "Édith Piaf", "Zebra").inOrder()
    }

    @Test fun `ordering is case-insensitive`() {
        Locale.setDefault(Locale.US)
        val sorted = listOf("beatles", "ABBA", "Cream").sortedWith(LocaleCollation.comparator())
        assertThat(sorted).containsExactly("ABBA", "beatles", "Cream").inOrder()
    }

    @Test fun `german umlauts sort within their base letter`() {
        Locale.setDefault(Locale.GERMANY)
        val sorted = listOf("Ozzy", "Özdemir", "Oasis").sortedWith(LocaleCollation.comparator())
        assertThat(sorted.first()).isEqualTo("Oasis")
        assertThat(sorted.last()).isEqualTo("Ozzy")
    }

    @Test fun `turkish case folding matches dotted and dotless i correctly`() {
        // Regression for search: "İstanbul".lowercase(tr) must contain "istanbul"
        // only under Turkish folding rules.
        val tr = Locale.forLanguageTag("tr")
        assertThat("İSTANBUL".lowercase(tr)).isEqualTo("istanbul")
    }
}
