package com.ajthom90.kwikfinder.data

import kotlinx.serialization.json.Json

/**
 * Base URL and shared JSON config for the KwikFinder catalog server.
 *
 * **Debug / emulator default:** `http://10.0.2.2:8080`
 * (`10.0.2.2` is the Android emulator’s alias for the host machine’s loopback.)
 *
 * **Physical device:** point at your TrueNAS / LAN host, e.g.
 * `http://192.168.1.50:8080` — set [baseUrl] before building the API client,
 * or pass a custom base URL into [KwikFinderApiFactory.create].
 *
 * Contract: `docs/api/openapi.yaml`.
 */
object APIConfig {
    /** Emulator → host machine Docker Compose / local server. */
    const val EMULATOR_BASE_URL: String = "http://10.0.2.2:8080"

    /**
     * Active base URL. Defaults to the emulator host alias.
     * Override at app startup for device / TrueNAS deployments.
     */
    @Volatile
    var baseUrl: String = EMULATOR_BASE_URL

    /** Shared kotlinx.serialization JSON matching camelCase OpenAPI payloads. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
}
