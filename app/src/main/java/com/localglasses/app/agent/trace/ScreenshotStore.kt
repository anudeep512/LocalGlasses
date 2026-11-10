package com.localglasses.app.agent.trace

import java.io.File

/**
 * Failure-only screenshots with a hard cap. Secure and hard-blocked screens never
 * reach this store.
 */
class ScreenshotStore(
    private val directory: File,
    private val maxFiles: Int = 50,
) {

    fun save(name: String, png: ByteArray): File {
        directory.mkdirs()
        val file = File(directory, name)
        file.writeBytes(png)
        prune()
        return file
    }

    fun files(): List<File> =
        directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()

    fun prune() {
        val all = files()
        if (all.size <= maxFiles) return
        all.take(all.size - maxFiles).forEach { it.delete() }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }
}
