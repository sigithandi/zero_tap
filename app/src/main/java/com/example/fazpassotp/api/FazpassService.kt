package com.example.fazpassotp.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface FazpassService {
    
    @POST("v1/otp/send")
    suspend fun sendOtp(
        @Header("Authorization") authorization: String,
        @Body request: OtpSendRequest
    ): Response<OtpSendResponse>

    @POST("v1/otp/verify")
    suspend fun verifyOtp(
        @Header("Authorization") authorization: String,
        @Body request: OtpVerifyRequest
    ): Response<OtpVerifyResponse>
}
