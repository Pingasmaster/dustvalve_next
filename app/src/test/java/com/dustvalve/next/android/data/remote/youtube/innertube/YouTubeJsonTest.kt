package com.dustvalve.next.android.data.remote.youtube.innertube

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeJsonTest {

    private val json = Json { isLenient = true }

    @Test fun `path returns null on JsonArray and JsonNull`() {
        val arr = json.parseToJsonElement("[1,2]")
        assertThat(arr.path("foo")).isNull()
        val nul = json.parseToJsonElement("null")
        assertThat(nul.path("foo")).isNull()
    }

    @Test fun `path returns null when key missing`() {
        val obj = json.parseToJsonElement("""{"a":1}""")
        assertThat(obj.path("b")).isNull()
    }

    @Test fun `path returns child element`() {
        val obj = json.parseToJsonElement("""{"a":{"b":1}}""")
        assertThat(obj.path("a")?.path("b")?.toString()).isEqualTo("1")
    }

    @Test fun `arr casts JsonArray and returns null on object`() {
        val arr = json.parseToJsonElement("[1,2,3]").arr()
        assertThat(arr).isNotNull()
        assertThat(arr!!.size).isEqualTo(3)
        assertThat(json.parseToJsonElement("""{"x":1}""").arr()).isNull()
    }

    @Test fun `str returns string for string primitive only`() {
        assertThat(json.parseToJsonElement(""""hello"""").str()).isEqualTo("hello")
        assertThat(json.parseToJsonElement("123").str()).isNull()
        assertThat(json.parseToJsonElement("true").str()).isNull()
        assertThat(json.parseToJsonElement("null").str()).isNull()
    }

    @Test fun `str by key`() {
        val obj = json.parseToJsonElement("""{"a":"x","b":1}""")
        assertThat(obj.str("a")).isEqualTo("x")
        assertThat(obj.str("b")).isNull()
        assertThat(obj.str("missing")).isNull()
    }

    @Test fun `int by key parses numeric primitives only`() {
        val obj = json.parseToJsonElement("""{"a":42,"b":"7","c":"x"}""")
        assertThat(obj.int("a")).isEqualTo(42)
        assertThat(obj.int("b")).isEqualTo(7)
        assertThat(obj.int("c")).isNull()
        assertThat(obj.int("missing")).isNull()
    }

    @Test fun `long by key parses numeric primitives`() {
        val obj = json.parseToJsonElement("""{"a":2147483648}""")
        assertThat(obj.long("a")).isEqualTo(2147483648L)
    }

    @Test fun `runsText prefers first run text`() {
        val obj = json.parseToJsonElement(
            """{"title":{"runs":[{"text":"Foo"},{"text":"Bar"}]}}"""
        )
        assertThat(obj.runsText("title")).isEqualTo("Foo")
    }

    @Test fun `runsText falls back to simpleText`() {
        val obj = json.parseToJsonElement("""{"title":{"simpleText":"Plain"}}""")
        assertThat(obj.runsText("title")).isEqualTo("Plain")
    }

    @Test fun `runsText falls back to content key`() {
        val obj = json.parseToJsonElement("""{"title":{"content":"Hello"}}""")
        assertThat(obj.runsText("title")).isEqualTo("Hello")
    }

    @Test fun `runsText returns null when neither runs nor simpleText present`() {
        val obj = json.parseToJsonElement("""{"title":{}}""")
        assertThat(obj.runsText("title")).isNull()
    }

    @Test fun `extractThumbnail picks largest by width and returns url verbatim`() {
        val obj = json.parseToJsonElement(
            """
            {
              "thumbnail": {
                "thumbnails": [
                  {"url":"https://i.ytimg.com/vi/x/default.jpg","width":120,"height":90},
                  {"url":"https://i.ytimg.com/vi/x/hqdefault.jpg","width":480,"height":360}
                ]
              }
            }
            """.trimIndent()
        )
        assertThat(obj.extractThumbnail()).isEqualTo("https://i.ytimg.com/vi/x/hqdefault.jpg")
    }

    @Test fun `extractThumbnail handles bare thumbnails array fallback`() {
        val obj = json.parseToJsonElement(
            """{"thumbnails":[{"url":"https://x/img","width":50}]}"""
        )
        assertThat(obj.extractThumbnail()).isEqualTo("https://x/img")
    }

    @Test fun `extractThumbnail returns null when no thumbnails present`() {
        val obj = json.parseToJsonElement("""{"foo":1}""")
        assertThat(obj.extractThumbnail()).isNull()
    }
}
