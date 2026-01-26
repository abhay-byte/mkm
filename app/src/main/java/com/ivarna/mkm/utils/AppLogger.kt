package com.ivarna.mkm.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // Add to top
        if (currentLogs.size > 100) {
            currentLogs.removeAt(currentLogs.lastIndex)
        }
        _logs.value = currentLogs
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
}
