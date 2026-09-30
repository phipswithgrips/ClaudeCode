package de.rezeptkiste

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.rezeptkiste.data.Repository
import de.rezeptkiste.db.RezeptDatabase
import de.rezeptkiste.sync.Api
import de.rezeptkiste.sync.AppJson
import de.rezeptkiste.sync.Hlc
import de.rezeptkiste.sync.PullResponse
import de.rezeptkiste.sync.PushRequest
import de.rezeptkiste.sync.PushResponse
import de.rezeptkiste.sync.Accepted
import de.rezeptkiste.sync.ServerRecord
import de.rezeptkiste.sync.SyncEngine
import de.rezeptkiste.sync.UnauthorizedException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Minimaler Server im Speicher mit derselben Konfliktregel wie der echte. */
private class FakeServer {
    val records = linkedMapOf<Pair<String, String>, ServerRecord>()
    var rev = 0L
    val pushes = mutableListOf<PushRequest>()
    var unauthorized = false

    fun put(type: String, id: String, updatedAt: String, data: JsonObject, deleted: Boolean = false) {
        rev++
        records[type to id] = ServerRecord(type, id, updatedAt, deleted, rev, data)
    }

    val engine = MockEngine { request ->
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        if (unauthorized) {
            respond("""{"detail":"Token ungültig."}""", HttpStatusCode.Unauthorized, json)
        } else when (request.url.encodedPath) {
            "/sync/pull" -> {
                val since = request.url.parameters["since"]!!.toLong()
                val limit = request.url.parameters["limit"]!!.toInt()
                val all = records.values.filter { it.serverRev > since }.sortedBy { it.serverRev }
                val page = all.take(limit)
                val body = PullResponse(page, page.lastOrNull()?.serverRev ?: since, all.size > limit)
                respond(AppJson.encodeToString(PullResponse.serializer(), body), HttpStatusCode.OK, json)
            }
            "/sync/push" -> {
                val req = AppJson.decodeFromString(PushRequest.serializer(), request.body.toByteArray().decodeToString())
                pushes += req
                val accepted = mutableListOf<Accepted>()
                val rejected = mutableListOf<ServerRecord>()
                for (r in req.records) {
                    val existing = records[r.type to r.id]
                    if (existing != null && r.updatedAt < existing.updatedAt) {
                        rejected += existing
                    } else {
                        put(r.type, r.id, r.updatedAt, r.data, r.deleted)
                        accepted += Accepted(r.type, r.id, rev)
                    }
                }
                respond(AppJson.encodeToString(PushResponse.serializer(), PushResponse(accepted, rejected)), HttpStatusCode.OK, json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
}

private fun recipeData(title: String, favourite: Boolean = false, categories: List<String> = emptyList()) = buildJsonObject {
    put("title", title)
    put("rating", 0)
    put("is_favourite", favourite)
    put("ingredients_text", "250 g Mehl\nOlivenöl 75 g")
    put("nutrition", buildJsonObject { put("kcal", "1798") })
    putJsonArray("category_ids") { categories.forEach { add(JsonPrimitive(it)) } }
}

private fun ts(ms: Long, node: String = "server") = Hlc.format(ms, 0, node)

class SyncEngineTest {
    private var clock = 1_000L
    private val repo = Repository(RezeptDatabase(createDesktopDriver(JdbcSqliteDriver.IN_MEMORY)))
    private val server = FakeServer()
    private val hlc = Hlc("pixel", { clock })
    private val engine = SyncEngine(Api("http://test", { "token" }, server.engine), repo, hlc, pageSize = 2)

    @Test
    fun firstSyncPullsEverythingAcrossPages() = runTest {
        server.put("category", "c1", ts(10), buildJsonObject { put("name", "Eis"); put("sort_order", 0) })
        server.put("recipe", "r1", ts(11), recipeData("Sorbet", categories = listOf("c1")))
        server.put("recipe", "r2", ts(12), recipeData("Brioche"))
        server.put("photo", "p1", ts(13), buildJsonObject { put("recipe_id", "r1"); put("sha256", "a".repeat(64)); put("sort_order", 0) })

        val result = engine.sync()

        assertEquals(4, result.pulled)
        assertEquals(4L, repo.cursor)
        val sorbet = repo.recipe("r1")!!
        assertEquals("Sorbet", sorbet.title)
        assertEquals("[\"c1\"]", sorbet.category_ids)
        assertEquals("250 g Mehl\nOlivenöl 75 g", sorbet.ingredients_text)
        assertTrue(sorbet.nutrition!!.contains("1798"))
    }

    @Test
    fun localChangeIsPushedAndMarkedClean() = runTest {
        server.put("recipe", "r1", ts(10), recipeData("Sorbet"))
        engine.sync()

        clock = 5_000
        repo.setFavourite("r1", true, hlc.now())
        assertEquals(1, repo.dirtyRecords().size)

        engine.sync()

        assertEquals(1, server.pushes.single().records.size)
        assertEquals(JsonPrimitive(true), server.records["recipe" to "r1"]!!.data["is_favourite"])
        assertEquals(0, repo.dirtyRecords().size)
        assertEquals(1L, repo.recipe("r1")!!.is_favourite)
    }

    @Test
    fun localEditIsNewerThanEverythingSeenEvenWithSlowClock() = runTest {
        // Server-Zeitstempel liegt weit in der Zukunft der Geräteuhr
        server.put("recipe", "r1", ts(9_000_000), recipeData("Sorbet"))
        engine.sync()
        repo.setFavourite("r1", true, hlc.now())
        engine.sync()
        assertEquals(JsonPrimitive(true), server.records["recipe" to "r1"]!!.data["is_favourite"])
    }

    @Test
    fun rejectedPushTakesServerVersion() = runTest {
        server.put("recipe", "r1", ts(10), recipeData("Sorbet"))
        engine.sync()
        clock = 20
        repo.setFavourite("r1", true, hlc.now())
        // Anderes Gerät hat inzwischen neuer gespeichert, noch nicht abgeholt
        server.put("recipe", "r1", ts(900_000, "windows"), recipeData("Sorbet Windows"))

        engine.sync()

        val local = repo.recipe("r1")!!
        assertEquals("Sorbet Windows", local.title)
        assertEquals(0L, local.dirty)
    }

    @Test
    fun pullDoesNotOverwriteNewerUnsentLocalChange() {
        server.put("recipe", "r1", ts(10), recipeData("Sorbet"))
        runTest { engine.sync() }
        clock = 50_000
        repo.setFavourite("r1", true, hlc.now())
        val older = ServerRecord("recipe", "r1", ts(20_000, "windows"), false, 99, recipeData("alt"))
        repo.applyServerRecords(listOf(older))
        assertEquals("Sorbet", repo.recipe("r1")!!.title)
        assertEquals(1L, repo.recipe("r1")!!.dirty)
    }

    @Test
    fun deletedRecordsAreHidden() = runTest {
        server.put("recipe", "r1", ts(10), recipeData("Sorbet"))
        server.put("recipe", "r1", ts(20), recipeData("Sorbet"), deleted = true)
        engine.sync()
        assertEquals(1L, repo.recipe("r1")!!.deleted)
    }

    @Test
    fun unauthorizedIsReported() = runTest {
        server.unauthorized = true
        assertFailsWith<UnauthorizedException> { engine.sync() }
    }
}
