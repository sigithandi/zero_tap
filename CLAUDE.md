# CLAUDE.md

Android demo app that requests an OTP through the Fazpass gateway and autofills it
via **WhatsApp Zero Tap**, with Google SMS Retriever as a secondary path.

Package: `com.example.fazpassotp` · Kotlin + Compose · minSdk 24 / targetSdk 34 · AGP 9.0

---

## Build & run

This machine has no `java`, `git`, or `xxd` on `PATH`, and `gradlew` is not executable.
Use:

```bash
JAVA_HOME=/home/widi/.jdks/jbr-21.0.11 sh gradlew assembleDebug --offline
```

Other tools live at:
- JDK / `keytool` / `javap`: `/home/widi/.jdks/jbr-21.0.11/bin`
- `apksigner`: `/home/widi/Android/Sdk/build-tools/36.0.0` (needs `java` on `PATH` to run)

There is no `app/src/main/res` directory — the app is Compose-only with a hardcoded
`android:label` and no icon. That is intentional, not missing files.

## Credentials

`Config.kt` reads from `BuildConfig`, which `app/build.gradle.kts` populates from
`local.properties` (git-ignored):

```properties
fazpass.bearerToken=...
fazpass.gatewayKey=...
```

A blank value makes `Config.isConfigured` false and `requestOtp()` refuses with a
message rather than firing a broken request. Note: the token was previously
hardcoded in `Config.kt` and is still in git history — rotate it if that matters.

---

## Zero Tap: how it works here

1. User taps **Request OTP**.
2. `MainViewModel.performWhatsAppHandshake()` broadcasts `com.whatsapp.otp.OTP_REQUESTED`
   to WhatsApp via `WhatsAppOtpHandler().sendOtpIntentToWhatsApp(context)`, which
   returns the `request_id` (handshake id) it attached. That id is persisted with
   `WhatsAppOtp.storeHandshakeId()`. **This must happen before the message is sent** —
   no fresh handshake means WhatsApp shows a copy-code button and never broadcasts back.
3. The OTP is sent to Fazpass (`POST v1/otp/send`).
4. WhatsApp delivers the code as a `com.whatsapp.otp.OTP_RETRIEVED` broadcast to the
   **manifest-declared** `SmsBroadcastReceiver`.
5. The receiver validates the sender and the echoed `request_id`, then publishes to `OtpBus`.
6. `MainActivity` collects `OtpBus.codes` and calls `viewModel.onOtpAutoFilled(code)`,
   which auto-submits `POST v1/otp/verify`.

### Invariants — do not break these

- **`OtpAutofillActivity` must stay declared in the manifest.** WhatsApp's
  eligibility checklist requires the app to define *both* a one-tap autofill
  activity and a zero-tap broadcast receiver for `OTP_RETRIEVED`. A missing
  activity fails the check, so the zero-tap broadcast is never started at all and
  the message downgrades to one-tap/copy-code. From the app's side that is
  indistinguishable from every other failure: nothing arrives, no error. This was
  the second real bug here — the receiver existed, the activity did not.
- **Never gate delivery on the handshake id.** The `request_id` WhatsApp echoes
  back is logged for correlation and nothing else. Provenance is established by
  `WhatsAppOtp.isIntentFromWhatsApp()` — a PendingIntent the caller cannot forge —
  and that is the security boundary. Treating an id mismatch as fatal broke
  autofill twice here (first accepting only the newest id, then only a recent
  set), each time by silently discarding a code WhatsApp had correctly delivered.
  Ids are still stored, in SharedPreferences so they survive the cold-start case,
  purely so the mismatch can be *logged*.
- **Do not extract the code with `getOtpCodeFromWhatsAppIntent` /
  `processOtpCode`.** In 1.0.0 both run `validateHandshakeId` first and throw on
  mismatch, which is exactly the gate above. `WhatsAppOtp.extractCode()` reads the
  `code` extra directly — verified against the AAR bytecode to be what the SDK
  does once validation passes.
- **`SmsBroadcastReceiver` must never hold a callback field.** The instance that
  receives WhatsApp's broadcast is constructed by the framework from the manifest
  entry, so any listener set from `MainActivity` lives on a *different* object. This
  was the original bug: a `lateinit onOtpReceived` that was never initialized on the
  manifest instance. Hand off through `OtpBus` only.
- **Do not register `com.whatsapp.otp.OTP_RETRIEVED` at runtime.** The manifest entry
  already covers it; registering both delivers every code twice.
- **Sender verification is mandatory.** The intent carries a `PendingIntent` under
  `_ci_` whose creator package must be `com.whatsapp` or `com.whatsapp.w4b`. Use
  `WhatsAppOtp.isIntentFromWhatsApp()`; the action alone is spoofable by any app.
  Do **not** use the SDK method of the same name — in 1.0.0 it stopped checking the
  sender and now only validates the handshake id (see the SDK section below).
- **SMS Retriever and SMS User Consent are mutually exclusive.** Only
  `startSmsRetriever()` is used. Adding `startSmsUserConsent()` back silently cancels
  it (this was a real bug here).
- **Start the SMS Retriever per request, not once at startup.** A session lasts
  five minutes. `MainViewModel.startSmsRetriever()` runs alongside the handshake in
  `requestOtp()`; calling it only from `MainActivity.onCreate` gave the fallback
  path the same first-attempt-only failure as the handshake-id bug above.
- **A code can arrive before `otpId` exists.** `onOtpAutoFilled` buffers into
  `pendingOtp` and `requestOtp()` flushes it. Do not re-add a `currentScreen` gate.

---

## App signing hash — the main footgun

WhatsApp only delivers if the template's registered hash matches the key the APK was
actually signed with.

| | |
|---|---|
| Package | `com.example.fazpassotp` |
| App hash (debug) | **`2D+LYGmTWtB`** |
| Cert SHA-256 | `f10e9d11b5db28336949ef9eb582abdc63de8bb83432dbb95a7f2818be1b4946` |

This machine has **two** debug keystores that produce different hashes:
`~/.config/.android/debug.keystore` → `2D+LYGmTWtB` (the real one), and
`~/.android/debug.keystore` → `i2i2GceG2S/`. Which one got used depended on how the
resolved user home differed between Android Studio and the CLI.

Fixed by committing the correct key to `app/debug.keystore` and pinning it in
`signingConfigs.debug`. `.gitignore` has a `!app/debug.keystore` negation to allow it.
**Do not remove that pin** — the hash silently changes and zero-tap stops working with
no error. (Debug key, public password `android`/`androiddebugkey`; no secret value.)

A **release build with a different key produces a different hash** and needs its own
registration on the template. Under Play App Signing the hash that matters comes from
Google's key (Play Console → App integrity → App signing), not the local upload key —
`AppSignatureHelper` prefers `signingCertificateHistory` for this reason.

Recompute the hash without running the app: see `OTP_GUIDE.txt` Method B (verified to
reproduce `2D+LYGmTWtB`).

---

## Debugging

```bash
adb logcat -s SmsBroadcastReceiver:* MainViewModel:* AppSignatureHelper:* OtpBus:*
```

`SmsBroadcastReceiver.logDebugSignals()` surfaces WhatsApp's `error` / `error_message`
extras via `processOtpDebugSignals` — **this is the fastest way to find out why
zero-tap silently did nothing** (unknown hash, missing handshake, unsupported client).

Checklist when nothing arrives:
- Logged "Hash identifying this app" matches the hash on the template.
- "Sent OTP_REQUESTED handshake to WhatsApp (request_id=…)" appears *before* the
  send request.
- `OtpAutofillActivity` is present in the merged manifest (see the invariant above).
- `SmsBroadcastReceiver` logs `onReceive action=…` for every intent it sees. If
  *nothing* is logged when a code should have arrived, WhatsApp never broadcast —
  that is a template/eligibility problem, not an app one, and no client change
  will fix it.
- Template is an Authentication template, zero-tap enabled, `zero_tap_terms_accepted: true`.

---

## File map

| File | Role |
|---|---|
| `MainActivity.kt` | Logs app hash, registers the runtime receiver (SMS action only), collects `OtpBus` |
| `OtpAutofillActivity.kt` | One-tap autofill target. Required for zero-tap eligibility; trampolines the code into `OtpBus` and returns to `MainActivity` |
| `utils/WhatsAppOtp.kt` | Rolling set of recent handshake ids + `_ci_` sender verification, shared by the receiver and the activity |
| `ui/MainViewModel.kt` | `AndroidViewModel` (uses `applicationContext` — never hold the Activity). Handshake, SMS Retriever start, request/verify, `pendingOtp` buffering |
| `utils/SmsBroadcastReceiver.kt` | Manifest receiver for WhatsApp; also handles SMS Retriever. Stateless by design |
| `utils/OtpBus.kt` | Process-level `SharedFlow` handoff, `replay = 1`. Call `clear()` after consuming |
| `utils/AppSignatureHelper.kt` | Computes the 11-char SMS Retriever hash |
| `utils/Config.kt` | `BuildConfig`-backed credentials |
| `data/Repository.kt`, `api/*` | Retrofit against `https://api.fazpass.com/` |
| `OTP_GUIDE.txt` | Integration notes, hash generation, debugging checklist |

## SDK reference — `com.whatsapp.otp:whatsapp-otp-android-sdk:1.0.0`

Verified against the AAR bytecode (`javap` the classes.jar in the Gradle cache).
Published versions are 0.1.0, 0.2.0, 1.0.0.

- `WhatsAppOtpHandler.sendOtpIntentToWhatsApp(Context)` loops over CONSUMER **and**
  BUSINESS — no need to send to both packages manually. In 1.0.0 it returns the
  `UUID` it put on the intent as `request_id`; there is also a
  `sendOtpIntentToWhatsApp(UUID, Context)` overload if you want to supply your own.
- `processOtpCode(intent, expectedHandshakeId, onCode, onError)` validates the
  echoed `request_id` first and reports `HANDSHAKE_ID_MISSING` /
  `HANDSHAKE_ID_INVALID_FORMAT` / `HANDSHAKE_ID_MISMATCH`. It wraps its
  `Consumer.accept` in `catch (Exception)`, so **any exception thrown inside your
  callback is swallowed** and reported as `GENERIC_EXCEPTION`. Do not rely on a
  crash to reveal callback bugs.
- **`isIntentFromWhatsApp` changed meaning in 1.0.0.** The 0.1.0 signature
  `(Intent)` checked the `_ci_` PendingIntent's creator package. The 1.0.0
  signature `(Intent, String)` does *not* look at the sender at all — it just runs
  `validateHandshakeId` and returns false on failure. The creator-package check now
  lives in `WhatsAppOtp.isIntentFromWhatsApp()`; keep both checks.
- `DebugSignal` fields are `otpErrorIdentifier` / `otpErrorMessage` (public fields, no getters).
- 1.0.0 pulls in `androidx.appcompat:appcompat:1.6.1` as a runtime dependency.
- Upgrading needs a **non-offline** build once so Gradle can fetch the artifact:
  `JAVA_HOME=/home/widi/.jdks/jbr-21.0.11 sh gradlew assembleDebug`.

---

## Open / unverified

Client side is believed correct but **has not been tested against a live zero-tap
template**. The missing one-tap activity (see invariants) was found by re-reading
Meta's eligibility checklist and is now fixed, but that fix is likewise untested
against a live template. Two things remain outside the app:

1. Register hash `2D+LYGmTWtB` + package `com.example.fazpassotp` on the WhatsApp
   template. It may still hold the stale `i2i2GceG2S/`.
2. Confirm with Fazpass that the template behind the gateway key is an Authentication
   template with zero-tap enabled. If it sends a plain text template, no client change
   can make autofill work.

Known non-blocking: `compileSdk`/`targetSdk` are 34 (Play requires 35+ for new
releases); `release` has no signing config.
