# Android Security (Kotlin)

This guide covers common mobile security practices for Android apps: authentication, secure storage & encryption, ProGuard/R8, Network Security Config, certificate pinning, token management, biometric integration, and testing/hardening tips.

---

## Table of contents

- Principles & threat model
- Authentication patterns (tokens, OAuth2, PKCE)
- Secure storage: Keystore, EncryptedSharedPreferences, EncryptedFile
- Cryptography best practices (AES-GCM, IVs, SecureRandom)
- Network security config & certificate pinning
- Token refresh & interceptors (OkHttp)
- Code obfuscation: ProGuard / R8
- App hardening & runtime protections
- Testing and validation
- Checklist & recommendations

---

## Principles & threat model

Design security from a clear threat model:
- Protect user data at rest and in transit
- Assume the device can be compromised — don’t store long-lived secrets in the app
- Server-side validation is the authoritative policy holder

Security is layered: cryptography + secure storage + transport protection + runtime hardening + monitoring.

---

## Authentication patterns

Token-based authentication (Bearer tokens / JWT):
- Client obtains access token (short-lived) and refresh token (long-lived) from auth server.
- Store access tokens securely and refresh them when expired.

OAuth2 + PKCE (recommended for native apps):
- Use Authorization Code Flow with PKCE (Proof Key for Code Exchange); avoids embedding client secrets.
- Use libraries such as AppAuth for correct and secure flows.

Example: sending Authorization header

```kotlin
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${tokenProvider.getAccessToken()}")
            .build()
        return chain.proceed(req)
    }
}
```

---

## Secure storage: Android Keystore, EncryptedSharedPreferences, EncryptedFile

Android provides built-in secure storage mechanisms:

- EncryptedSharedPreferences: easy key-value encryption (AndroidX Security)
- EncryptedFile: encrypted file API
- Android Keystore: store cryptographic keys that are non-exportable and hardware-backed when available

EncryptedSharedPreferences example:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secret_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

encryptedPrefs.edit { putString("refresh_token", token) }
```

Keystore-backed AES key generation example:

```kotlin
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
)
val spec = KeyGenParameterSpec.Builder(
    "my_key_alias",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
keyGenerator.init(spec)
val key = keyGenerator.generateKey()
```

Using Cipher with AES/GCM (example encrypt/decrypt):

```kotlin
fun encrypt(plain: ByteArray, key: SecretKey): Pair<ByteArray, ByteArray> {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv // unique per encryption
    val cipherText = cipher.doFinal(plain)
    return Pair(iv, cipherText)
}

fun decrypt(iv: ByteArray, cipherText: ByteArray, key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val spec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.DECRYPT_MODE, key, spec)
    return cipher.doFinal(cipherText)
}
```

Security notes:
- Use unique random IVs for AES-GCM. Do not reuse IVs with the same key.
- Use SecureRandom for IV generation if you must create IVs manually.
- Prefer the Android Keystore to generate and hold keys when available.

---

## Cryptography best practices

- Use authenticated encryption (AEAD) such as AES-GCM or AES-CCM. Avoid AES-CBC without HMAC.
- Use strong key sizes (AES-256 where supported).
- Use SecureRandom for nonces/IVs and salts.
- For password-based derivation, use PBKDF2, scrypt or Argon2 (use established libraries); prefer server-side authentication when possible.
- Avoid rolling your own crypto; use AndroidX Security, Tink, or vetted libraries.

---

## Network Security Config & certificate pinning

Use network security config (res/xml/network_security_config.xml) to control trusted CAs, debug overrides, and cleartext policies.

Example network_security_config.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Pin certificate for API domain -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.example.com</domain>
        <pin-set expiration="2026-01-01">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
        </pin-set>
    </domain-config>

    <!-- Allow cleartext for debug only via manifest attribute and build variant checks -->
</network-security-config>
```

Register in AndroidManifest (application):

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
</application>
```

Certificate pinning with OkHttp (runtime):

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder().certificatePinner(pinner).build()
```

Caveats:
- Pinning increases maintenance overhead (cert rotation). Consider pinning public key hashes and provide graceful pin rotation strategies.

---

## Token refresh, interceptors, and authenticators

Use an OkHttp Authenticator to react to 401/403 and refresh tokens synchronously. Ensure thread-safety and avoid refresh storms.

Simplified Authenticator example (synchronous refresh):

```kotlin
class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        synchronized(this) {
            // check if request already used the refreshed token
            val currentToken = tokenStorage.accessToken
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (currentToken != null && requestToken != currentToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // perform synchronous token refresh (careful with blocking)
            val refreshResp = authApi.refreshTokenSync(TokenRequest(tokenStorage.refreshToken))
            val newToken = refreshResp?.accessToken ?: return null
            tokenStorage.accessToken = newToken

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }
}
```

Notes:
- Prefer server-driven revocation, short-lived access tokens, and rotate refresh tokens.
- Avoid storing refresh tokens in plain text; prefer EncryptedSharedPreferences or Keystore-wrapped secrets.

---

## ProGuard / R8 (code shrinking & obfuscation)

R8 replaces ProGuard in modern Android builds and provides code shrinking, obfuscation, and optimization.

Enable in build.gradle:

```groovy
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

Recommended rules:
- Keep reflection-used classes (JSON serialization, DI frameworks) from obfuscation.
- Use `-adaptresourcefilecontents` if resource names are generated at runtime.

Example rules snippet (guideline):

```
# Keep model classes used by reflection (e.g., Moshi/Gson)
-keep class com.example.models.** { *; }

# Keep Retrofit interfaces
-keepattributes Signature
```

Obfuscation raises the bar but is not a security boundary. Do not rely solely on obfuscation for secret protection.

---

## App hardening & runtime protections

- Disable `android:debuggable` in release builds.
- Detect rooted devices if your threat model requires it (use with caution: can create false positives).
- Use SafetyNet or Play Integrity API to verify device integrity and app installation authenticity.
- Monitor and report suspicious behavior (server-side detection).

---

## Testing & validation

- Use static analysis (lint), dependency scanning for vulnerabilities, and secure code reviews.
- Test that keys are not present in APK (`strings.xml`, assets, or compiled code). Use `apktool` or `jadx` to inspect.
- Validate network behavior under man-in-the-middle tests (in a controlled environment) to ensure TLS and pinning behave as expected.
- Enforce TLS-only endpoints in CI checks where possible.

---

## Checklist & recommendations

- [ ] Use HTTPS (TLS 1.2+) and validate certificates
- [ ] Use AndroidX Security (EncryptedSharedPreferences / EncryptedFile) for local secrets
- [ ] Use Android Keystore for keys and prefer hardware-backed keys
- [ ] Implement OAuth2 with PKCE for native apps (AppAuth)
- [ ] Short-lived access tokens + refresh tokens with server-side revocation
- [ ] Use Network Security Config and consider certificate pinning with rotation strategy
- [ ] Enable R8/ProGuard and shrink resources for release builds
- [ ] Avoid hardcoding secrets in code or resources (use server or secure distribution)
- [ ] Respect user privacy and follow platform best practices

---

Security requires continuous attention: updates to libraries, OS security patches, server-side controls, and periodic audits are essential. Use platform-provided secure APIs rather than implementing custom cryptography.
