package com.mohammedyahya.quran

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var all: List<Surah>
    private lateinit var adapter: SurahAdapter

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
