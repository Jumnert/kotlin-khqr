package dev.khqr.internal

import dev.khqr.BakongEnvironment
import dev.khqr.KHQRException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Thin HTTP client for the Bakong Open API.
 *
 * Uses the JDK's built-in [HttpClient] (no external HTTP dependency). JSON is parsed
 * with kotlinx-serialization. All failures surface as [KHQRException].
 */
internal class BakongClient(
    private val token: String?,
    environment: BakongEnvironment,
    private val httpClient: HttpClient = defaultClient(),
    private val timeout: Duration = Duration.ofSeconds(15),
    baseUrlOverride: String? = null,
) {
    private val baseUrl: String = baseUrlOverride
        ?: if (token != null && token.startsWith("rbk")) RELAY_BASE_URL else environment.baseUrl

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /** POST a JSON object body and return the parsed response object. */
    fun postObject(endpoint: String, body: JsonObject, requireAuth: Boolean = true): JsonObject =
        post(endpoint, json.encodeToString(JsonObject.serializer(), body), requireAuth)

    /** POST a JSON array body (used by the bulk check endpoint). */
    fun postArray(endpoint: String, body: JsonArray, requireAuth: Boolean = true): JsonObject =
        post(endpoint, json.encodeToString(JsonArray.serializer(), body), requireAuth)

    private fun post(endpoint: String, bodyJson: String, requireAuth: Boolean): JsonObject {
        if (requireAuth && token.isNullOrBlank()) {
            throw KHQRException(
                "A Bakong Developer Token is required for this operation. " +
                    "Create the KHQR instance with a token, e.g. KHQR(\"your_token_here\").",
            )
        }

        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1$endpoint"))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "kotlin-bakong/$LIBRARY_VERSION (+https://github.com/Jumnert/Kotlin-Bakong)")
            .POST(HttpRequest.BodyPublishers.ofString(bodyJson, Charsets.UTF_8))

        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val response: HttpResponse<String> = try {
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        } catch (e: java.net.http.HttpTimeoutException) {
            throw KHQRException("Bakong API took too long to respond. Please check the transaction status later.", e)
        } catch (e: Exception) {
            throw KHQRException("Failed to connect to Bakong API: ${e.message}", e)
        }

        val status = response.statusCode()
        val text = response.body()
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject

        if (status == 200 || status == 201) {
            return parsed ?: throw KHQRException("Bakong returned invalid JSON: $text")
        }

        // Some errors (e.g. transaction-not-found) still arrive as a valid body
        // carrying a responseCode; pass those through so callers can interpret them.
        if (parsed != null && parsed.containsKey("responseCode")) {
            return parsed
        }

        throw KHQRException(mapHttpError(status, text))
    }

    private fun mapHttpError(status: Int, body: String): String = when (status) {
        400 -> "Bad request. Please check your input parameters and try again."
        401 -> "Your Developer Token is either incorrect or expired. Please renew it via the Bakong Developer portal."
        403 -> "Bakong API only accepts requests from Cambodia IP addresses. Your server IP may be blocked or restricted."
        404 -> "The requested Bakong API endpoint does not exist. Please check the endpoint URL."
        429 -> "Too many requests. Please wait a while before trying again."
        500 -> "Bakong server encountered an internal error. Please try again later."
        504 -> "Bakong server is busy, please try again later."
        else -> "HTTP $status: $body"
    }

    companion object {
        const val RELAY_BASE_URL = "https://api.bakongrelay.com"

        private fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        /** Convenience builders for request bodies. */
        fun md5Body(md5: String): JsonObject = buildJsonObject { put("md5", md5) }

        fun md5ListBody(md5List: List<String>): JsonArray =
            JsonArray(md5List.map { JsonPrimitive(it) })
    }
}

internal const val LIBRARY_VERSION = "0.1.0"

/** Read a top-level integer field, treating missing/invalid as null. */
internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
