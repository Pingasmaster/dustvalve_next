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
        val jsonStart = findAssignedJsonStart(html, variableName) ?: return null
        val endInclusive = scanBalancedJsonEnd(html, jsonStart) ?: return null
        return html.substring(jsonStart, endInclusive + 1)
    }

    /** Locates the `{` or `[` that starts the RHS of `var Name = ...` / `Name = ...`. */
    private fun findAssignedJsonStart(html: String, variableName: String): Int? {
        val varPattern = Regex(
            """(?:var\s+)?${Regex.escape(variableName)}\s*=\s*""",
            RegexOption.MULTILINE,
        )
        val matchResult = varPattern.find(html) ?: return null
        val startOfJson = matchResult.range.last + 1
        if (startOfJson >= html.length) return null

        val braceIdx = html.indexOf('{', startOfJson)
        val bracketIdx = html.indexOf('[', startOfJson)
        val jsonStart = when {
            braceIdx == -1 && bracketIdx == -1 -> return null
            braceIdx == -1 -> bracketIdx
            bracketIdx == -1 -> braceIdx
            else -> minOf(braceIdx, bracketIdx)
        }

        // Make sure there's no significant non-whitespace between the assignment and the JSON start
        val between = html.substring(startOfJson, jsonStart).trim()
        return jsonStart.takeIf { between.isEmpty() }
    }

    /**
     * Walks from [jsonStart] (a `{` or `[`) to the matching closer, honouring
     * strings and JS comments. Returns the inclusive end index, or null if
     * the input ends before the structure closes.
     */
    private fun scanBalancedJsonEnd(html: String, jsonStart: Int): Int? {
        val openChar = html[jsonStart]
        val closeChar = if (openChar == '{') '}' else ']'
        var depth = 0
        var inString = false
        var stringChar = ' '
        var escaped = false
        var i = jsonStart

        while (i < html.length) {
            val step = advanceJsonScan(
                html = html,
                index = i,
                openChar = openChar,
                closeChar = closeChar,
                depth = depth,
                inString = inString,
                stringChar = stringChar,
                escaped = escaped,
            )
            when (step) {
                is JsonScanStep.Found -> return step.endInclusive
                is JsonScanStep.Continue -> {
                    depth = step.depth
                    inString = step.inString
                    stringChar = step.stringChar
                    escaped = step.escaped
                    i = step.nextIndex
                }
            }
        }
        return null
    }

    private sealed class JsonScanStep {
        data class Found(val endInclusive: Int) : JsonScanStep()
        data class Continue(
            val nextIndex: Int,
            val depth: Int,
            val inString: Boolean,
            val stringChar: Char,
            val escaped: Boolean,
        ) : JsonScanStep()
    }

    private fun advanceJsonScan(
        html: String,
        index: Int,
        openChar: Char,
        closeChar: Char,
        depth: Int,
        inString: Boolean,
        stringChar: Char,
        escaped: Boolean,
    ): JsonScanStep {
        val c = html[index]

        if (escaped) {
            return JsonScanStep.Continue(index + 1, depth, inString, stringChar, escaped = false)
        }

        if (c == '\\' && inString) {
            return JsonScanStep.Continue(index + 1, depth, inString, stringChar, escaped = true)
        }

        if (inString) {
            val stillInString = c != stringChar
            return JsonScanStep.Continue(index + 1, depth, stillInString, stringChar, escaped = false)
        }

        // Not in a string - skip JS comments
        if (c == '/' && index + 1 < html.length) {
            val next = html[index + 1]
            val commentEnd = when (next) {
                '/' -> {
                    val lineEnd = html.indexOf('\n', index + 2)
                    if (lineEnd == -1) html.length else lineEnd + 1
                }

                '*' -> {
                    val blockEnd = html.indexOf("*/", index + 2)
                    if (blockEnd == -1) html.length else blockEnd + 2
                }

                else -> null
            }
            if (commentEnd != null) {
                return JsonScanStep.Continue(commentEnd, depth, inString = false, stringChar, escaped = false)
            }
        }

        if (c == '"' || c == '\'' || c == '`') {
            return JsonScanStep.Continue(index + 1, depth, inString = true, stringChar = c, escaped = false)
        }

        return when (c) {
            openChar -> JsonScanStep.Continue(index + 1, depth + 1, inString = false, stringChar, escaped = false)

            closeChar -> {
                val newDepth = depth - 1
                if (newDepth == 0) {
                    JsonScanStep.Found(index)
                } else {
                    JsonScanStep.Continue(index + 1, newDepth, inString = false, stringChar, escaped = false)
                }
            }

            else -> JsonScanStep.Continue(index + 1, depth, inString = false, stringChar, escaped = false)
        }
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
        // Also try reversed attribute order: content before property/name.
        val patterns = listOf(
            Regex(
                """<meta\s+[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*content\s*=\s*"([^"]*)"[^>]*/?>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\s+[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*content\s*=\s*'([^']*)'[^>]*/?>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\s+[^>]*content\s*=\s*"([^"]*)"[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*/?>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\s+[^>]*content\s*=\s*'([^']*)'[^>]*(?:property|name)\s*=\s*["']$escapedProp["'][^>]*/?>""",
                RegexOption.IGNORE_CASE,
            ),
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.get(1)
        }
    }
}
