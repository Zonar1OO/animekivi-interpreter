package com.animekivi.livetranslate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.mlkit.nl.translate.TranslateLanguage

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSIONS = 42
        private const val REQ_MEDIA_PROJECTION = 43
    }

    private lateinit var status: TextView
    private lateinit var source: Spinner
    private lateinit var voiceCheckBox: CheckBox
    private lateinit var captureModeCheckBox: CheckBox

    private var mediaProjectionManager: MediaProjectionManager? = null

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
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

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

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        try {
            val iconStream = assets.open("icon.png")
            val iconBitmap = android.graphics.BitmapFactory.decodeStream(iconStream)
            val iconView = ImageView(this).apply {
                setImageBitmap(iconBitmap)
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    marginEnd = 24
                }
            }
            titleLayout.addView(iconView)
        } catch (e: Exception) {
            // Иконка не найдена, пропускаем
        }

        val titleText = TextView(this).apply {
            text = "ПереводчикKivi"
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        titleLayout.addView(titleText)
        root.addView(titleLayout)

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

        captureModeCheckBox = CheckBox(this).apply {
            text = "Захват внутреннего звука устройства (требует API ключ)"
            isChecked = false
            setTextColor(Color.WHITE)
        }
        root.addView(captureModeCheckBox)

        status = label("Выбери язык эфира и нажми «Начать перевод».", 15f)
        root.addView(status)

        val overlayButton = Button(this).apply {
            text = "Разрешить субтитры поверх других приложений"
            setOnClickListener { onOverlayButtonClicked() }
        }
        root.addView(overlayButton)

        val apiKeyButton = Button(this).apply {
            text = "Установить API ключ Google Cloud"
            setOnClickListener { onApiKeyButtonClicked() }
        }
        root.addView(apiKeyButton)

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
                "Режим микрофона: включи громкую связь, чтобы микрофон слышал звук из динамиков. " +
                        "Режим захвата: требует API ключ Google Cloud и может не работать с TikTok из-за DRM. " +
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val prefs = getSharedPreferences("api", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("google_cloud_api_key", null)
            if (apiKey.isNullOrEmpty()) {
                status.text = "API ключ не установлен. Использую режим микрофона."
                startWithMicrophone()
            } else {
                startWithCapture(data, apiKey)
            }
        } else {
            status.text = "Захват отменён. Использую режим микрофона."
            startWithMicrophone()
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

    private fun onApiKeyButtonClicked() {
        val prefs = getSharedPreferences("api", Context.MODE_PRIVATE)
        val currentKey = prefs.getString("google_cloud_api_key", "")

        val input = android.widget.EditText(this).apply {
            hint = "Вставь API ключ Google Cloud"
            setText(currentKey)
            setPadding(32, 32, 32, 32)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("API ключ Google Cloud")
            .setMessage("Для режима захвата звука нужен API ключ Google Cloud Speech-to-Text. Получи его в Google Cloud Console.")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = input.text.toString().trim()
                prefs.edit().putString("google_cloud_api_key", key).apply()
                status.text = if (key.isNotEmpty()) "API ключ сохранён." else "API ключ удалён."
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startTranslation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISSIONS)
            status.text = "Сначала разреши доступ к микрофону."
            return
        }

        if (captureModeCheckBox.isChecked) {
            val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
            if (captureIntent != null) {
                startActivityForResult(captureIntent, REQ_MEDIA_PROJECTION)
            } else {
                status.text = "MediaProjection недоступен на этом устройстве."
                startWithMicrophone()
            }
        } else {
            startWithMicrophone()
        }
    }

    private fun startWithMicrophone() {
        val selectedCode = languageOptions[source.selectedItemPosition].second
        val overlayOn = Settings.canDrawOverlays(this)

        val intent = Intent(this, LiveTranslateService::class.java).apply {
            action = LiveTranslateService.ACTION_START
            putExtra(LiveTranslateService.EXTRA_LANGUAGE, selectedCode)
            putExtra(LiveTranslateService.EXTRA_VOICE, voiceCheckBox.isChecked)
            putExtra(LiveTranslateService.EXTRA_OVERLAY, overlayOn)
            putExtra(LiveTranslateService.EXTRA_CAPTURE_MODE, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        status.text = if (selectedCode == "auto") {
            "Автоопределение: слушаю эфир через микрофон…"
        } else {
            "Перевожу эфир через микрофон…"
        }

        if (!overlayOn) {
            status.text = status.text.toString() +
                    "\n(Субтитры выключены: нет разрешения «поверх окон». Результат будет озвучиваться.)"
        }
    }

    private fun startWithCapture(projectionData: Intent, apiKey: String) {
        val selectedCode = languageOptions[source.selectedItemPosition].second
        val overlayOn = Settings.canDrawOverlays(this)

        val intent = Intent(this, LiveTranslateService::class.java).apply {
            action = LiveTranslateService.ACTION_START
            putExtra(LiveTranslateService.EXTRA_LANGUAGE, selectedCode)
            putExtra(LiveTranslateService.EXTRA_VOICE, voiceCheckBox.isChecked)
            putExtra(LiveTranslateService.EXTRA_OVERLAY, overlayOn)
            putExtra(LiveTranslateService.EXTRA_CAPTURE_MODE, true)
            putExtra(LiveTranslateService.EXTRA_PROJECTION_DATA, projectionData)
            putExtra(LiveTranslateService.EXTRA_API_KEY, apiKey)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        status.text = if (selectedCode == "auto") {
            "Автоопределение: захватываю внутренний звук…"
        } else {
            "Захватываю и перевожу внутренний звук…"
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
