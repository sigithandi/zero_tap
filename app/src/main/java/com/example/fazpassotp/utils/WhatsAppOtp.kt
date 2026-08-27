package com.example.fazpassotp.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.os.BundleCompat
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Handshake state and sender verification shared by the two components that can
 * receive a WhatsApp code: [SmsBroadcastReceiver] (zero-tap broadcast) and
 * [com.example.fazpassotp.OtpAutofillActivity] (one-tap button).
 *
 * The handshake ids have to survive process death: WhatsApp's broadcast can
 * start this app's process from cold, so a field on the ViewModel would already
 * be gone by the time the code arrives. They live in SharedPreferences instead.
 */
object WhatsAppOtp {

    private const val TAG = "WhatsAppOtp"
    private const val PREFS = "whatsapp_otp"
    private const val KEY_HANDSHAKE_IDS = "handshake_ids"

    /** Extra WhatsApp puts its identifying PendingIntent under. */
    private const val EXTRA_CALLER_INFO = "_ci_"

    private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * How long an issued id stays acceptable. WhatsApp stops honouring a
     * handshake after 10 minutes (or the template's `code_expiration_minutes`),
     * so this is deliberately longer: the window exists to bound the stored set,
     * not to second-guess WhatsApp about which handshake is still live.
     */
    private val ID_RETENTION_MS = TimeUnit.MINUTES.toMillis(15)

    /** Belt-and-braces bound in case many requests are made inside the window. */
    private const val MAX_IDS = 10

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Remembers an id sent with an OTP_REQUESTED handshake.
     *
     * A *set* of recent ids is kept rather than just the latest one. WhatsApp
     * decides for itself which outstanding handshake a message belongs to, and
     * every handshake stays valid on its side for ten minutes. Asking for a
     * second OTP inside that window leaves two live handshakes, and WhatsApp may
     * echo back the id of the earlier one. Accepting only the newest id would
     * then reject a perfectly good code — a "works the first time, never again"
     * failure — so any id this install actually issued and has not aged out is
     * accepted. The anti-replay property is unchanged: these are random UUIDs
     * that no other app has seen.
     */
    fun storeHandshakeId(context: Context, handshakeId: UUID) {
        val kept = readEntries(context) + Entry(handshakeId.toString(), System.currentTimeMillis())
        writeEntries(context, kept)
    }

    /**
     * True when [handshakeId] is one this install issued recently.
     *
     * This is a *correlation* signal, not the security boundary — it answers
     * "which of my requests does this code belong to". Provenance is established
     * by [isIntentFromWhatsApp], which checks a PendingIntent the caller cannot
     * forge. Callers should log a mismatch and carry on rather than discarding
     * the code: WhatsApp picks which outstanding handshake a message belongs to,
     * and treating a mismatch as fatal has twice silently broken autofill here.
     */
    fun isKnownHandshakeId(context: Context, handshakeId: String?): Boolean =
        handshakeId != null && readEntries(context).any { it.id == handshakeId }

    /** The ids currently considered valid, newest last. Diagnostics only. */
    fun knownHandshakeIds(context: Context): List<String> = readEntries(context).map { it.id }

    /** Forgets every stored id, e.g. when the user resets the flow. */
    fun clearHandshakeIds(context: Context) {
        prefs(context).edit().remove(KEY_HANDSHAKE_IDS).apply()
    }

    /**
     * True when the intent really came from WhatsApp.
     *
     * SDK 1.0.0 repurposed `isIntentFromWhatsApp` to validate the handshake id
     * and no longer checks the sender at all, so the original check is done
     * here: WhatsApp attaches a PendingIntent under `_ci_` whose creator package
     * cannot be forged. The action on its own is broadcastable by any app.
     */
    fun isIntentFromWhatsApp(intent: Intent): Boolean {
        val extras = intent.extras ?: return false
        val callerInfo = BundleCompat.getParcelable(extras, EXTRA_CALLER_INFO, PendingIntent::class.java)
        if (callerInfo == null) {
            Log.w(TAG, "Intent has no $EXTRA_CALLER_INFO extra")
            return false
        }
        val creator = callerInfo.creatorPackage
        if (creator !in WHATSAPP_PACKAGES) {
            Log.w(TAG, "Intent was created by $creator, not WhatsApp")
            return false
        }
        return true
    }

    /**
     * The code WhatsApp put on the intent.
     *
     * Read directly rather than through the SDK. `getOtpCodeFromWhatsAppIntent`
     * in 1.0.0 is exactly `validateHandshakeId(intent, expected)` followed by
     * `intent.getStringExtra("code")` (confirmed against the AAR bytecode), and
     * that validation *throws* on any handshake-id mismatch, which would discard
     * the code. Sender verification is the security boundary here; see
     * [isKnownHandshakeId] for why the id is advisory.
     */
    fun extractCode(intent: Intent): String? = intent.getStringExtra(EXTRA_CODE)

    private const val EXTRA_CODE = "code"

    const val ACTION_OTP_RETRIEVED = "com.whatsapp.otp.OTP_RETRIEVED"

    private data class Entry(val id: String, val issuedAt: Long)

    /** Stored as "uuid|epochMillis" strings; order is irrelevant, membership is not. */
    private fun readEntries(context: Context): List<Entry> {
        val raw = prefs(context).getStringSet(KEY_HANDSHAKE_IDS, emptySet()).orEmpty()
        val cutoff = System.currentTimeMillis() - ID_RETENTION_MS
        return raw.mapNotNull { entry ->
            val id = entry.substringBefore('|', "")
            val issuedAt = entry.substringAfter('|', "").toLongOrNull()
            if (id.isEmpty() || issuedAt == null) null else Entry(id, issuedAt)
        }.filter { it.issuedAt >= cutoff }.sortedBy { it.issuedAt }
    }

    private fun writeEntries(context: Context, entries: List<Entry>) {
        val serialised = entries.takeLast(MAX_IDS).map { "${it.id}|${it.issuedAt}" }.toSet()
        prefs(context).edit().putStringSet(KEY_HANDSHAKE_IDS, serialised).apply()
    }
}
