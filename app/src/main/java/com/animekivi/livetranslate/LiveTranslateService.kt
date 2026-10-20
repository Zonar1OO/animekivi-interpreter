package com.animekivi.livetranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class LiveTranslateService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.animekivi.livetranslate.ACTION_START"
        const val ACTION_STOP = "com.animekivi.livetranslate.ACTION_STOP"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_VOICE = "extra_voice"
        const val EXTRA_OVERLAY = "extra_overlay"
        private const val CHANNEL_ID = "live_translate_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())

    private var listening = false
    private var voiceEnabled = true
    private var overlayEnabled = false
    private var selectedLanguage = "auto"

    private var recognizer: SpeechRecognizer? = null
    private var translator: Translator? = null
    private var activeTranslatorSource: String? = null
    private val languageIdentifier by lazy { LanguageIdentification.getClient() }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var windowManager: WindowManager? = null
    private var captionView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> {
                stopEverything()
                stopForegroundCompat()
                stopSelf()
            }
            else -> {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        selectedLanguage = intent.getStringExtra(EXTRA_LANGUAGE) ?: "auto"
        voiceEnabled = intent.getBooleanExtra(EXTRA_VOICE, true)
        overlayEnabled = intent.getBooleanExtra(EXTRA_OVERLAY, false)

        listening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null

        startForegroundCompat()
        updateNotification("Идёт перевод эфира")

        if (overlayEnabled) showOverlay() else hideOverlay()

        listening = true
        listenOnce()
    }

    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification("Идёт перевод эфира")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ПереводчикKivi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Служба живого перевода эфира"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ПереводчикKivi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showOverlay() {
        if (captionView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val textView = TextView(this).apply {
            text = "ПереводчикKivi: слушаю…"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(28, 18, 28, 18)
            setBackgroundColor(0xDD111827.toInt())
            gravity = Gravity.CENTER
        }
        captionView = textView

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        try {
            windowManager?.addView(textView, params)
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        captionView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        captionView = null
    }

    private fun updateCaption(text: String) {
        captionView?.post { captionView?.text = text }
        updateNotification(text)
    }

    private fun postDelayedListen(delayMillis: Long) {
        handler.postDelayed({ listenOnce() }, delayMillis)
    }

    private fun listenOnce() {
        if (!listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateCaption("На устройстве нет службы распознавания речи.")
            stopEverything()
            stopForegroundCompat()
            stopSelf()
            return
        }

        recognizer?.destroy()
        recognizer = null

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = newRecognizer

        newRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateCaption("Слушаю эфир…")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (listening) postDelayedListen(400)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    translateRecognizedText(text)
                }
                if (listening) postDelayedListen(300)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    updateCaption("…$partial")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            if (selectedLanguage != "auto") {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale(selectedLanguage).toLanguageTag()
                )
            } else if (Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
            }
        }

        try {
            newRecognizer.startListening(recognizerIntent)
        } catch (_: Exception) {
            if (listening) postDelayedListen(500)
        }
    }

    private fun translateRecognizedText(text: String) {
        if (selectedLanguage == "auto") {
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { detectedCode ->
                    val source = TranslateLanguage.fromLanguageTag(detectedCode)
                    handleTranslation(text, source)
                }
                .addOnFailureListener {
                    updateCaption("Не удалось определить язык.")
                }
        } else {
            handleTranslation(text, selectedLanguage)
        }
    }

    private fun handleTranslation(text: String, sourceCode: String?) {
        if (sourceCode == null ||
            sourceCode == TranslateLanguage.RUSSIAN ||
            sourceCode == TranslateLanguage.UNDETERMINED
        ) {
            if (sourceCode == TranslateLanguage.RUSSIAN) {
                updateCaption("Уже на русском: $text")
            }
            return
        }

        val current = translator
        if (current != null && activeTranslatorSource == sourceCode) {
            performTranslation(current, text)
            return
        }

        current?.close()
        activeTranslatorSource = sourceCode

        val newTranslator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build()
        )
        translator = newTranslator

        updateCaption("Загрузка языковой модели…")

        newTranslator.downloadModelIfNeeded()
            .addOnSuccessListener {
                if (listening) performTranslation(newTranslator, text)
            }
            .addOnFailureListener {
                updateCaption("Не удалось загрузить модель языка. Проверь интернет.")
            }
    }

    private fun performTranslation(activeTranslator: Translator, text: String) {
        activeTranslator.translate(text)
            .addOnSuccessListener { translated ->
                updateCaption(translated)
                if (voiceEnabled && ttsReady) {
                    tts.speak(
                        translated,
                        TextToSpeech.QUEUE_ADD,
                        null,
                        "translation_${System.currentTimeMillis()}"
                    )
                }
            }
            .addOnFailureListener {
                updateCaption("Не удалось перевести фразу.")
            }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = Locale("ru", "RU")
        }
    }

    private fun stopEverything() {
        listening = false
        handler.removeCallbacksAndMessages(null)

        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null

        translator?.close()
        translator = null
        activeTranslatorSource = null

        hideOverlay()
    }

    override fun onDestroy() {
        stopEverything()
        if (ttsReady) {
            tts.shutdown()
        }
        languageIdentifier.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}