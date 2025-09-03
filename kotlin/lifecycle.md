# Android Lifecycle (Kotlin)

Kotlin-focused guide explaining lifecycle concepts for Application, Activity, Fragment, and Service. Includes Kotlin code examples and real-life rationale for each pattern.

---

## Table of contents

- App (Application) lifecycle & process
- Activity lifecycle
  - basic callbacks
  - onSaveInstanceState / restoration
  - configuration changes & ViewModel
  - foreground/background & visibility
- Fragment lifecycle
  - view lifecycle vs fragment lifecycle
  - child fragments and transactions
- Service lifecycle
  - started services (onStartCommand)
  - bound services (onBind/onUnbind)
  - foreground services
- Lifecycle-aware components & observers
  - DefaultLifecycleObserver, LifecycleEventObserver
  - ProcessLifecycleOwner (app-level foreground/background)
- Common pitfalls and real-life examples
- Best practices checklist

---

## App (Application) lifecycle & process

What it is:
- `Application` runs when the Android process starts. Use it for app-scoped initialization (DI, analytics, crash reporting).
- The OS may kill the process when in background; assume `Application.onCreate()` can be called multiple times over the app's lifetime.

Example:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fast initialization only
        Logger.init(this)
        AppComponent.init(this) // DI/composition root
    }
}
```

Real-life:
- Initialize Crashlytics, remote config, and a lightweight DI root here. Defer heavy work with lazy initialization or background threads.

Notes:
- If your app uses multiple processes, check process name before running single-process initializers.

---

## Activity lifecycle

Callback order (common):
- onCreate -> onStart -> onResume (activity interactive)
- onPause -> onStop -> onDestroy (leaving foreground / UI hidden)

Simple Activity with state saving:

```kotlin
class MainActivity : AppCompatActivity() {
    private var count: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState != null) {
            count = savedInstanceState.getInt("count", 0)
        }

        findViewById<Button>(R.id.button).setOnClickListener {
            findViewById<TextView>(R.id.text).text = "Clicked ${++count}"
        }
    }

    override fun onStart() { super.onStart(); Log.d("Life", "onStart") }
    override fun onResume() { super.onResume(); Log.d("Life", "onResume") }
    override fun onPause() { super.onPause(); Log.d("Life", "onPause") }
    override fun onStop() { super.onStop(); Log.d("Life", "onStop") }
    override fun onDestroy() { super.onDestroy(); Log.d("Life", "onDestroy") }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("count", count)
    }
}
```

onSaveInstanceState / restoration:
- Use `onSaveInstanceState` for small UI state (primitive types, short strings). For larger or complex state prefer ViewModel or persistent storage.
- When the system kills the process in background, saved instance state helps restore transient UI state after recreation.

Configuration changes & ViewModel:
- ViewModel survives configuration changes and is the recommended place for UI-related data that must persist across rotations.

ViewModel example (Kotlin):

```kotlin
class CounterViewModel : ViewModel() {
    private val _count = MutableLiveData(0)
    val count: LiveData<Int> = _count
    fun inc() { _count.value = (_count.value ?: 0) + 1 }
}

class CounterActivity : AppCompatActivity() {
    private val vm: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_counter)
        val tv = findViewById<TextView>(R.id.text)
        findViewById<Button>(R.id.button).setOnClickListener { vm.inc() }
        vm.count.observe(this) { tv.text = it.toString() }
    }
}
```

Foreground/background & visibility:
- Use onResume/onPause for interactive work; onStart/onStop for visibility-related resources.
- Release expensive resources (camera, sensors) in onPause/onStop to save battery and allow other apps access.

Real-life:
- Pause video playback in onPause; unregister location updates in onStop.

---

## Fragment lifecycle

Fragment lifecycle has a distinct view lifecycle. Important callbacks:
- onAttach -> onCreate -> onCreateView -> onViewCreated -> onStart -> onResume
- onPause -> onStop -> onDestroyView -> onDestroy -> onDetach

View lifecycle vs fragment lifecycle:
- The fragment instance can outlive its view. Clear references to binding/view-related objects in onDestroyView to avoid leaks.

Fragment example (viewBinding-safe):

```kotlin
class DetailFragment : Fragment(R.layout.fragment_detail) {
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentDetailBinding.bind(view)
        binding.button.setOnClickListener { doWork() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

Child fragments and transactions:
- Use childFragmentManager for nested fragments. Commit transactions before state loss or use commitAllowingStateLoss with caution.

Real-life:
- Use fragments for modular UI components, adaptive layouts, and navigation flows.

---

## Service lifecycle

Started service (runs until stopped):
- onCreate -> onStartCommand -> (work) -> onDestroy

Kotlin example (started service):

```kotlin
class UploadService : Service() {
    override fun onCreate() { super.onCreate() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        Thread {
            upload(url)
            stopSelf(startId)
        }.start()
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

Bound service (Kotlin):

```kotlin
class LocalService : Service() {
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService() = this@LocalService }
    override fun onBind(intent: Intent) = binder
    fun getData(): String = "hello"
}

// Client
val conn = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        val s = (binder as LocalService.LocalBinder).getService()
        // use s
    }
    override fun onServiceDisconnected(name: ComponentName) {}
}
bindService(Intent(this, LocalService::class.java), conn, Context.BIND_AUTO_CREATE)
```

Foreground service:
- Call startForeground() with a notification to run as foreground. Use for playback, navigation, long-running uploads.

Notes:
- Since Android O, background service limits exist; prefer WorkManager for deferrable tasks that need guarantees.

---

## Lifecycle-aware components & observers

DefaultLifecycleObserver is recommended for type-safe lifecycle callbacks.

```kotlin
class LoggingObserver : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) { Log.d("Obs", "onResume") }
    override fun onPause(owner: LifecycleOwner) { Log.d("Obs", "onPause") }
}

// register in Activity
lifecycle.addObserver(LoggingObserver())
```

LifecycleEventObserver for explicit event handling:

```kotlin
val observer = LifecycleEventObserver { _, event ->
    if (event == Lifecycle.Event.ON_START) { /* start */ }
    else if (event == Lifecycle.Event.ON_STOP) { /* stop */ }
}
lifecycle.addObserver(observer)
```

ProcessLifecycleOwner (app-level):

```kotlin
ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> Log.d("AppLife", "App foreground")
            Lifecycle.Event.ON_STOP -> Log.d("AppLife", "App background")
            else -> {}
        }
    }
})
```

Real-life:
- Use app-level lifecycle events to pause polling or refresh tokens when app background/foreground state changes.

---

## Common pitfalls and real-life examples

- Leaking Activity or View references in long-lived objects (e.g., static fields). Use application context for singletons.
- Doing heavy work in Application.onCreate or Activity.onCreate: causes slow cold starts. Defer work to background threads.
- Committing fragment transactions after onSaveInstanceState: risk state loss. Prefer to commit earlier.
- Assuming process persistence for caching: OS may kill process and lose in-memory caches. Persist important state.

Real-life:
- Camera: release in onPause to allow other apps to access camera hardware.
- Messaging: pause background polling on onStop to save battery.
- Music apps: use foreground service so playback survives when UI is backgrounded.

---

## Best practices checklist

- Use ViewModel for UI state that must survive configuration changes.
- Save only small transient UI state in onSaveInstanceState.
- Prefer Lifecycle-aware components (LiveData, DefaultLifecycleObserver) to avoid manual checks.
- Use ProcessLifecycleOwner for app-level background/foreground handling.
- Avoid long blocking operations on the main thread; use coroutines/Executors/WorkManager as appropriate.
- Use application context for singletons and DI; avoid leaking Activity or Fragment views.
- Test rotation, backgrounding, and process death restores.

---

This Kotlin lifecycle guide focuses on practical, modern patterns (ViewModel, Lifecycle-aware components) to keep apps robust across configuration changes and process lifecycle events.
