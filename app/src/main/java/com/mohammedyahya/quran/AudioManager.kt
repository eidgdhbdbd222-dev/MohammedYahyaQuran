package com.mohammedyahya.quran

import android.content.Context
import android.media.MediaPlayer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AudioStore {
    private const val BASE = "https://everyayah.com/data/"
    private val pool = Executors.newFixedThreadPool(3)

    fun fileName(s: Int, a: Int) = String.format("%03d%03d.mp3", s, a)
    fun localFile(ctx: Context, r: Reciter, s: Int, a: Int): File {
        val dir = File(ctx.filesDir, "audio/${r.id}"); dir.mkdirs()
        return File(dir, fileName(s, a))
    }
    fun remoteUrl(r: Reciter, s: Int, a: Int) = BASE + r.folder + "/" + fileName(s, a)

    fun isDownloaded(ctx: Context, r: Reciter, s: Int, a: Int) = localFile(ctx, r, s, a).let { it.exists() && it.length() > 0 }

    /** Download one ayah (blocking). Returns true on success. */
    fun download(ctx: Context, r: Reciter, s: Int, a: Int): Boolean {
        val f = localFile(ctx, r, s, a)
        if (f.exists() && f.length() > 0) return true
        return try {
            val c = URL(remoteUrl(r, s, a)).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.inputStream.use { inp -> File(f.path + ".tmp").outputStream().use { inp.copyTo(it) } }
            File(f.path + ".tmp").renameTo(f)
        } catch (e: Exception) { false }
    }

    fun downloadSurah(ctx: Context, r: Reciter, surah: Surah, onProgress: (Int, Int) -> Unit, onDone: (Int) -> Unit) {
        pool.execute {
            var ok = 0
            surah.ayahs.forEachIndexed { i, a ->
                if (download(ctx, r, surah.num, a.num)) ok++
                onProgress(i + 1, surah.ayahs.size)
            }
            onDone(ok)
        }
    }
}

class AyahPlayer(private val ctx: Context) {
    private var mp: MediaPlayer? = null
    var onComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun play(r: Reciter, s: Int, a: Int) {
        stop()
        val local = AudioStore.localFile(ctx, r, s, a)
        val src = if (local.exists() && local.length() > 0) local.path else AudioStore.remoteUrl(r, s, a)
        try {
            mp = MediaPlayer().apply {
                setDataSource(src)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { onComplete?.invoke() }
                setOnErrorListener { _, _, _ -> onError?.invoke("تعذر تشغيل الآية — حمّل السورة أولاً أو تأكد من الإنترنت"); true }
                prepareAsync()
            }
        } catch (e: Exception) { onError?.invoke(e.message ?: "خطأ") }
    }

    fun stop() { mp?.let { try { it.stop() } catch (_: Exception) {}; it.release() }; mp = null }
    fun isPlaying() = mp?.isPlaying == true
}
