package com.zairxon.uzkeyboard

import android.content.Context

/**
 * Простое обучение словам: запоминает введённые слова с частотой в
 * SharedPreferences и предлагает продолжения по префиксу. Формат хранения —
 * строки «слово\tчастота», разделённые \n. Держим топ-2000 по частоте.
 */
object WordStore {
    private const val PREFS = "zairkey_prefs"
    private const val KEY = "learned_words"
    private const val MAX_KEEP = 2000

    private var loaded = false
    private val counts = HashMap<String, Int>()

    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        for (line in raw.split('\n')) {
            val i = line.lastIndexOf('\t')
            if (i <= 0) continue
            val w = line.substring(0, i)
            val c = line.substring(i + 1).toIntOrNull() ?: continue
            counts[w] = c
        }
    }

    /** Запомнить слово (или увеличить его частоту). */
    fun learn(ctx: Context, word: String) {
        if (word.length < 2 || !word.any { it.isLetter() }) return
        ensure(ctx)
        counts[word] = (counts[word] ?: 0) + 1
        save(ctx)
    }

    /** До [limit] слов, начинающихся на [prefix] (без учёта регистра), по убыванию частоты. */
    fun suggest(ctx: Context, prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        ensure(ctx)
        val p = prefix.lowercase()
        return counts.entries.asSequence()
            .filter { it.key.length > prefix.length && it.key.lowercase().startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toList()
    }

    private fun save(ctx: Context) {
        val top = counts.entries.sortedByDescending { it.value }.take(MAX_KEEP)
        val sb = StringBuilder()
        for (e in top) sb.append(e.key).append('\t').append(e.value).append('\n')
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, sb.toString()).apply()
        if (counts.size > MAX_KEEP + 500) {
            counts.clear()
            for (e in top) counts[e.key] = e.value
        }
    }
}
