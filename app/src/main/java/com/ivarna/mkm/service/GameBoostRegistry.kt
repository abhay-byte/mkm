package com.ivarna.mkm.service

import com.ivarna.mkm.data.model.GameBoostState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide ownership signal used to interlock manual tuning controls. */
object GameBoostRegistry {
    private val _state = MutableStateFlow<GameBoostState>(GameBoostState.Off)
    val state: StateFlow<GameBoostState> = _state.asStateFlow()
    @Volatile private var manager: GameBoostManager? = null

    fun ownsTuning(): Boolean = _state.value !is GameBoostState.Off

    internal fun publish(state: GameBoostState) {
        _state.value = state
    }

    fun manager(context: android.content.Context): GameBoostManager {
        return manager ?: synchronized(this) {
            manager ?: GameBoostManager(context.applicationContext).also { manager = it }
        }
    }
}
