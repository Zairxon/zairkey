package com.zairxon.uzkeyboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
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

        // Кнопки-темы (цвет фона = акцент темы)
        for (theme in Themes.all) {
            val selected = theme.id == t.id
            root.addView(Button(this).apply {
                text = (if (selected) "●  " else "") + theme.title
                isAllCaps = false
                setBackgroundColor(theme.accent)
                setTextColor(theme.textOnAccent)
                typeface = Fonts.medium(this@SetupActivity)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                setOnClickListener {
                    Themes.setCurrent(this@SetupActivity, theme.id)
                    Toast.makeText(
                        this@SetupActivity,
                        "Тема «${theme.title}» применена. Откройте клавиатуру заново.",
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }
            })
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
