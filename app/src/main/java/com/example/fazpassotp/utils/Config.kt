package com.example.fazpassotp.utils

import com.example.fazpassotp.BuildConfig

/**
 * Values are injected from local.properties at build time (see app/build.gradle.kts):
 *
 *   fazpass.bearerToken=...
 *   fazpass.gatewayKey=...
 *
 * local.properties is git-ignored, so credentials stay out of source control.
 */
object Config {
    val BEARER_TOKEN: String = BuildConfig.FAZPASS_BEARER_TOKEN
    val GATEWAY_KEY: String = BuildConfig.FAZPASS_GATEWAY_KEY

    val isConfigured: Boolean
        get() = BEARER_TOKEN.isNotBlank() && GATEWAY_KEY.isNotBlank()
}
