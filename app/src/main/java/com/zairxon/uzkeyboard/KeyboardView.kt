package com.zairxon.uzkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

    private val rowHeight = dp(44f)
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
    private val longPressRunnable = Runnable { fireLongPress() }
    private var deleteRunnable: Runnable? = null

    // Скольжение по пробелу для перемещения курсора
    private var spaceSwipeActive = false
    private var spaceStartX = 0f
    private var spaceLastStepX = 0f

    // Long-press alternates popup
    private var popupWindow: PopupWindow? = null
    private var popupActive = false
    private var popupAlternates: List<String> = emptyList()
    private var popupIndex = 0
    private var popupOriginX = 0f
    private var popupCellW = 0f
    private val altViews = ArrayList<TextView>()

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
        previewKey?.let { pk ->
            pressedRect?.let { pr ->
                if (pk.code == KeyCode.CHAR && !popupActive) drawPreview(canvas, pk, pr)
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
                val hit = keyAt(event.x, event.y)
                downKey = hit?.first
                pressedRect = hit?.second
                previewKey = downKey
                longPressFired = false
                spaceSwipeActive = false
                if (downKey?.code == KeyCode.SPACE) {
                    spaceStartX = event.x
                    spaceLastStepX = event.x
                }
                invalidate()
                if (downKey != null) handler.postDelayed(longPressRunnable, LONGPRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupActive) {
                    updatePopupSelection(event.rawX)
                } else if (downKey?.code == KeyCode.SPACE &&
                    (spaceSwipeActive || abs(event.x - spaceStartX) > dp(8f))) {
                    handleSpaceSwipe(event.x)
                } else {
                    val hit = keyAt(event.x, event.y)
                    // Long-press stays armed while the finger remains on the SAME key
                    // (small jitter no longer cancels it). Re-arm only when key changes.
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
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                stopDeleteRepeat()
                if (suggDownIndex >= 0) {
                    val word = suggestions.getOrNull(suggDownIndex)
                    suggDownIndex = -1
                    if (word != null) listener?.onSuggestionPicked(word)
                    return true
                }
                if (popupActive) {
                    commitPopupSelection()
                    dismissPopup()
                } else if (!longPressFired && !spaceSwipeActive) {
                    downKey?.let { handleTap(it) }
                }
                clearPressed()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                stopDeleteRepeat()
                dismissPopup()
                clearPressed()
                return true
            }
        }
        return true
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
                // одна альтернатива (узбекские буквы, ё) — вставляем сразу, не ждём отпускания
                longPressFired = true
                commitChar(k.alternates[0])
            }
            k.code == KeyCode.CHAR && k.alternates.size > 1 -> {
                // несколько знаков (точка/запятая/валюты) — показываем попап выбора
                longPressFired = true
                pressedRect?.let { showPopup(k, it) }
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

    private fun showPopup(k: Key, rect: RectF) {
        popupAlternates = k.alternates
        popupActive = true
        val cellW = dp(46f)
        val cellH = dp(52f)
        val pad = dp(4f)
        popupCellW = cellW

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.popupBg)
            setPadding(pad.toInt(), pad.toInt(), pad.toInt(), pad.toInt())
        }
        altViews.clear()
        for (alt in popupAlternates) {
            val shown = if (isShifted || isCapsLock) alt.uppercase() else alt
            val tv = TextView(context).apply {
                text = shown
                setTextColor(theme.text)
                textSize = 20f
                typeface = fontFor(shown)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(cellW.toInt(), cellH.toInt())
            }
            altViews.add(tv)
            container.addView(tv)
        }

        val pw = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        )
        pw.isClippingEnabled = false
        popupWindow = pw

        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val totalW = cellW * popupAlternates.size + pad * 2
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        var px = loc[0] + rect.centerX() - totalW / 2f
        px = px.coerceIn(0f, (screenW - totalW).coerceAtLeast(0f))
        val py = loc[1] + rect.top - cellH - dp(10f)
        popupOriginX = px + pad
        pw.showAtLocation(this, Gravity.NO_GRAVITY, px.toInt(), py.toInt())

        popupIndex = 0
        highlightPopup()
    }

    private fun updatePopupSelection(rawX: Float) {
        if (popupAlternates.isEmpty()) return
        val idx = ((rawX - popupOriginX) / popupCellW).toInt()
            .coerceIn(0, popupAlternates.size - 1)
        if (idx != popupIndex) {
            popupIndex = idx
            highlightPopup()
        }
    }

    private fun highlightPopup() {
        altViews.forEachIndexed { i, tv ->
            val selected = i == popupIndex
            tv.setBackgroundColor(if (selected) theme.popupSelected else Color.TRANSPARENT)
            tv.setTextColor(if (selected) theme.textOnAccent else theme.text)
        }
    }

    private fun commitPopupSelection() {
        val alt = popupAlternates.getOrNull(popupIndex) ?: return
        commitChar(alt)
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
        popupActive = false
        popupAlternates = emptyList()
        altViews.clear()
    }

    private fun clearPressed() {
        pressedRect = null
        previewKey = null
        downKey = null
        spaceSwipeActive = false
        suggDownIndex = -1
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
        dismissPopup()
    }

    companion object {
        private const val LONGPRESS_MS = 300L
        private const val DELETE_REPEAT_MS = 55L
    }
}
