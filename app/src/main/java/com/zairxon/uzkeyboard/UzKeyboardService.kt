package com.zairxon.uzkeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import kotlin.math.abs

/** The system keyboard service. Owns language / layer / shift state. */
class UzKeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Lang { EN, RU }
    private enum class Layer { ALPHA, SYMBOLS, SYMBOLS2, NUMBERS }

    private lateinit var kbView: KeyboardView
    private var language = Lang.EN
    private var layer = Layer.ALPHA
    private var shifted = false
    private var capsLock = false
    private var lastShiftTime = 0L

    override fun onCreateInputView(): View {
        kbView = KeyboardView(this)
        kbView.listener = this
        kbView.theme = Themes.current(this)
        applyLayout()
        applyWindowColors()
        return kbView
    }

    override fun onWindowShown() {
        super.onWindowShown()
        applyWindowColors()
    }

    /** Красим область системной навигации под клавиатурой в цвет фона — иначе снизу видна белая полоса. */
    private fun applyWindowColors() {
        if (!::kbView.isInitialized) return
        val w = window?.window ?: return
        val bg = kbView.theme.background
        // Без этого флага navigationBarColor игнорируется.
        // Красим системную навигацию под клавиатурой в цвет темы (полностью убрать её
        // нельзя — это системная панель жестов; edge-to-edge перекрывал нижний ряд).
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        w.navigationBarColor = bg
        // Тёмный фон → светлые иконки навигации, светлый фон → тёмные.
        val lightBg = ColorUtils.calculateLuminance(bg) > 0.5
        WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightNavigationBars = lightBg
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        layer = Layer.ALPHA
        shifted = false
        capsLock = false
        if (::kbView.isInitialized) {
            kbView.theme = Themes.current(this) // подхватить смену темы из приложения
            applyLayout()
            applyWindowColors()
            maybeAutoCaps() // заглавная в начале ввода
        }
    }

    /** Авто-регистр: заглавная в начале текста и после «. ! ?». */
    private fun maybeAutoCaps() {
        if (!::kbView.isInitialized) return
        if (capsLock || layer != Layer.ALPHA) return
        val caps = currentInputConnection?.getCursorCapsMode(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ) ?: 0
        shifted = caps != 0
        kbView.isShifted = shifted
        kbView.invalidate()
    }

    private fun currentRows() = when (layer) {
        Layer.ALPHA -> if (language == Lang.EN) Layouts.enAlpha else Layouts.ruAlpha
        Layer.SYMBOLS -> Layouts.symbols
        Layer.SYMBOLS2 -> Layouts.symbols2
        Layer.NUMBERS -> emptyList()
    }

    private fun applyLayout() {
        kbView.isShifted = shifted
        kbView.isCapsLock = capsLock
        if (layer == Layer.NUMBERS) kbView.setGrid(Layouts.numbersGrid)
        else kbView.setRows(currentRows())
    }

    override fun onCharCommit(text: String) {
        val ic = currentInputConnection ?: return
        // После точки автоматически ставим пробел (только в буквенном режиме).
        if (text == "." && layer == Layer.ALPHA) ic.commitText(". ", 1)
        else ic.commitText(text, 1)
        maybeAutoCaps() // сбрасывает разовый Shift и включает заглавную где нужно
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    override fun onLanguagePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    override fun onSpecial(code: KeyCode) {
        val ic = currentInputConnection ?: return
        when (code) {
            KeyCode.DELETE -> {
                val selected = ic.getSelectedText(0)
                if (selected.isNullOrEmpty()) ic.deleteSurroundingText(1, 0)
                else ic.commitText("", 1)
                maybeAutoCaps()
            }
            KeyCode.SHIFT -> toggleShift()
            KeyCode.SPACE -> {
                handleSpace(ic)
                maybeAutoCaps()
            }
            KeyCode.ENTER -> { handleEnter(ic); maybeAutoCaps() }
            KeyCode.SYMBOLS -> { layer = Layer.SYMBOLS; applyLayout() }
            KeyCode.SYMBOLS2 -> { layer = Layer.SYMBOLS2; applyLayout() }
            KeyCode.NUMBERS -> { layer = Layer.NUMBERS; applyLayout() }
            KeyCode.ALPHA -> { layer = Layer.ALPHA; applyLayout(); maybeAutoCaps() }
            KeyCode.LANGUAGE -> {
                language = if (language == Lang.EN) Lang.RU else Lang.EN
                layer = Layer.ALPHA
                applyLayout()
                maybeAutoCaps()
            }
            KeyCode.CHAR -> Unit
        }
    }

    private fun toggleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftTime < 300) {
            // double tap -> caps lock
            capsLock = !capsLock
            shifted = capsLock
        } else if (capsLock) {
            capsLock = false
            shifted = false
        } else {
            shifted = !shifted
        }
        lastShiftTime = now
        kbView.isShifted = shifted
        kbView.isCapsLock = capsLock
        kbView.invalidate()
    }

    /** Пробел; двойной пробел подряд после слова превращается в «. ». */
    private fun handleSpace(ic: InputConnection) {
        if (layer == Layer.ALPHA) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                return
            }
        }
        ic.commitText(" ", 1)
    }

    private fun handleEnter(ic: InputConnection) {
        val ei = currentInputEditorInfo
        val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }
}
