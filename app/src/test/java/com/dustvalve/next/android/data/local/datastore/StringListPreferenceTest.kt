package com.dustvalve.next.android.data.local.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringListPreferenceTest {

    @Test fun `absent value decodes to empty`() {
        assertThat(StringListPreference.decode(null)).isEmpty()
    }

    @Test fun `well formed json array roundtrips in order`() {
        assertThat(StringListPreference.decode("""["content://a","content://b"]"""))
            .containsExactly("content://a", "content://b")
            .inOrder()
    }

    @Test fun `malformed json reads as empty instead of throwing`() {
        // A garbage value written by an older build must not make the key
        // permanently unreadable; the next successful set() overwrites it.
        assertThat(StringListPreference.decode("not [ json")).isEmpty()
        assertThat(StringListPreference.decode("{broken")).isEmpty()
    }

    @Test fun `json of the wrong shape reads as empty`() {
        assertThat(StringListPreference.decode("""{"a":1}""")).isEmpty()
        assertThat(StringListPreference.decode("[1,2,3]")).isEmpty()
    }
}
