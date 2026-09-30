package com.jipraks.ytshortclipper

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Manages the bundled ggml-base.bin Whisper model. */
class WhisperModelManager(private val context: Context) {
    companion object {
        const val ASSET_PATH = "whisper/ggml-base.bin"
        const val FILE_NAME = "ggml-base.bin"
        // Official whisper.cpp base model SHA-1.
        const val EXPECTED_SHA1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac"
    }

    fun modelFile(): File {
        val out = File(context.filesDir, "whisper/$FILE_NAME")
        if (!out.exists() || out.length() == 0L) {
            copyBundledModel(out)
        }
        return out
    }

    fun isBundled(): Boolean = try {
        context.assets.open(ASSET_PATH).use { it.available() > 100_000_000 }
    } catch (_: Exception) { false }

    private fun copyBundledModel(out: File) {
        out.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            out.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        }
    }

    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
