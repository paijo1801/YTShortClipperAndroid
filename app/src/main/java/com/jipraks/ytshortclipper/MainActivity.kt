package com.jipraks.ytshortclipper

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperResult
import dev.ffmpegkit.whisper.WhisperModel
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var sourceUri: Uri? = null
    private lateinit var status: TextView
    private lateinit var aiButton: MaterialButton
    private lateinit var modelButton: MaterialButton
    private var lastSrt: File? = null
    private var faceX = 0.5
    private var highlightStartMs = 0L
    private var highlightDurationMs = 30_000L
    private val engine by lazy { VideoAiEngine(this) }
    private var whisperModel: WhisperModel? = null

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        sourceUri = uri
        player?.setMediaItem(MediaItem.fromUri(uri)); player?.prepare(); player?.playWhenReady = false
        status.text = "Video siap. Jalankan AI Highlights untuk mencari bagian terbaik."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        aiButton = findViewById(R.id.aiButton)
        modelButton = findViewById(R.id.modelButton)
        player = ExoPlayer.Builder(this).build().also { findViewById<PlayerView>(R.id.player).player = it }

        findViewById<MaterialButton>(R.id.importButton).setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        findViewById<MaterialButton>(R.id.downloadButton).setOnClickListener { downloadYouTube() }
        findViewById<MaterialButton>(R.id.aiButton).setOnClickListener { runAiPipeline() }
        modelButton.setOnClickListener { status.text = if (engine.hasBundledWhisperModel()) "AI Model terpasang ✓" else "AI Model belum dimasukkan ke assets/whisper/ggml-base.bin" }
        findViewById<MaterialButton>(R.id.trimButton).setOnClickListener {
            if (sourceUri == null) toast("Pilih video terlebih dahulu") else status.text = "Mode otomatis: highlight ${highlightStartMs / 1000}s – ${(highlightStartMs + highlightDurationMs) / 1000}s"
        }
        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener { exportShort() }
        updateModelState()
    }

    private fun updateModelState() {
        modelButton.text = if (engine.hasBundledWhisperModel()) "AI Model ✓" else "AI Model Missing"
    }

    private fun downloadYouTube() {
        val url = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.urlInput).text?.toString()?.trim().orEmpty()
        if (url.isBlank()) { toast("Masukkan URL YouTube"); return }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@MainActivity)
                val outDir = File(getExternalFilesDir("downloads"), "yt").apply { mkdirs() }
                val template = File(outDir, "%(title)s.%(ext)s").absolutePath
                val req = YoutubeDLRequest(url).apply {
                    addOption("-o", template)
                    addOption("-f", "bv*[height<=1080]+ba/b[height<=1080]")
                    addOption("--merge-output-format", "mp4")
                    addOption("--no-playlist")
                }
                withContext(Dispatchers.Main) { status.text = "Mengunduh YouTube…" }
                val response = YoutubeDL.getInstance().execute(req) { progress, _, _ -> runOnUiThread { status.text = "Download YouTube: ${progress.toInt()}%" } }
                var found: File? = outDir.listFiles()?.filter { it.isFile && it.length() > 1_000_000 }?.maxByOrNull { it.lastModified() }
                found?.let { f ->
                    sourceUri = Uri.fromFile(f)
                    runOnUiThread { player?.setMediaItem(MediaItem.fromUri(sourceUri!!)); player?.prepare(); status.text = "Download selesai: ${f.name}" }
                } ?: runOnUiThread { status.text = "Download belum selesai atau gagal." }
            } catch (e: Exception) { withContext(Dispatchers.Main) { status.text = "Download gagal: ${e.message}" } }
        }
    }

    private fun runAiPipeline() {
        val uri = sourceUri ?: run { toast("Pilih/download video terlebih dahulu"); return }
        if (!engine.hasBundledWhisperModel()) { toast("Masukkan ggml-base.bin ke app/src/main/assets/whisper/"); return }
        aiButton.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                statusMain("AI: tracking wajah…")
                faceX = engine.chooseCropX(engine.trackFaces(uri))
                val input = copyUriToCache(uri, "input_ai")
                val wav = File(cacheDir, "audio_ai.wav")
                extractAudio(input, wav)
                statusMain("AI: transkripsi & mencari highlight…")
                if (whisperModel == null) whisperModel = Whisper.loadModelFromAsset(this@MainActivity, "whisper/ggml-base.bin")
                val result: WhisperResult = Whisper.transcribe(whisperModel!!, wav.absolutePath, WhisperConfig(language = "id"))
                val raw = result.segments.map { VideoAiEngine.Segment(it.startMs, it.endMs, it.text, 0.0) }
                val scored = engine.scoreSegments(raw)
                val best = scored.firstOrNull()
                if (best != null) {
                    highlightStartMs = maxOf(0L, best.startMs - 3000L)
                    highlightDurationMs = minOf(30_000L, maxOf(8_000L, best.endMs - best.startMs + 6000L))
                }
                val srt = File(cacheDir, "captions.srt"); srt.writeText(engine.buildSrt(raw)); lastSrt = srt
                statusMain("AI selesai ✓ Wajah terdeteksi, highlight dipilih, subtitle siap.\nHighlight: ${highlightStartMs/1000}s – ${(highlightStartMs+highlightDurationMs)/1000}s")
            } catch (e: Exception) { statusMain("AI gagal: ${e.message}") }
            finally { aiButton.isEnabled = true }
        }
    }

    private suspend fun extractAudio(input: File, wav: File) = withContext(Dispatchers.IO) {
        val cmd = "-y -i ${q(input.absolutePath)} -vn -ac 1 -ar 16000 -c:a pcm_s16le ${q(wav.absolutePath)}"
        val session = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(session.returnCode)) error("Ekstraksi audio gagal")
    }

    private fun exportShort() {
        val uri = sourceUri ?: run { toast("Pilih video terlebih dahulu"); return }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                statusMain("Export Shorts 9:16 + face crop + subtitle…")
                val input = copyUriToCache(uri, "export_input")
                val output = File(cacheDir, "short_${System.currentTimeMillis()}.mp4")
                val cropW = "ih*9/16"
                val x = "(iw-$cropW)*${faceX.coerceIn(0.28125, 0.71875)}"
                val vf = buildString {
                    append("crop=w=$cropW:h=ih:x=$x:y=0,scale=1080:1920")
                    lastSrt?.takeIf { it.exists() }?.let { append(",subtitles='${escapeFilterPath(it.absolutePath)}'") }
                }
                val cmd = "-y -ss ${highlightStartMs/1000.0} -t ${highlightDurationMs/1000.0} -i ${q(input.absolutePath)} -vf \"$vf\" -c:v h264_mediacodec -b:v 6M -c:a aac -b:a 128k -movflags +faststart ${q(output.absolutePath)}"
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) error(session.failStackTrace ?: "FFmpeg export gagal")
                saveToGallery(output)
                statusMain("Export selesai ✓ Tersimpan di Movies/YT Short Clipper")
            } catch (e: Exception) { statusMain("Export gagal: ${e.message}") }
        }
    }

    private fun copyUriToCache(uri: Uri, prefix: String): File {
        val out = File(cacheDir, "$prefix.mp4")
        if (uri.scheme == "file") return File(uri.path!!)
        contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: error("Tidak bisa membaca video")
        return out
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, file.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YT Short Clipper") }
        val out = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gagal membuat file galeri")
        contentResolver.openOutputStream(out)?.use { dst -> file.inputStream().use { it.copyTo(dst) } }
        file.delete()
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun escapeFilterPath(s: String) = s.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")
    private fun statusMain(s: String) = runOnUiThread { status.text = s }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { whisperModel?.let { Whisper.releaseModel(it) }; player?.release(); player = null; super.onDestroy() }
}
