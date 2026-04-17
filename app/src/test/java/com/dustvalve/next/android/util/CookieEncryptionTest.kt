package com.dustvalve.next.android.util

import org.junit.Ignore
import org.junit.Test

/**
 * CookieEncryption uses the AndroidKeyStore provider, which Robolectric does not emulate on
 * the JVM (KeyStore.getInstance("AndroidKeyStore") throws NoSuchAlgorithmException). These
 * behaviors must be verified via an instrumented androidTest on a real device / emulator.
 *
 * Kept as a stub so the intent is visible next to the other util tests.
 */
@Ignore("AndroidKeyStore is unavailable in Robolectric; verify in instrumented tests.")
class CookieEncryptionTest {
    @Test fun placeholder() { /* see class-level @Ignore */ }
}
