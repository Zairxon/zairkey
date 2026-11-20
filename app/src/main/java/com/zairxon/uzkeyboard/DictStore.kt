package com.zairxon.uzkeyboard

import android.content.Context
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
                ctx.assets.open("dict/$name.txt").bufferedReader().useLines { seq ->
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

    /** Есть ли слово в встроенных словарях. */
    fun contains(word: String): Boolean {
        if (!ready || word.isEmpty()) return false
        return java.util.Arrays.binarySearch(words, word.lowercase()) >= 0
    }

    /**
     * Правописание: слова словаря, похожие на [word] (опечатка — замена/вставка/пропуск/
     * перестановка соседних букв). Ищем среди слов с той же первой буквой (они лежат в
     * отсортированном массиве подряд). Порядок: меньше правок → лучше подходит по контексту
     * ([context] — например, сколько раз слово шло после предыдущего) → чаще в языке.
     */
    fun correct(word: String, limit: Int, context: (String) -> Int = { 0 }): List<String> {
        if (!ready || word.length < 2) return emptyList()
        val w = word.lowercase()
        val maxD = if (w.length <= 6) 1 else 2
        val first = w.substring(0, 1)
        class Cand(val word: String, val dist: Int, val ctx: Int, val rank: Int)
        val found = ArrayList<Cand>()
        var i = lowerBound(first)
        while (i < words.size && words[i].startsWith(first)) {
            val c = words[i]
            if (c != w && kotlin.math.abs(c.length - w.length) <= maxD) {
                val d = editDistance(w, c, maxD)
                if (d <= maxD) found.add(Cand(c, d, context(c), ranks[i]))
            }
            i++
        }
        return found
            .sortedWith(compareBy<Cand>({ it.dist }, { -it.ctx }, { it.rank }))
            .take(limit).map { it.word }
    }

    /** Расстояние Дамерау–Левенштейна (с перестановкой соседних); > [max] → max + 1. */
    private fun editDistance(a: String, b: String, max: Int): Int {
        val n = a.length; val m = b.length
        var pp = IntArray(m + 1)
        var p = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(p[j] + 1, cur[j - 1] + 1, p[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                    v = minOf(v, pp[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = pp; pp = p; p = cur; cur = t
        }
        return p[m]
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
