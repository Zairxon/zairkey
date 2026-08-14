package com.zairxon.uzkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Fully custom on-screen keyboard. Draws its own keys and handles taps,
 * long-press alternate popups, backspace auto-repeat and a key preview bubble.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onCharCommit(text: String)
        fun onSpecial(code: KeyCode)
        fun onLanguagePicker()
        fun onCursorMove(steps: Int)
        fun onSuggestionPicked(word: String)
    }

    var listener: Listener? = null
    var isShifted = false
    var isCapsLock = false
    var theme: KbTheme = Themes.default
        set(value) { field = value; invalidate() }

    private var rows: List<List<Key>> = emptyList()
    private var gridKeys: List<GridKey> = emptyList()
    private val keyRects = ArrayList<Pair<Key, RectF>>()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val rowHeight = dp(36f)
    private val vGap = dp(5f)
    private val hGap = dp(4f)
    private val topPad = dp(5f)
    private val corner = dp(Fonts.cornerDp)

    // Панель подсказок (обучение словам). Высота ЗАРЕЗЕРВИРОВАНА всегда, чтобы
    // клавиши не смещались при появлении/исчезновении подсказок (иначе мис-тапы).
    private var suggestions: List<String> = emptyList()
    private val suggBarH = dp(36f)
    private var suggDownIndex = -1
    private val topOffset get() = suggBarH

    private val serifFont = Fonts.serif(context)
    private val interFont = Fonts.medium(context)
    private val glyphPaint = Paint().apply { typeface = serifFont }

    /** Serif, если в нём есть все символы текста; иначе Inter (напр. для ҳ, √, π). */
    private fun fontFor(text: String): Typeface =
        if (text.isNotEmpty() && text.all { glyphPaint.hasGlyph(it.toString()) }) serifFont else interFont

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = serifFont
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = dp(11f)
        typeface = Fonts.regular(context)
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Touch state
    private val handler = Handler(Looper.getMainLooper())
    private var downKey: Key? = null
    private var pressedRect: RectF? = null
    private var previewKey: Key? = null
    private var longPressFired = false
    private var downPointerId = -1
    private val longPressRunnable = Runnable { fireLongPress() }
    private var deleteRunnable: Runnable? = null

    // Скольжение по пробелу для перемещения курсора
    private var spaceSwipeActive = false
    private var spaceStartX = 0f
    private var spaceLastStepX = 0f

    // Long-press alternates popup — рисуется на канве (PopupWindow в IME часто не виден)
    private var popupActive = false
    private var popupAlternates: List<String> = emptyList()
    private var popupIndex = 0
    private val popupCells = ArrayList<RectF>()
    private var altPreview: String? = null // превью вставленной узбекской буквы

    fun setRows(newRows: List<List<Key>>) {
        gridKeys = emptyList()
        rows = newRows
        if (width > 0) computeRects()
        requestLayout()
        invalidate()
    }

    /** Раскладка на явной сетке (числовой блок). */
    fun setGrid(keys: List<GridKey>) {
        rows = emptyList()
        gridKeys = keys
        if (width > 0) computeRects()
        requestLayout()
        invalidate()
    }

    /** Подсказки слов. Высота панели постоянна — перекладка не нужна, только перерисовка. */
    fun setSuggestions(list: List<String>) {
        if (list == suggestions) return
        suggestions = list
        suggDownIndex = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = if (gridKeys.isNotEmpty())
            ceil(gridKeys.maxOf { it.row + it.hRows }).toInt()
        else rows.size.coerceAtLeast(4)
        val h = (topPad + topOffset + rowCount * rowHeight + rowCount * vGap).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        keyRects.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        if (gridKeys.isNotEmpty()) {
            computeGridRects(w)
            return
        }
        if (rows.isEmpty()) return
        var y = topPad + topOffset
        for (row in rows) {
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val usable = w - hGap * (row.size + 1)
            var x = hGap
            for (k in row) {
                val kw = usable * (k.weight / totalWeight)
                keyRects.add(k to RectF(x, y, x + kw, y + rowHeight))
                x += kw + hGap
            }
            y += rowHeight + vGap
        }
    }

    private fun computeGridRects(w: Float) {
        val cols = gridKeys.maxOf { it.col + it.wCols }
        val unitW = (w - hGap) / cols
        val unitH = rowHeight + vGap
        for (gk in gridKeys) {
            val left = hGap + gk.col * unitW
            val top = topPad + topOffset + gk.row * unitH
            val right = left + gk.wCols * unitW - hGap
            val bottom = top + gk.hRows * unitH - vGap
            keyRects.add(gk.key to RectF(left, top, right, bottom))
        }
    }

    private fun displayLabel(k: Key): String = when (k.code) {
        KeyCode.CHAR -> if (isShifted || isCapsLock) k.label.uppercase() else k.label
        KeyCode.SHIFT -> if (isCapsLock) "⇪" else "⇧"
        else -> k.label
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        drawSuggestions(canvas)
        hintPaint.color = theme.hint
        for ((k, rect) in keyRects) {
            val special = k.code != KeyCode.CHAR
            val pressed = rect === pressedRect
            keyPaint.color = when {
                pressed -> theme.keyPressed
                k.code == KeyCode.ENTER -> theme.accent
                special -> theme.keySpecial
                else -> theme.keyNormal
            }
            canvas.drawRoundRect(rect, corner, corner, keyPaint)

            textPaint.color = if (k.code == KeyCode.ENTER || pressed) theme.textOnAccent else theme.text
            textPaint.textSize = if (special) dp(15f) else dp(20f)
            val label = displayLabel(k)
            textPaint.typeface = fontFor(label)
            val ty = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, rect.centerX(), ty, textPaint)

            if (k.code == KeyCode.CHAR && k.alternates.isNotEmpty()) {
                canvas.drawText(k.alternates[0], rect.right - dp(6f), rect.top + dp(14f), hintPaint)
            }
        }
        when {
            popupActive -> drawAltPopup(canvas)
            altPreview != null -> drawAltPreview(canvas)
            else -> previewKey?.let { pk ->
                pressedRect?.let { pr -> if (pk.code == KeyCode.CHAR) drawPreview(canvas, pk, pr) }
            }
        }
    }

    private fun drawSuggestions(canvas: Canvas) {
        if (suggestions.isEmpty()) return
        val cellW = width.toFloat() / suggestions.size
        // подсветка нажатой подсказки
        if (suggDownIndex in suggestions.indices) {
            keyPaint.color = theme.keyPressed
            val l = cellW * suggDownIndex + hGap
            val r = cellW * (suggDownIndex + 1) - hGap
            canvas.drawRoundRect(RectF(l, topPad, r, suggBarH - dp(2f)), corner, corner, keyPaint)
        }
        textPaint.textSize = dp(15f)
        val ty = suggBarH / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        for (i in suggestions.indices) {
            if (i > 0) {
                keyPaint.color = theme.keySpecial
                val x = cellW * i
                canvas.drawRect(x - dp(0.5f), suggBarH * 0.2f, x + dp(0.5f), suggBarH * 0.8f, keyPaint)
            }
            val label = suggestions[i]
            textPaint.color = if (i == suggDownIndex) theme.textOnAccent else theme.text
            textPaint.typeface = fontFor(label)
            canvas.drawText(label, cellW * (i + 0.5f), ty, textPaint)
        }
    }

    private fun suggIndexAt(x: Float): Int {
        if (suggestions.isEmpty()) return -1
        val cellW = width.toFloat() / suggestions.size
        return (x / cellW).toInt().coerceIn(0, suggestions.size - 1)
    }

    private fun drawPreview(canvas: Canvas, k: Key, rect: RectF) {
        val pw = rect.width().coerceAtLeast(dp(40f))
        val ph = dp(52f)
        val cx = rect.centerX()
        val left = (cx - pw / 2f).coerceIn(0f, width - pw)
        val top = rect.top - ph - dp(4f)
        if (top < 0f) return
        val pr = RectF(left, top, left + pw, top + ph)
        previewPaint.color = theme.keyPressed
        canvas.drawRoundRect(pr, corner, corner, previewPaint)
        textPaint.color = theme.textOnAccent
        textPaint.textSize = dp(26f)
        val label = displayLabel(k)
        textPaint.typeface = fontFor(label)
        val ty = pr.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, cx, ty, textPaint)
    }

    private fun keyAt(x: Float, y: Float): Pair<Key, RectF>? {
        for (kr in keyRects) if (kr.second.contains(x, y)) return kr
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (suggestions.isNotEmpty() && event.y < topOffset) {
                    suggDownIndex = suggIndexAt(event.x)
                    downKey = null
                    invalidate()
                    return true
                }
                downPointerId = event.getPointerId(0)
                startKey(event.x, event.y)
                return true
            }
            // Второй палец лёг раньше, чем отпущен первый (быстрый набор «внахлёст»):
            // сразу фиксируем текущую клавишу и начинаем новую — иначе буквы «выпадают».
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (popupActive || spaceSwipeActive || suggDownIndex >= 0) return true
                flushTap()
                val idx = event.actionIndex
                downPointerId = event.getPointerId(idx)
                startKey(event.getX(idx), event.getY(idx))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pi = if (downPointerId >= 0) event.findPointerIndex(downPointerId) else 0
                if (pi < 0) return true
                val mx = event.getX(pi)
                val my = event.getY(pi)
                if (popupActive) {
                    updatePopupSelection(mx)
                } else if (downKey?.code == KeyCode.SPACE &&
                    (spaceSwipeActive || abs(mx - spaceStartX) > dp(8f))) {
                    handleSpaceSwipe(mx)
                } else {
                    val hit = keyAt(mx, my)
                    if (hit?.first !== downKey) {
                        handler.removeCallbacks(longPressRunnable)
                        downKey = hit?.first
                        pressedRect = hit?.second
                        previewKey = downKey
                        longPressFired = false
                        invalidate()
                        if (downKey != null) handler.postDelayed(longPressRunnable, LONGPRESS_MS)
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == downPointerId) {
                    commitCurrent()
                    downPointerId = -1
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (suggDownIndex >= 0) {
                    val word = suggestions.getOrNull(suggDownIndex)
                    suggDownIndex = -1
                    if (word != null) listener?.onSuggestionPicked(word)
                    return true
                }
                commitCurrent()
                downPointerId = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                stopDeleteRepeat()
                closeAltPopup()
                clearPressed()
                downPointerId = -1
                return true
            }
        }
        return true
    }

    /** Начать отслеживание клавиши под пальцем (общий код для DOWN/POINTER_DOWN). */
    private fun startKey(x: Float, y: Float) {
        val hit = keyAt(x, y)
        downKey = hit?.first
        pressedRect = hit?.second
        previewKey = downKey
        longPressFired = false
        spaceSwipeActive = false
        if (downKey?.code == KeyCode.SPACE) {
            spaceStartX = x
            spaceLastStepX = x
        }
        invalidate()
        if (downKey != null) handler.postDelayed(longPressRunnable, LONGPRESS_MS)
    }

    /** Немедленно ввести текущую клавишу как тап (для набора внахлёст). */
    private fun flushTap() {
        handler.removeCallbacks(longPressRunnable)
        if (!longPressFired && !spaceSwipeActive) downKey?.let { handleTap(it) }
    }

    /** Завершить текущее касание (отпускание): попап / тап / очистка. */
    private fun commitCurrent() {
        handler.removeCallbacks(longPressRunnable)
        stopDeleteRepeat()
        if (popupActive) {
            commitPopupSelection()
            closeAltPopup()
        } else if (!longPressFired && !spaceSwipeActive) {
            downKey?.let { handleTap(it) }
        }
        clearPressed()
    }

    private fun handleTap(k: Key) {
        if (k.code == KeyCode.CHAR) commitChar(k.output) else listener?.onSpecial(k.code)
    }

    private fun commitChar(text: String) {
        val out = if (isShifted || isCapsLock) text.uppercase() else text
        listener?.onCharCommit(out)
    }

    private fun fireLongPress() {
        val k = downKey ?: return
        when {
            k.code == KeyCode.DELETE -> { longPressFired = true; startDeleteRepeat() }
            k.code == KeyCode.LANGUAGE -> { longPressFired = true; listener?.onLanguagePicker() }
            k.code == KeyCode.CHAR && k.alternates.size == 1 -> {
                // узбекская буква — вставляем сразу на долгом нажатии + показываем её над клавишей
                longPressFired = true
                commitChar(k.alternates[0])
                altPreview = k.alternates[0]
                invalidate()
            }
            k.code == KeyCode.CHAR && k.alternates.size > 1 -> {
                // несколько символов (точка/запятая/валюты) — попап выбора над клавишей
                longPressFired = true
                pressedRect?.let { openAltPopup(k, it) }
            }
            else -> longPressFired = false // no long action: still type on release
        }
    }

    private fun startDeleteRepeat() {
        val r = object : Runnable {
            override fun run() {
                listener?.onSpecial(KeyCode.DELETE)
                handler.postDelayed(this, DELETE_REPEAT_MS)
            }
        }
        deleteRunnable = r
        handler.post(r)
    }

    private fun stopDeleteRepeat() {
        deleteRunnable?.let { handler.removeCallbacks(it) }
        deleteRunnable = null
    }

    /** Открыть попап альтернатив (рисуется на канве над клавишей). */
    private fun openAltPopup(k: Key, keyRect: RectF) {
        popupAlternates = k.alternates
        popupActive = true
        popupIndex = 0
        popupCells.clear()
        val cellW = dp(36f)
        val cellH = dp(40f)
        val n = popupAlternates.size
        val totalW = cellW * n
        val left = (keyRect.centerX() - totalW / 2f)
            .coerceIn(hGap, (width - totalW - hGap).coerceAtLeast(hGap))
        var top = keyRect.top - cellH - dp(8f)
        if (top < 0f) top = keyRect.bottom + dp(8f) // не влезает сверху — покажем снизу
        for (i in 0 until n) {
            val l = left + i * cellW
            popupCells.add(RectF(l, top, l + cellW, top + cellH))
        }
        invalidate()
    }

    private fun updatePopupSelection(x: Float) {
        if (popupCells.isEmpty()) return
        val first = popupCells.first()
        val idx = (((x - first.left) / first.width()).toInt())
            .coerceIn(0, popupCells.size - 1)
        if (idx != popupIndex) {
            popupIndex = idx
            invalidate()
        }
    }

    private fun commitPopupSelection() {
        popupAlternates.getOrNull(popupIndex)?.let { commitChar(it) }
    }

    private fun closeAltPopup() {
        popupActive = false
        popupAlternates = emptyList()
        popupCells.clear()
    }

    private fun drawAltPopup(canvas: Canvas) {
        if (popupCells.isEmpty()) return
        val bg = RectF(
            popupCells.first().left - dp(4f), popupCells.first().top - dp(4f),
            popupCells.last().right + dp(4f), popupCells.first().bottom + dp(4f)
        )
        keyPaint.color = theme.popupBg
        canvas.drawRoundRect(bg, corner, corner, keyPaint)
        keyPaint.color = theme.accent
        keyPaint.style = Paint.Style.STROKE
        keyPaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(bg, corner, corner, keyPaint)
        keyPaint.style = Paint.Style.FILL
        for (i in popupCells.indices) {
            val cell = popupCells[i]
            if (i == popupIndex) {
                keyPaint.color = theme.accent
                canvas.drawRoundRect(cell, corner, corner, keyPaint)
            }
            val shown = if (isShifted || isCapsLock) popupAlternates[i].uppercase() else popupAlternates[i]
            textPaint.color = if (i == popupIndex) theme.textOnAccent else theme.text
            textPaint.textSize = dp(17f)
            textPaint.typeface = fontFor(shown)
            val ty = cell.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(shown, cell.centerX(), ty, textPaint)
        }
    }

    private fun drawAltPreview(canvas: Canvas) {
        val text = altPreview ?: return
        val rect = pressedRect ?: return
        val pw = rect.width().coerceAtLeast(dp(40f))
        val ph = dp(48f)
        val cx = rect.centerX()
        val left = (cx - pw / 2f).coerceIn(0f, width - pw)
        var top = rect.top - ph - dp(4f)
        if (top < 0f) top = rect.top + dp(2f)
        val pr = RectF(left, top, left + pw, top + ph)
        previewPaint.color = theme.accent
        canvas.drawRoundRect(pr, corner, corner, previewPaint)
        val shown = if (isShifted || isCapsLock) text.uppercase() else text
        textPaint.color = theme.textOnAccent
        textPaint.textSize = dp(24f)
        textPaint.typeface = fontFor(shown)
        val ty = pr.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(shown, cx, ty, textPaint)
    }

    private fun clearPressed() {
        pressedRect = null
        previewKey = null
        downKey = null
        spaceSwipeActive = false
        suggDownIndex = -1
        altPreview = null
        invalidate()
    }

    /** Долгое скольжение по пробелу двигает курсор (шаг ≈ 11dp = 1 позиция). */
    private fun handleSpaceSwipe(x: Float) {
        if (!spaceSwipeActive) {
            spaceSwipeActive = true
            handler.removeCallbacks(longPressRunnable)
            previewKey = null
            invalidate()
        }
        val step = dp(11f)
        var moved = 0
        while (x - spaceLastStepX >= step) { moved++; spaceLastStepX += step }
        while (x - spaceLastStepX <= -step) { moved--; spaceLastStepX -= step }
        if (moved != 0) listener?.onCursorMove(moved)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        closeAltPopup()
    }

    companion object {
        private const val LONGPRESS_MS = 300L
        private const val DELETE_REPEAT_MS = 55L
    }
}
