package com.example.fazpassotp.data

import com.example.fazpassotp.api.ApiClient
import com.example.fazpassotp.api.OtpSendRequest
import com.example.fazpassotp.api.OtpSendResponse
import com.example.fazpassotp.api.OtpVerifyRequest
import com.example.fazpassotp.api.OtpVerifyResponse
import com.example.fazpassotp.utils.Config
import retrofit2.Response
import kotlin.random.Random

class Repository {
    private val service = ApiClient.service

    fun generateRandomOtp(): String {
        // Upper bound is exclusive, so 10000 is needed to make 9999 reachable.
        return Random.nextInt(1000, 10000).toString()
    }

    suspend fun sendOtp(phone: String, otp: String): Response<OtpSendResponse> {
        val request = OtpSendRequest(
            phone = phone,
            gatewayKey = Config.GATEWAY_KEY,
            otp = otp
        )
        val authHeader = "Bearer ${Config.BEARER_TOKEN}"
        return service.sendOtp(authHeader, request)
    }

    suspend fun verifyOtp(otpId: String, otp: String): Response<OtpVerifyResponse> {
        val request = OtpVerifyRequest(
            otpId = otpId,
            otp = otp
        )
        val authHeader = "Bearer ${Config.BEARER_TOKEN}"
        return service.verifyOtp(authHeader, request)
    }
}
