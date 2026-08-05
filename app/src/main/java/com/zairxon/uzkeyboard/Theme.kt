package com.zairxon.uzkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/**
 * A keyboard color palette. Values mirror BuildFlow's themes
 * (buildflow/src/styles/variables.css). Изменить/добавить тему — здесь.
 */
data class KbTheme(
    val id: String,
    val title: String,
    val background: Int,   // --bg
    val keyNormal: Int,    // --bg-elevated
    val keySpecial: Int,   // --bg-card
    val accent: Int,       // --accent
    val text: Int,         // --text
    val textOnAccent: Int, // текст на акцентных клавишах (Enter/нажатие)
    val hint: Int          // --text-muted (подсказка альтернатив)
) {
    val popupBg get() = keyNormal
    val keyPressed get() = accent
    val popupSelected get() = accent
}

private fun col(hex: String) = Color.parseColor(hex)

/** Реестр всех тем BuildFlow + текущий выбор (SharedPreferences). */
object Themes {
    val all: List<KbTheme> = listOf(
        KbTheme("botanical", "Botanical Earth",
            col("#1C1612"), col("#2E271F"), col("#241E18"), col("#C4933F"),
            col("#F5EDE0"), col("#1C1612"), col("#9C8B75")),
        KbTheme("buildflow", "BuildFlow (бирюза)",
            col("#0D1A22"), col("#1C3541"), col("#142631"), col("#17838A"),
            col("#EAF4F3"), col("#FFFFFF"), col("#7FA0A6")),
        KbTheme("fire", "Fire",
            col("#0A0808"), col("#1F1008"), col("#150C0A"), col("#FF6B35"),
            col("#FFF5E8"), col("#0A0808"), col("#C4956A")),
        KbTheme("oryzo", "Oryzo",
            col("#100904"), col("#40372E"), col("#382416"), col("#DC5000"),
            col("#FFEDD7"), col("#FFFFFF"), col("#6C5F51")),
        KbTheme("blueprint", "Blueprint",
            col("#0A0F1E"), col("#1A2234"), col("#111726"), col("#3B82F6"),
            col("#EAF1FB"), col("#FFFFFF"), col("#8EA2C2")),
        KbTheme("light", "Light",
            col("#FAF3E8"), col("#FFFFFF"), col("#F0E4CE"), col("#D08A52"),
            col("#2C1F14"), col("#FFFFFF"), col("#7A6550")),
        KbTheme("retro", "Retro",
            col("#D6D3C9"), col("#F5F3EC"), col("#E8E0CE"), col("#D9A032"),
            col("#3D3833"), col("#3D3833"), col("#7A6F63")),
        KbTheme("terra", "Terra",
            col("#D9E0C8"), col("#E9EDD9"), col("#DDE4C9"), col("#79A03E"),
            col("#2A3018"), col("#FFFFFF"), col("#61684A"))
    )

    val default = all[0]

    private const val PREFS = "zairkey_prefs"
    private const val KEY = "theme_id"

    fun byId(id: String?): KbTheme = all.firstOrNull { it.id == id } ?: default

    fun current(context: Context): KbTheme =
        byId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, default.id))

    fun setCurrent(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).apply()
    }
}

/**
 * Шрифты. Клавиши — стильный serif Playfair Display; символы/буквы, которых в
 * нём нет (напр. ҳ), автоматически берутся из Inter (см. KeyboardView.hasGlyph).
 */
object Fonts {
    const val cornerDp = 8f
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var serif: Typeface? = null

    fun regular(context: Context): Typeface =
        regular ?: (ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT)
            .also { regular = it }

    fun medium(context: Context): Typeface =
        medium ?: (ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT)
            .also { medium = it }

    /** Основной шрифт клавиш — Playfair Display. */
    fun serif(context: Context): Typeface =
        serif ?: (ResourcesCompat.getFont(context, R.font.playfair) ?: Typeface.SERIF)
            .also { serif = it }
}
