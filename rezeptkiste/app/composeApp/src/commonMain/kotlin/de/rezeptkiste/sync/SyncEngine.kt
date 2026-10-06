package de.rezeptkiste.sync

import de.rezeptkiste.data.Repository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncResult(val pushed: Int, val rejected: Int, val errors: Int, val pulled: Int)

/**
 * Ein Sync-Lauf: erst eigene Änderungen hochladen, dann alles Neue abholen.
 * Läufe werden nie parallel ausgeführt.
 */
class SyncEngine(
    private val api: Api,
    private val repo: Repository,
    private val hlc: Hlc,
    private val pageSize: Int = 500,
    /** Liefert die Bilddatei eines neuen Fotos aus dem lokalen Speicher. */
    private val localFile: (String) -> ByteArray? = { null },
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        uploadPhotos()
        val (pushed, rejected, errors) = push()
        val pulled = pull()
        SyncResult(pushed, rejected, errors, pulled)
    }

    /** Bilddateien neuer Fotos hochladen, bevor ihre Datensätze gepusht werden. */
    private suspend fun uploadPhotos() {
        for (rec in repo.dirtyRecords().filter { it.type == EntityType.PHOTO && !it.deleted }) {
            val sha = rec.data["sha256"]?.toString()?.trim('"') ?: continue
            if (api.fileExists(sha)) continue
            val bytes = localFile(sha) ?: continue
            api.putFile(sha, bytes)
        }
    }

    private suspend fun push(): Triple<Int, Int, Int> {
        var accepted = 0
        var rejected = 0
        var errors = 0
        val (extra, core) = repo.dirtyRecords().partition { it.type == EntityType.SHOPPING_ITEM }
        // Die Einkaufsliste getrennt senden: ein älterer Server ohne Einkaufsliste lehnt sie ab,
        // Rezepte sollen trotzdem synchron bleiben.
        val batches = core.chunked(pageSize).map { it to false } + extra.chunked(pageSize).map { it to true }
        for ((batch, optional) in batches) {
            val sent = batch.associateBy { it.type to it.id }
            val res = try {
                api.push(PushRequest(batch))
            } catch (e: ApiException) {
                if (optional && e !is UnauthorizedException) continue else throw e
            }
            for (a in res.accepted) {
                val rec = sent[a.type to a.id] ?: continue
                repo.markAccepted(a.type, a.id, a.serverRev, rec.updatedAt)
            }
            // Server-Fassung ist jünger: sie ersetzt die lokale
            repo.applyServerRecords(res.rejected, force = true)
            res.rejected.forEach { hlc.observe(it.updatedAt) }
            accepted += res.accepted.size
            rejected += res.rejected.size
            errors += res.errors.size
        }
        return Triple(accepted, rejected, errors)
    }

    private suspend fun pull(): Int {
        var cursor = repo.cursor
        var total = 0
        while (true) {
            val page = api.pull(cursor, pageSize)
            total += repo.applyServerRecords(page.records)
            page.records.forEach { hlc.observe(it.updatedAt) }
            cursor = page.cursor
            repo.cursor = cursor
            if (!page.hasMore) break
        }
        return total
    }
}
