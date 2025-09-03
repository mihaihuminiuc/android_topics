# Android Lifecycle (Java)

This Java-focused guide explains lifecycle concepts for Application, Activity, Fragment, and Service. Each section includes code examples and real-life reasons for the patterns shown.

---

## Table of contents

- App (Application) lifecycle & process
- Activity lifecycle
  - basic callbacks
  - onSaveInstanceState / restoration
  - config changes & retained state
  - foreground/background & visibility
- Fragment lifecycle
  - view lifecycle vs fragment lifecycle
  - child fragments and transactions
- Service lifecycle
  - started services (onStartCommand)
  - bound services (onBind/onUnbind)
  - foreground services
- Lifecycle-aware components & observers
  - LifecycleOwner, LifecycleObserver, LifecycleEventObserver
  - ProcessLifecycleOwner (app-level foreground/background)
- Common pitfalls and real-life examples
- Best practices checklist

---

## App (Application) lifecycle & process

What it is:
- `Application` is created when the app process starts. It's a place for application-scoped initialization and composition roots (DI, singletons).
- The process can be killed anytime by the OS when background — don't assume `Application.onCreate()` will be called only once across installs or updates.

Example:

```java
public class App extends Application {
  @Override public void onCreate() {
    super.onCreate();
    // Initialize logging, DI, analytics, and global singletons.
    // Keep initialization fast; avoid blocking the main thread.
    MyLogger.init(this);
    AppComponent.init(this); // composition root
  }
}
```

Real-life:
- Use Application for one-time setup (CrashReporting, DI), but defer expensive work (lazy init) to avoid slow cold starts.

Notes:
- Multiple processes: `manifest` `android:process` may create multiple App instances — guard init code with process name checks if necessary.

---

## Activity lifecycle

Basic callbacks and order:
- onCreate -> onStart -> onResume (activity is in foreground and interactive)
- onPause -> onStop -> onDestroy (leaving foreground, then UI hidden, possibly destroyed)

Example Activity with logging and simple state save:

```java
public class MainActivity extends AppCompatActivity {
  private static final String KEY_COUNT = "key_count";
  private int count = 0;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Log.d("Life", "onCreate");
    if (savedInstanceState != null) {
      count = savedInstanceState.getInt(KEY_COUNT, 0);
    }
    Button b = findViewById(R.id.button);
    TextView tv = findViewById(R.id.text);
    b.setOnClickListener(v -> tv.setText("Clicked " + (++count)) );
  }

  @Override protected void onStart() { super.onStart(); Log.d("Life", "onStart"); }
  @Override protected void onResume() { super.onResume(); Log.d("Life", "onResume"); }
  @Override protected void onPause() { super.onPause(); Log.d("Life", "onPause"); }
  @Override protected void onStop() { super.onStop(); Log.d("Life", "onStop"); }
  @Override protected void onDestroy() { super.onDestroy(); Log.d("Life", "onDestroy"); }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_COUNT, count);
  }
}
```

onSaveInstanceState / restoration:
- `onSaveInstanceState` is used to persist transient UI state across configuration changes and process death while the activity is in background.
- Restore in `onCreate` (Bundle param) or in `onRestoreInstanceState`.

Real-life:
- Preserve form contents, scroll position, input text — not large data; persist large data in ViewModel or persistent storage.

Config changes & retained state:
- By default, config changes (rotation, locale, screen size) cause Activity recreation. Avoid `android:configChanges` unless necessary.
- Use ViewModel to keep UI-related data across recreation without using static singletons.

ViewModel example (Java):

```java
public class CounterViewModel extends ViewModel {
  private final MutableLiveData<Integer> count = new MutableLiveData<>(0);
  public LiveData<Integer> getCount() { return count; }
  public void inc() { Integer v = count.getValue(); count.setValue((v == null ? 0 : v) + 1); }
}

// In Activity
CounterViewModel vm = new ViewModelProvider(this).get(CounterViewModel.class);
vm.getCount().observe(this, value -> textView.setText(String.valueOf(value)));
```

Foreground/background & visibility:
- onResume = interactive; onPause = partially obscured; onStop = not visible.
- Use these to pause animations, release camera, or stop location updates.

Real-life:
- Pause video when onPause; unregister sensor listeners to save battery in onStop.

---

## Fragment lifecycle

Fragment lifecycle intersects Activity lifecycle, but has its own view lifecycle (important when using view binding).

Key callbacks:
- onAttach -> onCreate -> onCreateView -> onViewCreated -> onStart -> onResume
- onPause -> onStop -> onDestroyView -> onDestroy -> onDetach

Important note: `onDestroyView` vs `onDestroy`
- The Fragment's view can be destroyed while the fragment still exists (e.g., on navigation to another fragment on the back stack). Clear view references in `onDestroyView` to avoid leaks.

Fragment example with view binding safety:

```java
public class DetailFragment extends Fragment {
  private ViewBinding binding; // replace with actual generated binding type

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentDetailBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    binding.button.setOnClickListener(v -> doSomething());
  }

  @Override public void onDestroyView() {
    super.onDestroyView();
    binding = null; // avoid leaking the view
  }
}
```

Child fragments and transactions:
- When adding fragments dynamically, use `getChildFragmentManager()` for nested fragments.
- Use `addToBackStack()` for user-back navigation; be mindful of state saving when committing after `onSaveInstanceState` (use `commitAllowingStateLoss()` only when acceptable).

Real-life:
- Use fragments for modular UI, master-detail layouts, and navigation component flows.

---

## Service lifecycle

Started service (background task that can run indefinitely until stopped):
- onCreate -> onStartCommand -> (work) -> onDestroy

Started service example:

```java
public class UploadService extends Service {
  @Override public void onCreate() { super.onCreate(); }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    final String url = intent.getStringExtra("url");
    new Thread(() -> {
      upload(url);
      stopSelf(startId);
    }).start();
    return START_REDELIVER_INTENT; // or START_NOT_STICKY depending on desired behavior
  }

  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public void onDestroy() { super.onDestroy(); }
}
```

Bound service (clients bind and call methods):

```java
public class LocalService extends Service {
  private final IBinder binder = new LocalBinder();
  public class LocalBinder extends Binder { LocalService getService(){ return LocalService.this; } }
  @Nullable @Override public IBinder onBind(Intent intent) { return binder; }
  public String getData() { return "hello"; }
}

// Client
private ServiceConnection conn = new ServiceConnection() {
  @Override public void onServiceConnected(ComponentName name, IBinder b) {
    LocalService.LocalBinder binder = (LocalService.LocalBinder) b;
    LocalService s = binder.getService();
    // use s
  }
  @Override public void onServiceDisconnected(ComponentName name) { }
};
bindService(new Intent(this, LocalService.class), conn, Context.BIND_AUTO_CREATE);
```

Foreground service (user-visible with notification):
- Call `startForeground(notificationId, notification)` inside `onStartCommand` to elevate the service and reduce kill likelihood.

Real-life:
- Foreground services for music playback, navigation, file uploads/downloads that the user should be aware of.

Notes:
- Since Android O, background service limitations and adaptive battery affect behavior; prefer WorkManager for deferrable background tasks.

---

## Lifecycle-aware components & observers

LifecycleOwner / LifecycleObserver (legacy) and LifecycleEventObserver (newer) allow components to observe lifecycle changes without tight coupling.

Simple observer:

```java
public class LoggingObserver implements LifecycleObserver {
  @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
  public void onResume() { Log.d("Obs", "Owner resumed"); }
  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  public void onPause() { Log.d("Obs", "Owner paused"); }
}

// register in Activity
getLifecycle().addObserver(new LoggingObserver());
```

LifecycleEventObserver example (preferred for explicit events):

```java
LifecycleEventObserver observer = (source, event) -> {
  if (event == Lifecycle.Event.ON_START) { /* start resources */ }
  else if (event == Lifecycle.Event.ON_STOP) { /* stop resources */ }
};
getLifecycle().addObserver(observer);
```

ProcessLifecycleOwner (app-level):

```java
ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifecycleEventObserver() {
  @Override public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
    if (event == Lifecycle.Event.ON_START) { Log.d("AppLife", "App in foreground"); }
    else if (event == Lifecycle.Event.ON_STOP) { Log.d("AppLife", "App in background"); }
  }
});
```

Real-life:
- Start/stop analytics session, pause background polling when app goes to background, release expensive resources.

---

## Common pitfalls and real-life examples

- Holding Activity context in static fields → memory leaks. Use `getApplicationContext()` for long-lived objects.
- Performing heavy work on `onCreate` of `Activity` or `Application` → slow startup. Defer with background thread or lazy init.
- Committing fragment transactions after `onSaveInstanceState` → IllegalStateException or state loss. Use `commitNow()` in safe contexts or handle state carefully.
- Relying on process lifetime to persist state — process may be killed; persist critical data to disk or a DB.

Real-life examples:
- Camera app: release camera in `onPause` to allow other apps to take the camera.
- Messaging app: pause message polling in `onStop` and resume in `onStart` to save battery.
- Navigation/music app: run playback in a foreground service so audio continues when UI is backgrounded.

---

## Best practices checklist

- Prefer ViewModel for UI-related state that must survive configuration changes.
- Save minimal transient UI state in `onSaveInstanceState` (primitive types, small bundles).
- Use `ProcessLifecycleOwner` to handle app-level background/foreground transitions.
- Use Lifecycle-aware components (LiveData, LifecycleObserver) to avoid manual lifecycle checks.
- Avoid long blocking operations on UI thread; use Executors, HandlerThread, or WorkManager depending on guarantees.
- For long-running background work that must survive process death or restarts, use WorkManager (not plain Services).
- Use application context for singletons; avoid leaking Activity/Fragment views.
- Test lifecycle flows: rotation, backgrounding, low-memory process kills, and restore from savedInstanceState.

---

This Java lifecycle guide focuses on practical rules and code you can apply to keep apps responsive, memory-safe, and consistent across configuration and process changes.
