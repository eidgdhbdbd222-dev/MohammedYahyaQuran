package com.mohammedyahya.quran

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SurahActivity : AppCompatActivity() {
    private lateinit var surah: Surah
    private lateinit var player: AyahPlayer
    private lateinit var adapter: AyahAdapter
    private lateinit var status: TextView
    private lateinit var btnReciter: Button
    private lateinit var btnPlayAll: Button
    private var reciter: Reciter = Reciters.list[0]
    private var playingIndex = -1
    private var continuous = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)
        surah = QuranData.load(this).first { it.num == intent.getIntExtra("surah", 1) }
        title = "سورة ${surah.name}"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        reciter = Reciters.byId(prefs.getString("reciter", "dossary")!!)

        status = findViewById(R.id.status)
        btnReciter = findViewById(R.id.btnReciter)
        btnPlayAll = findViewById(R.id.btnPlayAll)
        player = AyahPlayer(this)
        player.onComplete = { runOnUiThread { onAyahFinished() } }
        player.onError = { m -> runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); stopAll() } }

        val list = findViewById<RecyclerView>(R.id.list)
        adapter = AyahAdapter(surah) { i -> continuous = false; playAyah(i) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        updateReciterBtn()
        btnReciter.setOnClickListener { chooseReciter() }
        btnPlayAll.setOnClickListener { if (playingIndex >= 0) stopAll() else { continuous = true; playAyah(0) } }
        findViewById<Button>(R.id.btnDownload).setOnClickListener { downloadSurah() }
        updateStatus()
    }

    private fun updateReciterBtn() { btnReciter.text = reciter.name.replace("الشيخ ", "") }

    private fun updateStatus() {
        val n = surah.ayahs.count { AudioStore.isDownloaded(this, reciter, surah.num, it.num) }
        status.text = if (n == surah.ayahs.size) "✅ السورة محفوظة على الجهاز — تعمل بدون إنترنت (${reciter.name})"
        else "محفوظ $n من ${surah.ayahs.size} آية بصوت ${reciter.name}"
    }

    private fun chooseReciter() {
        val names = Reciters.list.map { it.name }.toTypedArray()
        val cur = Reciters.list.indexOf(reciter)
        AlertDialog.Builder(this).setTitle(R.string.choose_reciter)
            .setSingleChoiceItems(names, cur) { d, w ->
                reciter = Reciters.list[w]
                getSharedPreferences("p", MODE_PRIVATE).edit().putString("reciter", reciter.id).apply()
                updateReciterBtn(); updateStatus(); d.dismiss()
                if (playingIndex >= 0) playAyah(playingIndex)
            }.show()
    }

    private fun playAyah(i: Int) {
        playingIndex = i
        adapter.setPlaying(i)
        btnPlayAll.text = getString(R.string.stop)
        findViewById<RecyclerView>(R.id.list).smoothScrollToPosition(i)
        player.play(reciter, surah.num, surah.ayahs[i].num)
    }

    private fun onAyahFinished() {
        if (continuous && playingIndex + 1 < surah.ayahs.size) playAyah(playingIndex + 1) else stopAll()
    }

    private fun stopAll() {
        player.stop(); playingIndex = -1; continuous = false
        adapter.setPlaying(-1); btnPlayAll.text = getString(R.string.play_all)
    }

    private fun downloadSurah() {
        Toast.makeText(this, "جاري تحميل سورة ${surah.name} بصوت ${reciter.name}…", Toast.LENGTH_SHORT).show()
        val r = reciter
        AudioStore.downloadSurah(this, r, surah,
            onProgress = { d, t -> runOnUiThread { status.text = "⬇️ تحميل $d / $t" } },
            onDone = { ok -> runOnUiThread {
                Toast.makeText(this, if (ok == surah.ayahs.size) "تم التحميل ✅" else "تم تحميل $ok فقط، تأكد من الإنترنت", Toast.LENGTH_LONG).show()
                updateStatus()
            } })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { player.stop(); super.onDestroy() }
}

class AyahAdapter(private val surah: Surah, val onPlay: (Int) -> Unit) : RecyclerView.Adapter<AyahAdapter.VH>() {
    private var playing = -1
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.text); val meta: TextView = v.findViewById(R.id.meta)
        val play: ImageButton = v.findViewById(R.id.play); val root: View = v.findViewById(R.id.root)
    }
    fun setPlaying(i: Int) { val old = playing; playing = i; if (old >= 0) notifyItemChanged(old); if (i >= 0) notifyItemChanged(i) }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_ayah, p, false))
    override fun getItemCount() = surah.ayahs.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val a = surah.ayahs[i]
        h.text.text = "${a.text} ﴿${toArabicDigits(a.num)}﴾"
        h.meta.text = "الآية ${a.num} • صفحة ${a.page} • جزء ${a.juz}"
        h.play.setImageResource(if (i == playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        h.root.setBackgroundResource(R.drawable.ayah_bg)
        if (i == playing) h.root.setBackgroundColor(0xFFFFF3C4.toInt())
        h.play.setOnClickListener { onPlay(i) }
        h.root.setOnClickListener { onPlay(i) }
    }
    private fun toArabicDigits(n: Int) = n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
}
