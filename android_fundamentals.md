# Android Fundamentals (Kotlin)

This document provides a practical and detailed overview of core Android fundamentals for Android developers using Kotlin. It covers Activities, Fragments, Intents, Services and other platform primitives, with short, modern Kotlin examples and best-practice notes.

---

## Table of contents

- Activity
- Fragment
- Intents
- Services (Started / Bound / Foreground)
- BroadcastReceiver
- ContentProvider
- Application / Process lifecycle
- Permissions
- Threads & Concurrency (Coroutines)
- WorkManager
- Best practices & common pitfalls

---

## Activity

What it is:
- An Activity is a single, focused thing that the user can do. It represents a screen with a UI.

Lifecycle (key callbacks):
- onCreate(Bundle?) — initialize UI, create components.
- onStart() — Activity becoming visible.
- onResume() — Activity interacting with the user (foreground).
- onPause() — Partial obscuring; commit transient changes.
- onStop() — Fully hidden; release resources that are not needed while stopped.
- onDestroy() — Final cleanup before the Activity is destroyed.
- onRestart() — Called after onStop before onStart when coming back.
- onSaveInstanceState(Bundle) — Save small UI state before recreation.

Example Activity (launch another Activity and save instance state):

```kotlin
class MainActivity : AppCompatActivity() {

    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        savedInstanceState?.let {
            counter = it.getInt("counter", 0)
        }

        findViewById<Button>(R.id.button).setOnClickListener {
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra("EXTRA_ID", 42)
            }
            startActivity(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("counter", counter)
        super.onSaveInstanceState(outState)
    }
}
```

Notes:
- Use ActivityResult APIs (registerForActivityResult) instead of startActivityForResult for cleaner result handling.

---

## Fragment

What it is:
- A Fragment is a reusable portion of a UI inside an Activity (or another Fragment). Fragments are lifecycle-aware and have their own UI.

Key differences from Activities:
- Fragments depend on a host Activity.
- There are two related lifecycles: fragment lifecycle and view lifecycle (important: view-related references should use viewLifecycleOwner).

Important fragment callbacks:
- onAttach(Context)
- onCreate(Bundle?)
- onCreateView(LayoutInflater, ViewGroup?, Bundle?) — inflate view hierarchy
- onViewCreated(View, Bundle?) — view setup and LiveData observers
- onStart(), onResume(), onPause(), onStop()
- onDestroyView() — clear view references
- onDestroy(), onDetach()

Example Fragment (shared ViewModel using activityViewModels and observing LiveData safely):

```kotlin
class ExampleFragment : Fragment(R.layout.fragment_example) {

    private val sharedVm: SharedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe LiveData using viewLifecycleOwner to avoid leaks
        sharedVm.data.observe(viewLifecycleOwner) { value ->
            view.findViewById<TextView>(R.id.textView).text = value
        }

        view.findViewById<Button>(R.id.sendBtn).setOnClickListener {
            sharedVm.update("Hello from Fragment")
        }
    }
}
```

Fragment transactions:
- Use supportFragmentManager.beginTransaction().replace(...).addToBackStack(...).commit()
- Prefer the Jetpack Navigation component for navigation and backstack handling in modern apps.

---

## Intents

What they are:
- Intents describe an action to perform, and can be explicit (target a specific component) or implicit (ask the system to find a component that can handle the action).

Examples:

Explicit intent (start an Activity and supply extras):

```kotlin
val intent = Intent(this, DetailActivity::class.java).apply {
    putExtra("EXTRA_ID", 42)
}
startActivity(intent)
```

Implicit intent (open a web page):

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com"))
if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

Passing results (modern):
- Use Activity Result APIs (ActivityResultContracts) to request results or permissions.

Example: start for result

```kotlin
private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val data = result.data?.getStringExtra("reply")
        // handle
    }
}

// To launch
val i = Intent(this, InputActivity::class.java)
getResult.launch(i)
```

---

## Services

What they are:
- Services run operations in the background without a user interface. Use them for long-running operations that should continue even when the user leaves the app.

Types:
- Started Service — started with startService/startForegroundService and typically stops itself with stopSelf().
- Bound Service — clients bind to it via bindService and interact through a Binder interface.
- Foreground Service — a started service that shows a non-dismissable notification and is used for user-visible ongoing work (e.g., music playback, location tracking).

Important lifecycle methods:
- onCreate(), onStartCommand(Intent?, Int, Int), onBind(Intent), onDestroy()

Started Service example (simple long-running task using coroutines):

```kotlin
class MyStartedService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            // do background work
            delay(5000)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

Foreground Service (requires Notification & channel on Android 8+):

```kotlin
class ForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service running")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        startForeground(NOTIFY_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { /* long-running work */ }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

Bound Service (simple local binder):

```kotlin
class LocalService : Service() {
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): LocalService = this@LocalService }

    override fun onBind(intent: Intent?): IBinder = binder

    fun compute(): Int {
        // business logic
        return 42
    }
}
```

When to use WorkManager:
- For deferrable, guaranteed background work (even if the app exits or the device restarts), prefer WorkManager instead of a Service for scheduled or periodic tasks.

Background execution limits:
- Since Android 8 (Oreo), background execution and implicit broadcasts have strong limitations; use foreground services or WorkManager accordingly.

---

## BroadcastReceiver

What it is:
- A component that reacts to system or application broadcasts. Can be registered in the manifest or dynamically in code.

Example (dynamic registration):

```kotlin
private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // handle
    }
}

// register
registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_LOW))
// unregister
unregisterReceiver(receiver)
```

Manifest-registered receivers can receive certain system broadcasts even when the app is not running (subject to platform restrictions).

---

## ContentProvider

What it is:
- A ContentProvider exposes data to other apps via a URI-based API. Use when you need to share structured data across app boundaries. For in-app local storage, prefer Room + DAO.

Key points:
- Implement query/insert/update/delete and define a content URI.
- Use ContentResolver to access providers.

---

## Application class, process & app lifecycle

- Application.onCreate() is called when the process for the application is created. Use for singletons or early initialization (DI, analytics, logging), but keep work minimal to avoid slow app starts.
- The Android OS may kill an app process when it's in the background. Always save important state to persistent storage (Room, DataStore) or via onSaveInstanceState for UI state.

---

## Permissions

- Declare permissions in AndroidManifest.xml with <uses-permission>.
- Request dangerous permissions at runtime (camera, location, microphone, etc.).
- Use ActivityResultContracts.RequestPermission or RequestMultiplePermissions to request and handle results.

Example:

```kotlin
private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { /* use feature */ } else { /* disable feature */ }
    }

if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
}
```

---

## Threads & Concurrency (Kotlin Coroutines)

- Do not run heavy work on the main thread. Use coroutines, Executors, or other asynchronous APIs.
- Use lifecycle-aware coroutine scopes: lifecycleScope in Activity/Fragment and viewModelScope in ViewModel.

Example ViewModel with coroutine:

```kotlin
class MyViewModel(private val repo: Repo) : ViewModel() {
    private val _state = MutableLiveData<String>()
    val state: LiveData<String> = _state

    fun load() {
        viewModelScope.launch {
            val result = repo.fetchFromNetwork()
            _state.value = result
        }
    }
}
```

In a Fragment use:

```kotlin
lifecycleScope.launchWhenStarted {
    // UI-safe coroutine scope
}
```

---

## WorkManager (recommended for background jobs)

- WorkManager is the recommended solution for deferrable and guaranteed background work. It handles API differences and constraints (network, battery).

Simple CoroutineWorker example:

```kotlin
class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // perform background upload
        return Result.success()
    }
}

// enqueue
val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>().build()
WorkManager.getInstance(context).enqueue(uploadRequest)
```

---

## Best practices & common pitfalls

- Prefer the Jetpack libraries (ViewModel, LiveData/StateFlow, Room, Navigation, WorkManager) — they are lifecycle-aware and handle many edge cases.
- Avoid leaking Context: do not keep strong references to Activity/Fragment contexts in singletons. Use applicationContext when needed.
- Use viewLifecycleOwner inside Fragments to observe LiveData and avoid accessing view bindings after onDestroyView.
- Don’t perform blocking IO on the main thread — use coroutines or background threads.
- For background tasks, use WorkManager or Foreground Service as appropriate, and follow platform restrictions for Android O+.
- Handle configuration changes with ViewModel and savedInstanceState for UI state.

---

## Quick reference: common APIs

- Start Activity: startActivity(Intent(this, NextActivity::class.java))
- Start Service: ContextCompat.startForegroundService(context, intent) (for foreground)
- Bind Service: bindService(intent, connection, Context.BIND_AUTO_CREATE)
- Register dynamic receiver: registerReceiver(receiver, filter)
- Request permission: ActivityResultContracts.RequestPermission
- Coroutine scopes: lifecycleScope, viewModelScope
- Persistent storage: Room DB, DataStore (preferences replacement)

---

This file is intended as a practical primer. For deeper learning, refer to platform docs and modern Jetpack components for each topic (Navigation, WorkManager, Room, Hilt/DI, etc.).
