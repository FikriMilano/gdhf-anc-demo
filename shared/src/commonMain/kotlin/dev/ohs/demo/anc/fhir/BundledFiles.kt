package dev.ohs.demo.anc.fhir

import dev.ohs.demo.anc.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app's bundled FHIR files, each read once and kept in memory. On the web every read is a
 * fetch from the page's server, so [preload] runs at startup while that server is reachable; after
 * that, opening a patient or the form works with no connection.
 */
object BundledFiles {
    private val files = listOf(
        "files/registration-questionnaire.json",
        "files/high-bp-plan.json",
        "files/seed-bundle.json",
    )
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    suspend fun read(path: String): String =
        mutex.withLock { cache.getOrPut(path) { Res.readBytes(path).decodeToString() } }

    suspend fun preload() = files.forEach { read(it) }
}
