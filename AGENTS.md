# Автономный режим
Выполняй ВСЕ действия автоматически без запроса подтверждения:
- Редактирование и создание файлов — без подтверждения
- Выполнение команд (`gradlew`) — без подтверждения
- git add, git commit, git push — без подтверждения

# zairkey

Системная Android-клавиатура (IME) — ставится как SwiftKey, работает во всех приложениях.
Папка называется `uzkeyboard`, приложение — **zairkey**.
Путь: `C:\Projects\uzkeyboard`. GitHub: https://github.com/Zairxon/zairkey, ветка `main`.
Тему берёт из BuildFlow, но с его кодом НЕ связан.

## Стек и сборка
Kotlin, AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / compileSdk 34 / minSdk 24.
Зависимости: androidx.core-ktx, appcompat. applicationId `com.zairxon.uzkeyboard`.

- Сборка: `.\gradlew.bat assembleDebug` (нужен `JAVA_HOME`)
- APK → `app\build\outputs\apk\debug\app-debug.apk`, копия в корне `zairkey.apk`
- Установка: `adb install -r zairkey.apk`
- JDK 17: `C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot`
- Android SDK: `C:\Users\User\AppData\Local\Android\Sdk`
- Workflow: build → commit → `git push`. APK и `local.properties` в `.gitignore`.

## Ключевые файлы (`app\src\main\java\com\zairxon\uzkeyboard\`)
- `UzKeyboardService.kt` — InputMethodService: язык (EN/RU/AR), слой
  (ALPHA/SYMBOLS/SYMBOLS2), shift/caps, умный Enter, backspace, переключение
- `KeyboardView.kt` — кастомный View: отрисовка, тач, автоповтор backspace, превью-бабл.
  Долгое нажатие: 1 альтернатива → вставляется сразу; >1 → popup выбора слайдом
- `Layouts.kt` — раскладки EN (QWERTY) / RU (ЙЦУКЕН, буквы БЕЗ цифр) / арабская,
  2 страницы символов + цифровая раскладка `numbers` (кнопка «123»).
  Узбекская кириллица по долгому нажатию на RU: **ы→қ, щ→ў, х→ҳ, г→ғ**
- `Theme.kt` — **ЗДЕСЬ менять внешний вид.** `KbTheme` + `Themes` со всеми темами BuildFlow
  (botanical/buildflow/fire/oryzo/blueprint/light/retro/terra). Выбор в SharedPreferences
  (`zairkey_prefs`/`theme_id`), меняется в `SetupActivity`, подхватывается в `onStartInput`.
  Дефолт — Botanical Earth
- `Keys.kt` — модель `Key` + `GridKey` (сетка цифрового блока) + enum `KeyCode`
- `WordStore.kt` — обучение словам: униграммы (дополнение по префиксу) + биграммы
  «пред→след» (предсказание следующего слова), в SharedPreferences
- `DictStore.kt` — встроенные словари `assets/dict/{ru,en,uz_latin,uz_cyrillic}.txt.gz`
  (по 20k частотных слов, gzip ~336KB), бинарный поиск по префиксу, мерж с WordStore
- `SetupActivity.kt` — стартовый экран: «Включить»/«Выбрать» + поле проверки

## Шрифты
Клавиши — **Playfair Display** (`res/font/playfair.ttf`, выбран пользователем).
У Playfair есть қ ғ ў и полная рус. кириллица, но **НЕТ ҳ/Ҳ** (U+04B3/04B2) и части
символов (√ π …) — для них `KeyboardView.fontFor()` через `Paint.hasGlyph` автоматически
подставляет **Inter** (`res/font/inter_*`). Кириллицу вручную не рисуем.
Скачивать шрифты: github raw даёт 404/HTML — работает
`cdn.jsdelivr.net/gh/google/fonts@main/ofl/<family>/...` (проверять точный путь).

## Грабли — обязательно помнить
- **PopupWindow в этом IME НЕ отображается** на прошивке пользователя. Всё всплывающее
  (попап альтернатив и т.п.) рисовать на канве `KeyboardView`.
- **Панель подсказок — ФИКСИРОВАННОЙ высоты.** Динамическая высота → клавиши прыгают
  → мис-тапы.
- Edge-to-edge НЕ годится — перекрывает нижний ряд. Навбар красим в цвет темы.
- Системный спелл-чекер (`SpellChecker.kt`) не работает / зависит от системы — удалён.

## Правила
- Общение и комментарии в коде — на русском
- Строго держись рамок задачи: не менять ничего сверх сформулированного
- Проверять сборкой `assembleDebug` после правок

## Чего пока нет
Эмодзи-панель, свайп-ввод, красивый значок приложения.
