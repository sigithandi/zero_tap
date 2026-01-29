package com.example.fazpassotp.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.whatsapp.otp.android.sdk.WhatsAppOtpIncomingIntentHandler
import com.whatsapp.otp.android.sdk.enums.WhatsAppOtpError
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.regex.Pattern


class SmsBroadcastReceiver() : BroadcastReceiver() {
    private lateinit var onOtpReceived: (String) -> Unit

    fun setOnOtpReceivedListener(listener: (String) -> Unit) {
        onOtpReceived = listener
    }


    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("SmsBroadcastReceiver", "onReceive triggered. Action: ${intent.action}")
        android.widget.Toast.makeText(context, "SMS Broadcast Received!", android.widget.Toast.LENGTH_LONG).show() // Debug Toast
        val whatsAppOtpIncomingIntentHandler = WhatsAppOtpIncomingIntentHandler()
        whatsAppOtpIncomingIntentHandler.processOtpCode(
            intent,  // call your function to validate
            Consumer { code: String? ->  if (code != null) {onOtpReceived(code)} },  // call your function to handle errors
            BiConsumer { error: WhatsAppOtpError?, exception: Exception? ->
                Log.e(
                    "SmsBroadcastReceiver",
                    exception.toString()
                )
            })
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
            val extras = intent.extras
            val status = extras?.get(SmsRetriever.EXTRA_STATUS) as? com.google.android.gms.common.api.Status

            Log.d("SmsBroadcastReceiver", "SMS Retriever Status Code: ${status?.statusCode}")

            when (status?.statusCode) {
                CommonStatusCodes.SUCCESS -> {
                    // Get SMS message contents
                    val message = extras.get(SmsRetriever.EXTRA_SMS_MESSAGE) as? String
                    Log.d("SmsBroadcastReceiver", "SMS Received: $message")
                    if (!message.isNullOrBlank()) {
                        // Extract the 4-digit code using Regex
                        val pattern = Pattern.compile("(\\d{4})")
                        val matcher = pattern.matcher(message)
                        if (matcher.find()) {
                            val otp = matcher.group(1)
                            Log.d("SmsBroadcastReceiver", "OTP Found: $otp")
                            if (otp != null) {
                                onOtpReceived(otp)
                            }
                        } else {
                            Log.e("SmsBroadcastReceiver", "No 4-digit OTP found in message")
                        }
                    } else {
                        Log.e("SmsBroadcastReceiver", "Message is null or blank")
                    }
                }
                CommonStatusCodes.TIMEOUT -> {
                    Log.d("SmsBroadcastReceiver", "SMS Retriever timed out")
                }
                else -> {
                    Log.e("SmsBroadcastReceiver", "Unknown Status Code: ${status?.statusCode}")
                }
            }
        } else {
            val pendingIntent = intent.getParcelableExtra<PendingIntent>("_ci_" )
            Log.e("SmsBroadcastReceiver", "pending intent 1: $pendingIntent")
            if(pendingIntent == null) {
                return
            }
            // verify source of the pendingIntent
            val pendingIntentCreatorPackage = pendingIntent.getCreatorPackage()

            val creatorPackage = pendingIntent.getCreatorPackage()
            Log.e("SmsBroadcastReceiver", "creator pakage 1: $creatorPackage")
            if ("com.whatsapp" == creatorPackage ||
                "com.whatsapp.w4b" == creatorPackage
            ) {

                // use OTP code

                val otpCode = intent.getStringExtra("code")
                Log.e("SmsBroadcastReceiver", "otp code 1: $otpCode")
                if (otpCode != null)
                {
                    onOtpReceived(otpCode)
                }
                // ...
            }
        }
    }
}
