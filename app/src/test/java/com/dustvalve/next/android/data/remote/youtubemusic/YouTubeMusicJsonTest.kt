package com.dustvalve.next.android.data.remote.youtubemusic

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class YouTubeMusicJsonTest {

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

    @Test fun `runsText returns null when neither runs nor simpleText present`() {
        val obj = json.parseToJsonElement("""{"title":{}}""")
        assertThat(obj.runsText("title")).isNull()
    }

    @Test fun `extractMusicThumbnail picks largest and rewrites size suffix`() {
        val obj = json.parseToJsonElement(
            """
            {
              "thumbnail": {
                "musicThumbnailRenderer": {
                  "thumbnail": {
                    "thumbnails": [
                      {"url":"https://yt3.example/img=w226-h226-l90-rj","width":226,"height":226},
                      {"url":"https://yt3.example/img=w720-h720-l90-rj","width":544,"height":544}
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
        // Largest = 544, but extractMusicThumbnail also normalizes to w544-h544 anyway
        assertThat(obj.extractMusicThumbnail()).isEqualTo("https://yt3.example/img=w720-h720-l90-rj")
    }

    @Test fun `extractMusicThumbnail uses thumbnailRenderer fallback`() {
        val obj = json.parseToJsonElement(
            """
            {
              "thumbnailRenderer": {
                "musicThumbnailRenderer": {
                  "thumbnail": {
                    "thumbnails": [
                      {"url":"https://x.example/img=w120-h120","width":120,"height":120}
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
        assertThat(obj.extractMusicThumbnail()).isEqualTo("https://x.example/img=w720-h720")
    }

    @Test fun `extractMusicThumbnail uses bare thumbnail thumbnails fallback`() {
        val obj = json.parseToJsonElement(
            """
            {
              "thumbnail": {
                "thumbnails": [ {"url":"https://x.example/img","width":50,"height":50} ]
              }
            }
            """.trimIndent()
        )
        // No size suffix => stays as-is (no =wH-hH match to replace)
        assertThat(obj.extractMusicThumbnail()).isEqualTo("https://x.example/img")
    }

    @Test fun `extractMusicThumbnail returns null when no thumbnails`() {
        val obj = json.parseToJsonElement("""{"foo":1}""")
        assertThat(obj.extractMusicThumbnail()).isNull()
    }
}
