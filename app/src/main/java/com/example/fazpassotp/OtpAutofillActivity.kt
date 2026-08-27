package com.example.fazpassotp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.fazpassotp.utils.OtpBus
import com.example.fazpassotp.utils.WhatsAppOtp
import com.whatsapp.otp.android.sdk.WhatsAppOtpIncomingIntentHandler

/**
 * Receives the code when the user taps WhatsApp's one-tap autofill button.
 *
 * This activity is not optional for zero-tap. WhatsApp only starts the zero-tap
 * broadcast once every eligibility check passes, and one of those checks is that
 * the app declares a one-tap autofill activity for OTP_RETRIEVED. Without it
 * WhatsApp silently downgrades the message to a copy-code button and no
 * broadcast is ever sent — which is exactly what a missing activity looks like
 * from this side: nothing at all.
 *
 * It is a trampoline: extract the code, hand it to [OtpBus], bring MainActivity
 * forward, finish. It never draws anything, hence the translucent theme in the
 * manifest.
 */
class OtpAutofillActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != WhatsAppOtp.ACTION_OTP_RETRIEVED) {
            Log.w(TAG, "Started with an unexpected intent: ${intent?.action}")
            return
        }
        if (!WhatsAppOtp.isIntentFromWhatsApp(intent)) {
            Log.w(TAG, "Dropping one-tap intent that did not originate from WhatsApp")
            return
        }

        // Advisory only, exactly as in SmsBroadcastReceiver.
        val incoming = WhatsAppOtpIncomingIntentHandler().getHandshakeIdFromWhatsAppIntent(intent)
        if (!WhatsAppOtp.isKnownHandshakeId(this, incoming)) {
            Log.w(TAG, "request_id=$incoming is not one this install issued; accepting anyway")
        }

        val code = WhatsAppOtp.extractCode(intent)
        if (code.isNullOrBlank()) {
            Log.e(TAG, "One-tap intent carried no code extra")
            return
        }
        Log.d(TAG, "Received WhatsApp one-tap OTP")
        OtpBus.publish(code)
    }

    private companion object {
        const val TAG = "OtpAutofillActivity"
    }
}
