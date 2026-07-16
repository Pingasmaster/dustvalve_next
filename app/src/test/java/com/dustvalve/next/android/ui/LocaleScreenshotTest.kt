package com.dustvalve.next.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dustvalve.next.android.R
import com.dustvalve.next.android.ui.util.tracksHeaderLabel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Localizability regression net: renders a showcase of representative,
 * translation-sensitive UI (plurals, format patterns, buttons, directional
 * icons) in every shipped locale plus the two pseudolocales, and diffs the
 * result against checked-in baselines.
 *
 * - en-rXA catches clipped layouts (accented + ~40% expanded English)
 * - ar-rXB catches RTL mistakes (mirrored layout; transport icons must NOT flip)
 * - ru exercises one/few/many plurals, de text expansion, ja CJK metrics
 *
 * Refresh baselines with: ./gradlew :app:recordRoborazziDebug
 * Verify (CI gate) with:  ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class LocaleScreenshotTest {

    // A single test method: Robolectric's native-graphics pipeline only
    // renders real pixels for the first test in a class, so all locales are
    // captured in one run, switching resource qualifiers between captures
    // (the standalone captureRoboImage spins up a fresh activity per call).
    @Test fun allLocales() {
        for (locale in listOf("en", "de", "es", "fr", "it", "pt-rBR", "ja", "zh-rCN", "ru", "en-rXA", "ar-rXB")) {
            RuntimeEnvironment.setQualifiers("+$locale")
            captureRoboImage(filePath = "src/test/snapshots/roborazzi/locale_showcase_$locale.png") {
                MaterialTheme {
                    LocaleShowcase()
                }
            }
        }
    }
}

@Composable
private fun LocaleShowcase() {
    Column(
        modifier = Modifier
            .width(411.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Long body copy: expansion / wrapping
        Text(stringResource(R.string.settings_dedicated_folder_desc), style = MaterialTheme.typography.bodyMedium)
        // Two-arg pattern with a brand name and an inserted noun
        Text(stringResource(R.string.provider_enable_text, "Bandcamp", stringResource(R.string.link_kind_album)))
        // Plurals across quantity classes (ru: one/few/many)
        Text(pluralStringResource(R.plurals.track_count, 1, 1))
        Text(pluralStringResource(R.plurals.track_count, 3, 3))
        Text(pluralStringResource(R.plurals.track_count, 21, 21))
        Text(pluralStringResource(R.plurals.scan_found_detailed, 5, 5, 2, 1))
        // Multi-placeholder line + duration patterns + count/duration combo
        Text(stringResource(R.string.storage_info, "1.2 GB", "300 MB", "12 GB"))
        Text(stringResource(R.string.detail_duration_hours_minutes, 2, 5))
        Text(tracksHeaderLabel(trackCount = 5, totalDurationSec = 23 * 60L))
        // Buttons: label fit at real component metrics
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text(stringResource(R.string.common_action_enable)) }
            OutlinedButton(onClick = {}) { Text(stringResource(R.string.common_action_cancel)) }
        }
        // Directional icons: back arrow MUST mirror in RTL, skip-next must NOT
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.common_cd_back),
            )
            Icon(
                painter = painterResource(R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.player_cd_next),
            )
        }
    }
}
