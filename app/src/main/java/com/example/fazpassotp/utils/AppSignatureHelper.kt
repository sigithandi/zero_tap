package com.example.fazpassotp.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays

/**
 * This is a helper class to generate your message hash to be included in your SMS message.
 *
 * Without the correct hash, your app won't recieve the message callback. This only needs to be
 * generated once per app and stored. Then you can remove this helper class from your code.
 */
class AppSignatureHelper(context: Context) : ContextWrapper(context) {

    /**
     * Note: for an app distributed through Play App Signing, the hash that
     * matters in production is derived from Google's signing key, which is only
     * visible in the Play Console (App integrity > App signing). The value
     * computed here reflects the key this APK was actually signed with.
     */

    val appSignatures: ArrayList<String>
        get() {
            val appCodes = ArrayList<String>()

            try {
                // Get all package signatures for the current package
                val packageName = packageName
                val packageManager = packageManager
                
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val signingInfo = packageInfo.signingInfo
                    when {
                        signingInfo == null -> {
                            Log.e(TAG, "No signing info available for $packageName")
                            emptyArray<Signature>()
                        }
                        // Under Play App Signing, apkContentsSigners reports the
                        // upload key, not the key Play re-signs with. The rotation
                        // history contains every certificate the app has shipped
                        // with, so hash all of them and register each one.
                        signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                        else -> signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners
                    }
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                }

                // For each signature create a compatible hash
                for (signature in signatures) {
                    val hash = hash(packageName, signature.toCharsString())
                    if (hash != null) {
                        appCodes.add(String.format("%s", hash))
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Unable to find package to obtain hash.", e)
            }

            return appCodes
        }

    companion object {
        val TAG = AppSignatureHelper::class.java.simpleName
        private const val HASH_TYPE = "SHA-256"
        private const val NUM_HASHED_BYTES = 9
        private const val NUM_BASE64_CHAR = 11

        private fun hash(packageName: String, signature: String): String? {
            val appInfo = "$packageName $signature"
            try {
                val messageDigest = MessageDigest.getInstance(HASH_TYPE)
                messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))
                var hashSignature = messageDigest.digest()

                // truncated into NUM_HASHED_BYTES
                hashSignature = Arrays.copyOfRange(hashSignature, 0, NUM_HASHED_BYTES)
                // encode into Base64
                var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
                base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)

                Log.d(TAG, String.format("pkg: %s -- hash: %s", packageName, base64Hash))
                return base64Hash
            } catch (e: Exception) {
                Log.e(TAG, "hash:Exception", e)
            }

            return null
        }
    }
}
