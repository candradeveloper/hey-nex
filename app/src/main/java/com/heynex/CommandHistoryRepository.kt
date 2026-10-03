package com.heynex

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Simple in-memory repository for command history updates across the app.
 */
object CommandHistoryRepository {

    private val _commands = MutableLiveData<List<String>>(emptyList())
    val commands: LiveData<List<String>> = _commands

    fun add(command: String) {
        val current = _commands.value ?: emptyList()
        val updated = (current + command).takeLast(50)
        _commands.postValue(updated)
    }
}
