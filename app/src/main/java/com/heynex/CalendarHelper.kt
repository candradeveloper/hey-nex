package com.heynex

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for reading calendar events.
 */
class CalendarHelper(private val context: Context) {

    fun readNextEvents(): String {
        val now = System.currentTimeMillis()
        val end = now + 24 * 60 * 60 * 1000
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            selection,
            arrayOf(now.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )
        val events = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val title = it.getString(0) ?: "Acara tanpa judul"
                val start = it.getLong(1)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))
                events.add("$title jam $time")
            }
        }
        return if (events.isEmpty()) {
            "Tidak ada acara dalam 24 jam ke depan"
        } else {
            "Acara Anda: ${events.joinToString(", ")}"
        }
    }
}
