package com.zairxon.uzkeyboard

/** Type of a key: a plain character or a functional key. */
enum class KeyCode {
    CHAR, SHIFT, DELETE, SPACE, ENTER, SYMBOLS, SYMBOLS2, NUMBERS, ALPHA, LANGUAGE
}

/**
 * A single key on the keyboard.
 *
 * @param label      what is drawn on the key
 * @param output     what is typed when tapped (defaults to [label])
 * @param code       functional role of the key
 * @param alternates characters shown on long-press (first one is also drawn as a hint)
 * @param weight     relative width within its row
 */
data class Key(
    val label: String,
    val output: String = label,
    val code: KeyCode = KeyCode.CHAR,
    val alternates: List<String> = emptyList(),
    val weight: Float = 1f
)

/**
 * A key placed on an explicit grid (used by the numeric keypad, where the left
 * operator column has 4 keys next to a 3-row digit grid — impossible with plain rows).
 *
 * @param col/row   top-left position in column/row units
 * @param wCols     width in column units
 * @param hRows     height in row units
 */
data class GridKey(
    val key: Key,
    val col: Float,
    val row: Float,
    val wCols: Float = 1f,
    val hRows: Float = 1f
)
