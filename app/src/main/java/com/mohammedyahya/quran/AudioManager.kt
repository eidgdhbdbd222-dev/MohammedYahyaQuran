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
    private val bundledCache = HashMap<String, Boolean>()

    fun fileName(s: Int, a: Int) = String.format("%03d%03d.mp3", s, a)
    fun assetPath(r: Reciter, s: Int, a: Int) = "audio/${r.id}/" + fileName(s, a)
    fun localFile(ctx: Context, r: Reciter, s: Int, a: Int): File {
        val dir = File(ctx.filesDir, "audio/${r.id}"); dir.mkdirs()
        return File(dir, fileName(s, a))
    }
    fun remoteUrl(r: Reciter, s: Int, a: Int) = BASE + r.folder + "/" + fileName(s, a)

    /** True when the reciter's audio ships inside the APK (assets/audio/<id>/). */
    fun isBundled(ctx: Context, r: Reciter): Boolean = bundledCache.getOrPut(r.id) {
        try { (ctx.assets.list("audio/${r.id}")?.size ?: 0) > 6000 } catch (e: Exception) { false }
    }

    fun hasAsset(ctx: Context, r: Reciter, s: Int, a: Int): Boolean =
        try { ctx.assets.openFd(assetPath(r, s, a)).close(); true } catch (e: Exception) { false }

    fun isDownloaded(ctx: Context, r: Reciter, s: Int, a: Int) =
        isBundled(ctx, r) || localFile(ctx, r, s, a).let { it.exists() && it.length() > 0 }

    fun download(ctx: Context, r: Reciter, s: Int, a: Int): Boolean {
        if (isBundled(ctx, r)) return true
        val f = localFile(ctx, r, s, a)
        if (f.exists() && f.length() > 0) return true
        repeat(3) { attempt ->
            try {
                val c = URL(remoteUrl(r, s, a)).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MohammedYahyaQuran/1.0")
                c.connectTimeout = 20000; c.readTimeout = 60000
                if (c.responseCode == 200) {
                    val tmp = File(f.path + ".tmp")
                    c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                    if (tmp.length() > 0 && tmp.renameTo(f)) return true
                }
                c.disconnect()
            } catch (e: Exception) { }
            try { Thread.sleep(1500L * (attempt + 1)) } catch (_: Exception) {}
        }
        return false
    }

    @Volatile var cancelAll = false
    @Volatile var downloadingAll = false

    fun countDownloaded(ctx: Context, r: Reciter): Int {
        if (isBundled(ctx, r)) return 6236
        val dir = File(ctx.filesDir, "audio/${r.id}")
        return dir.listFiles()?.count { it.name.endsWith(".mp3") && it.length() > 0 } ?: 0
    }

    fun downloadAll(ctx: Context, r: Reciter, surahs: List<Surah>, onProgress: (Int, Int) -> Unit, onDone: (Boolean) -> Unit) {
        if (downloadingAll) return
        downloadingAll = true; cancelAll = false
        Thread {
            val total = surahs.sumOf { it.ayahs.size }
            var done = 0; var allOk = true
            outer@ for (s in surahs) for (a in s.ayahs) {
                if (cancelAll) { allOk = false; break@outer }
                if (!download(ctx, r, s.num, a.num)) { allOk = false }
                done++
                if (done % 5 == 0 || done == total) onProgress(done, total)
            }
            downloadingAll = false
            onDone(allOk)
        }.start()
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
        try {
            val p = MediaPlayer()
            val local = AudioStore.localFile(ctx, r, s, a)
            when {
                AudioStore.hasAsset(ctx, r, s, a) -> {
                    val afd = ctx.assets.openFd(AudioStore.assetPath(r, s, a))
                    p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
                }
                local.exists() && local.length() > 0 -> p.setDataSource(local.path)
                else -> p.setDataSource(AudioStore.remoteUrl(r, s, a))
            }
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { onComplete?.invoke() }
            p.setOnErrorListener { _, _, _ -> onError?.invoke("تعذر تشغيل الآية — تأكد من الإنترنت أو حمّل السورة"); true }
            p.prepareAsync()
            mp = p
        } catch (e: Exception) { onError?.invoke(e.message ?: "خطأ") }
    }

    fun stop() { mp?.let { try { it.stop() } catch (_: Exception) {}; it.release() }; mp = null }
    fun isPlaying() = mp?.isPlaying == true
}
