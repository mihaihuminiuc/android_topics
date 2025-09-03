# Android Security (Java)

A practical, Java-focused guide to securing Android apps. Covers secure storage, crypto APIs, network security, authentication tokens, permissions/privacy, WebView safety, IPC, code hardening, logging, and real-world patterns with concise Java examples.

---

## Table of contents

- Threat model & basics
- Secure storage (EncryptedSharedPreferences, EncryptedFile)
- Android Keystore crypto (AES/GCM, RSA/EC)
- Biometric auth (BiometricPrompt + CryptoObject)
- Network security (HTTPS, Network Security Config, TLS pinning)
- Authentication & token handling
- Data at rest (DB encryption, backups)
- IPC & intents (PendingIntent flags, ContentProvider)
- Permissions & privacy (scoped storage, runtime)
- WebView security
- Code hardening (R8/ProGuard, FLAG_SECURE)
- Logging & secrets
- Practical best practices checklist

---

## Threat model & basics

Why it matters:
- Knowing what you’re defending against (lost device, malware, rooted device, MITM, backend compromise) drives sane defenses.

Real-life:
- Focus on protecting user data at rest, in transit, and preventing easy abuse; assume attackers can reverse your APK.

Core tips:
- Prefer server-side enforcement; do not hardcode secrets; use strong TLS; keep dependencies updated.

---

## Secure storage (EncryptedSharedPreferences, EncryptedFile)

Why it matters:
- Store small sensitive fields (tokens) and files encrypted at rest without custom crypto.

EncryptedSharedPreferences:

```java
MasterKey masterKey = new MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build();
SharedPreferences secure = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);
secure.edit().putString("access_token", token).apply();
String token = secure.getString("access_token", null);
```

EncryptedFile:

```java
File file = new File(context.getFilesDir(), "secret.bin");
EncryptedFile enc = new EncryptedFile.Builder(
    context, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
).build();
try (FileOutputStream out = enc.openFileOutput()) {
  out.write(data);
}
```

Real-life:
- Store auth tokens, user profile cache, or exported reports securely; avoid plain SharedPreferences for secrets.

---

## Android Keystore crypto (AES/GCM, RSA/EC)

Why it matters:
- Keys generated in Keystore are non-exportable and may be backed by hardware (TEE/StrongBox).

Generate an AES key and encrypt/decrypt with GCM:

```java
KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
    "aes_key_alias",
    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setRandomizedEncryptionRequired(true)
    .setUserAuthenticationRequired(false) // true if gating by biometrics/lock
    .build();
KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
kg.init(spec); SecretKey key = kg.generateKey();

Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
enc.init(Cipher.ENCRYPT_MODE, key);
byte[] iv = enc.getIV();
byte[] ct = enc.doFinal(plain);

Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
GCMParameterSpec gcm = new GCMParameterSpec(128, iv);
dec.init(Cipher.DECRYPT_MODE, key, gcm);
byte[] pt = dec.doFinal(ct);
```

Generate an EC key pair for signing (JWT, attestation):

```java
KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
    "ec_sign_key",
    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(false)
    .build();
KeyPairGenerator kpg = KeyPairGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
kpg.initialize(spec); KeyPair kp = kpg.generateKeyPair();
Signature s = Signature.getInstance("SHA256withECDSA");
s.initSign(kp.getPrivate()); s.update(data); byte[] sig = s.sign();
```

Real-life:
- Encrypt local cache; sign payloads or challenge-responses without exposing private keys.

---

## Biometric auth (BiometricPrompt + CryptoObject)

Why it matters:
- Gate sensitive actions with biometrics or device credential; optionally unlock Keystore keys.

Prompt with CryptoObject (decrypt example):

```java
Executor exec = ContextCompat.getMainExecutor(this);
BiometricPrompt prompt = new BiometricPrompt(this, exec, new BiometricPrompt.AuthenticationCallback() {
  @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
    Cipher cipher = result.getCryptoObject().getCipher();
    try { byte[] pt = cipher.doFinal(ciphertext); /* use plaintext */ } catch (Exception ignored) {}
  }
});
BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
    .setTitle("Authenticate")
    .setSubtitle("Unlock secure data")
    .setNegativeButtonText("Cancel")
    .build();
// Prepare cipher with key requiring user auth
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
SecretKey key = ((KeyStore)null) /* load from Keystore */; // pseudo; load your key
cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
```

Real-life:
- Protect viewing of saved cards, passwords, or exporting data with fingerprint/face auth.

---

## Network security (HTTPS, Network Security Config, TLS pinning)

Why it matters:
- Prevent MITM and insecure transport; fail closed when certificates are not the expected ones.

Block cleartext by default and allow only specific domains via Network Security Config:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
  <base-config cleartextTrafficPermitted="false" />
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain> <!-- local dev only -->
  </domain-config>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application android:networkSecurityConfig="@xml/network_security_config" />
```

OkHttp certificate pinning:

```java
CertificatePinner pinner = new CertificatePinner.Builder()
  .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
  .build();
OkHttpClient client = new OkHttpClient.Builder().certificatePinner(pinner).build();
Retrofit retrofit = new Retrofit.Builder()
  .baseUrl("https://api.example.com/")
  .client(client)
  .addConverterFactory(GsonConverterFactory.create())
  .build();
```

Real-life:
- Enforce HTTPS; allow cleartext only for dev endpoints; pin certs to reduce risk from compromised CAs.

---

## Authentication & token handling

Why it matters:
- Securely store and refresh tokens; avoid leaks and replay.

Tips:
- Store tokens in EncryptedSharedPreferences; prefer short-lived access tokens + refresh tokens.
- Send tokens via Authorization header only; never in URLs.
- Rotate tokens on logout; wipe on device compromise signals.

Refreshing with Retrofit (conceptual):

```java
class AuthInterceptor implements Interceptor {
  private final TokenStore store; private final TokenRefresher refresher;
  @Override public Response intercept(Chain chain) throws IOException {
    Request req = chain.request().newBuilder()
      .addHeader("Authorization", "Bearer " + store.accessToken())
      .build();
    Response res = chain.proceed(req);
    if (res.code() == 401) { // try one refresh
      synchronized (this) {
        if (store.isExpired()) refresher.refresh();
      }
      Request retry = chain.request().newBuilder()
        .header("Authorization", "Bearer " + store.accessToken()).build();
      res.close();
      return chain.proceed(retry);
    }
    return res;
  }
}
```

Real-life:
- Keep users logged in seamlessly, handle token expiry safely, and avoid infinite refresh loops.

---

## Data at rest (DB encryption, backups)

Why it matters:
- Protect local databases and control what’s backed up to cloud.

Room + SQLCipher (conceptual; third-party):

- Use SQLCipher-enabled SQLite and configure the Room open helper with a passphrase.
- Store passphrase derived from Keystore-wrapped secret.

Backup controls:

```xml
<application
  android:allowBackup="true"
  android:fullBackupContent="@xml/backup_rules" />
```

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
  <exclude domain="sharedpref" path="secure_prefs.xml"/>
  <exclude domain="database" path="app.db"/>
</full-backup-content>
```

Disable screenshots on sensitive screens:

```java
getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
```

Real-life:
- Prevent leaking secrets via backups or screenshots; encrypt DB for regulated industries.

---

## IPC & intents (PendingIntent flags, ContentProvider)

Why it matters:
- Prevent intent spoofing, hijacking, and unauthorized data access.

PendingIntent immutability:

```java
PendingIntent pi = PendingIntent.getActivity(
  context, 0, new Intent(context, DetailActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
```

Explicit intents and packages:

```java
Intent i = new Intent(context, TargetService.class); // explicit is safer
// or restrict implicit intents
i.setPackage(context.getPackageName());
```

ContentProvider permissions and URI grants:

```xml
<provider
  android:authorities="com.example.files"
  android:exported="false" />
```

```java
Intent share = new Intent(Intent.ACTION_SEND);
Uri uri = FileProvider.getUriForFile(context, "com.example.files", file);
share.putExtra(Intent.EXTRA_STREAM, uri);
share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
```

Real-life:
- Stop other apps from injecting extras or reading files; avoid mutable PendingIntents for notifications.

---

## Permissions & privacy (scoped storage, runtime)

Why it matters:
- Minimize access to user data; ensure smooth permission UX.

Runtime permission request:

```java
ActivityResultLauncher<String> launcher = registerForActivityResult(
  new ActivityResultContracts.RequestPermission(), isGranted -> {
    if (isGranted) { /* access */ } else { /* explain */ }
  });
launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
```

Scoped storage & MediaStore:
- Use SAF or MediaStore for shared media; avoid broad storage permissions.
- For notifications (Android 13+), request POST_NOTIFICATIONS permission when needed.

Real-life:
- Ask only when necessary and with rationale; reduce denial rates and reviews impact.

---

## WebView security

Why it matters:
- WebViews can expose your app to XSS/remote code execution if misconfigured.

Safe defaults:

```java
WebView wv = findViewById(R.id.web);
WebSettings s = wv.getSettings();
s.setJavaScriptEnabled(false); // enable only if you control content
s.setAllowFileAccess(false);
s.setAllowFileAccessFromFileURLs(false);
s.setAllowUniversalAccessFromFileURLs(false);
if (Build.VERSION.SDK_INT >= 21) wv.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
```

Avoid addJavascriptInterface unless necessary; if used, annotate methods with @JavascriptInterface and restrict pages.

Real-life:
- Many breaches exploit overly permissive WebViews or JS bridges; lock them down.

---

## Code hardening (R8/ProGuard, FLAG_SECURE)

Why it matters:
- Make static analysis and tampering harder; protect sensitive screens.

R8 sample rules (proguard-rules.pro):

```
# Keep models used by Gson/reflective libs
-keep class com.example.model.** { *; }
# Remove logs in release
-assumenosideeffects class android.util.Log { *; }
```

Enable shrinking/obfuscation:

```gradle
buildTypes {
  release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
  }
}
```

FLAG_SECURE for sensitive UIs (see earlier example).

Real-life:
- Smaller APK, fewer attack surfaces, and reduced info leakage in release builds.

---

## Logging & secrets

Why it matters:
- Logs live forever; avoid leaking PII or tokens.

```java
if (BuildConfig.DEBUG) {
  Log.d("Auth", "request=" + redacted(req));
}
```

Tips:
- Redact tokens/PII; use network interceptors to mask headers; never commit API keys—use backend or remote config.

Real-life:
- Prevents accidental exposure via logcat, crash reports, or 3rd-party SDKs.

---

## Practical best practices checklist

- Use HTTPS everywhere; block cleartext via Network Security Config.
- Pin certificates for high-risk endpoints; rotate pins carefully.
- Store tokens in EncryptedSharedPreferences; encrypt files with EncryptedFile.
- Use Android Keystore for keys; prefer AES/GCM; don’t roll your own crypto.
- Gate sensitive actions with BiometricPrompt; bind Keystore keys to user auth if appropriate.
- Control backups; exclude secrets; use FLAG_SECURE on sensitive screens.
- Prefer explicit intents; set FLAG_IMMUTABLE on PendingIntents; lock down providers.
- Ask for minimal permissions; explain rationale; follow scoped storage rules.
- Harden code with R8; remove logs in release; keep reflective models.
- Keep dependencies up to date; audit 3rd-party SDKs; monitor for CVEs.

---

This Java security primer focuses on pragmatic defenses that meaningfully reduce risk without hurting UX. Pair with the Networking and Data Storage guides for end-to-end protection.
