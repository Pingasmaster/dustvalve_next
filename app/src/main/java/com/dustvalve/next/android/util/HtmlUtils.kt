package com.dustvalve.next.android.util

object HtmlUtils {

    /**
     * Extracts JSON content assigned to a JavaScript variable in a script block.
     *
     * For example, given HTML containing:
     *   var TralbumData = { "key": "value" };
     * calling extractJsonFromScript(html, "TralbumData") returns "{ \"key\": \"value\" }".
     *
     * Handles nested braces by counting brace depth.
     */
    fun extractJsonFromScript(html: String, variableName: String): String? {
        // Match patterns like: var TralbumData = {...}; or variableName = {...};
        val varPattern = Regex(
            """(?:var\s+)?${Regex.escape(variableName)}\s*=\s*""",
            RegexOption.MULTILINE
        )
        val matchResult = varPattern.find(html) ?: return null
        val startOfJson = matchResult.range.last + 1

        if (startOfJson >= html.length) return null

        // Determine if the JSON starts with { or [
        val jsonStart = html.indexOf('{', startOfJson).let { braceIdx ->
            val bracketIdx = html.indexOf('[', startOfJson)
            when {
                braceIdx == -1 && bracketIdx == -1 -> return null
                braceIdx == -1 -> bracketIdx
                bracketIdx == -1 -> braceIdx
                else -> minOf(braceIdx, bracketIdx)
            }
        }

        // Make sure there's no significant non-whitespace between the assignment and the JSON start
        val between = html.substring(startOfJson, jsonStart).trim()
        if (between.isNotEmpty()) return null

        val openChar = html[jsonStart]
        val closeChar = if (openChar == '{') '}' else ']'

        var depth = 0
        var inString = false
        var stringChar = ' '
        var escaped = false
        var i = jsonStart

        while (i < html.length) {
            val c = html[i]

            if (escaped) {
                escaped = false
                i++
                continue
            }

            if (c == '\\' && inString) {
                escaped = true
                i++
                continue
            }

            if (inString) {
                if (c == stringChar) {
                    inString = false
                }
                i++
                continue
            }

            // Not in a string — skip JS comments
            if (c == '/' && i + 1 < html.length) {
                val next = html[i + 1]
                if (next == '/') {
                    // Line comment: skip to end of line
                    val lineEnd = html.indexOf('\n', i + 2)
                    i = if (lineEnd == -1) html.length else lineEnd + 1
                    continue
                } else if (next == '*') {
                    // Block comment: skip to closing */
                    val blockEnd = html.indexOf("*/", i + 2)
                    i = if (blockEnd == -1) html.length else blockEnd + 2
                    continue
                }
            }

            if (c == '"' || c == '\'' || c == '`') {
                inString = true
                stringChar = c
                i++
                continue
            }

            when (c) {
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        return html.substring(jsonStart, i + 1)
                    }
                }
            }

            i++
        }

        return null
    }

    /**
     * Extracts the value of an HTML data attribute (e.g. data-tralbum="...") from the page.
     * The value is expected to be HTML-encoded JSON, which is decoded before returning.
     */
    fun extractDataAttribute(html: String, attributeName: String): String? {
        // Try double-quoted value first, then single-quoted.
        // Each pattern only excludes its own delimiter, so the other quote type is allowed inside.
        val doubleQuoted = Regex("""${Regex.escape(attributeName)}\s*=\s*"([^"]*)"""")
        val singleQuoted = Regex("""${Regex.escape(attributeName)}\s*=\s*'([^']*)'""")
        val match = doubleQuoted.find(html) ?: singleQuoted.find(html) ?: return null
        val encoded = match.groupValues[1]
        if (encoded.isEmpty()) return null
        return decodeHtmlEntities(encoded)
    }

    /**
     * Decodes HTML entities in a string.
     */
    fun decodeHtmlEntities(text: String): String {
        var result = text
        // Decode numeric entities first
        result = Regex("&#(\\d+);").replace(result) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull()
            if (code != null && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull(16)
            if (code != null && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }
        // Named entities — &amp; must be decoded LAST to prevent double-decoding
        result = result
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        return result
    }

    /**
     * Strips HTML tags and decodes common HTML entities.
     */
    fun cleanHtml(html: String): String {
        // Remove HTML tags
        var cleaned = html.replace(Regex("<[^>]*>"), "")

        // Decode numeric HTML entities first (before named entities to prevent double-decoding)
        // Decimal entities
        cleaned = Regex("&#(\\d+);").replace(cleaned) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull()
            if (code != null && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }

        // Hexadecimal entities
        cleaned = Regex("&#x([0-9a-fA-F]+);").replace(cleaned) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull(16)
            if (code != null && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }

        // Decode common named HTML entities — &amp; must be LAST to prevent double-decoding
        cleaned = cleaned
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

        // Collapse whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        return cleaned
    }

    /**
     * Extracts the content attribute from a meta tag with the given property or name.
     *
     * Matches patterns like:
     *   <meta property="og:title" content="Some Title">
     *   <meta name="description" content="Some description">
     */
    fun extractMetaContent(html: String, property: String): String? {
        // Match meta tags with property or name attribute
        val pattern = Regex(
            """<meta\s+[^>]*(?:property|name)\s*=\s*["']${Regex.escape(property)}["'][^>]*content\s*=\s*["']([^"']*)["'][^>]*/?>""",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(html)
        if (match != null) {
            return match.groupValues[1]
        }

        // Also try reversed attribute order: content before property/name
        val reversedPattern = Regex(
            """<meta\s+[^>]*content\s*=\s*["']([^"']*)["'][^>]*(?:property|name)\s*=\s*["']${Regex.escape(property)}["'][^>]*/?>""",
            RegexOption.IGNORE_CASE
        )
        val reversedMatch = reversedPattern.find(html)
        return reversedMatch?.groupValues?.get(1)
    }
}
