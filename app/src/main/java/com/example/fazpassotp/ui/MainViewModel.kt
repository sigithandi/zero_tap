package com.example.fazpassotp.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fazpassotp.data.Repository
import com.example.fazpassotp.utils.Config
import com.example.fazpassotp.utils.WhatsAppOtp
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.gson.Gson
import com.whatsapp.otp.android.sdk.WhatsAppOtpHandler
import kotlinx.coroutines.launch

sealed class ScreenState {
    object PhoneInput : ScreenState()
    object OtpInput : ScreenState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository()
    private val whatsAppOtpHandler = WhatsAppOtpHandler()

    // UI State
    var phoneNumber by mutableStateOf("")
    var otpInput by mutableStateOf("")

    var currentScreen by mutableStateOf<ScreenState>(ScreenState.PhoneInput)
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("")
    var appHash by mutableStateOf("Calculating...")

    // Internal data
    private var otpId: String? = null

    /**
     * An autofilled code can arrive before the send request has returned an
     * otpId. Hold on to it and submit as soon as verification is possible,
     * instead of dropping it.
     */
    private var pendingOtp: String? = null

    fun clear() {
        phoneNumber = ""
        otpInput = ""
        currentScreen = ScreenState.PhoneInput
        statusMessage = ""
        otpId = null
        pendingOtp = null
        isLoading = false
    }

    fun requestOtp() {
        if (phoneNumber.isBlank()) {
            statusMessage = "Please enter a phone number."
            return
        }
        if (!Config.isConfigured) {
            statusMessage = "Missing credentials. Set fazpass.bearerToken and " +
                "fazpass.gatewayKey in local.properties, then rebuild."
            return
        }

        isLoading = true
        pendingOtp = null
        statusMessage = "Generating OTP and sending..."

        viewModelScope.launch {
            try {
                // Both listeners must be armed before the message goes out.
                performWhatsAppHandshake()
                startSmsRetriever()

                val randomOtp = repository.generateRandomOtp()
                val response = repository.sendOtp(phoneNumber, randomOtp)

                val responseBody = response.body()
                val jsonResponse = Gson().toJson(responseBody ?: response.errorBody()?.string())
                statusMessage = "Request Response:\n$jsonResponse"

                if (response.isSuccessful && responseBody?.status == true) {
                    otpId = responseBody.data?.id
                    if (otpId != null) {
                        currentScreen = ScreenState.OtpInput
                        // Flush a code that arrived while the request was in flight.
                        pendingOtp?.let { buffered ->
                            pendingOtp = null
                            submitOtp(buffered)
                        }
                    } else {
                        statusMessage += "\n\nError: OTP ID not found in response."
                    }
                } else {
                    statusMessage += "\n\nRequest Failed: ${response.message()}"
                }
            } catch (e: Exception) {
                statusMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Tells WhatsApp this app is ready to receive a zero-tap code. Without a
     * fresh handshake WhatsApp falls back to showing a copy-code button and
     * never broadcasts OTP_RETRIEVED.
     */
    private fun performWhatsAppHandshake() {
        val context = getApplication<Application>()
        try {
            if (!whatsAppOtpHandler.isWhatsAppInstalled(context)) {
                Log.w(TAG, "WhatsApp is not installed; zero-tap will not fire")
                statusMessage = "WhatsApp is not installed - zero-tap unavailable."
                return
            }
            if (!whatsAppOtpHandler.isWhatsAppOtpHandshakeSupported(context)) {
                Log.w(TAG, "Installed WhatsApp version does not support the OTP handshake")
                statusMessage = "Installed WhatsApp does not support zero-tap."
                return
            }
            // The SDK returns the request_id it put on the handshake. WhatsApp
            // echoes it back with the code, and it has to outlive this process,
            // so it is persisted rather than kept in a field here.
            val handshakeId = whatsAppOtpHandler.sendOtpIntentToWhatsApp(context)
            WhatsAppOtp.storeHandshakeId(context, handshakeId)
            Log.d(TAG, "Sent OTP_REQUESTED handshake to WhatsApp (request_id=$handshakeId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send the WhatsApp handshake", e)
        }
    }

    /**
     * An SMS Retriever session only lasts five minutes, so it is started per
     * request rather than once at startup - otherwise the fallback path works on
     * the first attempt and silently stops working on any later one.
     *
     * Note: SMS Retriever and the SMS User Consent API are mutually exclusive.
     * Only Retriever is started, since zero-tap autofill requires no interaction.
     */
    private fun startSmsRetriever() {
        val context = getApplication<Application>()
        SmsRetriever.getClient(context).startSmsRetriever()
            .addOnSuccessListener { Log.d(TAG, "SMS Retriever started") }
            .addOnFailureListener { Log.e(TAG, "SMS Retriever failed to start", it) }
    }

    fun validateOtp() {
        val id = otpId
        if (id == null) {
            statusMessage = "Error: No OTP ID to verify against."
            return
        }
        if (otpInput.isBlank()) {
            statusMessage = "Please enter the OTP."
            return
        }

        isLoading = true
        statusMessage = "Verifying..."

        viewModelScope.launch {
            try {
                val response = repository.verifyOtp(id, otpInput)
                val responseBody = response.body()
                statusMessage = "Verify Response:\n" +
                    Gson().toJson(responseBody ?: response.errorBody()?.string())
            } catch (e: Exception) {
                statusMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    /** Called for codes delivered by WhatsApp zero-tap or the SMS Retriever. */
    fun onOtpAutoFilled(otp: String) {
        if (otpId == null) {
            Log.d(TAG, "OTP arrived before the send request completed; buffering")
            pendingOtp = otp
            return
        }
        submitOtp(otp)
    }

    private fun submitOtp(otp: String) {
        otpInput = otp
        currentScreen = ScreenState.OtpInput
        validateOtp()
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
