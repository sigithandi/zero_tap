package com.example.fazpassotp.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.os.BundleCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.whatsapp.otp.android.sdk.WhatsAppOtpIncomingIntentHandler
import com.whatsapp.otp.android.sdk.data.DebugSignal
import com.whatsapp.otp.android.sdk.enums.WhatsAppOtpError
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * Receives OTP codes from two independent sources:
 *
 *  - WhatsApp zero-tap ([WhatsAppOtp.ACTION_OTP_RETRIEVED]), declared in the manifest so it
 *    works even when this app has no live Activity.
 *  - Google's SMS Retriever ([SmsRetriever.SMS_RETRIEVED_ACTION]), registered at
 *    runtime by MainActivity.
 *
 * Codes are handed off through [OtpBus]. This class deliberately holds no
 * callback field: the manifest-declared instance is constructed by the framework,
 * so any instance state set from MainActivity would not exist on it.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        when (intent.action) {
            WhatsAppOtp.ACTION_OTP_RETRIEVED -> handleWhatsAppOtp(context, intent)
            SmsRetriever.SMS_RETRIEVED_ACTION -> handleSmsRetrieverOtp(intent)
            else -> Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
        }
    }

    private fun handleWhatsAppOtp(context: Context, intent: Intent) {
        val handler = WhatsAppOtpIncomingIntentHandler()

        // The intent carries a PendingIntent under "_ci_" whose creator package
        // proves it came from WhatsApp. Anything else is spoofed.
        if (!WhatsAppOtp.isIntentFromWhatsApp(intent)) {
            Log.w(TAG, "Dropping OTP_RETRIEVED intent that did not originate from WhatsApp")
            return
        }

        logDebugSignals(handler, intent)

        // The request_id is logged, never enforced. The intent is already proven
        // to come from WhatsApp; which handshake WhatsApp chose to associate the
        // message with is its business, and gating on it has twice thrown away
        // codes that were perfectly good.
        logHandshakeMatch(context, handler, intent)

        val code = WhatsAppOtp.extractCode(intent)
        if (code.isNullOrBlank()) {
            Log.e(TAG, "WhatsApp intent from ${intent.action} carried no code extra")
            return
        }
        Log.d(TAG, "Received WhatsApp zero-tap OTP")
        OtpBus.publish(code)
    }

    private fun logHandshakeMatch(
        context: Context,
        handler: WhatsAppOtpIncomingIntentHandler,
        intent: Intent
    ) {
        val incoming = handler.getHandshakeIdFromWhatsAppIntent(intent)
        if (WhatsAppOtp.isKnownHandshakeId(context, incoming)) {
            Log.d(TAG, "Matched handshake request_id=$incoming")
        } else {
            Log.w(
                TAG,
                "request_id=$incoming is not one this install issued " +
                    "(known: ${WhatsAppOtp.knownHandshakeIds(context)}); " +
                    "accepting the code anyway, the intent is verifiably from WhatsApp"
            )
        }
    }

    /**
     * WhatsApp reports *why* zero-tap did not work through the "error" and
     * "error_message" extras (unknown app hash, missing handshake, unsupported
     * client, ...). Surfacing these is the fastest way to diagnose a template
     * that silently fails to autofill.
     */
    private fun logDebugSignals(handler: WhatsAppOtpIncomingIntentHandler, intent: Intent) {
        handler.processOtpDebugSignals(
            intent,
            Consumer { signal: DebugSignal? ->
                if (signal?.otpErrorIdentifier != null) {
                    Log.e(
                        TAG,
                        "WhatsApp zero-tap debug signal: ${signal.otpErrorIdentifier} - ${signal.otpErrorMessage}"
                    )
                }
            },
            BiConsumer { error: WhatsAppOtpError?, exception: Exception? ->
                Log.d(TAG, "No debug signal on this intent: $error", exception)
            }
        )
    }

    private fun handleSmsRetrieverOtp(intent: Intent) {
        val extras = intent.extras ?: return
        val status = BundleCompat.getParcelable(extras, SmsRetriever.EXTRA_STATUS, Status::class.java)

        when (status?.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                if (message.isNullOrBlank()) {
                    Log.e(TAG, "SMS Retriever returned an empty message")
                    return
                }
                val matcher = OTP_PATTERN.matcher(message)
                if (matcher.find()) {
                    matcher.group(1)?.let {
                        Log.d(TAG, "Received SMS Retriever OTP")
                        OtpBus.publish(it)
                    }
                } else {
                    Log.e(TAG, "No ${OTP_LENGTH}-digit OTP found in SMS message")
                }
            }
            CommonStatusCodes.TIMEOUT -> Log.d(TAG, "SMS Retriever timed out")
            else -> Log.e(TAG, "SMS Retriever status: ${status?.statusCode}")
        }
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        private const val OTP_LENGTH = 4
        private val OTP_PATTERN: Pattern = Pattern.compile("(\\d{$OTP_LENGTH})")
    }
}
