package com.ajthom90.kwikfinder.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Retrofit client for the KwikFinder catalog server (v1 endpoints).
 * Contract: docs/api/openapi.yaml
 */
interface KwikFinderApi {
    /** Lightweight catalog version probe (GET v1/meta). */
    @GET("v1/meta")
    suspend fun fetchMeta(): CatalogMeta

    /** Full catalog: meta + stores (GET v1/stores). */
    @GET("v1/stores")
    suspend fun fetchStores(): CatalogResponse
}

object KwikFinderApiFactory {
    fun create(
        baseUrl: String = APIConfig.baseUrl,
        client: OkHttpClient = defaultClient(),
    ): KwikFinderApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(APIConfig.json.asConverterFactory(contentType))
            .build()
            .create(KwikFinderApi::class.java)
    }

    fun defaultClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }
}
