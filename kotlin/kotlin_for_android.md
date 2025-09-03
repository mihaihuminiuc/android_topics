# Kotlin for Android (Kotlin)

This guide covers idiomatic Kotlin patterns and features most useful for Android apps: Coroutines, Extension functions/properties, Sealed classes, delegation, Flow/StateFlow, and practical examples tailored to Android development.

---

## Table of contents

- Coroutines (suspend, scopes, dispatchers)
- Flow & StateFlow
- Extensions (functions & properties)
- Sealed classes and result types
- Delegation & property delegates
- Reified generics & inline functions
- Null-safety and common idioms
- Interop tips and Android KTX
- Testing coroutines
- Best practices & common pitfalls

---

## Coroutines (Kotlinx.coroutines)

Coroutines provide lightweight concurrency and structured concurrency for asynchronous code.

Key concepts:
- suspend functions: functions that can suspend without blocking a thread.
- CoroutineScope: defines lifecycle of coroutines. Use lifecycleScope and viewModelScope on Android.
- Dispatchers: Dispatchers.Main (UI), Dispatchers.IO (network/disk), Dispatchers.Default (CPU).
- Structured concurrency: use coroutineScope / supervisorScope to manage child coroutines.

Simple suspend example:

```kotlin
suspend fun fetchItems(api: ApiService, page: Int): List<Item> =
    withContext(Dispatchers.IO) {
        api.getItems(page)
    }
```

ViewModel usage:

```kotlin
class ItemsViewModel(private val repo: ItemsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                val items = repo.fetch()
                _state.value = UiState.Success(items)
            } catch (e: Exception) {
                _state.value = UiState.Error(e)
            }
        }
    }
}
```

Cancellation & exceptions:
- Cancellation is cooperative: coroutines cancel at suspension points.
- Use try/catch for expected errors; CancellationException is rethrown automatically and should not be swallowed.
- Use SupervisorJob when you need child coroutines to fail independently.

---

## Flow & StateFlow

Flow is a cold asynchronous stream. StateFlow is a hot, state-holder flow that always has a value — great for UI state.

Basic Flow example:

```kotlin
fun searchResults(queryFlow: Flow<String>): Flow<List<Result>> =
    queryFlow
        .debounce(300)
        .filter { it.length >= 2 }
        .distinctUntilChanged()
        .flatMapLatest { query -> repository.search(query) }
```

StateFlow in ViewModel:

```kotlin
class Vm(private val repo: Repo) : ViewModel() {
    private val _query = MutableStateFlow("")
    val results: StateFlow<List<Item>> = _query
        .debounce(300)
        .flatMapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setQuery(q: String) { _query.value = q }
}
```

Collecting in UI (Compose/Jetpack):
- Compose: collectAsState()
- Lifecycle-backed UIs: use lifecycleScope + repeatOnLifecycle to collect flows safely.

---

## Extension functions & properties

Extensions add functionality to existing classes without inheritance.

Examples useful on Android:

```kotlin
// Simple context extension for toasts
fun Context.toast(msg: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, msg, duration).show()
}

// View extension property for visibility
var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) { visibility = if (value) View.VISIBLE else View.GONE }
```

Notes:
- Extensions do not actually modify the class; they are compiled as static functions. They do not have access to private members.
- Use extension functions to keep code concise and readable.

---

## Sealed classes (closed hierarchies)

Sealed classes model restricted hierarchies and are great for representing UI state or events.

Example UI state:

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: Throwable) : UiState<Nothing>()
}

// usage
when (val s = uiState) {
    is UiState.Loading -> // show loader
    is UiState.Success -> // show data
    is UiState.Error -> // show error
}
```

Sealed interfaces and sealed classes allow exhaustive when expressions, improving safety.

---

## Delegation & property delegates

Common delegates in Android:

- lazy: lazy initialization
- Delegates.observable / vetoable
- by viewModels() / by activityViewModels() for ViewModel delegation in Activities/Fragments

Examples:

```kotlin
val prefs by lazy { context.getSharedPreferences("app", Context.MODE_PRIVATE) }

var name: String by Delegates.observable("") { _, old, new ->
    // react to changes
}
```

Custom delegate example (arguments bundle delegate in Fragment):

```kotlin
class ArgDelegate<T>(private val key: String) {
    operator fun getValue(fragment: Fragment, prop: KProperty<*>): T =
        fragment.arguments?.get(key) as T
}

class MyFragment : Fragment() {
    private val userId: Int by ArgDelegate("userId")
}
```

---

## Reified generics & inline functions

Use inline + reified to access generic type at runtime (useful for JSON parsing, reflection-like utilities).

```kotlin
inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson(json, T::class.java)
```

Inline functions also allow non-local returns and performance benefits by avoiding function object allocations.

---

## Null-safety & common idioms

- Use nullable types (String?) and safe-call operator (?.) and Elvis operator (?:) to avoid NPEs.
- Prefer let, run, apply, also for scoped operations.

Examples:

```kotlin
val length = name?.length ?: 0

intent.getStringExtra("key")?.let { value ->
    // use value
}
```

---

## Android KTX & Interop

- Android KTX libraries (core-ktx, activity-ktx, fragment-ktx) add useful Kotlin extension APIs for Android.
- Avoid old Kotlin Android synthetics (deprecated). Prefer ViewBinding or Compose.

Examples from KTX:
- context.getColorCompat(R.color.myColor) (via ContextCompat though KTX simplifies patterns)
- fragment.requireActivity() / requireContext() convenience methods

---

## Testing coroutines

Use kotlinx-coroutines-test for deterministic coroutine tests.

Example with runTest:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@Test
fun viewModelLoadsData() = runTest {
    val repo = FakeRepo()
    val vm = MyViewModel(repo)

    vm.load()

    assertThat(vm.state.value).isInstanceOf(UiState.Success::class.java)
}
```

When testing code that uses Dispatchers.Main, set a TestDispatcher via Dispatchers.setMain and reset it after tests.

---

## Best practices & pitfalls

- Prefer structured concurrency: avoid GlobalScope. Use viewModelScope or lifecycleScope.
- Keep suspend, non-blocking code: don’t block threads inside coroutines.
- Use StateFlow for UI state in ViewModels and expose it as immutable flow.
- Don’t expose mutable state types (MutableStateFlow) publicly; provide read-only interfaces (StateFlow).
- Use inline/reified for type-safe utilities and avoid reflection when possible.
- Use extension functions to encapsulate small utilities and keep code readable.
- Be mindful of memory leaks when launching coroutines that outlive components; tie coroutines to lifecycle scopes.

---

Kotlin provides concise, expressive tools that map well to Android app architecture. Using coroutines, Flow/StateFlow, and idiomatic Kotlin features (extensions, sealed classes, delegates) leads to clearer, safer, and more testable code.
