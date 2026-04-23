package com.dustvalve.next.android.data.remote.youtube.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Loads a JSON fixture from the classpath under test/resources/fixtures/youtube/. */
internal object Fixtures {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(name: String): JsonElement {
        val stream = Fixtures::class.java.classLoader
            .getResourceAsStream("fixtures/youtube/$name")
            ?: error("Missing test fixture: fixtures/youtube/$name")
        return stream.use { json.parseToJsonElement(it.bufferedReader().readText()) }
    }

    fun loadString(name: String): String {
        val stream = Fixtures::class.java.classLoader
            .getResourceAsStream("fixtures/youtube/$name")
            ?: error("Missing test fixture: fixtures/youtube/$name")
        return stream.use { it.bufferedReader().readText() }
    }
}
