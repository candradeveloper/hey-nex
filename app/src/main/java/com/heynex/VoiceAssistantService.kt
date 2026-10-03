package com.heynex

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that listens for the wake word and processes voice commands.
 */
class VoiceAssistantService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var overlayManager: OverlayManager
    private lateinit var settings: AppSettings
    private val handler = Handler(Looper.getMainLooper())
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var isListening = false
    private var ttsReady = false
    private val wakeWord = "hey nex"

    companion object {
        private const val CHANNEL_ID = "heynex_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "VoiceAssistantService"
        private const val WAKE_WORD_CONFIDENCE = 0.6f

        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java)
            context.stopService(intent)
        }

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = AppSettings(this)
        overlayManager = OverlayManager(this)
        createNotificationChannel()
        initTts()
        initSpeechRecognizer()
    }

    private fun initTts() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = Locale("id", "ID")
            tts.setSpeechRate(settings.voiceSpeed)
            commandProcessor = CommandProcessor(this, tts, settings)
            commandProcessor.listener = object : CommandProcessor.CommandListener {
                override fun onCommandProcessed(command: String, response: String) {
                    Log.d(TAG, "Response: $response")
                    if (command.isNotBlank()) {
                        CommandHistoryRepository.add(command)
                    }
                    if (response.isNotBlank()) {
                        CommandHistoryRepository.add(response)
                    }
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (::speechRecognizer.isInitialized) {
            startListening()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return
        }
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            isListening = false
        }
    }

    private fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop listening", e)
        }
        isListening = false
    }

    private fun restartListening(delayMs: Long = 500) {
        stopListening()
        handler.postDelayed({ startListening() }, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        restartListening(300)
    }

    override fun onError(error: Int) {
        Log.w(TAG, "Speech error: $error")
        restartListening(300)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
        if (text.isNotBlank()) {
            handleHeardText(text)
        }
        restartListening(300)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
        if (text.contains(wakeWord)) {
            overlayManager.show()
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun handleHeardText(text: String) {
        if (text.contains(wakeWord)) {
            overlayManager.show()
            serviceScope.launch {
                delay(1500)
                overlayManager.hide()
            }
            val command = text.replace(wakeWord, "").trim()
            if (command.isNotBlank()) {
                if (::commandProcessor.isInitialized) {
                    commandProcessor.processCommand(command)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hey Nex aktif")
            .setContentText("Mendengarkan perintah suara...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey Nex Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service for Hey Nex voice assistant"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        overlayManager.hide()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
}
