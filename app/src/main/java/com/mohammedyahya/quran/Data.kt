package com.mohammedyahya.quran

import android.content.Context
import org.json.JSONArray

data class Ayah(val num: Int, val text: String, val page: Int, val juz: Int)
data class Surah(val num: Int, val name: String, val en: String, val type: String, val ayahs: List<Ayah>)

data class Reciter(val id: String, val name: String, val folder: String)

object Reciters {
    // Sources: everyayah.com (free, per-ayah mp3)
    val list = listOf(
        Reciter("dossary", "الشيخ ياسر الدوسري", "Yasser_Ad-Dussary_128kbps"),
        Reciter("shaarawy", "الشيخ محمد متولي الشعراوي", "Alafasy_128kbps"), // انظر الملاحظة في README
        Reciter("afasy", "الشيخ مشاري العفاسي", "Alafasy_128kbps"),
        Reciter("basit", "الشيخ عبدالباسط عبدالصمد (مجود)", "Abdul_Basit_Mujawwad_128kbps"),
        Reciter("sudais", "الشيخ عبدالرحمن السديس", "Abdurrahmaan_As-Sudais_192kbps")
    )
    fun byId(id: String) = list.firstOrNull { it.id == id } ?: list[0]
}

object QuranData {
    private var cache: List<Surah>? = null
    fun load(ctx: Context): List<Surah> {
        cache?.let { return it }
        val json = ctx.assets.open("quran.json").bufferedReader().readText()
        val arr = JSONArray(json)
        val res = ArrayList<Surah>(114)
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val aa = s.getJSONArray("ayahs")
            val ayahs = ArrayList<Ayah>(aa.length())
            for (j in 0 until aa.length()) {
                val a = aa.getJSONObject(j)
                ayahs.add(Ayah(a.getInt("a"), a.getString("t"), a.getInt("p"), a.getInt("j")))
            }
            res.add(Surah(s.getInt("n"), s.getString("name"), s.getString("en"), s.getString("type"), ayahs))
        }
        cache = res
        return res
    }
}
