package com.example.fazpassotp.api

import com.google.gson.annotations.SerializedName

// POST /v1/otp/send
data class OtpSendRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("gateway_key") val gatewayKey: String,
    @SerializedName("otp") val otp: String
)

data class OtpSendResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: OtpSendData?
)

data class OtpSendData(
    @SerializedName("id") val id: String?,
    @SerializedName("otp") val otp: String?
)

// POST /v1/otp/verify
data class OtpVerifyRequest(
    @SerializedName("otp_id") val otpId: String,
    @SerializedName("otp") val otp: String
)

data class OtpVerifyResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String
)
