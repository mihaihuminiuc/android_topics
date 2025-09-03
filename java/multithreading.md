# Android Multithreading (Java)

A pragmatic Java guide to concurrency on Android. Learn how the main thread works, how to offload work, coordinate tasks, post results back to the UI, and avoid ANRs—with real-life rationale and runnable-style snippets.

---

## Table of contents

- Threading model & main thread
- Handlers, Looper, MessageQueue
- Posting to the UI thread
- HandlerThread
- Thread & Runnable
- Executors (Fixed, Cached, Single, Scheduled)
- Futures, Callables, and cancellation
- Synchronization (synchronized, volatile, atomics, locks)
- Concurrency utilities (CountDownLatch, Semaphore, BlockingQueue)
- Background work vs. scheduling (WorkManager overview)
- Networking off the main thread (OkHttp example)
- ANR & StrictMode
- Testing and debugging concurrency
- Best practices checklist

---

## Threading model & main thread

Why it matters:
- Android enforces single-threaded UI updates. Long work on the main thread causes jank and ANRs (Application Not Responding).

Real-life:
- Scroll stutters, frozen inputs, and OS dialog “App isn’t responding” come from blocking the main thread.

Key points:
- UI toolkit is not thread-safe: only touch views from the main thread.
- Offload IO/network/CPU-heavy work to background threads.

---

## Handlers, Looper, MessageQueue

Why it matters:
- The main thread runs a Looper that processes messages and runnables. Handlers post work to a Looper’s queue.

Post a runnable with a Handler:

```java
Handler mainHandler = new Handler(Looper.getMainLooper());
mainHandler.post(() -> {
  // Update UI safely here
});
```

Schedule with delay:

```java
mainHandler.postDelayed(() -> { /* do later */ }, 300);
```

Real-life:
- Debounce button clicks; sequence small UI updates; throttle search queries.

Pitfall:
- Don’t keep implicit references to Activities. Prefer static inner classes and WeakReference if a Handler is long-lived.

---

## Posting to the UI thread

Why it matters:
- Background work must marshal results back to the main thread before touching views.

Options:
- Activity.runOnUiThread
- View.post
- Handler bound to Looper.getMainLooper()

```java
runOnUiThread(() -> textView.setText("Done"));
// or
textView.post(() -> textView.setText("Done"));
```

Real-life:
- Network callback completes off the main thread; safely update RecyclerView adapter on the main thread.

---

## HandlerThread

Why it matters:
- A background thread with its own Looper/MessageQueue. Good for serializing tasks without blocking the UI.

```java
public class CameraWorker {
  private final HandlerThread thread = new HandlerThread("camera");
  private Handler handler;
  public void start(){ thread.start(); handler = new Handler(thread.getLooper()); }
  public void stop(){ thread.quitSafely(); }
  public void doWork(){ handler.post(() -> { /* camera or IO work */ }); }
}
```

Real-life:
- Camera/image processing, audio recording, or sensor parsing that benefit from a dedicated serial thread.

---

## Thread & Runnable

Why it matters:
- Lowest-level building block. Useful for simple one-off tasks, but prefer higher-level APIs for pools and scheduling.

```java
Thread t = new Thread(() -> {
  // background work
});
t.start();
// cancel cooperatively
volatile boolean cancelled = false;
// inside work loop: if (cancelled) return;
```

Real-life:
- Quick scripts or legacy code. Move to Executors for production.

---

## Executors (Fixed, Cached, Single, Scheduled)

Why it matters:
- Thread pools manage concurrency efficiently and avoid creating too many threads.

Create pools:

```java
ExecutorService io = Executors.newFixedThreadPool(4);
ExecutorService single = Executors.newSingleThreadExecutor();
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
```

Submit work and schedule:

```java
io.execute(() -> loadFromDisk());
scheduler.schedule(() -> refresh(), 5, TimeUnit.SECONDS);
```

Post back to main thread:

```java
Handler main = new Handler(Looper.getMainLooper());
io.execute(() -> {
  Result r = compute();
  main.post(() -> show(r));
});
```

Real-life:
- Fixed pool for DB/IO; single for serializing writes; scheduled for periodic cache refresh.

---

## Futures, Callables, and cancellation

Why it matters:
- Get results from background tasks and cancel when needed (e.g., Activity stops).

```java
ExecutorService pool = Executors.newCachedThreadPool();
Future<Integer> f = pool.submit(() -> heavyComputation());
try {
  Integer value = f.get(2, TimeUnit.SECONDS); // timeout to avoid hangs
} catch (TimeoutException e) {
  f.cancel(true); // interrupt if running
}
```

Real-life:
- Time-bounded work (network, parsing); cancel when user navigates away.

---

## Synchronization (synchronized, volatile, atomics, locks)

Why it matters:
- Avoid race conditions and visibility bugs when sharing state across threads.

synchronized and volatile:

```java
class Counter {
  private int count;
  public synchronized void inc(){ count++; }
  public synchronized int get(){ return count; }
}

volatile boolean isActive = true; // visibility across threads
```

Atomics and locks:

```java
AtomicInteger ai = new AtomicInteger(0);
ai.incrementAndGet();

ReentrantLock lock = new ReentrantLock();
lock.lock();
try { /* critical */ } finally { lock.unlock(); }
```

Real-life:
- Guard shared caches, in-memory queues, or cancellation flags.

Pitfalls:
- Deadlocks and contention. Keep critical sections small; prefer immutability and thread confinement.

---

## Concurrency utilities (CountDownLatch, Semaphore, BlockingQueue)

Why it matters:
- Coordinate threads and backpressure.

CountDownLatch:

```java
CountDownLatch latch = new CountDownLatch(2);
io.execute(() -> { taskA(); latch.countDown(); });
io.execute(() -> { taskB(); latch.countDown(); });
latch.await(2, TimeUnit.SECONDS);
```

Semaphore (limit concurrency):

```java
Semaphore sem = new Semaphore(3);
Runnable job = () -> { try { sem.acquire(); doWork(); } catch (InterruptedException ignored) { } finally { sem.release(); } };
for (int i = 0; i < 10; i++) io.execute(job);
```

BlockingQueue:

```java
BlockingQueue<String> q = new ArrayBlockingQueue<>(100);
io.execute(() -> { while (true) q.put(fetch()); });
io.execute(() -> { while (true) consume(q.take()); });
```

Real-life:
- Wait for multiple startup tasks; throttle simultaneous uploads; producer-consumer patterns.

---

## Background work vs. scheduling (WorkManager overview)

Why it matters:
- Ad-hoc threads die with the process. Use WorkManager for deferrable, guaranteed, and constraint-aware work (charging, network).

Conceptual Java usage:

```java
Data input = new Data.Builder().putString("url", "https://...").build();
OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UploadWorker.class)
  .setInputData(input)
  .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
  .build();
WorkManager.getInstance(context).enqueue(req);
```

Real-life:
- Reliable sync, retries, and constraints (e.g., only on Wi‑Fi and charging) even after app restarts.

---

## Networking off the main thread (OkHttp example)

Why it matters:
- Network on main thread throws NetworkOnMainThreadException; even if allowed, it would block UI.

OkHttp async call and update UI:

```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder().url("https://example.com").build();
Handler main = new Handler(Looper.getMainLooper());
client.newCall(request).enqueue(new Callback() {
  @Override public void onFailure(Call call, IOException e) {
    main.post(() -> toast("Error: " + e.getMessage()));
  }
  @Override public void onResponse(Call call, Response response) throws IOException {
    String body = response.body().string();
    main.post(() -> showData(body));
  }
});
```

Real-life:
- Fetch data and refresh RecyclerView without freezing the UI.

---

## ANR & StrictMode

Why it matters:
- ANRs occur if the main thread is blocked for too long (e.g., >5s in foreground). StrictMode catches bad patterns early.

Enable StrictMode in debug builds:

```java
if (BuildConfig.DEBUG) {
  StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
    .detectAll()
    .penaltyLog()
    .build());
  StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
    .detectAll()
    .penaltyLog()
    .build());
}
```

Real-life:
- Quickly catch accidental disk/network on main thread; surface leaked Closable or Activity leaks early.

---

## Testing and debugging concurrency

Why it matters:
- Races and timing issues are subtle. Make tests deterministic.

Tips:
- Use IdlingResource/CountingIdlingResource to coordinate Espresso with background work.
- Inject Executors so tests can use a direct executor.
- Add timeouts on Futures; avoid Thread.sleep in tests.

Example: direct executor for tests

```java
Executor direct = Runnable::run; // runs inline on the calling thread
```

---

## Best practices checklist

- Never block the main thread; use background threads for IO/CPU.
- Always post UI updates to the main thread.
- Prefer thread pools (Executors) over raw Threads; size pools appropriately.
- Use WorkManager for deferrable, guaranteed background work.
- Design for cancellation; use timeouts on blocking calls.
- Keep shared state minimal; prefer immutability and thread confinement.
- Use synchronization primitives sparingly and correctly; avoid deadlocks.
- Enable StrictMode in debug; monitor ANRs and jank via system tools.
- Separate concerns: background work (data layer) vs UI thread (presentation).

---

This Java multithreading primer focuses on safe, practical patterns to keep apps responsive and reliable. Pair it with the Performance and Networking docs for end-to-end smoothness.
