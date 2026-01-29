package com.example.fazpassotp

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.fazpassotp.ui.AppContent
import com.example.fazpassotp.ui.MainViewModel
import com.example.fazpassotp.utils.SmsBroadcastReceiver
import com.google.android.gms.auth.api.phone.SmsRetriever


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var smsBroadcastReceiver: SmsBroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setcontext(this)
        // Log App Hash for SMS Retriever
        try {
            val appSignatureHelper = com.example.fazpassotp.utils.AppSignatureHelper(this)
            for (signature in appSignatureHelper.appSignatures) {
                android.util.Log.e("AppSignatureHelper", "Hash identifying this app: $signature")
                viewModel.appHash = signature
            }
        } catch (e: Throwable) {
            android.util.Log.e("AppSignatureHelper", "Failed to generate app hash", e)
        }

        // Start SMS Retriever
        try {
            val client = SmsRetriever.getClient(this)
            val task = client.startSmsRetriever()
            task.addOnSuccessListener {
                android.util.Log.d("SMS_RETRIEVER", "Task Started Successfully")
            }
            task.addOnFailureListener {
                android.util.Log.e("SMS_RETRIEVER", "Task Failed to Start", it)
            }
        } catch (e: Throwable) {
            android.util.Log.e("SMS_RETRIEVER", "Failed to initialize SmsRetriever: ${e.message}", e)
        }

        // Register Broadcast Receiver for Zero Tap
        smsBroadcastReceiver = SmsBroadcastReceiver ()
        smsBroadcastReceiver.setOnOtpReceivedListener{ otp ->
            viewModel.onOtpAutoFilled(otp)
        }
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        intentFilter.addAction("com.whatsapp.otp.OTP_RETRIEVED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsBroadcastReceiver, intentFilter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsBroadcastReceiver, intentFilter)
        }

        // --- Start User Consent API (Fallback) ---
        val consentTask = SmsRetriever.getClient(this).startSmsUserConsent(null)
        consentTask.addOnSuccessListener {
            android.util.Log.d("SMS_CONSENT", "Consent Task Started")
        }
        consentTask.addOnFailureListener {
            android.util.Log.e("SMS_CONSENT", "Consent Task Failed", it)
        }
        
        // Register receiver for User Consent
        val consentIntentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        registerReceiver(smsConsentReceiver, consentIntentFilter, if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) android.content.Context.RECEIVER_EXPORTED else 0)
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
    
    // User Consent Receiver
    private val smsConsentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? com.google.android.gms.common.api.Status
                
                when (status?.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.SUCCESS -> {
                        // Get consent intent
                        val consentIntent = extras.getParcelable<android.content.Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                        try {
                            // Start activity to show consent dialog
                            consentLauncher.launch(consentIntent)
                        } catch (e: Exception) {
                            android.util.Log.e("SMS_CONSENT", "Failed to launch consent intent", e)
                        }
                    }
                    com.google.android.gms.common.api.CommonStatusCodes.TIMEOUT -> {
                         android.util.Log.d("SMS_CONSENT", "Consent Timeout")
                    }
                }
            }
        }
    }
    
    // Handle Result from Consent Dialog
    private val consentLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            if (!message.isNullOrBlank()) {
                 // Extract OTP
                 val pattern = java.util.regex.Pattern.compile("(\\d{4})")
                 val matcher = pattern.matcher(message)
                 if (matcher.find()) {
                     val otp = matcher.group(1)
                     if (otp != null) {
                        viewModel.onOtpAutoFilled(otp)
                        android.widget.Toast.makeText(this, "OTP User Consent Success", android.widget.Toast.LENGTH_SHORT).show()
                     }
                 }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::smsBroadcastReceiver.isInitialized) {
            unregisterReceiver(smsBroadcastReceiver)
        }
    }
    }
