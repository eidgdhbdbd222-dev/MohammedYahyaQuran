package com.mohammedyahya.quran

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var all: List<Surah>
    private lateinit var adapter: SurahAdapter
    private lateinit var btnAll: Button
    private lateinit var btnRec: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusAll: TextView
    private var reciter: Reciter = Reciters.list[0]

    private fun prefs() = getSharedPreferences("p", MODE_PRIVATE)

    private fun refreshStatus() {
        btnRec.text = reciter.name.replace("الشيخ ", "")
        val n = AudioStore.countDownloaded(this, reciter)
        if (AudioStore.downloadingAll) return
        progress.visibility = android.view.View.GONE
        statusAll.text = if (n >= 6236) "✅ المصحف كامل محفوظ بصوت ${reciter.name} — يعمل بدون إنترنت"
            else if (n == 0) "لم يتم تحميل المصحف بعد بصوت ${reciter.name}" else "محفوظ $n من 6236 آية بصوت ${reciter.name} — اضغط تحميل للإكمال"
        btnAll.text = if (n >= 6236) "✅ المصحف محمّل" else "⬇️ تحميل المصحف كامل بدون نت"
    }

    private fun startDownloadAll() {
        if (AudioStore.downloadingAll) {
            AlertDialog.Builder(this).setMessage("إيقاف التحميل؟").setPositiveButton("نعم") { _, _ -> AudioStore.cancelAll = true }.setNegativeButton("لا", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("تحميل المصحف كامل")
            .setMessage("سيتم تحميل 6236 آية بصوت ${reciter.name} (حوالي 1 جيجا). يُفضّل الاتصال بالواي فاي وإبقاء التطبيق مفتوحاً. يمكنك الإيقاف والإكمال لاحقاً.")
            .setPositiveButton("ابدأ") { _, _ ->
                progress.visibility = android.view.View.VISIBLE
                btnAll.text = "⏹ إيقاف التحميل"
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                AudioStore.downloadAll(this, reciter, all,
                    onProgress = { d, t -> runOnUiThread { progress.progress = d; statusAll.text = "⬇️ جاري التحميل $d / $t (${d * 100 / t}%)" } },
                    onDone = { ok -> runOnUiThread {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        Toast.makeText(this, if (ok) "تم تحميل المصحف كامل ✅" else "توقف التحميل — اضغط مرة أخرى للإكمال", Toast.LENGTH_LONG).show()
                        refreshStatus()
                    } })
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun chooseReciter() {
        val names = Reciters.list.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.choose_reciter)
            .setSingleChoiceItems(names, Reciters.list.indexOf(reciter)) { d, w ->
                reciter = Reciters.list[w]; prefs().edit().putString("reciter", reciter.id).apply()
                refreshStatus(); d.dismiss()
            }.show()
    }

    override fun onResume() { super.onResume(); reciter = Reciters.byId(prefs().getString("reciter", "dossary")!!); refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        all = QuranData.load(this)
        val list = findViewById<RecyclerView>(R.id.list)
        adapter = SurahAdapter(all) { s ->
            startActivity(Intent(this, SurahActivity::class.java).putExtra("surah", s.num))
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        btnAll = findViewById(R.id.btnDownloadAll); btnRec = findViewById(R.id.btnReciterMain)
        progress = findViewById(R.id.progressAll); statusAll = findViewById(R.id.statusAll)
        btnAll.setOnClickListener { startDownloadAll() }
        btnRec.setOnClickListener { chooseReciter() }
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                adapter.update(if (q.isEmpty()) all else all.filter { it.name.contains(q) || it.en.contains(q, true) || it.num.toString() == q })
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }
}

class SurahAdapter(private var items: List<Surah>, val onClick: (Surah) -> Unit) : RecyclerView.Adapter<SurahAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val num: TextView = v.findViewById(R.id.num); val name: TextView = v.findViewById(R.id.name); val info: TextView = v.findViewById(R.id.info)
    }
    fun update(l: List<Surah>) { items = l; notifyDataSetChanged() }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_surah, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val s = items[i]
        h.num.text = s.num.toString(); h.name.text = s.name
        h.info.text = "${s.ayahs.size} آية • ${if (s.type == "Meccan") "مكية" else "مدنية"}"
        h.itemView.setOnClickListener { onClick(s) }
    }
}
