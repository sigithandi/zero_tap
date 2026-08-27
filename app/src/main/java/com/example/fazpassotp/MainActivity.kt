package com.example.fazpassotp

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fazpassotp.ui.AppContent
import com.example.fazpassotp.ui.MainViewModel
import com.example.fazpassotp.utils.AppSignatureHelper
import com.example.fazpassotp.utils.OtpBus
import com.example.fazpassotp.utils.SmsBroadcastReceiver
import com.google.android.gms.auth.api.phone.SmsRetriever
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val smsBroadcastReceiver = SmsBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logAppHash()
        registerSmsRetrieverReceiver()
        collectOtpCodes()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel = viewModel)
                }
            }
        }
    }

    /**
     * The hash printed here is the one that must be registered on the WhatsApp
     * zero-tap template. It is derived from the signing key, so it differs
     * between debug and release builds.
     */
    private fun logAppHash() {
        val signatures = AppSignatureHelper(this).appSignatures
        if (signatures.isEmpty()) {
            Log.e(TAG_HASH, "Could not determine the app signing hash")
            return
        }
        signatures.forEach { Log.i(TAG_HASH, "Hash identifying this app: $it") }
        viewModel.appHash = signatures.first()
    }

    /**
     * Only the SMS Retriever action is registered here. WhatsApp's
     * OTP_RETRIEVED broadcast is handled by the manifest-declared receiver, which
     * works even when no Activity is alive; registering it here as well would
     * deliver every code twice.
     */
    private fun registerSmsRetrieverReceiver() {
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(smsBroadcastReceiver, filter)
        }
    }

    private fun collectOtpCodes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                OtpBus.codes.collect { code ->
                    OtpBus.clear()
                    viewModel.onOtpAutoFilled(code)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(smsBroadcastReceiver) }
            .onFailure { Log.w(TAG_SMS, "Receiver was not registered", it) }
    }

    private companion object {
        const val TAG_HASH = "AppSignatureHelper"
        const val TAG_SMS = "SMS_RETRIEVER"
    }
}
