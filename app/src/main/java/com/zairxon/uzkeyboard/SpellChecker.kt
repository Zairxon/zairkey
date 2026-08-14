package com.zairxon.uzkeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager

/**
 * Обёртка над системным спелл-чекером (Android Text Services). Использует
 * проверку орфографии и языки, настроенные в системе, поэтому работает для всех
 * включённых пользователем языков. Если спелл-чекер недоступен — просто молчит.
 *
 * @param onResults вызывается в главном потоке: (проверенное слово, список исправлений).
 */
class SpellChecker(
    context: Context,
    private val onResults: (String, List<String>) -> Unit
) : SpellCheckerSession.SpellCheckerSessionListener {

    private val main = Handler(Looper.getMainLooper())
    private var session: SpellCheckerSession? = null
    private var pending = ""

    fun start(context: Context) {
        val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE)
                as? TextServicesManager ?: return
        // locale=null + referToSpellCheckerLanguageSettings=true → берём язык(и) из системы.
        session = runCatching {
            tsm.newSpellCheckerSession(null, null, this, true)
        }.getOrNull()
    }

    val available get() = session != null

    fun query(word: String) {
        val s = session ?: return
        if (word.length < 2) return
        pending = word
        runCatching { s.getSentenceSuggestions(arrayOf(TextInfo(word)), 5) }
    }

    fun close() {
        runCatching { session?.close() }
        session = null
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val word = pending
        val out = ArrayList<String>()
        results?.forEach { ssi ->
            for (i in 0 until ssi.suggestionsCount) {
                val si = ssi.getSuggestionsInfoAt(i)
                // Пропускаем слова, признанные корректными (нет смысла предлагать замену).
                val looksWrong = (si.suggestionsAttributes and
                        SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
                if (!looksWrong) continue
                for (j in 0 until si.suggestionsCount) out.add(si.getSuggestionAt(j))
            }
        }
        main.post { onResults(word, out) }
    }

    @Deprecated("Старый API, не используется")
    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
    }
}
