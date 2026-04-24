package com.dustvalve.next.android.data.remote.youtube.innertube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeYtcfgExtractorTest {

    @Test fun `extracts visitorData from INNERTUBE_CONTEXT`() {
        val html = """
            <script>
            ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"VD_ABC","clientName":"WEB_REMIX"}},"INNERTUBE_CLIENT_VERSION":"1.2.3"});
            </script>
        """.trimIndent()
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg).isNotNull()
        assertThat(cfg!!.visitorData).isEqualTo("VD_ABC")
        assertThat(cfg.clientVersion).isEqualTo("1.2.3")
    }

    @Test fun `extracts top-level VISITOR_DATA when no INNERTUBE_CONTEXT`() {
        val html = """<script>ytcfg.set({"VISITOR_DATA":"plain_vd","INNERTUBE_CLIENT_VERSION":"9.9.9"});</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.visitorData).isEqualTo("plain_vd")
        assertThat(cfg.clientVersion).isEqualTo("9.9.9")
    }

    @Test fun `skips small ytcfg calls and picks the one with INNERTUBE_CONTEXT`() {
        // Mirrors real YT Music HTML where the first call can be a small
        // CSI_SERVICE_NAME side-call and the INNERTUBE_CONTEXT block comes
        // next. Our picker must not stop at the first call.
        val html = """
            <script>ytcfg.set({"CSI_SERVICE_NAME": 'youtube_web_music', "TIMING_INFO": {}});</script>
            <script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"real_vd"}}});</script>
        """.trimIndent()
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg).isNotNull()
        assertThat(cfg!!.visitorData).isEqualTo("real_vd")
    }

    @Test fun `handles nested braces inside the body`() {
        // "TIMING_INFO": {} is a real shape seen in live HTML; a lazy regex
        // would truncate at the inner `}` and yield invalid JSON.
        val html = """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"vd_nested"}},"TIMING":{},"INNERTUBE_CLIENT_VERSION":"2.0"});</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.visitorData).isEqualTo("vd_nested")
        assertThat(cfg.clientVersion).isEqualTo("2.0")
    }

    @Test fun `accepts missing terminal semicolon`() {
        // Google emits `ytcfg.set({...}))</script>` in some variants — the
        // old regex required `);`.
        val html = """<script>ytcfg.set({"VISITOR_DATA":"vd_no_semi","INNERTUBE_CLIENT_VERSION":"1.0"})</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.visitorData).isEqualTo("vd_no_semi")
    }

    @Test fun `handles JSON string containing a brace`() {
        // A `}` inside a string literal must not prematurely close the body.
        val html = """<script>ytcfg.set({"INNERTUBE_CONTEXT":{"client":{"visitorData":"vd_brace"}},"DEVICE":"cbr=Chrome; tricky }"});</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.visitorData).isEqualTo("vd_brace")
    }

    @Test fun `returns null for HTML with no ytcfg_set`() {
        // e.g. YT's "Your browser is deprecated" stub page.
        val html = "<html><body>Browser is deprecated</body></html>"
        assertThat(YouTubeYtcfgExtractor.extract(html)).isNull()
    }

    @Test fun `returns null when ytcfg body lacks visitorData`() {
        val html = """<script>ytcfg.set({"OTHER":"x"});ytcfg.set({"ANOTHER":"y"});</script>"""
        assertThat(YouTubeYtcfgExtractor.extract(html)).isNull()
    }

    @Test fun `skips bodies that fail to parse as JSON`() {
        // The third YT Music call is literal JS with unquoted identifiers:
        // ytcfg.set({'YTMUSIC_INITIAL_DATA': initialData});
        // It must be silently skipped without aborting the walk.
        val html = """
            <script>ytcfg.set({'YTMUSIC_INITIAL_DATA': initialData});</script>
            <script>ytcfg.set({"VISITOR_DATA":"good_vd"});</script>
        """.trimIndent()
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.visitorData).isEqualTo("good_vd")
    }

    @Test fun `clientVersion falls back from INNERTUBE_CLIENT_VERSION to INNERTUBE_CONTEXT_CLIENT_VERSION`() {
        val html = """<script>ytcfg.set({"VISITOR_DATA":"x","INNERTUBE_CONTEXT_CLIENT_VERSION":"ctx_ver_7"});</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg!!.clientVersion).isEqualTo("ctx_ver_7")
    }

    @Test fun `clientVersion is null when neither field is present`() {
        val html = """<script>ytcfg.set({"VISITOR_DATA":"x"});</script>"""
        val cfg = YouTubeYtcfgExtractor.extract(html)
        assertThat(cfg).isNotNull()
        assertThat(cfg!!.clientVersion).isNull()
    }
}
