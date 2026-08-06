package com.zairxon.uzkeyboard

import android.content.Context

/**
 * Обучение словам и их структуре (биграммы «предыдущее→следующее»).
 *
 * - Униграммы: слово → частота (для дополнения по префиксу).
 * - Биграммы: пред. слово → (след. слово → частота) — для предсказания
 *   следующего слова и контекстного ранжирования дополнений.
 *
 * Хранится в SharedPreferences (ключи lowercase для регистронезависимого поиска).
 */
object WordStore {
    private const val PREFS = "zairkey_prefs"
    private const val KEY_UNI = "learned_words"
    private const val KEY_BI = "learned_bigrams"
    private const val MAX_UNI = 2000
    private const val MAX_BI_LINES = 4000
    private const val MAX_NEXT_PER_WORD = 8

    private var loaded = false
    private val uni = HashMap<String, Int>()
    private val bi = HashMap<String, HashMap<String, Int>>()

    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (line in (sp.getString(KEY_UNI, "") ?: "").split('\n')) {
            val i = line.lastIndexOf('\t'); if (i <= 0) continue
            uni[line.substring(0, i)] = line.substring(i + 1).toIntOrNull() ?: continue
        }
        for (line in (sp.getString(KEY_BI, "") ?: "").split('\n')) {
            val p = line.split('\t'); if (p.size != 3) continue
            val c = p[2].toIntOrNull() ?: continue
            bi.getOrPut(p[0]) { HashMap() }[p[1]] = c
        }
    }

    /** Запомнить слово. */
    fun learn(ctx: Context, word: String) {
        if (!valid(word)) return
        ensure(ctx)
        val w = word.lowercase()
        uni[w] = (uni[w] ?: 0) + 1
        save(ctx)
    }

    /** Запомнить пару «prev → next». */
    fun learnPair(ctx: Context, prev: String, next: String) {
        if (!valid(prev) || !valid(next)) return
        ensure(ctx)
        bi.getOrPut(prev.lowercase()) { HashMap() }.also { m ->
            m[next.lowercase()] = (m[next.lowercase()] ?: 0) + 1
        }
        save(ctx)
    }

    /**
     * Подсказки: если [prefix] пуст — предсказываем следующее слово по [prev];
     * иначе дополняем по префиксу, приоритет — словам, что часто идут после [prev].
     */
    fun suggest(ctx: Context, prev: String, prefix: String, limit: Int = 3): List<String> {
        ensure(ctx)
        val p = prefix.lowercase()
        val nexts = if (prev.isNotEmpty()) bi[prev.lowercase()] else null
        val seen = HashSet<String>()
        val out = ArrayList<String>()

        fun add(word: String) {
            if (seen.add(word) && out.size < limit) out.add(word)
        }

        if (prefix.isEmpty()) {
            // предсказание следующего слова
            nexts?.entries?.sortedByDescending { it.value }?.forEach { add(it.key) }
        } else {
            // 1) следующие слова после prev, подходящие под префикс (по частоте пары)
            nexts?.entries
                ?.filter { it.key.startsWith(p) && it.key.length > p.length }
                ?.sortedByDescending { it.value }
                ?.forEach { add(it.key) }
            // 2) обычное дополнение по глобальной частоте
            uni.entries
                .filter { it.key.startsWith(p) && it.key.length > p.length }
                .sortedByDescending { it.value }
                .forEach { add(it.key) }
        }
        return out
    }

    private fun valid(word: String) = word.length >= 2 && word.any { it.isLetter() }

    private fun save(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()

        val topUni = uni.entries.sortedByDescending { it.value }.take(MAX_UNI)
        sp.putString(KEY_UNI, topUni.joinToString("\n") { "${it.key}\t${it.value}" })

        val biLines = ArrayList<String>()
        for ((prev, m) in bi) {
            for ((next, c) in m.entries.sortedByDescending { it.value }.take(MAX_NEXT_PER_WORD)) {
                biLines.add("$prev\t$next\t$c")
            }
        }
        if (biLines.size > MAX_BI_LINES) {
            biLines.sortByDescending { it.substringAfterLast('\t').toIntOrNull() ?: 0 }
            while (biLines.size > MAX_BI_LINES) biLines.removeAt(biLines.size - 1)
        }
        sp.putString(KEY_BI, biLines.joinToString("\n"))
        sp.apply()
    }
}
