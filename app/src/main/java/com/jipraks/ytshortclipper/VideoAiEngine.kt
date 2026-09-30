package com.jipraks.ytshortclipper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** On-device analysis: ML Kit face tracking + Whisper transcript + deterministic highlight scoring. */
class VideoAiEngine(private val context: Context) {
    private val whisperModel = WhisperModelManager(context)

    /** Returns the local Whisper model, copying the bundled asset on first use. */
    fun hasBundledWhisperModel(): Boolean = try {
        context.assets.open(WhisperModelManager.ASSET_PATH).use { it.available() > 100_000_000 }
    } catch (_: Exception) { false }

    suspend fun prepareWhisperModel(): File = withContext(Dispatchers.IO) {
        val file = whisperModel.modelFile()
        require(file.length() > 100_000_000L) { "Bundled Whisper model is missing or incomplete." }
        file
    }
    data class FaceTrack(val timeMs: Long, val centerX: Float, val faceWidth: Float)
    data class Segment(val startMs: Long, val endMs: Long, val text: String, val score: Double)

    suspend fun trackFaces(uri: Uri): List<FaceTrack> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (duration <= 0) return@withContext emptyList()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .enableTracking()
                .build()
        )
        val result = mutableListOf<FaceTrack>()
        try {
            var t = 0L
            while (t < duration) {
                val bmp = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val image = InputImage.fromBitmap(bmp, 0)
                    val faces = detector.process(image).await()
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    if (face != null) {
                        val box = face.boundingBox
                        result += FaceTrack(t, (box.centerX().toFloat() / bmp.width).coerceIn(0f, 1f), box.width().toFloat() / bmp.width)
                    }
                    bmp.recycle()
                }
                t += 1000L
            }
        } finally {
            detector.close()
            retriever.release()
        }
        result
    }

    fun chooseCropX(tracks: List<FaceTrack>): Double {
        if (tracks.isEmpty()) return 0.5
        val sorted = tracks.map { it.centerX }.sorted()
        return sorted[sorted.size / 2].toDouble().coerceIn(0.28125, 0.71875)
    }

    fun buildSrt(segments: List<Segment>): String = buildString {
        segments.forEachIndexed { i, s ->
            append(i + 1).append('\n')
            append(toSrtTime(s.startMs)).append(" --> ").append(toSrtTime(s.endMs)).append('\n')
            append(s.text.trim()).append("\n\n")
        }
    }

    private fun toSrtTime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val z = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, z)
    }

    /** Scores transcript segments for short-form highlights without requiring a cloud API. */
    fun scoreSegments(raw: List<Segment>): List<Segment> {
        val hooks = listOf("wow", "gila", "ternyata", "akhirnya", "ternyata", "rahasia", "penting", "jangan", "lihat", "kenapa", "bagaimana", "serius", "tunggu", "wow", "amazing", "secret", "finally", "important", "why", "how", "wait")
        return raw.map { s ->
            val lower = s.text.lowercase()
            var score = 0.0
            score += min(3.0, lower.length / 45.0)
            hooks.forEach { if (lower.contains(it)) score += 2.0 }
            if (lower.contains("?") || lower.contains("!")) score += 1.5
            val duration = (s.endMs - s.startMs).coerceAtLeast(1)
            if (duration in 1500..8000) score += 1.0
            s.copy(score = score)
        }.sortedByDescending { it.score }
    }
}
