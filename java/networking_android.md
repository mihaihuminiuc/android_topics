# Android Networking (Java)

A practical, Java-focused guide to Android networking with real-world rationale and runnable-style snippets: connectivity checks, HttpURLConnection, OkHttp, Retrofit, JSON parsing, threading and background work, downloads/uploads with progress, pagination, retries/backoff, caching/offline, TLS & certificate pinning, WebSockets, testing with MockWebServer, and best practices.

---

## Table of contents

- Permissions & project setup
- Connectivity & network state
- HTTP clients overview
- HttpURLConnection (GET/POST)
- OkHttp (timeouts, interceptors, retries, cache)
- Retrofit (services, converters, LiveData/RxJava)
- JSON parsing (Gson/Moshi)
- Background threading (Executors) & WorkManager
- File download/upload (streaming, multipart) + progress
- Pagination patterns
- Error handling & retries (exponential backoff)
- Caching & offline (OkHttp cache + Room)
- Security (TLS, certificate pinning, cleartext)
- WebSockets (OkHttp)
- Testing networking (MockWebServer)
- Best practices checklist

---

## Permissions & project setup

Why it matters:
- NETWORK features require manifest permissions; missing them leads to confusing failures. Libraries need correct Gradle setup.

Manifest:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
```

Gradle (core libs):

```gradle
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "com.squareup.retrofit2:retrofit:2.11.0"
implementation "com.squareup.retrofit2:converter-gson:2.11.0" // or Moshi
// For tests
testImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"
```

---

## Connectivity & network state

Why it matters:
- Avoid starting requests when offline; adapt UI to metered/roaming; fail fast with good messages.

Connectivity check (API 23+):

```java
public final class Net {
  public static boolean isOnline(Context ctx) {
    ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (cm == null) return false;
    Network nw = cm.getActiveNetwork();
    if (nw == null) return false;
    NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
    return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }
}
```

Listen to changes (API 24+):

```java
ConnectivityManager cm = getSystemService(ConnectivityManager.class);
ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
  @Override public void onAvailable(@NonNull Network network) { /* came online */ }
  @Override public void onLost(@NonNull Network network) { /* lost connectivity */ }
};
cm.registerDefaultNetworkCallback(cb);
// remember to unregister in onDestroy
```

---

## HTTP clients overview

- HttpURLConnection: built-in, minimal, manual work; good for simple one-offs.
- OkHttp: modern HTTP client with pooling, caching, interceptors, TLS. Recommended foundation.
- Retrofit: type-safe HTTP API wrapper on top of OkHttp with converters (Gson/Moshi), error handling.

---

## HttpURLConnection (GET/POST)

Why it matters:
- Zero dependency baseline; useful for small utilities or understanding basics.

GET example:

```java
public String getText(String urlStr) throws IOException {
  URL url = new URL(urlStr);
  HttpURLConnection c = (HttpURLConnection) url.openConnection();
  c.setConnectTimeout(15000);
  c.setReadTimeout(15000);
  c.setRequestMethod("GET");
  c.setDoInput(true);
  try (InputStream in = new BufferedInputStream(c.getInputStream());
       ByteArrayOutputStream out = new ByteArrayOutputStream()) {
    byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    return out.toString(StandardCharsets.UTF_8.name());
  } finally { c.disconnect(); }
}
```

POST JSON:

```java
public String postJson(String urlStr, String json) throws IOException {
  HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
  c.setConnectTimeout(15000); c.setReadTimeout(15000);
  c.setRequestMethod("POST"); c.setDoOutput(true);
  c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
  try (OutputStream os = c.getOutputStream()) { os.write(json.getBytes(StandardCharsets.UTF_8)); }
  try (InputStream in = c.getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
  finally { c.disconnect(); }
}
```

---

## OkHttp (timeouts, interceptors, retries, cache)

Why it matters:
- Production-grade client with TLS, HTTP/2, caching, connection reuse, and powerful interceptors for auth/logging.

Client with timeouts, retry, and cache:

```java
File cacheDir = new File(context.getCacheDir(), "http");
Cache cache = new Cache(cacheDir, 20L * 1024 * 1024); // 20 MB

OkHttpClient client = new OkHttpClient.Builder()
  .connectTimeout(15, TimeUnit.SECONDS)
  .readTimeout(30, TimeUnit.SECONDS)
  .callTimeout(60, TimeUnit.SECONDS)
  .retryOnConnectionFailure(true)
  .cache(cache)
  .addInterceptor(chain -> {
    Request req = chain.request().newBuilder()
      .header("User-Agent", "MyApp/1.0")
      .build();
    return chain.proceed(req);
  })
  .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
  .build();
```

Synchronous and async calls:

```java
Request req = new Request.Builder().url("https://api.example.com/items").build();
Response resp = client.newCall(req).execute();

client.newCall(req).enqueue(new Callback() {
  @Override public void onFailure(@NotNull Call call, @NotNull IOException e) { /* show error */ }
  @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException { /* parse */ }
});
```

---

## Retrofit (services, converters, LiveData/RxJava)

Why it matters:
- Defines HTTP APIs as Java interfaces. Retrofit handles conversion, threading (if using Rx), and errors.

Service interface + Gson converter:

```java
public interface ApiService {
  @GET("items") Call<List<Item>> listItems();
  @GET("items/{id}") Call<Item> getItem(@Path("id") int id);
  @POST("items") Call<Item> create(@Body ItemCreate body);
}

Retrofit retrofit = new Retrofit.Builder()
  .baseUrl("https://api.example.com/")
  .addConverterFactory(GsonConverterFactory.create(new Gson()))
  .client(client) // OkHttpClient
  .build();
ApiService api = retrofit.create(ApiService.class);
```

Execute (enqueue on background thread automatically):

```java
api.listItems().enqueue(new Callback<List<Item>>() {
  @Override public void onResponse(Call<List<Item>> call, Response<List<Item>> resp) {
    if (resp.isSuccessful()) { List<Item> items = resp.body(); /* update UI */ }
    else { /* handle HTTP error: resp.code() */ }
  }
  @Override public void onFailure(Call<List<Item>> call, Throwable t) { /* network/parse error */ }
});
```

Optional RxJava adapter (Java-friendly reactive):

```gradle
implementation "com.squareup.retrofit2:adapter-rxjava3:2.11.0"
```

```java
public interface RxApiService { @GET("items") Single<List<Item>> listItems(); }
Retrofit rxRetrofit = new Retrofit.Builder()
  .baseUrl("https://api.example.com/")
  .addConverterFactory(GsonConverterFactory.create())
  .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
  .build();
RxApiService rxApi = rxRetrofit.create(RxApiService.class);
rxApi.listItems().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
  .subscribe(items -> {/* UI */}, err -> {/* show error */});
```

---

## JSON parsing (Gson/Moshi)

Why it matters:
- Converts JSON to Java objects safely and concisely.

Gson model:

```java
public class Item {
  public int id; public String name; public long createdAt;
}
Gson gson = new Gson();
Item item = gson.fromJson("{\"id\":1,\"name\":\"A\"}", Item.class);
```

Moshi alternative:

```gradle
implementation "com.squareup.moshi:moshi:1.15.1"
implementation "com.squareup.retrofit2:converter-moshi:2.11.0"
```

---

## Background threading (Executors) & WorkManager

Why it matters:
- Don’t block the main thread. Use Executors for short-lived tasks; WorkManager for deferrable, guaranteed work.

Executors sample:

```java
ExecutorService io = Executors.newFixedThreadPool(4);
io.execute(() -> {/* network call with OkHttp */});
```

WorkManager constrained job (needs network):

```java
Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
OneTimeWorkRequest sync = new OneTimeWorkRequest.Builder(SyncWorker.class)
  .setConstraints(net)
  .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
  .build();
WorkManager.getInstance(context).enqueue(sync);
```

---

## File download/upload (streaming, multipart) + progress

Why it matters:
- Large transfers must stream to disk and show progress; uploads often require multipart.

Download with progress (OkHttp):

```java
public File downloadToFile(OkHttpClient client, String url, File dest, Consumer<Integer> onProgress) throws IOException {
  Request req = new Request.Builder().url(url).build();
  try (Response resp = client.newCall(req).execute()) {
    if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
    ResponseBody body = resp.body(); if (body == null) throw new IOException("Empty body");
    long total = body.contentLength(); long read = 0;
    try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(dest)) {
      byte[] buf = new byte[8192]; int n;
      int last = -1;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
        read += n;
        if (total > 0) {
          int p = (int) ((read * 100) / total);
          if (p != last) { onProgress.accept(p); last = p; }
        }
      }
    }
    return dest;
  }
}
```

Multipart upload:

```java
RequestBody fileBody = RequestBody.create(file, MediaType.parse("image/jpeg"));
MultipartBody body = new MultipartBody.Builder()
  .setType(MultipartBody.FORM)
  .addFormDataPart("meta", null, RequestBody.create("{\"tag\":\"demo\"}", MediaType.parse("application/json")))
  .addFormDataPart("photo", file.getName(), fileBody)
  .build();
Request req = new Request.Builder().url("https://api.example.com/upload").post(body).build();
client.newCall(req).enqueue(/* callback */);
```

---

## Pagination patterns

Why it matters:
- Load large lists incrementally to reduce bandwidth and memory.

APIs: use query params like `page`, `pageSize`.

```java
public interface ApiService { @GET("items") Call<Page<Item>> items(@Query("page") int page, @Query("size") int size); }
```

Handle next page token:

```java
int page = 1; boolean more = true;
while (more) {
  Response<Page<Item>> r = api.items(page, 20).execute();
  if (r.isSuccessful()) {
    Page<Item> p = r.body(); render(p.items);
    more = p.hasMore; page++;
  } else { break; }
}
```

---

## Error handling & retries (exponential backoff)

Why it matters:
- Networks are unreliable. Handle timeouts, 5xx, and transient DNS issues gracefully.

Exponential backoff utility:

```java
public <T> T withBackoff(Callable<T> call, int maxAttempts) throws Exception {
  int attempt = 0; long delay = 500; // ms
  while (true) {
    try { return call.call(); }
    catch (IOException e) {
      attempt++; if (attempt >= maxAttempts) throw e;
      Thread.sleep(delay); delay = Math.min((long)(delay * 2.0), 8000);
    }
  }
}
```

Tip: Only retry idempotent requests (GET/PUT) unless the API explicitly supports retry-safe POST with idempotency keys.

---

## Caching & offline (OkHttp cache + Room)

Why it matters:
- Faster, cheaper, and works offline. Combine HTTP cache with a local DB for robust UX.

OkHttp cache control:

```java
// Force cache for 60s when online
.addNetworkInterceptor(chain -> {
  Response r = chain.proceed(chain.request());
  return r.newBuilder().header("Cache-Control", "public, max-age=60").build();
})
// Stale cache when offline
.addInterceptor(chain -> {
  Request req = chain.request();
  if (!Net.isOnline(context)) {
    req = req.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=2419200").build(); // 4 weeks
  }
  return chain.proceed(req);
})
```

Offline-first pattern:
- Read from Room; refresh from network; update DB; UI observes LiveData and updates automatically.

---

## Security (TLS, certificate pinning, cleartext)

Why it matters:
- Protect users from MITM attacks and data leaks. Avoid sending secrets in cleartext.

Certificate pinning (OkHttp):

```java
CertificatePinner pinner = new CertificatePinner.Builder()
  .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
  .build();
OkHttpClient secureClient = client.newBuilder().certificatePinner(pinner).build();
```

Cleartext traffic:
- Disabled by default on Android 9+. If needed (dev only), allow per-domain via Network Security Config instead of global allow.

Secrets management:
- Don’t hardcode API keys in source. Use remote config, backend mediation, or store in NDK/Keystore with obfuscation.

---

## WebSockets (OkHttp)

Why it matters:
- Real-time updates (chat, live scores) with persistent connections.

```java
Request r = new Request.Builder().url("wss://example.com/socket").build();
WebSocket ws = client.newWebSocket(r, new WebSocketListener() {
  @Override public void onOpen(WebSocket webSocket, Response response) { webSocket.send("hello"); }
  @Override public void onMessage(WebSocket webSocket, String text) { /* update UI */ }
  @Override public void onClosing(WebSocket webSocket, int code, String reason) { webSocket.close(1000, null); }
  @Override public void onFailure(WebSocket webSocket, Throwable t, Response r2) { /* retry/backoff */ }
});
```

Remember to close the socket in onStop/onDestroy to avoid leaks.

---

## Testing networking (MockWebServer)

Why it matters:
- Deterministic, fast tests without hitting real servers.

```java
MockWebServer server = new MockWebServer();
server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":1,\"name\":\"A\"}"));
server.start();

HttpUrl base = server.url("/");
OkHttpClient c = new OkHttpClient();
Request req = new Request.Builder().url(base.resolve("items")).build();
Response resp = c.newCall(req).execute();

RecordedRequest recorded = server.takeRequest(); // assert path/method/headers
server.shutdown();
```

With Retrofit:

```java
Retrofit testRetrofit = new Retrofit.Builder()
  .baseUrl(server.url("/").toString())
  .addConverterFactory(GsonConverterFactory.create())
  .build();
```

---

## Best practices checklist

- Use OkHttp + Retrofit for most apps; keep a single OkHttpClient instance.
- Set sensible timeouts; handle cancellations in UI (dispose/interrupt ongoing calls when leaving screen).
- Detect connectivity; show offline UI and offer retry.
- Use HTTPS everywhere; consider certificate pinning for sensitive endpoints.
- Implement retries with exponential backoff for transient failures; avoid retrying non-idempotent requests.
- Stream large downloads/uploads; show progress; use WorkManager for long-running deferrable work.
- Cache aggressively with proper Cache-Control; combine with Room for offline-first.
- Log in debug builds only (HttpLoggingInterceptor) and redact sensitive headers.
- Test with MockWebServer; add E2E tests for auth and error cases.
- Monitor with analytics/logging of error codes, timeouts, and retry outcomes.

---

This Java networking primer is designed for pragmatic app development. Adapt patterns to your architecture (MVVM/MVI), and keep networking concerns isolated in repositories/data sources for testability.
