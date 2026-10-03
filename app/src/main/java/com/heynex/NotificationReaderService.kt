package com.heynex

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listens to notifications so the assistant can read them back on request.
 */
class NotificationReaderService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val text = extractText(it)
            if (text.isNotBlank()) {
                recentNotifications.add(text)
                if (recentNotifications.size > MAX_SIZE) {
                    recentNotifications.removeAt(0)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
    }

    companion object {
        private const val MAX_SIZE = 20
        private var instance: NotificationReaderService? = null
        private var connected = false
        private val recentNotifications = CopyOnWriteArrayList<String>()

        fun isConnected(): Boolean = connected && instance != null

        fun getActiveNotificationsText(): List<String> {
            return recentNotifications.toList()
        }
    }

    private fun extractText(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        return "${sbn.packageName}: $title. $text"
    }
}
