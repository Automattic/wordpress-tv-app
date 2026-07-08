package com.automattic.wordpresstv.core.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText

internal data class PlatformHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal class PlatformHttpClient {
    private val client = HttpClient(platformHttpClientEngine()) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): PlatformHttpResponse {
        val response = client.get(url) {
            headers {
                headers.forEach { (name, value) -> append(name, value) }
            }
        }
        return PlatformHttpResponse(
            statusCode = response.status.value,
            body = response.bodyAsText(),
        )
    }
}

internal expect fun platformHttpClientEngine(): HttpClientEngineFactory<*>
