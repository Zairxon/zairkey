package com.zairxon.uzkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Стартовый экран: включение/выбор клавиатуры, выбор темы, поле проверки. */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = Themes.current(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(t.background) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(32))
        }

        root.addView(TextView(this).apply {
            text = "zairkey"
            textSize = 28f
            setTextColor(t.text)
            typeface = Fonts.medium(this@SetupActivity)
        })

        root.addView(TextView(this).apply {
            val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            text = "Версия $ver"
            textSize = 14f
            setTextColor(t.hint)
            typeface = Fonts.regular(this@SetupActivity)
        })

        root.addView(TextView(this).apply {
            text = "1.  Нажмите «Включить» и включите zairkey в списке.\n" +
                "2.  Нажмите «Выбрать» и выберите zairkey.\n" +
                "3.  Проверьте ввод в поле ниже.\n\n" +
                "🌐 — переключение EN / RU.\n" +
                "В русской раскладке долгое нажатие даёт узбекские буквы:\n" +
                "ы → қ,   щ → ў,   х → ҳ,   г → ғ.\n" +
                "?123 — знаки, кнопка 123 — только цифры."
            textSize = 16f
            setTextColor(t.text)
            typeface = Fonts.regular(this@SetupActivity)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(18), 0, dp(20))
        })

        root.addView(Button(this).apply {
            text = "Включить клавиатуру"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Выбрать клавиатуру"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })

        root.addView(TextView(this).apply {
            text = "Тема оформления:"
            textSize = 16f
            setTextColor(t.text)
            typeface = Fonts.regular(this@SetupActivity)
            setPadding(0, dp(24), 0, dp(8))
        })

        // Темы как в Gboard: мини-клавиатура с оформлением темы, по две в ряд
        for (pair in Themes.all.chunked(2)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
            }
            for ((i, theme) in pair.withIndex()) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { if (i == 0) rightMargin = dp(6) else leftMargin = dp(6) }
                    setOnClickListener {
                        Themes.setCurrent(this@SetupActivity, theme.id)
                        Toast.makeText(
                            this@SetupActivity,
                            "Тема «${theme.title}» применена. Откройте клавиатуру заново.",
                            Toast.LENGTH_SHORT
                        ).show()
                        recreate()
                    }
                }
                card.addView(ThemePreview(this, theme, theme.id == t.id).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(120)
                    )
                })
                card.addView(TextView(this).apply {
                    text = theme.title
                    textSize = 14f
                    setTextColor(t.text)
                    typeface = Fonts.regular(this@SetupActivity)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                row.addView(card)
            }
            root.addView(row)
        }

        root.addView(TextView(this).apply {
            text = "Проверка ввода:"
            textSize = 16f
            setTextColor(t.text)
            typeface = Fonts.regular(this@SetupActivity)
            setPadding(0, dp(28), 0, dp(8))
        })
        root.addView(EditText(this).apply {
            hint = "Печатайте здесь…"
            textSize = 18f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#F0F1F4"))
            setTextColor(Color.parseColor("#101010"))
            minLines = 3
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

/** Мини-превью клавиатуры в цветах темы (как в Gboard): ряды клавиш, пробел и Enter. */
private class ThemePreview(
    context: Context,
    private val theme: KbTheme,
    private val selected: Boolean
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letters = arrayOf("ЙЦУКЕН", "ФЫВАП", "ЯЧСМИ")

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val r = 8f * d
        val w = width.toFloat()
        val h = height.toFloat()
        // Фон панели
        paint.style = Paint.Style.FILL
        paint.color = theme.background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)

        val pad = 6f * d
        val gap = 3f * d
        val rows = 4
        val keyH = (h - 2 * pad - (rows - 1) * gap) / rows
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Fonts.regular(context)
        paint.textSize = keyH * 0.5f

        for (ri in 0 until 3) {
            val row = letters[ri]
            val keyW = (w - 2 * pad - 5 * gap) / 6 // все ряды — по ширине шести клавиш
            val rowW = row.length * keyW + (row.length - 1) * gap
            var x = (w - rowW) / 2
            val top = pad + ri * (keyH + gap)
            for (ch in row) {
                paint.color = theme.keyNormal
                canvas.drawRoundRect(RectF(x, top, x + keyW, top + keyH), 4f * d, 4f * d, paint)
                paint.color = theme.text
                canvas.drawText(ch.toString(), x + keyW / 2, top + keyH / 2 + paint.textSize * 0.35f, paint)
                x += keyW + gap
            }
        }
        // Нижний ряд: спецклавиша, пробел, Enter (акцент)
        val top = pad + 3 * (keyH + gap)
        val special = 0.2f * (w - 2 * pad)
        val enter = 0.2f * (w - 2 * pad)
        val space = (w - 2 * pad) - special - enter - 2 * gap
        paint.color = theme.keySpecial
        canvas.drawRoundRect(RectF(pad, top, pad + special, top + keyH), 4f * d, 4f * d, paint)
        paint.color = theme.keyNormal
        canvas.drawRoundRect(RectF(pad + special + gap, top, pad + special + gap + space, top + keyH), 4f * d, 4f * d, paint)
        paint.color = theme.accent
        canvas.drawRoundRect(RectF(w - pad - enter, top, w - pad, top + keyH), 4f * d, 4f * d, paint)

        // Рамка выбранной темы
        if (selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.color = theme.accent
            canvas.drawRoundRect(RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d), r, r, paint)
        }
    }
}
