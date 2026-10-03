package com.heynex

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Processes recognized voice commands and dispatches device actions.
 */
class CommandProcessor(
    private val context: Context,
    private val tts: TextToSpeech,
    private val settings: AppSettings
) {

    interface CommandListener {
        fun onCommandProcessed(command: String, response: String)
    }

    var listener: CommandListener? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentCommand = ""

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                @Suppress("DEPRECATION")
                audioManager.mode = AudioManager.MODE_NORMAL
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun processCommand(command: String) {
        currentCommand = command
        val lower = command.lowercase(Locale.getDefault())
        when {
            lower.contains("buka") -> openApp(lower)
            lower.contains("telpon") || lower.contains("telepon") || lower.contains("panggil") -> makePhoneCall(lower)
            lower.contains("kirim wa") || lower.contains("kirim whatsapp") -> sendWhatsApp(lower)
            lower.contains("alarm") -> setAlarm(lower)
            lower.contains("timer") -> setTimer(lower)
            lower.contains("bacain notifikasi") || lower.contains("baca notifikasi") -> readNotifications()
            lower.contains("wifi") -> toggleWifi(lower)
            lower.contains("bluetooth") -> toggleBluetooth(lower)
            lower.contains("volume") -> adjustVolume(lower)
            lower.contains("brightness") || lower.contains("kecerahan") -> adjustBrightness(lower)
            lower.contains("musik") || lower.contains("lagu") || lower.contains("media") -> controlMedia(lower)
            lower.contains("cari") -> webSearch(lower)
            lower.contains("kalender") || lower.contains("agenda") -> readCalendarEvents()
            else -> handleGeneralQuery(command)
        }
    }

    private fun openApp(command: String) {
        val appName = command.replace(".*buka\\s?".toRegex(), "").trim()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matched = apps.find {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            label.contains(appName) || appName.contains(label)
        }
        if (matched != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                speak("Membuka ${pm.getApplicationLabel(matched)}")
                return
            }
        }
        speak("Aplikasi $appName tidak ditemukan")
    }

    private fun makePhoneCall(command: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS) || !hasPermission(Manifest.permission.CALL_PHONE)) {
            speak("Izinkan akses kontak dan telepon untuk menelepon")
            return
        }
        val name = command.replace(".*(?:telpon|telepon|panggil)\\s?".toRegex(), "").trim()
        val number = findContactNumber(name)
        if (number != null) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            speak("Menelepon $name")
        } else {
            speak("Kontak $name tidak ditemukan")
        }
    }

    private fun sendWhatsApp(command: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            speak("Izinkan akses kontak untuk mengirim pesan WhatsApp")
            return
        }
        val regex = "kirim\\s+(?:wa|whatsapp)(?:\\s+ke)?\\s+(.+?)(?:\\s+yang\\s+berbunyi)?(?:\\s+pesan\\s+(.+))?".toRegex()
        val match = regex.find(command.lowercase())
        val contactName = match?.groupValues?.get(1)?.trim() ?: command
        val message = match?.groupValues?.get(2)?.trim() ?: ""
        val number = findContactNumber(contactName) ?: contactName.replace("[^0-9]".toRegex(), "")
        if (number.isBlank()) {
            speak("Kontak $contactName tidak ditemukan")
            return
        }
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            speak("Membuka WhatsApp untuk $contactName")
        } catch (e: ActivityNotFoundException) {
            speak("WhatsApp tidak terpasang")
        }
    }

    private fun setAlarm(command: String) {
        val hour = parseHour(command)
        val minute = parseMinute(command)
        val isPm = command.contains("sore") || command.contains("malam") || command.contains("pm")
        val isAm = command.contains("pagi") || command.contains("subuh")
        var adjustedHour = hour
        if (isPm && hour != 12) adjustedHour += 12
        if (isAm && hour == 12) adjustedHour = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, adjustedHour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Hey Nex Alarm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            speak("Mengatur alarm jam ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
        } catch (e: ActivityNotFoundException) {
            speak("Tidak dapat mengatur alarm")
        }
    }

    private fun setTimer(command: String) {
        val minutes = parseNumber(command) ?: 1
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Hey Nex Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            speak("Mengatur timer selama $minutes menit")
        } catch (e: ActivityNotFoundException) {
            speak("Tidak dapat mengatur timer")
        }
    }

    private fun readNotifications() {
        if (!NotificationReaderService.isConnected()) {
            speak("Aktifkan akses notifikasi Hey Nex di pengaturan")
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        val notifications = NotificationReaderService.getActiveNotificationsText()
        if (notifications.isEmpty()) {
            speak("Tidak ada notifikasi saat ini")
        } else {
            speak("Anda punya ${notifications.size} notifikasi. ${notifications.joinToString(". ")}")
        }
    }

    private fun toggleWifi(command: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                speak("Silakan aktifkan WiFi di pengaturan")
            } else {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                val turnOn = !command.contains("mati")
                wifiManager.isWifiEnabled = turnOn
                speak("WiFi ${if (turnOn) "diaktifkan" else "dimatikan"}")
            }
        } catch (e: SecurityException) {
            speak("Gagal mengubah pengaturan WiFi")
        }
    }

    private fun toggleBluetooth(command: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            speak("Perangkat ini tidak memiliki Bluetooth")
            return
        }
        val turnOn = !command.contains("mati")
        try {
            if (turnOn) {
                if (!adapter.isEnabled) adapter.enable()
            } else {
                if (adapter.isEnabled) adapter.disable()
            }
            speak("Bluetooth ${if (turnOn) "diaktifkan" else "dimatikan"}")
        } catch (e: SecurityException) {
            speak("Gagal mengubah pengaturan Bluetooth")
        }
    }

    private fun adjustVolume(command: String) {
        val stream = when {
            command.contains("media") || command.contains("musik") -> AudioManager.STREAM_MUSIC
            command.contains("ring") || command.contains("nada") -> AudioManager.STREAM_RING
            command.contains("alarm") -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        val max = audioManager.getStreamMaxVolume(stream)
        val current = audioManager.getStreamVolume(stream)
        when {
            command.contains("naik") || command.contains("besar") || command.contains("up") || command.contains("louder") -> {
                audioManager.setStreamVolume(stream, (current + 1).coerceAtMost(max), AudioManager.FLAG_SHOW_UI)
                speak("Volume dinaikkan")
            }
            command.contains("turun") || command.contains("kecil") || command.contains("down") || command.contains("softer") -> {
                audioManager.setStreamVolume(stream, (current - 1).coerceAtLeast(0), AudioManager.FLAG_SHOW_UI)
                speak("Volume diturunkan")
            }
            command.contains("mati") || command.contains("mute") -> {
                audioManager.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                speak("Volume dimatikan")
            }
            else -> {
                speak("Volume saat ini $current dari $max")
            }
        }
    }

    private fun adjustBrightness(command: String) {
        try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            when {
                command.contains("naik") || command.contains("besar") -> {
                    val newValue = (current + 50).coerceAtMost(255)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
                    speak("Kecerahan dinaikkan")
                }
                command.contains("turun") || command.contains("kecil") -> {
                    val newValue = (current - 50).coerceAtLeast(0)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
                    speak("Kecerahan diturunkan")
                }
                else -> speak("Kecerahan saat ini $current dari 255")
            }
        } catch (e: SecurityException) {
            speak("Diperlukan izin mengubah kecerahan layar")
        }
    }

    private fun controlMedia(command: String) {
        val keyCode = when {
            command.contains("play") || command.contains("main") || command.contains("putar") -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            command.contains("pause") || command.contains("jeda") -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            command.contains("next") || command.contains("lanjut") || command.contains("skip") -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            command.contains("previous") || command.contains("sebelum") -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        speak("Perintah media diproses")
    }

    private fun webSearch(command: String) {
        val query = command.replace(".*cari\\s?".toRegex(), "").trim()
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
        intent.putExtra("query", query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            speak("Mencari $query")
        } catch (e: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    private fun readCalendarEvents() {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            speak("Izinkan akses kalender untuk membaca acara")
            return
        }
        val now = Calendar.getInstance().timeInMillis
        val end = now + 24 * 60 * 60 * 1000
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, arrayOf(now.toString(), end.toString()), "${CalendarContract.Events.DTSTART} ASC")
        val events = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0) ?: "Acara tanpa judul"
                val start = it.getLong(1)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))
                events.add("$title jam $time")
            }
        }
        if (events.isEmpty()) {
            speak("Tidak ada acara dalam 24 jam ke depan")
        } else {
            speak("Acara Anda: ${events.joinToString(", ")}")
        }
    }

    private fun handleGeneralQuery(command: String) {
        val apiKey = settings.groqApiKey
        if (apiKey.isNullOrBlank()) {
            speak("Simpan kunci API Groq di pengaturan untuk pertanyaan umum")
            return
        }
        val client = GroqApiClient(apiKey)
        GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val result = client.ask(command)
            result.onSuccess { answer ->
                speak(answer)
            }.onFailure { error ->
                val msg = error.message ?: "Terjadi kesalahan"
                speak(msg)
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        listener?.onCommandProcessed(currentCommand, text)
        val utteranceId = System.currentTimeMillis().toString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun findContactNumber(name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        val cursor = context.contentResolver.query(uri, projection, selection, arrayOf("%$name%"), null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun parseHour(command: String): Int {
        val match = Pattern.compile("(\\d+)\\s*(?::|\\s|$)").matcher(command)
        if (match.find()) {
            return match.group(1)?.toIntOrNull() ?: 6
        }
        return 6
    }

    private fun parseMinute(command: String): Int {
        val match = Pattern.compile("(\\d+):\\s*(\\d+)").matcher(command)
        if (match.find()) {
            return match.group(2)?.toIntOrNull() ?: 0
        }
        return 0
    }

    private fun parseNumber(command: String): Int? {
        val match = Pattern.compile("(\\d+)\\s*(?:menit|minute|min)").matcher(command)
        if (match.find()) {
            return match.group(1)?.toIntOrNull()
        }
        return null
    }
}
