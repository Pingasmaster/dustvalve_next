package com.dustvalve.next.android.player

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class MediaSessionTrustTest {

    private val pm = mockk<PackageManager>()
    private val context = mockk<Context>().also {
        every { it.packageName } returns "com.dustvalve.next"
        every { it.packageManager } returns pm
    }

    @Test fun `own package is trusted`() {
        assertThat(MediaSessionTrust.isTrustedController(context, "com.dustvalve.next")).isTrue()
    }

    @Test fun `blank and null packages are untrusted`() {
        assertThat(MediaSessionTrust.isTrustedController(context, null)).isFalse()
        assertThat(MediaSessionTrust.isTrustedController(context, "")).isFalse()
        assertThat(MediaSessionTrust.isTrustedController(context, "   ")).isFalse()
    }

    @Test fun `well-known system media packages are trusted`() {
        for (pkg in MediaSessionTrust.TRUSTED_MEDIA_PACKAGES) {
            assertThat(MediaSessionTrust.isTrustedController(context, pkg)).isTrue()
        }
    }

    @Test fun `unknown third-party package without permission is untrusted`() {
        every {
            pm.checkPermission(Manifest.permission.MEDIA_CONTENT_CONTROL, "com.evil.mediacontroller")
        } returns PackageManager.PERMISSION_DENIED
        every { pm.getApplicationInfo("com.evil.mediacontroller", 0) } throws
            PackageManager.NameNotFoundException()
        assertThat(MediaSessionTrust.isTrustedController(context, "com.evil.mediacontroller")).isFalse()
    }

    @Test fun `MEDIA_CONTENT_CONTROL holders are trusted`() {
        every {
            pm.checkPermission(Manifest.permission.MEDIA_CONTENT_CONTROL, "com.oem.car")
        } returns PackageManager.PERMISSION_GRANTED
        assertThat(MediaSessionTrust.isTrustedController(context, "com.oem.car")).isTrue()
    }

    @Test fun `FLAG_SYSTEM package is trusted`() {
        every {
            pm.checkPermission(Manifest.permission.MEDIA_CONTENT_CONTROL, "com.oem.systemmedia")
        } returns PackageManager.PERMISSION_DENIED
        every { pm.getApplicationInfo("com.oem.systemmedia", 0) } returns ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_SYSTEM
        }
        assertThat(MediaSessionTrust.isTrustedController(context, "com.oem.systemmedia")).isTrue()
    }
}
