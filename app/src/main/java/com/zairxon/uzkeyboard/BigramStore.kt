package com.zairxon.uzkeyboard

import android.content.Context
import kotlin.concurrent.thread

/**
 * Встроенные пары слов «предыдущее → самые частые следующие» (assets/bigram/<lang>.txt:
 * строка `слово<TAB>след1 след2 …`, следующие по убыванию частоты). Собраны из текстов
 * Википедии и сведены к словам встроенных словарей. Дают предсказание следующего слова
 * «из коробки», до того как клавиатура научится по тексту пользователя.
 */
object BigramStore {
    private val FILES = arrayOf("ru", "en", "uz_latin")

    @Volatile private var ready = false
    private var map: HashMap<String, Array<String>> = HashMap()

    fun load(ctx: Context) {
        if (ready) return
        val app = ctx.applicationContext
        thread(name = "bigram-load") { doLoad(app) }
    }

    private fun doLoad(ctx: Context) {
        val m = HashMap<String, Array<String>>(60_000)
        for (name in FILES) {
            try {
                ctx.assets.open("bigram/$name.txt").bufferedReader().useLines { seq ->
                    for (line in seq) {
                        val t = line.indexOf('\t')
                        if (t <= 0) continue
                        val next = line.substring(t + 1).split(' ').filter { it.isNotEmpty() }
                        if (next.isEmpty()) continue
                        val key = line.substring(0, t)
                        val old = m[key]
                        m[key] = if (old == null) next.toTypedArray()
                                 else (old.toList() + next).distinct().toTypedArray()
                    }
                }
            } catch (_: Exception) { /* нет файла — пропускаем язык */ }
        }
        map = m
        ready = true
    }

    /** Следующие слова после [prev] по убыванию частоты (пусто, если не знаем). */
    fun next(prev: String): List<String> {
        if (!ready || prev.isEmpty()) return emptyList()
        return map[prev.lowercase()]?.asList() ?: emptyList()
    }

    /** Насколько [word] подходит после [prev]: 0 — не встречалось, больше — лучше. */
    fun score(prev: String, word: String): Int {
        if (!ready || prev.isEmpty()) return 0
        val a = map[prev.lowercase()] ?: return 0
        val i = a.indexOf(word.lowercase())
        return if (i < 0) 0 else a.size - i
    }
}
