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
            RegexOption.MULTILINE,
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

            // Not in a string - skip JS comments
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

    // One combined pattern so decoding happens in a single left-to-right pass: each
    // entity is decoded exactly once and its replacement is never re-scanned. Multi-pass
    // decoding double-decoded escapes like "&#38;quot;" (-> "&quot;" -> '"'), which can
    // corrupt data-tralbum JSON payloads.
    private val ENTITY_RE = Regex("&(?:(amp|lt|gt|quot|apos|nbsp)|#(\\d+)|#x([0-9a-fA-F]+));")

    /**
     * Decodes HTML entities in a string. Single pass: an entity whose decoded form spells
     * out another entity (e.g. "&#38;quot;") stays literal ("&quot;"), it is NOT decoded
     * a second time.
     */
    fun decodeHtmlEntities(text: String): String = ENTITY_RE.replace(text) { matchResult ->
        val named = matchResult.groupValues[1]
        if (named.isNotEmpty()) {
            when (named) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> matchResult.value
            }
        } else {
            val code = if (matchResult.groupValues[2].isNotEmpty()) {
                matchResult.groupValues[2].toIntOrNull()
            } else {
                matchResult.groupValues[3].toIntOrNull(16)
            }
            if (code != null && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }
    }

    /**
     * Extracts the content attribute from a meta tag with the given property or name.
     *
     * Matches patterns like:
     *   <meta property="og:title" content="Some Title">
     *   <meta name="description" content="Some description">
     */
    fun extractMetaContent(html: String, property: String): String? {
        val escapedProp = Regex.escape(property)
        // Try double-quoted content first, then single-quoted.
        // Each pattern only excludes its own delimiter, so the other quote type is allowed.
        val dqPattern = Regex(
            """<meta\s+[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*content\s*=\s*"([^"]*)"[^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )
        dqPattern.find(html)?.let { return it.groupValues[1] }

        val sqPattern = Regex(
            """<meta\s+[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*content\s*=\s*'([^']*)'[^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )
        sqPattern.find(html)?.let { return it.groupValues[1] }

        // Also try reversed attribute order: content before property/name
        val dqReversed = Regex(
            """<meta\s+[^>]*content\s*=\s*"([^"]*)"[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )
        dqReversed.find(html)?.let { return it.groupValues[1] }

        val sqReversed = Regex(
            """<meta\s+[^>]*content\s*=\s*'([^']*)'[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )
        sqReversed.find(html)?.let { return it.groupValues[1] }

        return null
    }
}
