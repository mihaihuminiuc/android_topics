# Android Architecture Components (Kotlin)

This document covers modern Android Architecture Components with practical Kotlin examples and best practices. Topics include Lifecycle, ViewModel, LiveData, StateFlow/Flow, Navigation, Room, Paging (Paging 3), WorkManager, DataStore, and dependency injection (Hilt).

---

## Table of contents

- Lifecycle & LifecycleOwner
- ViewModel & SavedStateHandle
- LiveData vs Flow/StateFlow
- Room (local persistence)
- Paging (Paging 3)
- Navigation Component (NavHost, Safe Args)
- WorkManager
- DataStore
- Dependency Injection (Hilt, brief)
- Repository pattern & single source of truth
- Testing & best practices

---

## Lifecycle & LifecycleOwner

- Architecture Components are lifecycle-aware: Activities and Fragments implement LifecycleOwner.
- Use lifecycle-aware APIs (LiveData, lifecycleScope, repeatOnLifecycle) to avoid leaks and race conditions.

Example: collecting a Flow safely in a Fragment:

```kotlin
lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            // update UI
        }
    }
}
```

Use DefaultLifecycleObserver for lifecycle callbacks instead of deprecated LifecycleObserver.

---

## ViewModel & SavedStateHandle

What it is:
- ViewModel stores and manages UI-related data for lifecycle owners. It survives configuration changes (rotation).
- SavedStateHandle allows ViewModel to persist small pieces of state across process death.

Example ViewModel with LiveData and SavedStateHandle:

```kotlin
class CounterViewModel(private val state: SavedStateHandle) : ViewModel() {
    private val _count = MutableLiveData(state.get("count") ?: 0)
    val count: LiveData<Int> = _count

    fun increment() {
        val new = (count.value ?: 0) + 1
        _count.value = new
        state.set("count", new)
    }
}
```

Example ViewModel with StateFlow (preferred for Kotlin Coroutines):

```kotlin
data class UiState(val loading: Boolean = false, val items: List<String> = emptyList())

class MainViewModel(private val repo: Repo) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val result = repo.fetch()
            _uiState.update { it.copy(loading = false, items = result) }
        }
    }
}
```

Share data between fragments in a single-activity architecture using activityViewModels() or a shared NavGraph ViewModel.

---

## LiveData vs Flow / StateFlow

- LiveData: lifecycle-aware observable that works well with XML binding and the Android lifecycle.
- Kotlin Flow / StateFlow: more powerful, integrates with coroutines, recommended for new code.
- When in UI layer, convert Flow to lifecycle-aware collection using repeatOnLifecycle or asLiveData() if needed.

Observe LiveData in Fragment safely:

```kotlin
viewModel.count.observe(viewLifecycleOwner) { value ->
    binding.countText.text = value.toString()
}
```

Collect Flow safely:

```kotlin
lifecycleScope.launchWhenStarted {
    viewModel.events.collect { event ->
        // handle
    }
}
```

---

## Room (local persistence)

- Room is the recommended SQLite abstraction. Use entities, DAOs, and the Database class.
- DAOs can return Flow or PagingSource for reactive queries.

Entity + DAO example:

```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val createdAt: Long
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun itemsFlow(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun pagingSource(): PagingSource<Int, ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)
}

@Database(entities = [ItemEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
```

---

## Paging (Paging 3)

- Paging 3 integrates with Kotlin Flow and Jetpack components to load data progressively.
- Use PagingSource for database/network paging, Pager to create a Flow<PagingData<T>>, and PagingDataAdapter to display data.

ViewModel providing paging stream:

```kotlin
class ItemsViewModel(private val dao: ItemDao) : ViewModel() {
    val pagerFlow = Pager(PagingConfig(pageSize = 20)) {
        dao.pagingSource()
    }.flow.cachedIn(viewModelScope)
}
```

Adapter usage in Fragment:

```kotlin
val adapter = ItemsPagingAdapter()
binding.recyclerView.adapter = adapter

lifecycleScope.launchWhenStarted {
    viewModel.pagerFlow.collectLatest { pagingData ->
        adapter.submitData(pagingData)
    }
}
```

PagingSource (network-based) example skeleton:

```kotlin
class RemotePagingSource(private val api: Api) : PagingSource<Int, Item>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {
        val page = params.key ?: 1
        return try {
            val response = api.getItems(page, params.loadSize)
            LoadResult.Page(
                data = response.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.items.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Item>): Int? = null
}
```

Consider RemoteMediator for combined local cache + network strategies.

---

## Navigation Component

- The Navigation component simplifies navigation between destinations (fragments/activities) and handles back stack, deep links, and type-safe arguments via Safe Args.
- Use a NavHostFragment in your Activity layout as the navigation container.

activity_main.xml sketch:

```xml
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/nav_host"
    android:name="androidx.navigation.fragment.NavHostFragment"
    app:navGraph="@navigation/nav_graph"
    app:defaultNavHost="true"
    ... />
```

Navigating with Safe Args (Kotlin):

```kotlin
val action = FirstFragmentDirections.actionFirstToSecond(itemId = 42)
findNavController().navigate(action)
```

Shared ViewModel pattern with Navigation: scope a ViewModel to a navigation graph to share data among fragments in that graph.

---

## WorkManager

- WorkManager is the recommended API for deferrable, guaranteed background work.
- Use CoroutineWorker for coroutine-based work.

CoroutineWorker example:

```kotlin
class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // perform network upload
        return Result.success()
    }
}

// enqueue
val work = OneTimeWorkRequestBuilder<UploadWorker>().build()
WorkManager.getInstance(context).enqueue(work)
```

---

## DataStore (Preferences & Proto)

- DataStore replaces SharedPreferences with a Kotlin Flow-based, type-safe API.

Preferences DataStore example:

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
val THEME_KEY = stringPreferencesKey("theme")

suspend fun saveTheme(context: Context, theme: String) {
    context.dataStore.edit { prefs -> prefs[THEME_KEY] = theme }
}

val themeFlow: Flow<String> = context.dataStore.data
    .map { prefs -> prefs[THEME_KEY] ?: "light" }
```

---

## Dependency Injection (Hilt, brief)

- Hilt simplifies DI on Android and integrates with ViewModel, WorkManager, Room, etc.

Minimal Hilt usage:

```kotlin
@HiltAndroidApp
class MyApp : Application()

@AndroidEntryPoint
class MainActivity : AppCompatActivity()

@HiltViewModel
class MyViewModel @Inject constructor(private val repo: Repo) : ViewModel()
```

Hilt can provide Room Database, Dao, Repo, and Api instances through @Module and @Provides/@Binds.

---

## Repository pattern & single source of truth

- Create repository classes that mediate between local (Room) and remote (network) data sources.
- Repositories expose Flows, LiveData, or PagingData for the UI layer. Keep ViewModel logic thin and testable.

Example repository exposing paging and a Flow:

```kotlin
class ItemsRepository(private val dao: ItemDao, private val api: Api) {
    fun itemsPager() = Pager(PagingConfig(20)) { dao.pagingSource() }.flow

    fun observeAll(): Flow<List<ItemEntity>> = dao.itemsFlow()
}
```

---

## Testing & best practices

- Test ViewModel logic using standard JUnit + coroutines test tools (StandardTestDispatcher, runTest).
- Test Room DAOs using an in-memory database.
- Use instrumented tests for Navigation and UI flows; unit test ViewModel and Repository logic.

Best practices summary:
- Prefer StateFlow/Flow for new code and use repeatOnLifecycle to collect safely.
- Keep UI state in ViewModels; avoid storing UI references in ViewModel.
- Use SavedStateHandle for small persisted UI state.
- Prefer WorkManager for deferrable background tasks and ForegroundService for ongoing user-visible tasks.
- Use Paging 3 with Room or RemoteMediator to implement efficient pagination.
- Use Hilt to inject dependencies and simplify testing.

---

This primer is focused on practical Kotlin examples and modern Jetpack usage. For each component, consult the official AndroidX docs and library guides for deeper API details and migration notes.
