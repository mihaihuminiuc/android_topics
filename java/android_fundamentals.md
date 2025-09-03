# Android Fundamentals (Java)

A practical, Java-focused primer covering Activities, Fragments, Intents, Services, BroadcastReceiver, ContentProvider, Application/process lifecycle, Permissions, Threads & Concurrency (Executors/Handlers/Rx), WorkManager, best practices, and quick reference. Each section explains why it matters with real-world examples and includes code.

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
- Threads & Concurrency (Executors/Handlers/Rx)
- WorkManager
- Best practices & common pitfalls
- Quick reference

---

## Activity

What it is:
- An Activity is a single, focused thing the user can interact with — usually one screen with a UI. It is an Android component the system instantiates and drives through a well-defined lifecycle.

Core responsibilities:
- Inflate and manage the UI (setContentView / view binding) and handle user input on that screen.
- Coordinate navigation and transitions (startActivity, finish, fragments, Navigation component).
- Persist small transient UI state via `onSaveInstanceState` and hold longer-lived UI state in ViewModels or persistent storage.
- Manage resources tied to visibility (register/unregister listeners, start/stop animations).

High-level lifecycle (summary):
- `onCreate()` → `onStart()` → `onResume()` (activity is visible and interactive)
- `onPause()` → `onStop()` → `onDestroy()` (activity is leaving foreground or being destroyed)
- Use `onSaveInstanceState()` to save short-lived UI state; use `ViewModel` for configuration-surviving state.

When to use an Activity:
- Represent a distinct user task or screen (settings screen, conversation screen, media player UI).
- Host fragments for modular UI. For many modern apps prefer a single-activity architecture with the Navigation component to simplify back stack handling.

Best practices:
- Keep Activities thin: move business logic to ViewModels or UseCase classes.
- Avoid heavy/blocking work in `onCreate()`; defer with background threads or WorkManager.
- Use `registerForActivityResult` instead of deprecated `startActivityForResult`.
- Use `applicationContext` for app singletons; avoid leaking Activity context.

Common pitfalls:
- Holding strong references to Activity or Views in static singletons → memory leaks.
- Performing network or disk IO on the main thread → jank / ANR.
- Committing fragment transactions after `onSaveInstanceState()` → IllegalStateException or state loss; prefer safe commit timing.

Lifecycle (key callbacks):
- onCreate(Bundle?) — initialize UI, create components.
- onStart() — Activity becoming visible.
- onResume() — Activity interacting with the user (foreground).
- onPause() — Partial obscuring; commit transient changes.
- onStop() — Fully hidden; release resources that are not needed while stopped.
- onDestroy() — Final cleanup before the Activity is destroyed.
- onRestart() — Called after onStop before onStart when coming back.
- onSaveInstanceState(Bundle) — Save small UI state before recreation.

Example Activity (launch another Activity and save/restore small state):

```java
public class MainActivity extends AppCompatActivity {
  private int counter = 0;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (savedInstanceState != null) {
      counter = savedInstanceState.getInt("counter", 0);
    }

    findViewById(R.id.button).setOnClickListener(v -> {
      Intent i = new Intent(this, DetailActivity.class);
      i.putExtra("EXTRA_ID", 42);
      startActivity(i);
    });
  }

  @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putInt("counter", counter);
    super.onSaveInstanceState(outState);
  }
}
```

Modern Activity results (registerForActivityResult):

```java
public class HostActivity extends AppCompatActivity {
  private final ActivityResultLauncher<Intent> launcher =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
          String reply = result.getData().getStringExtra("reply");
          // handle reply
        }
      });

  void open() {
    Intent i = new Intent(this, InputActivity.class);
    launcher.launch(i);
  }
}
```

---

## Fragment

What it is:
- A reusable UI portion hosted inside an Activity. Has its own lifecycle and view lifecycle.

Why it matters (real world):
- Enables modular UIs, navigation, and tablet layouts. Use viewLifecycleOwner to avoid leaking view references.

Fragment basics with LiveData observation:

```java
public class ExampleFragment extends Fragment {
  public ExampleFragment() { super(R.layout.fragment_example); }
  private SharedViewModel vm;

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    vm = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    TextView tv = view.findViewById(R.id.textView);
    vm.getData().observe(getViewLifecycleOwner(), value -> tv.setText(value));

    view.findViewById(R.id.sendBtn).setOnClickListener(v -> vm.update("Hello from Fragment"));
  }
}
```

Fragment transactions (manual):

```java
getSupportFragmentManager()
  .beginTransaction()
  .replace(R.id.container, new ExampleFragment())
  .addToBackStack(null)
  .commit();
```

Prefer Navigation Component for most apps.

---

## Intents

What they are:
- Describe an action to perform; explicit (target component) or implicit (system picks handler).

Why it matters (real world):
- Enables screen-to-screen navigation and inter-app actions like sharing, dialing, opening URLs.

Explicit intent (start Activity with extras):

```java
Intent intent = new Intent(this, DetailActivity.class);
intent.putExtra("EXTRA_ID", 42);
startActivity(intent);
```

Implicit intent (open web page):

```java
Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com"));
if (view.resolveActivity(getPackageManager()) != null) {
  startActivity(view);
}
```

Requesting a result (modern API in Java): see Activity section.

---

## Services (Started / Bound / Foreground)

What they are:
- Run background operations without a UI.

Why it matters (real world):
- Use for ongoing tasks like music playback, location tracking, or work that must continue when UI is gone.

Started Service (simple background task with Executor):

```java
public class MyStartedService extends Service {
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    io.execute(() -> {
      // do background work
      SystemClock.sleep(5000);
      stopSelf();
    });
    return START_NOT_STICKY;
  }

  @Override public IBinder onBind(Intent intent) { return null; }

  @Override public void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
```

Foreground Service (requires notification on Android 8+):

```java
public class ForegroundService extends Service {
  private static final String CHANNEL_ID = "svc";

  @Override public void onCreate() {
    super.onCreate();
    NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(ch);
    Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Service running")
      .setSmallIcon(R.drawable.ic_notification)
      .build();
    startForeground(1, n);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
  @Override public IBinder onBind(Intent intent) { return null; }
}
```

Bound Service (local binder):

```java
public class LocalService extends Service {
  public class LocalBinder extends Binder { LocalService getService() { return LocalService.this; } }
  private final IBinder binder = new LocalBinder();
  @Override public IBinder onBind(Intent intent) { return binder; }
  public int compute() { return 42; }
}
```

When to use WorkManager:
- Prefer WorkManager for deferrable, guaranteed background work (sync, uploads) that can tolerate being deferred.

---

## BroadcastReceiver

What it is:
- Reacts to system or app broadcasts.

Why it matters (real world):
- Handle system events like connectivity changes, battery status, or your own in-app events.

Dynamic registration:

```java
BroadcastReceiver receiver = new BroadcastReceiver() {
  @Override public void onReceive(Context context, Intent intent) {
    // handle
  }
};
registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));
// later
unregisterReceiver(receiver);
```

Manifest-registered (receives certain broadcasts even when app not running):

```xml
<receiver android:name=".BootReceiver" android:exported="true">
  <intent-filter>
    <action android:name="android.intent.action.BOOT_COMPLETED"/>
  </intent-filter>
</receiver>
```

---

## ContentProvider

What it is:
- Exposes structured data via URI to other apps. In-app, prefer Room for local storage.

Why it matters (real world):
- Share data across apps or with system (e.g., files, contacts-style data) using permissions.

Skeleton:

```java
public class MyProvider extends ContentProvider {
  @Override public boolean onCreate() { return true; }
  @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
    // return cursor
    return null;
  }
  @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
  @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
  @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
  @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
```

---

## Application class, process & app lifecycle

What it is:
- Application is created when the app process starts. Good for light initialization and process-wide hooks.

Why it matters (real world):
- Centralize DI setup, logging, and register lifecycle callbacks. Handle memory pressure in onTrimMemory.

Process model highlights:
- System may kill your process at any time in background; persist important state.
- Multiple processes are possible (android:process) but add complexity.

Example (light init and deferred work):

```java
public class MyApp extends Application {
  @Override public void onCreate() {
    super.onCreate();
    registerActivityLifecycleCallbacks(new SimpleCallbacks());
    Executors.newSingleThreadExecutor().execute(() -> {
      // warm caches, schedule WorkManager, pre-load
    });
  }

  @Override public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
      // clear caches
    }
  }

  static class SimpleCallbacks implements ActivityLifecycleCallbacks {
    public void onActivityCreated(Activity a, Bundle b) {}
    public void onActivityStarted(Activity a) {}
    public void onActivityResumed(Activity a) {}
    public void onActivityPaused(Activity a) {}
    public void onActivityStopped(Activity a) {}
    public void onActivitySaveInstanceState(Activity a, Bundle out) {}
    public void onActivityDestroyed(Activity a) {}
  }
}
```

---

## Permissions

- Declare in AndroidManifest with <uses-permission>.
- Request dangerous permissions at runtime.

Why it matters (real world):
- Required for camera, location, notifications, etc. Handle denial gracefully.

Runtime request (ActivityResultContracts):

```java
public class CameraActivity extends AppCompatActivity {
  private final ActivityResultLauncher<String> requestCamera =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) {
          // use camera
        } else {
          // disable feature / explain rationale
        }
      });

  void checkAndRequest() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestCamera.launch(Manifest.permission.CAMERA);
    } else {
      // use camera
    }
  }
}
```

---

## Threads & Concurrency (Executors/Handlers/Rx)

- Don’t block the main thread. Use ExecutorService, HandlerThread, WorkManager, or RxJava in Java code.

Why it matters (real world):
- Smooth UI, avoids ANRs. Offload IO/network, post results back to main thread.

Example using Executor and main thread Handler:

```java
public class TimeViewModel extends ViewModel {
  private final MutableLiveData<String> state = new MutableLiveData<>();
  public LiveData<String> getState() { return state; }

  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public void load() {
    io.execute(() -> {
      try {
        String result = fetchFromNetwork();
        main.post(() -> state.setValue(result));
      } catch (Exception e) {
        main.post(() -> state.setValue("Error: " + e.getMessage()));
      }
    });
  }

  private String fetchFromNetwork() { /* ... */ return "OK"; }

  @Override protected void onCleared() { super.onCleared(); io.shutdownNow(); }
}
```

HandlerThread example:

```java
HandlerThread ht = new HandlerThread("worker");
ht.start();
Handler worker = new Handler(ht.getLooper());
worker.post(() -> {/* background task */});
```

RxJava snippet:

```java
Disposable d = Single.fromCallable(() -> api.fetch())
  .subscribeOn(Schedulers.io())
  .observeOn(AndroidSchedulers.mainThread())
  .subscribe(result -> {/* update UI */}, err -> {/* show error */});
```

---

## WorkManager (recommended for background jobs)

- For deferrable, guaranteed work with constraints.

Why it matters (real world):
- Ensure sync/uploads/logging run eventually, even across process death or reboot.

Simple Worker with progress:

```java
public class UploadWorker extends Worker {
  public static final String KEY_PROGRESS = "progress";

  public UploadWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

  @NonNull @Override public Result doWork() {
    try {
      for (int i = 1; i <= 100; i+=10) {
        setProgressAsync(new Data.Builder().putInt(KEY_PROGRESS, i).build());
        SystemClock.sleep(200);
      }
      return Result.success();
    } catch (Exception e) {
      return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
    }
  }
}
```

Enqueue and observe:

```java
OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UploadWorker.class).build();
WorkManager wm = WorkManager.getInstance(context);
wm.enqueue(req);
wm.getWorkInfoByIdLiveData(req.getId()).observe(this, info -> {
  if (info == null) return;
  if (info.getState() == WorkInfo.State.RUNNING) {
    int p = info.getProgress().getInt(UploadWorker.KEY_PROGRESS, 0);
    // update progress bar
  } else if (info.getState() == WorkInfo.State.SUCCEEDED) {
    // done
  } else if (info.getState() == WorkInfo.State.FAILED) {
    // show error
  }
});
```

---

## Best practices & common pitfalls

- Prefer AndroidX components (ViewModel, LiveData, Room, Navigation, WorkManager).
- Avoid leaking Context: don’t keep Activity/Fragment references in singletons or ViewModels; use Application context when needed.
- Use getViewLifecycleOwner() in Fragments for LiveData observers.
- Don’t block main thread; use background executors and post results to main.
- Use WorkManager or Foreground Service according to background execution limits (Android 8+).
- Handle configuration changes with ViewModel and onSaveInstanceState for small UI state.

---

## Quick reference

- Start Activity: `startActivity(new Intent(this, NextActivity.class))`
- Start Service: `ContextCompat.startForegroundService(ctx, intent)` (for foreground)
- Bind Service: `bindService(intent, connection, Context.BIND_AUTO_CREATE)`
- Register receiver: `registerReceiver(receiver, filter)` / `unregisterReceiver(receiver)`
- Request permission: `ActivityResultContracts.RequestPermission()` launcher
- Threading: `Executors.newSingleThreadExecutor()` / `HandlerThread`
- Persistence: Room + DAO; preferences: DataStore or SharedPreferences (legacy)

---

This file aims to be a concise, practical reference. For deeper dives, consult the AndroidX documentation and Jetpack guides for each component.
