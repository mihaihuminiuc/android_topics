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

What it is:
- The Application class is instantiated when the app process is created. It's a singleton tied to the process and provides an Application Context.
- Application.onCreate() is called on the main thread during process start. Use it for light-weight initializations (DI wiring, lightweight logging init, registering lifecycle callbacks) and defer heavy work.

Process model:
- Android apps run in a Linux process; by default all components (activities, services, broadcast receivers, providers) run in the same process.
- The system may kill the process to reclaim memory; when that happens no lifecycle callbacks are delivered — the process simply dies. Rely on persistent storage and process-agnostic recovery.
- You can opt-in to run specific components in separate processes via android:process in the manifest. Use this sparingly and test IPC, because separate processes don't share memory.

Initialization order:
- ContentProviders registered in the manifest are created before Application.onCreate(); avoid heavy work or long blocking calls in provider init.
- Application.onCreate() runs after providers are created, so prefer lazily initializing heavy subsystems (e.g., using lazy { } or background workers).

Common lifecycle callbacks & signals:
- onCreate() — process-level entry point. Keep short.
- onTerminate() — only for emulators and should not be relied on in production.
- onConfigurationChanged() — if you opt out of configuration changes in manifest for Application (rare).
- Component callbacks: registerActivityLifecycleCallbacks, registerComponentCallbacks are available to observe lower-level lifecycle events.
- onTrimMemory(level) / onLowMemory() — the system informs you when memory is low; release caches/resources accordingly.

Best practices:
- Avoid blocking the main thread during startup. Defer initialization with lazy delegates, WorkManager, or background coroutines.
- Use dependency injection (Hilt/ Dagger) to centralize wiring, but keep actual heavy work off the UI thread.
- Use WorkManager for guaranteed background initialization tasks that can be deferred and survive process death.
- Use applicationContext for singletons to avoid leaking Activity contexts.
- Avoid storing references to Activities, Views, or Contexts in static singletons.

Handling process death and restore:
- Process death differs from configuration change. When the system kills the process for memory, Activities are recreated later without guarantees for non-persistent state.
- Persist important state to Room, DataStore, or savedInstanceState for quick UI restoration.
- Use SavedStateHandle in ViewModels and persist long-lived state in local storage.

Example (lightweight Application wiring and deferred init):

```kotlin
class MyApplication : Application() {

    // lazy init ensures no work is done until actually needed
    val analytics by lazy { Analytics.init(this) }

    override fun onCreate() {
        super.onCreate()

        // register lifecycle callbacks for simple process-wide hooks
        registerActivityLifecycleCallbacks(MyActivityLifecycleListener())

        // Initialize DI container quickly (no heavy work here)
        AppComponent.initialize(this)

        // Defer heavy work to background so startup isn't blocked
        CoroutineScope(Dispatchers.Default).launch {
            // e.g. migrate cached DB, warm up caches, start WorkManager jobs
            Repository.warmUpCache()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            // release caches / reduce memory footprint
            CacheManager.clearNonCriticalCaches()
        }
    }
}
```

Debugging & diagnosis:
- Use logcat and Process.is64Bit() / ActivityManager to inspect processes.
- Use StrictMode to catch accidental disk/network operations on the main thread during development.
- Profile app cold start/warm start using Android Studio profiler and traceview.

When to use separate processes:
- Only for isolating memory- or security-sensitive work (e.g., crash isolations, renderers). Separate processes increase complexity (IPC, testing) and should be a last resort.

Notes:
- Application.onCreate is executed once per process. In apps that use multiple processes, each process will create its own Application instance.
- Keep startup fast: Android reports perceived app start time; long startup hurts user experience.

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

See a practical, more complete example in this repository: [view_mode_example.kt](code_examples/view_mode_example.kt)

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

See a practical, more complete example in this repository: [worker_manager.kt](code_examples/worker_manager.kt)

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
