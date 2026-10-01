package de.rezeptkiste.sync

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class ApiException(message: String, val status: Int? = null) : Exception(message)

class UnauthorizedException : ApiException("Anmeldung abgelaufen oder Gerät gesperrt.", 401)

/** Zugriff auf die Rezeptkiste-API. */
class Api(
    baseUrl: String,
    private val token: () -> String?,
    engine: HttpClientEngine? = null,
) {
    val baseUrl: String = normalizeUrl(baseUrl)

    private val client: HttpClient = if (engine != null) HttpClient(engine) { configure() } else HttpClient { configure() }

    private fun HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(ContentNegotiation) { json(AppJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
        }
    }

    private fun HttpRequestBuilder.auth() {
        token()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    suspend fun login(username: String, password: String, deviceName: String): DeviceToken {
        val res = client.post("$baseUrl/auth/device") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLogin(username, password, deviceName))
        }
        return check(res).body()
    }

    suspend fun pull(since: Long, limit: Int = 500): PullResponse {
        val res = client.get("$baseUrl/sync/pull") {
            auth()
            parameter("since", since)
            parameter("limit", limit)
        }
        return check(res).body()
    }

    suspend fun push(request: PushRequest): PushResponse {
        val res = client.post("$baseUrl/sync/push") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return check(res).body()
    }

    /** Bilddatei laden; size = thumb | medium | original. */
    suspend fun file(sha256: String, size: String): ByteArray? {
        val res = client.get("$baseUrl/files/$sha256") {
            auth()
            parameter("size", size)
        }
        if (res.status == HttpStatusCode.NotFound) return null
        return check(res).body()
    }

    suspend fun fileExists(sha256: String): Boolean {
        val res = client.head("$baseUrl/files/$sha256") { auth() }
        if (res.status == HttpStatusCode.Unauthorized) throw UnauthorizedException()
        return res.status.isSuccess()
    }

    suspend fun putFile(sha256: String, bytes: ByteArray) {
        val res = client.put("$baseUrl/files/$sha256") {
            auth()
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        check(res)
    }

    /** Recipe-Keeper-Export hochladen und importieren; liefert die Antwort des Servers als Text. */
    suspend fun importRecipeKeeper(fileName: String, bytes: ByteArray): String {
        val res = client.submitFormWithBinaryData(
            url = "$baseUrl/import/recipekeeper?commit=true",
            formData = formData {
                append("file", bytes, io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, "application/zip")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            },
        ) { auth() }
        return check(res).bodyAsText()
    }

    private suspend fun check(res: HttpResponse): HttpResponse {
        if (res.status.isSuccess()) return res
        if (res.status == HttpStatusCode.Unauthorized && !res.call.request.url.encodedPath.endsWith("/auth/device")) {
            throw UnauthorizedException()
        }
        val detail = runCatching {
            AppJson.parseToJsonElement(res.bodyAsText()).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
        throw ApiException(detail ?: "Serverfehler ${res.status.value}", res.status.value)
    }

    fun close() = client.close()

    companion object {
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
