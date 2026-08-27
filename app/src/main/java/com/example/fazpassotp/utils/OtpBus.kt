package com.example.fazpassotp.utils

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-level handoff between [SmsBroadcastReceiver] and the UI.
 *
 * The receiver that actually gets WhatsApp's OTP is the one declared in the
 * manifest, which Android instantiates itself via the no-arg constructor. That
 * instance can never hold a reference to a ViewModel or an Activity, so it
 * publishes here instead and the UI collects.
 *
 * replay = 1 covers the case where the broadcast arrives before the Activity is
 * ready to collect (e.g. the process was started by the broadcast itself).
 * Call [clear] once a code has been consumed so it is not redelivered.
 */
object OtpBus {
    private const val TAG = "OtpBus"

    private val _codes = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val codes: SharedFlow<String> = _codes

    fun publish(code: String) {
        Log.d(TAG, "Publishing OTP of length ${code.length}")
        _codes.tryEmit(code)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clear() {
        _codes.resetReplayCache()
    }
}
