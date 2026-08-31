package com.ivarna.mkm.utils

import com.ivarna.mkm.data.model.FpsSample
import com.ivarna.mkm.data.model.FpsSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton recording FPS samples for session analysis and graphing.
 */
object FpsSessionRecorder {

    private const val MAX_SAMPLES = 300

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _session = MutableStateFlow<FpsSession?>(null)
    val session: StateFlow<FpsSession?> = _session.asStateFlow()

    private val lock = Any()
    private val samples = mutableListOf<FpsSample>()
    private var startedAtMs = 0L
    private var platformName = "UNKNOWN"

    fun start(platform: String = "UNKNOWN") {
        synchronized(lock) {
            samples.clear()
            startedAtMs = System.currentTimeMillis()
            platformName = platform
            _session.value = FpsSession(startedAtMs, emptyList(), platformName)
            _isRecording.value = true
        }
    }

    fun setPlatform(platform: String) {
        synchronized(lock) {
            platformName = platform
            _session.value = _session.value?.copy(platform = platform)
        }
    }

    fun stop(): FpsSession? {
        synchronized(lock) {
            val endedAt = System.currentTimeMillis()
            _isRecording.value = false
            val currentSession = _session.value?.copy(
                endedAtMs = endedAt,
                samples = samples.toList()
            )
            _session.value = currentSession
            return currentSession
        }
    }

    fun add(sample: FpsSample) {
        synchronized(lock) {
            if (!_isRecording.value) return
            samples.add(sample)
            if (samples.size > MAX_SAMPLES) {
                samples.removeAt(0)
            }
            _session.value = FpsSession(
                startedAtMs = startedAtMs,
                samples = samples.toList(),
                platform = platformName,
                endedAtMs = null
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            samples.clear()
            _session.value = null
            _isRecording.value = false
        }
    }
}
