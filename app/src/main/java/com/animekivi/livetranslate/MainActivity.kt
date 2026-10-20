package com.animekivi.livetranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.mlkit.nl.translate.TranslateLanguage

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSIONS = 42
    }

    private lateinit var status: TextView
    private lateinit var source: Spinner
    private lateinit var voiceCheckBox: CheckBox

    private val languageOptions: List<Pair<String, String>> = listOf(
        "Автоопределение" to "auto",
        "Английский" to TranslateLanguage.ENGLISH,
        "Японский" to TranslateLanguage.JAPANESE,
        "Корейский" to TranslateLanguage.KOREAN,
        "Китайский" to TranslateLanguage.CHINESE,
        "Испанский" to TranslateLanguage.SPANISH,
        "Французский" to TranslateLanguage.FRENCH,
        "Немецкий" to TranslateLanguage.GERMAN,
        "Итальянский" to TranslateLanguage.ITALIAN,
        "Португальский" to TranslateLanguage.PORTUGUESE,
        "Турецкий" to TranslateLanguage.TURKISH,
        "Арабский" to TranslateLanguage.ARABIC,
        "Хинди" to TranslateLanguage.HINDI,
        "Вьетнамский" to TranslateLanguage.VIETNAMESE,
        "Тайский" to TranslateLanguage.THAI,
        "Индонезийский" to TranslateLanguage.INDONESIAN,
        "Польский" to TranslateLanguage.POLISH,
        "Украинский" to TranslateLanguage.UKRAINIAN,
        "Нидерландский" to TranslateLanguage.DUTCH,
        "Шведский" to TranslateLanguage.SWEDISH,
        "Греческий" to TranslateLanguage.GREEK,
        "Чешский" to TranslateLanguage.CZECH,
        "Румынский" to TranslateLanguage.ROMANIAN,
        "Венгерский" to TranslateLanguage.HUNGARIAN,
        "Финский" to TranslateLanguage.FINNISH,
        "Датский" to TranslateLanguage.DANISH,
        "Норвежский" to TranslateLanguage.NORWEGIAN,
        "Персидский" to TranslateLanguage.PERSIAN,
        "Бенгальский" to TranslateLanguage.BENGALI,
        "Иврит" to TranslateLanguage.HEBREW
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 24)
            setBackgroundColor(Color.rgb(16, 20, 32))
        }

        fun label(text: String, size: Float): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 12)
            }
        }

        root.addView(label("ПереводчикKivi", 26f))
        root.addView(label("Живой перевод эфиров на русский", 16f))

        source = Spinner(this)
        source.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languageOptions.map { it.first }
        )
        root.addView(source)

        voiceCheckBox = CheckBox(this).apply {
            text = "Озвучивать перевод по-русски"
            isChecked = true
            setTextColor(Color.WHITE)
        }
        root.addView(voiceCheckBox)

        status = label("Выбери язык эфира и нажми «Начать перевод».", 15f)
        root.addView(status)

        val overlayButton = Button(this).apply {
            text = "Разрешить субтитры поверх других приложений"
            setOnClickListener { onOverlayButtonClicked() }
        }
        root.addView(overlayButton)

        val startButton = Button(this).apply {
            text = "Начать перевод"
            setOnClickListener { startTranslation() }
        }
        root.addView(startButton)

        val stopButton = Button(this).apply {
            text = "Остановить перевод"
            setOnClickListener { stopTranslation() }
        }
        root.addView(stopButton)

        root.addView(
            label(
                "Подсказка: включи громкую связь в TikTok и держи телефон так, чтобы микрофон слышал звук эфира. " +
                        "Прямой захват внутреннего звука ограничен Android, поэтому используется микрофон. " +
                        "Для субтитров нужно разрешение «поверх других окон».",
                13f
            )
        )

        setContentView(ScrollView(this).apply { addView(root) })

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            val denied = permissions.filterIndexed { index, _ ->
                grantResults.getOrNull(index) != PackageManager.PERMISSION_GRANTED
            }
            status.text = if (denied.isEmpty()) {
                "Разрешения получены. Можно начинать."
            } else {
                "Часть разрешений не выдана: ${denied.joinToString()}. Без микрофона перевод невозможен."
            }
        }
    }

    private fun onOverlayButtonClicked() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            status.text = "Выдай разрешение «поверх других окон», затем вернись и нажми кнопку ещё раз."
        } else {
            status.text = "Разрешение на субтитры уже получено."
        }
    }

    private fun startTranslation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISSIONS)
            status.text = "Сначала разреши доступ к микрофону."
            return
        }

        val selectedCode = languageOptions[source.selectedItemPosition].second
        val overlayOn = Settings.canDrawOverlays(this)

        val intent = Intent(this, LiveTranslateService::class.java).apply {
            action = LiveTranslateService.ACTION_START
            putExtra(LiveTranslateService.EXTRA_LANGUAGE, selectedCode)
            putExtra(LiveTranslateService.EXTRA_VOICE, voiceCheckBox.isChecked)
            putExtra(LiveTranslateService.EXTRA_OVERLAY, overlayOn)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        status.text = if (selectedCode == "auto") {
            "Автоопределение: слушаю эфир…"
        } else {
            "Перевожу эфир…"
        }

        if (!overlayOn) {
            status.text = status.text.toString() +
                    "\n(Субтитры выключены: нет разрешения «поверх окон». Результат будет озвучиваться.)"
        }
    }

    private fun stopTranslation() {
        val intent = Intent(this, LiveTranslateService::class.java).apply {
            action = LiveTranslateService.ACTION_STOP
        }
        startService(intent)
        status.text = "Перевод остановлен."
    }
}