# Multithreading & Concurrency on Android (Kotlin)

This guide explains multithreading options on Android: Kotlin Coroutines (recommended), Executors/Threads, HandlerThread/Looper, WorkManager for background jobs, synchronization primitives, and best practices with Kotlin examples.

---

## Table of contents

- Concurrency basics
- Kotlin Coroutines (structured concurrency)
- Dispatchers (Main, IO, Default)
- Scopes, cancellation, exception handling
- Channels, Actors, and Flows
- Executors, ThreadPoolExecutor, HandlerThread
- WorkManager for deferrable/background work
- Synchronization primitives (Mutex, synchronized, Atomic types)
- Choosing the right tool
- Debugging & testing
- Best practices and pitfalls

---

## Concurrency basics

- Concurrency lets your app perform work in parallel or off the main/UI thread to avoid jank.
- Main/UI thread must remain responsive; perform disk/network/CPU or long-running tasks on background threads.

---

## Kotlin Coroutines (recommended)

Coroutines provide lightweight asynchronous computations and structured concurrency.

Suspend function example:

```kotlin
suspend fun fetchData(repo: Repo): List<Item> = withContext(Dispatchers.IO) {
    repo.getRemoteItems() // suspend API
}
```

ViewModel usage with viewModelScope:

```kotlin
class MyViewModel(private val repo: Repo) : ViewModel() {
    val items = MutableStateFlow<List<Item>>(emptyList())

    fun load() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { repo.fetch() }
            items.value = data
        }
    }
}
```

---

## Dispatchers

- Dispatchers.Main — runs on main thread (UI). Use for UI updates.
- Dispatchers.IO — optimized for blocking I/O (file, network). Backed by a shared thread pool.
- Dispatchers.Default — optimized for CPU-bound work.
- Dispatchers.Unconfined — runs coroutine in caller thread until first suspension; rarely used.

Use `withContext(Dispatchers.IO)` for synchronous blocking calls.

---

## Scopes, cancellation & exception handling

- CoroutineScope defines lifecycle; use lifecycleScope (Activity/Fragment) or viewModelScope.
- Structured concurrency: parent scope controls child coroutines; cancelling parent cancels children.
- Use SupervisorJob when you want independent child failures.

Example supervisor usage:

```kotlin
val supervisor = SupervisorJob()
val scope = CoroutineScope(Dispatchers.Default + supervisor)
```

Exception handling:
- Use CoroutineExceptionHandler for uncaught exceptions in a scope.
- In structured concurrency, exceptions propagate to parent by default.

---

## Channels, Actors & Flows

- Channel: coroutine-based queue for communication between producers and consumers.
- Actor: Coroutine-based single-threaded state holder (actor model).
- Flow: cold async stream for reactive data; use operators like buffer, conflate, flatMapLatest for concurrency control.

Simple actor example (serialize updates):

```kotlin
sealed class UICommand { data class Add(val n: Int): UICommand() }

fun CoroutineScope.counterActor() = actor<UICommand> {
    var counter = 0
    for (msg in channel) {
        when (msg) { is UICommand.Add -> counter += msg.n }
    }
}
```

Flow concurrency operators:
- `buffer()` to decouple producers/consumers
- `conflate()` to drop intermediate values
- `flatMapLatest()` to cancel previous inner flow when new value arrives

---

## Executors, ThreadPoolExecutor, HandlerThread

When interoperating with Java APIs or old code, executors are useful.

ExecutorService example:

```kotlin
val executor: ExecutorService = Executors.newFixedThreadPool(4)
executor.submit {
    // background work
}
executor.shutdown()
```

HandlerThread example (useful for serial background thread with a Looper):

```kotlin
val handlerThread = HandlerThread("db-thread")
handlerThread.start()
val handler = Handler(handlerThread.looper)
handler.post {
    // perform DB work
}
```

---

## WorkManager (deferrable, guaranteed background work)

WorkManager is ideal for tasks that must run even if the app exits or device restarts (sync, uploads).

One-time WorkManager job with constraints:

```kotlin
val work = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()
WorkManager.getInstance(context).enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, work)
```

Coroutine-based worker:

```kotlin
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            repository.sync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

Notes:
- WorkManager schedules work according to constraints and system policies — not guaranteed immediate execution.
- For immediate background processing that must run now, use coroutines/Executors or foreground service.

---

## Synchronization primitives

- `synchronized(obj) { }` — JVM monitor-based lock.
- `Mutex` — coroutine-friendly mutual exclusion from kotlinx.coroutines.sync.
- Atomic types: AtomicInteger, AtomicReference for low-level lock-free operations.

Mutex example:

```kotlin
val mutex = Mutex()

suspend fun safeIncrement(counter: MutableState<Int>) {
    mutex.withLock { counter.value += 1 }
}
```

Prefer coroutine-friendly primitives when working inside coroutines to avoid blocking threads.

---

## Choosing the right tool

- UI updates and short-lived tasks: `Dispatchers.Main` + coroutines in lifecycleScope.
- Blocking I/O (disk/network): `Dispatchers.IO` or dedicated Executor.
- CPU heavy ops: `Dispatchers.Default`.
- Work that must survive process death: `WorkManager` (with constraints and/or foreground service).
- Interop with Java libs expecting Executor: use ExecutorService or provide a coroutine dispatcher via `asExecutor()`.

---

## Debugging & testing

- Use `kotlinx-coroutines-debug` to detect leaked coroutines and debug.
- For tests, control dispatchers via `TestDispatcher` and `Dispatchers.setMain` (kotlinx-coroutines-test).
- Use strict thread assertions in tests (e.g., assert that UI updates happen on Main).

Example test rule for main dispatcher:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description?) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description?) { Dispatchers.resetMain() }
}
```

---

## Best practices & pitfalls

- Do not block the main thread. Use coroutines or background threads for heavy operations.
- Prefer structured concurrency: tie coroutine scopes to lifecycles (viewModelScope, lifecycleScope).
- Avoid `GlobalScope` for most use cases; it creates coroutines that outlive app boundaries.
- Avoid mixing blocking locks with suspending functions: use coroutine-friendly primitives (Mutex).
- Protect shared mutable state: prefer single-writer strategies (actors) or immutable snapshots.
- Cancel coroutines on lifecycle destruction to avoid leaks.
- Use `Dispatchers.IO` for blocking operations; it will expand the pool as needed for blocking tasks.

---

Concurrency is a complex area — prefer high-level constructs (coroutines, flows, WorkManager) over manual thread management. Measure and test concurrency logic thoroughly; ensure thread-safety and lifecycle-awareness in your app.
