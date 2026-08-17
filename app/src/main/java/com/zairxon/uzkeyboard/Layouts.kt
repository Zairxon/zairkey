package com.zairxon.uzkeyboard

/**
 * Keyboard layouts.
 *
 * Буквенные клавиши НЕ содержат цифр — цифры только на странице «?123» и на
 * отдельной цифровой раскладке «123».
 *
 * Узбекская кириллица по ДОЛГОМУ нажатию на русской раскладке:
 *   ы → қ,   щ → ў,   х → ҳ,   г → ғ.
 */
object Layouts {

    private fun c(label: String, alternates: List<String> = emptyList()) =
        Key(label = label, output = label, code = KeyCode.CHAR, alternates = alternates)

    private val shift = Key("⇧", code = KeyCode.SHIFT, weight = 1.5f)
    private val del = Key("⌫", code = KeyCode.DELETE, weight = 1.5f)
    private val space = Key("␣", output = " ", code = KeyCode.SPACE, weight = 4f)
    private val enter = Key("⏎", code = KeyCode.ENTER, weight = 1.8f)
    private val globe = Key("🌐", code = KeyCode.LANGUAGE, weight = 1.2f)
    private val toSym = Key("?#", code = KeyCode.SYMBOLS, weight = 1.3f)
    private val toNum = Key("123", code = KeyCode.NUMBERS, weight = 1.3f)
    private val toAbc = Key("ABC", code = KeyCode.ALPHA, weight = 1.5f)

    // Разные наборы для точки и запятой (без повторов между ними).
    private val period = Key(".", alternates = listOf("?", "!", "…", ":", "-", "\"", "%"))
    private val comma = Key(",", alternates = listOf(";", "'", "@", "&", "_", "(", ")"))

    // ---- Арабская пунктуация — отдельные точка/запятая только для арабской раскладки ----
    // Долгое нажатие открывает арабские знаки: ، (запятая) ؛ (точка с запятой) ؟ (вопрос) ٪ (процент).
    private val arComma = Key(",", alternates = listOf("،", "؛", "؟", ":", "!", "@"))
    private val arPeriod = Key(".", alternates = listOf("؟", "،", "؛", "!", "…", "٪"))

    // ---- Огласовки (харакат) — 3 отдельные клавиши в арабской раскладке ----
    // Tap ставит знак на предыдущую букву (بَ بُ بِ). Редкие огласовки — по долгому
    // нажатию. Метка ◌ — пунктирный круг для наглядности (рисуется в KeyboardView).
    private val fatha = Key("◌َ", output = "َ", alternates = listOf("ً", "ّ")) // فتحة: танвин фатх, шадда
    private val damma = Key("◌ُ", output = "ُ", alternates = listOf("ٌ"))      // ضمة: танвин дамм
    private val kasra = Key("◌ِ", output = "ِ", alternates = listOf("ٍ"))      // كسرة: танвин касра
    private val sukun = Key("◌ْ", output = "ْ")                                // سكون: «нет огласовки» (кружок)

    // ---- Арабские (арабо-индийские) цифры — верхний ряд арабской раскладки ----
    // Долгое нажатие даёт латинскую цифру (٥ → 5) — для номеров/кодов.
    private val arDigitsRow = listOf(
        c("١", listOf("1")), c("٢", listOf("2")), c("٣", listOf("3")), c("٤", listOf("4")),
        c("٥", listOf("5")), c("٦", listOf("6")), c("٧", listOf("7")), c("٨", listOf("8")),
        c("٩", listOf("9")), c("٠", listOf("0"))
    )

    // Верхний цифровой ряд для основной клавиатуры (цифры перенесены из символов).
    private val digitsRow = listOf(
        c("1"), c("2"), c("3"), c("4"), c("5"), c("6"), c("7"), c("8"), c("9"), c("0")
    )

    // ---- English (QWERTY) — цифровой ряд + буквы ----
    val enAlpha: List<List<Key>> = listOf(
        digitsRow,
        listOf(c("q"), c("w"), c("e"), c("r"), c("t"), c("y"), c("u"), c("i"), c("o"), c("p")),
        listOf(c("a"), c("s"), c("d"), c("f"), c("g"), c("h"), c("j"), c("k"), c("l")),
        listOf(shift, c("z"), c("x"), c("c"), c("v"), c("b"), c("n"), c("m"), del),
        listOf(toNum, toSym, globe, comma, space, period, enter)
    )

    // ---- Russian (ЙЦУКЕН) + узбекские буквы по долгому нажатию ----
    val ruAlpha: List<List<Key>> = listOf(
        digitsRow,
        listOf(
            c("й"), c("ц"), c("у"), c("к"), c("е", listOf("ё")), c("н"),
            c("г", listOf("ғ")), c("ш"), c("щ", listOf("ў")), c("з"),
            c("х", listOf("ҳ")), c("ъ")
        ),
        listOf(
            c("ф"), c("ы", listOf("қ")), c("в"), c("а"), c("п"), c("р"),
            c("о"), c("л"), c("д"), c("ж"), c("э")
        ),
        listOf(
            shift,
            c("я"), c("ч"), c("с"), c("м"), c("и"), c("т"), c("ь"), c("б"), c("ю"),
            del
        ),
        listOf(toNum, toSym, globe, comma, space, period, enter)
    )

    // ---- Arabic (арабская) ----
    // Базовые арабские буквы. Начальная/срединная/конечная формы система рисует
    // САМА при рендеринге (HarfBuzz) — мы вводим только базовые символы Unicode,
    // это правильный способ (presentation-forms ломали бы поиск и копирование).
    // Хамза/варианты алифа — по долгому нажатию: ا → أ إ آ ٱ ء, ي → ئ ى, و → ؤ, ل → لا.
    val arAlpha: List<List<Key>> = listOf(
        arDigitsRow,
        listOf(
            c("ض"), c("ص"), c("ث"), c("ق"), c("ف"), c("غ"),
            c("ع"), c("ه", listOf("ة")), c("خ"), c("ح"), c("ج")
        ),
        listOf(
            c("ش"), c("س"), c("ي", listOf("ئ", "ى")), c("ب"), c("ل", listOf("لا")),
            c("ا", listOf("أ", "إ", "آ", "ٱ", "ء")), c("ت"), c("ن"), c("م"), c("ك"), c("ط")
        ),
        listOf(
            fatha, damma, kasra, sukun,
            c("ذ"), c("ظ"), c("د"), c("ز"), c("ر"), c("و", listOf("ؤ")),
            del
        ),
        listOf(toNum, toSym, globe, arComma, space, arPeriod, enter)
    )

    // ---- Symbols page 1 (цифры перенесены в основную клавиатуру) ----
    val symbols: List<List<Key>> = listOf(
        listOf(
            c("@"), c("#", listOf("№")), c("$", listOf("¢", "£", "€", "¥", "₽", "₴")),
            c("%", listOf("‰", "℅")), c("&"), c("-", listOf("_")),
            c("+", listOf("±")), c("(", listOf("[", "{", "<")),
            c(")", listOf("]", "}", ">")), c("/", listOf("\\"))
        ),
        listOf(
            Key("=\\<", code = KeyCode.SYMBOLS2, weight = 1.5f),
            c("*", listOf("†", "‡")), c("\"", listOf("“", "”", "«", "»", "„")),
            c("'", listOf("‘", "’", "`")), c(":"), c(";"),
            c("!", listOf("¡")), c("?", listOf("¿")),
            del
        ),
        listOf(toAbc, toNum, comma, space, period, enter)
    )

    // ---- Symbols page 2 ----
    val symbols2: List<List<Key>> = listOf(
        listOf(
            c("~"), c("`"), c("|"), c("•", listOf("·", "◦")), c("√"),
            c("π"), c("÷"), c("×"), c("¶", listOf("§")), c("∆")
        ),
        listOf(
            c("£"), c("¢"), c("€"), c("¥", listOf("₽", "₴", "₸")), c("^"),
            c("°"), c("="), c("{"), c("}"), c("\\")
        ),
        listOf(
            toSym,
            c("%"), c("©"), c("®"), c("™"), c("✓", listOf("✗")), c("["), c("]"),
            del
        ),
        listOf(toAbc, toNum, comma, space, period, enter)
    )

    // ---- Pure numeric keypad (кнопка «123») — сетка как в Gboard ----
    // Слева колонка из 4 операторов (+ − * /) высотой в 3 ряда цифр, сетка 1–9,
    // справа % / пробел / ⌫, снизу АБВ , !?# 0 = . ⏎ (0 под 8). Всего 5 колонок × 4 ряда.
    val numbersGrid: List<GridKey> = listOf(
        // левая колонка операторов: 4 клавиши на высоту 3 рядов (по 0.75)
        GridKey(c("+"), col = 0f, row = 0f,    hRows = 0.75f),
        GridKey(c("-"), col = 0f, row = 0.75f, hRows = 0.75f),
        GridKey(c("*"), col = 0f, row = 1.5f,  hRows = 0.75f),
        GridKey(c("/"), col = 0f, row = 2.25f, hRows = 0.75f),
        // цифровая сетка 1–9
        GridKey(c("1"), 1f, 0f), GridKey(c("2"), 2f, 0f), GridKey(c("3"), 3f, 0f),
        GridKey(c("4"), 1f, 1f), GridKey(c("5"), 2f, 1f), GridKey(c("6"), 3f, 1f),
        GridKey(c("7"), 1f, 2f), GridKey(c("8"), 2f, 2f), GridKey(c("9"), 3f, 2f),
        // правая колонка
        GridKey(c("%"), 4f, 0f),
        GridKey(Key("␣", output = " ", code = KeyCode.SPACE), 4f, 1f),
        GridKey(Key("⌫", code = KeyCode.DELETE), 4f, 2f),
        // нижний ряд
        GridKey(Key("АБВ", code = KeyCode.ALPHA), col = 0f, row = 3f, wCols = 1f),
        GridKey(Key(","), col = 1f, row = 3f, wCols = 0.5f),
        GridKey(Key("!?#", code = KeyCode.SYMBOLS), col = 1.5f, row = 3f, wCols = 0.5f),
        GridKey(c("0"), col = 2f, row = 3f, wCols = 1f),
        GridKey(Key("="), col = 3f, row = 3f, wCols = 0.5f),
        GridKey(Key("."), col = 3.5f, row = 3f, wCols = 0.5f),
        GridKey(Key("⏎", code = KeyCode.ENTER), col = 4f, row = 3f, wCols = 1f)
    )
}
