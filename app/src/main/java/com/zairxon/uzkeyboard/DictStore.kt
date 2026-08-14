package com.zairxon.uzkeyboard

import android.content.Context
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/**
 * Встроенные словари: топ-частотные слова для ru / en / uz-латиница / uz-кириллица
 * (assets/dict/&lt;lang&gt;.txt.gz, по одному слову в строке в порядке частоты). Даёт подсказки
 * по префиксу с первой буквы. Загружается один раз в фоне.
 *
 * Слова всех языков объединяются в один отсортированный массив; для каждого слова
 * хранится ранг (позиция по частоте) — по нему выбираем самые частотные совпадения.
 */
object DictStore {
    private val FILES = arrayOf("ru", "en", "uz_latin", "uz_cyrillic")

    @Volatile private var ready = false
    private var words: Array<String> = emptyArray() // отсортированы по алфавиту
    private var ranks: IntArray = IntArray(0)       // ранг частоты (меньше = чаще)

    fun load(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        thread(name = "dict-load") { doLoad(app) }
    }

    private fun doLoad(ctx: Context) {
        val map = HashMap<String, Int>(90_000)
        for (name in FILES) {
            try {
                GZIPInputStream(ctx.assets.open("dict/$name.txt.gz")).bufferedReader().useLines { seq ->
                    var rank = 0
                    for (line in seq) {
                        val w = line.trim()
                        if (w.length >= 2) {
                            val prev = map[w]
                            if (prev == null || rank < prev) map[w] = rank
                        }
                        rank++
                    }
                }
            } catch (_: Exception) { /* нет файла — пропускаем язык */ }
        }
        if (map.isEmpty()) return
        val keys = map.keys.toTypedArray()
        keys.sort()
        val r = IntArray(keys.size) { map[keys[it]]!! }
        words = keys
        ranks = r
        ready = true
    }

    /** До [limit] самых частотных слов, начинающихся на [prefix] (длиннее префикса). */
    fun suggest(prefix: String, limit: Int): List<String> {
        if (!ready || prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val start = lowerBound(p)
        val topW = arrayOfNulls<String>(limit)
        val topR = IntArray(limit) { Int.MAX_VALUE }
        var i = start
        val ws = words
        while (i < ws.size) {
            val word = ws[i]
            if (!word.startsWith(p)) break
            if (word.length > p.length) insertTop(topW, topR, word, ranks[i])
            i++
        }
        return topW.filterNotNull()
    }

    private fun insertTop(topW: Array<String?>, topR: IntArray, word: String, rank: Int) {
        if (rank >= topR[topR.size - 1]) return
        var j = topR.size - 1
        while (j > 0 && topR[j - 1] > rank) {
            topR[j] = topR[j - 1]; topW[j] = topW[j - 1]; j--
        }
        topR[j] = rank; topW[j] = word
    }

    private fun lowerBound(p: String): Int {
        var lo = 0; var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
