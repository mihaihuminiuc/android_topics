# Android Networking (Kotlin)

This guide covers networking on Android using OkHttp and Retrofit, how to call REST APIs safely from Kotlin, handling headers/auth, caching, streaming, testing, and best practices.

---

## Table of contents

- Networking fundamentals
- OkHttp (client, interceptors, cache, timeouts)
- Retrofit (interfaces, converters, suspend support)
- Auth, token refresh, and authenticators
- File upload & download / multipart
- WebSockets
- Caching strategies
- Error handling & result modeling
- Testing networking (MockWebServer)
- Security: TLS & certificate pinning
- Best practices

---

## Networking fundamentals

- Always prefer HTTPS. Mobile apps should not rely on cleartext HTTP in production.
- Perform network operations off the main thread (use coroutines / Dispatchers.IO).
- Retry and backoff strategies are useful for network robustness.

---

## OkHttp

OkHttp is a performant HTTP client used by Retrofit. You configure interceptors, cache, timeouts, and connection pooling here.

Basic client setup with logging and cache:

```kotlin
val cacheSize = 10L * 1024L * 1024L // 10 MiB
val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .cache(Cache(File(context.cacheDir, "http_cache"), cacheSize))
    .build()
```

Interceptors:
- application interceptor: modify request/response (e.g., add headers)
- network interceptor: inspect response after network

Example Authorization interceptor:

```kotlin
class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${tokenProvider.token}")
            .build()
        return chain.proceed(request)
    }
}
```

Certificate pinning:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder().certificatePinner(pinner).build()
```

---

## Retrofit

Retrofit wraps OkHttp and provides a declarative API for REST endpoints. It supports suspend functions (Kotlin coroutines) natively.

Retrofit setup with Moshi (for Kotlin data classes):

```kotlin
val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

interface ApiService {
    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): Response<UserDto>

    @POST("login")
    suspend fun login(@Body req: LoginRequest): LoginResponse
}

val api = retrofit.create(ApiService::class.java)
```

Notes:
- Use Response<T> when you need to inspect HTTP status / error body, otherwise return T directly for a simpler flow (exceptions thrown on non-2xx only if you add call adapters).
- Use converters: Moshi, Gson, kotlinx.serialization (with converter), etc.

Using suspend functions in Repository/ViewModel:

```kotlin
suspend fun fetchUser(id: Int): Result<User> {
    val resp = api.getUser(id)
    return if (resp.isSuccessful) {
        Result.success(resp.body()!!)
    } else {
        Result.failure(HttpException(resp))
    }
}
```

---

## Authentication & token refresh

To automatically attach tokens use an interceptor. To refresh tokens on 401, use an Authenticator which can issue a synchronous refresh and retry the request.

Auth Authenticator (synchronous refresh example):

```kotlin
class TokenAuthenticator(
    private val tokenProvider: TokenProvider,
    private val authApi: AuthApi
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // synchronized block to avoid multiple refreshes
        synchronized(this) {
            val newToken = runBlocking {
                // perform synchronous refresh - avoid blocking UI
                authApi.refreshToken(tokenProvider.refreshToken()).token
            }
            tokenProvider.setToken(newToken)

            // return new request with updated token
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }
}
```

Caution: Authenticator runs on OkHttp threads; network calls here must be synchronous or executed with runBlocking. Avoid long blocking operations or deadlocks.

---

## Upload & Download

Multipart upload example (file + form fields):

```kotlin
@Multipart
@POST("upload")
suspend fun upload(@Part file: MultipartBody.Part, @Part("desc") desc: RequestBody): UploadResponse

// create part
val filePart = MultipartBody.Part.createFormData(
    "file", file.name, file.asRequestBody("image/jpeg".toMediaType())
)
```

File download (streaming to avoid OOM):

```kotlin
suspend fun downloadFile(url: String, dest: File) = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("Failed to download")
        resp.body?.byteStream()?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

Progress tracking: implement a counting RequestBody/ResponseBody wrapper that reports bytes written/read.

---

## WebSockets

OkHttp provides WebSocket support:

```kotlin
val request = Request.Builder().url("wss://example.com/socket").build()
val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) { }
    override fun onMessage(webSocket: WebSocket, text: String) { }
    override fun onFailure(webSocket: WebSocket, t: Throwable, resp: Response?) { }
}
client.newWebSocket(request, listener)
```

---

## Caching strategies

- OkHttp cache honors Cache-Control headers by default.
- You can implement offline caching by intercepting requests and returning cached responses when network is unavailable.

Offline cache interceptor example:

```kotlin
val offlineInterceptor = Interceptor { chain ->
    var request = chain.request()
    if (!isNetworkAvailable(context)) {
        request = request.newBuilder()
            .header("Cache-Control", "public, only-if-cached, max-stale=86400")
            .build()
    }
    chain.proceed(request)
}
```

---

## Error handling & modeling results

Model network results explicitly with a sealed type:

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class ApiError(val code: Int, val body: String?) : NetworkResult<Nothing>()
    data class NetworkError(val exception: IOException) : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): NetworkResult<T> {
    return try {
        val response = call()
        if (response.isSuccessful) NetworkResult.Success(response.body()!!)
        else NetworkResult.ApiError(response.code(), response.errorBody()?.string())
    } catch (ioe: IOException) {
        NetworkResult.NetworkError(ioe)
    }
}
```

Parse error bodies into typed error objects if the server follows a structured error format.

---

## Testing networking

- Use MockWebServer to simulate HTTP responses in unit tests.

Example with MockWebServer:

```kotlin
val server = MockWebServer()
server.enqueue(MockResponse().setBody("{...}").setResponseCode(200))
server.start()
val retrofit = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(MoshiConverterFactory.create())
    .client(client)
    .build()
// run tests against retrofit.create(ApiService::class.java)
server.shutdown()
```

---

## Security: TLS & certificate pinning

- Always use HTTPS and modern TLS.
- Certificate pinning increases security against MITM but make updates harder (rotate pins carefully).
- Keep secret keys off the app and use short-lived tokens.

---

## Best practices

- Use Retrofit + OkHttp with a shared OkHttpClient instance for connection reuse.
- Use suspend functions on Retrofit for idiomatic coroutine support.
- Keep network calls in repositories; expose safe/typed results to ViewModels.
- Implement token refresh carefully with an Authenticator; avoid race conditions.
- Use MockWebServer for deterministic tests.
- Respect user privacy and permissions (e.g., request storage or network-related permissions where necessary).
- Monitor network usage and implement pagination for large datasets.

---

This guide provides practical patterns and code snippets to implement robust networking on Android using Kotlin. For third-party libraries check their respective docs (OkHttp, Retrofit, Moshi, Kotlinx.serialization, and OkHttp MockWebServer).
