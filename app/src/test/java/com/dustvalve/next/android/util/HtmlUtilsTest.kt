package com.dustvalve.next.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlUtilsTest {

    @Test fun `extractJsonFromScript simple object`() {
        val html = """<script>var TralbumData = {"key":"value"};</script>"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "TralbumData"))
            .isEqualTo("""{"key":"value"}""")
    }

    @Test fun `extractJsonFromScript without var keyword`() {
        val html = """<script>TralbumData = {"k":1};</script>"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "TralbumData"))
            .isEqualTo("""{"k":1}""")
    }

    @Test fun `extractJsonFromScript nested braces`() {
        val html = """var X = {"a":{"b":{"c":1}}};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("""{"a":{"b":{"c":1}}}""")
    }

    @Test fun `extractJsonFromScript handles string with closing brace`() {
        val html = """var X = {"msg":"hello } world"};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("""{"msg":"hello } world"}""")
    }

    @Test fun `extractJsonFromScript handles escaped quotes`() {
        val html = """var X = {"msg":"she said \"hi\""};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("""{"msg":"she said \"hi\""}""")
    }

    @Test fun `extractJsonFromScript skips line comments`() {
        val html = """var X = {
            // a comment with {
            "k":1
        };"""
        val result = HtmlUtils.extractJsonFromScript(html, "X")
        assertThat(result).isNotNull()
        assertThat(result).contains("\"k\":1")
    }

    @Test fun `extractJsonFromScript skips block comments`() {
        val html = """var X = {/* { weird */"k":1};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("""{/* { weird */"k":1}""")
    }

    @Test fun `extractJsonFromScript handles arrays`() {
        val html = """var X = [1,2,3];"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("[1,2,3]")
    }

    @Test fun `extractJsonFromScript returns null when variable missing`() {
        assertThat(HtmlUtils.extractJsonFromScript("plain html", "X")).isNull()
    }

    @Test fun `extractJsonFromScript returns null for truncated json`() {
        val html = """var X = {"k":1"""  // unclosed
        assertThat(HtmlUtils.extractJsonFromScript(html, "X")).isNull()
    }

    @Test fun `extractJsonFromScript returns null when garbage between equals and brace`() {
        val html = """var X = notjson {"k":1};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X")).isNull()
    }

    @Test fun `extractJsonFromScript handles backtick strings`() {
        val html = """var X = {"k":`brace } inside`};"""
        assertThat(HtmlUtils.extractJsonFromScript(html, "X"))
            .isEqualTo("""{"k":`brace } inside`}""")
    }

    @Test fun `extractDataAttribute double quoted`() {
        val html = """<div data-foo="bar">"""
        assertThat(HtmlUtils.extractDataAttribute(html, "data-foo")).isEqualTo("bar")
    }

    @Test fun `extractDataAttribute single quoted`() {
        val html = """<div data-foo='bar'>"""
        assertThat(HtmlUtils.extractDataAttribute(html, "data-foo")).isEqualTo("bar")
    }

    @Test fun `extractDataAttribute decodes html entities`() {
        val html = """<div data-foo="a &amp; b">"""
        assertThat(HtmlUtils.extractDataAttribute(html, "data-foo")).isEqualTo("a & b")
    }

    @Test fun `extractDataAttribute returns null when missing`() {
        assertThat(HtmlUtils.extractDataAttribute("<div>", "data-foo")).isNull()
    }

    @Test fun `extractDataAttribute empty value returns null`() {
        assertThat(HtmlUtils.extractDataAttribute("<div data-foo=\"\">", "data-foo")).isNull()
    }

    @Test fun `extractDataAttribute allows alternate quote type inside`() {
        val html = """<div data-foo="contains 'apostrophes'">"""
        assertThat(HtmlUtils.extractDataAttribute(html, "data-foo"))
            .isEqualTo("contains 'apostrophes'")
    }

    @Test fun `decodeHtmlEntities named`() {
        assertThat(HtmlUtils.decodeHtmlEntities("&lt;tag&gt; &quot;q&quot; &amp; &apos;a&apos; &nbsp;"))
            .isEqualTo("<tag> \"q\" & 'a'  ")
    }

    @Test fun `decodeHtmlEntities numeric decimal`() {
        assertThat(HtmlUtils.decodeHtmlEntities("&#65;&#66;&#67;")).isEqualTo("ABC")
    }

    @Test fun `decodeHtmlEntities numeric hex`() {
        assertThat(HtmlUtils.decodeHtmlEntities("&#x41;&#x42;&#x43;")).isEqualTo("ABC")
    }

    @Test fun `decodeHtmlEntities amp last so no double decode`() {
        // "&amp;lt;" should stay "&lt;", not become "<"
        assertThat(HtmlUtils.decodeHtmlEntities("&amp;lt;")).isEqualTo("&lt;")
    }

    @Test fun `decodeHtmlEntities malformed numeric left alone`() {
        val out = HtmlUtils.decodeHtmlEntities("&#abc;")
        assertThat(out).isEqualTo("&#abc;")
    }

    @Test fun `cleanHtml strips tags`() {
        assertThat(HtmlUtils.cleanHtml("<b>hello</b> <i>world</i>")).isEqualTo("hello world")
    }

    @Test fun `cleanHtml collapses whitespace`() {
        assertThat(HtmlUtils.cleanHtml("a    b\n\tc")).isEqualTo("a b c")
    }

    @Test fun `cleanHtml decodes entities`() {
        assertThat(HtmlUtils.cleanHtml("a &amp; b")).isEqualTo("a & b")
    }

    @Test fun `cleanHtml handles multi-line tags`() {
        assertThat(HtmlUtils.cleanHtml("<span\nclass=\"x\">hi</span>")).isEqualTo("hi")
    }

    @Test fun `extractMetaContent og property`() {
        val html = """<meta property="og:title" content="My Title">"""
        assertThat(HtmlUtils.extractMetaContent(html, "og:title")).isEqualTo("My Title")
    }

    @Test fun `extractMetaContent name attribute`() {
        val html = """<meta name="description" content="A description">"""
        assertThat(HtmlUtils.extractMetaContent(html, "description")).isEqualTo("A description")
    }

    @Test fun `extractMetaContent reversed order`() {
        val html = """<meta content="My Title" property="og:title">"""
        assertThat(HtmlUtils.extractMetaContent(html, "og:title")).isEqualTo("My Title")
    }

    @Test fun `extractMetaContent single quoted`() {
        val html = """<meta property='og:title' content='My Title'>"""
        assertThat(HtmlUtils.extractMetaContent(html, "og:title")).isEqualTo("My Title")
    }

    @Test fun `extractMetaContent case insensitive tag`() {
        val html = """<META PROPERTY="og:title" CONTENT="T">"""
        assertThat(HtmlUtils.extractMetaContent(html, "og:title")).isEqualTo("T")
    }

    @Test fun `extractMetaContent returns null for missing`() {
        assertThat(HtmlUtils.extractMetaContent("<html></html>", "og:title")).isNull()
    }
}
