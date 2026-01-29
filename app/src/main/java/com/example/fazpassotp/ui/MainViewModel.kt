package com.example.fazpassotp.ui

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fazpassotp.data.Repository
import com.google.gson.Gson
import kotlinx.coroutines.launch

sealed class ScreenState {
    object PhoneInput : ScreenState()
    object OtpInput : ScreenState()
}

class MainViewModel : ViewModel() {
    private val repository = Repository()
    private lateinit var context: Context

    // UI State
    var phoneNumber by mutableStateOf("")
    var otpInput by mutableStateOf("") // User input for verification
    
    var currentScreen by mutableStateOf<ScreenState>(ScreenState.PhoneInput)
    var isLoading by mutableStateOf(false)
    var statusMessage by mutableStateOf("") // To show JSON response or errors
    var appHash by mutableStateOf("Calculating...")
    
    // Internal data
    private var otpId: String? = null // To store the ID from send response
    
    fun clear() {
        phoneNumber = ""
        otpInput = ""
        currentScreen = ScreenState.PhoneInput
        statusMessage = ""
        otpId = null
        isLoading = false
    }

    fun requestOtp() {
        if (phoneNumber.isBlank()) {
            statusMessage = "Please enter a phone number."
            return
        }
        
        isLoading = true
        statusMessage = "Generating OTP and sending..."
        
        viewModelScope.launch {
            try {
                sendOtpIntentToWhatsApp()
                val randomOtp = repository.generateRandomOtp()
                val response = repository.sendOtp(phoneNumber, randomOtp)
                
                val responseBody = response.body()
                val jsonResponse = Gson().toJson(responseBody ?: response.errorBody()?.string())
                
                statusMessage = "Request Response:\n$jsonResponse"
                
                if (response.isSuccessful && responseBody?.status == true) {
                    otpId = responseBody.data?.id
                    if (otpId != null) {
                       currentScreen = ScreenState.OtpInput
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

    fun validateOtp() {
        if (otpId == null) {
            statusMessage = "Error: No OTP ID to verify against."
            return
        }
        
        if (otpInput.isBlank()) {
            statusMessage = "Please enter the OTP."
            return
        }

        isLoading = true
        statusMessage = "Verifying..." // Keep previous response visible? No, request says display response.
        
        viewModelScope.launch {
            try {
                val response = repository.verifyOtp(otpId!!, otpInput)
                val responseBody = response.body()
                val jsonResponse = Gson().toJson(responseBody ?: response.errorBody()?.string())
                
                statusMessage = "Verify Response:\n$jsonResponse"
                
            } catch (e: Exception) {
                statusMessage = "Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
    fun onOtpAutoFilled(otp: String) {
        if (currentScreen is ScreenState.OtpInput) {
            otpInput = otp
            validateOtp() // Auto-submit for true "Zero Tap" experience
        }
    }
    fun setcontext(context: Context){
        this.context = context

    }

    fun sendOtpIntentToWhatsApp() {
        // Send OTP_REQUESTED intent to both WA and WA Business App
        sendOtpIntentToWhatsApp("com.whatsapp")
        sendOtpIntentToWhatsApp("com.whatsapp.w4b")
    }
    private fun sendOtpIntentToWhatsApp(packageName: String?) {
        /**
         * Starting with Build.VERSION_CODES.S, it will be required to explicitly
         * specify the mutability of  PendingIntents on creation with either
         * (@link #FLAG_IMMUTABLE} or FLAG_MUTABLE
         */

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(
            context,
            0,
            Intent(),
            flags
        )

        // Send OTP_REQUESTED intent to WhatsApp
        val intentToWhatsApp = Intent()
        intentToWhatsApp.setPackage(packageName)
        intentToWhatsApp.setAction("com.whatsapp.otp.OTP_REQUESTED")
        // WA will use this to verify the identity of the caller app.
        var extras = intentToWhatsApp.getExtras()
        if (extras == null) {
            extras = Bundle()
        }
        extras.putParcelable("_ci_", pi)
        intentToWhatsApp.putExtras(extras)
        context.sendBroadcast(intentToWhatsApp)
    }

}
