package de.rezeptkiste.sync

/**
 * Hybrid Logical Clock im Format des Servers:
 * `<Millisekunden, 15 Stellen>-<Zähler, 5 Stellen>-<Knoten>`.
 *
 * Der Zeichenkettenvergleich entspricht der zeitlichen Reihenfolge. Nach jedem
 * Pull merkt sich die Uhr den jüngsten gesehenen Zeitstempel, damit eine lokale
 * Änderung immer jünger ist als alles, was das Gerät schon kennt, auch wenn
 * seine Uhr nachgeht.
 */
class Hlc(node: String, private val clock: () -> Long) {
    private val node: String = sanitize(node)
    private var lastMs = 0L
    private var counter = 0L

    @kotlin.jvm.Synchronized
    fun now(): String {
        val physical = clock()
        if (physical > lastMs) {
            lastMs = physical
            counter = 0
        } else {
            counter++
        }
        return format(lastMs, counter, node)
    }

    @kotlin.jvm.Synchronized
    fun observe(remote: String) {
        val ms = remote.take(15).toLongOrNull() ?: return
        val c = remote.drop(16).take(5).toLongOrNull() ?: 0L
        if (ms > lastMs) {
            lastMs = ms
            counter = c
        } else if (ms == lastMs && c > counter) {
            counter = c
        }
    }

    companion object {
        private val invalid = Regex("[^A-Za-z0-9_.]")

        fun sanitize(node: String): String = node.replace(invalid, "").take(32).ifEmpty { "device" }

        fun format(ms: Long, counter: Long, node: String): String =
            "${ms.toString().padStart(15, '0')}-${counter.toString().padStart(5, '0')}-$node"
    }
}
