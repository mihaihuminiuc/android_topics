# Android Architecture Components (Java)

A practical guide to using Architecture Components in Java Android apps. Includes Lifecycle, ViewModel, LiveData, Room, Paging 3, Navigation, WorkManager, DataStore (RxJava), Hilt DI, repositories, and testing tips—with Java code samples.

---

## Table of contents

- Lifecycle & LifecycleOwner
- ViewModel & SavedStateHandle
- LiveData (and Flow/Rx interop notes)
- Room (local persistence)
- Paging (Paging 3)
- Navigation Component (NavHost, Safe Args)
- WorkManager
- DataStore (Preferences, RxJava3)
- Dependency Injection (Hilt)
- Repository pattern & single source of truth
- Testing & best practices

---

## Lifecycle & LifecycleOwner

Activities/Fragments implement LifecycleOwner. Prefer lifecycle-aware observers to avoid leaks.

Why it matters (real-world):
- Prevents memory leaks and crashes by scoping work to visible/started UI. Example: start camera preview in onStart, stop in onStop.
- Avoids wasted work when UI isn’t visible. Example: pause network polling when user leaves the screen.
- Enables lifecycle-aware components (LiveData, coroutines with repeatOnLifecycle) to auto-clean up.

Common pitfalls:
- Subscribing in onCreate and forgetting to unsubscribe. Use getViewLifecycleOwner() in Fragments.
- Holding long-lived references to Views/Contexts beyond onDestroyView. Prefer Application context for singletons.

DefaultLifecycleObserver (Java):

```java
public class LoggerObserver implements DefaultLifecycleObserver {
  @Override public void onStart(@NonNull LifecycleOwner owner) { Log.d("LC", "onStart"); }
  @Override public void onStop(@NonNull LifecycleOwner owner) { Log.d("LC", "onStop"); }
}

public class MainActivity extends AppCompatActivity {
  private final LoggerObserver observer = new LoggerObserver();
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getLifecycle().addObserver(observer);
  }
}
```

Observe LiveData safely in Fragments:

```java
public class MyFragment extends Fragment {
  private MyViewModel vm;
  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    vm = new ViewModelProvider(this).get(MyViewModel.class);
    vm.getCount().observe(getViewLifecycleOwner(), count -> {
      // update UI
    });
  }
}
```

---

## ViewModel & SavedStateHandle

ViewModel holds UI data across configuration changes. SavedStateHandle persists small state across process death.

Why it matters (real-world):
- Keeps UI state across rotation and configuration changes without manual Bundle plumbing. Example: results of a search query remain after rotate.
- Avoids re-fetching data on every recreation; cache in ViewModel and expose via LiveData.
- SavedStateHandle restores small critical state (IDs, filters) after process death; pairs well with process death testing.

Common pitfalls:
- Don’t keep references to Activity/Fragment/View in ViewModel. Pass only data; use Application context if needed via AndroidViewModel or injected @ApplicationContext.
- Use SavedStateHandle for small values; use Room/DataStore for larger persistence.

```java
public class CounterViewModel extends ViewModel {
  private static final String KEY = "count";
  private final SavedStateHandle state;
  private final MutableLiveData<Integer> _count;

  @Inject // if using Hilt; otherwise remove
  public CounterViewModel(SavedStateHandle state) {
    this.state = state;
    Integer initial = state.get(KEY);
    _count = new MutableLiveData<>(initial != null ? initial : 0);
  }

  public LiveData<Integer> getCount() { return _count; }

  public void increment() {
    int newVal = (_count.getValue() == null ? 0 : _count.getValue()) + 1;
    _count.setValue(newVal);
    state.set(KEY, newVal);
  }
}
```

Use from Activity/Fragment:

```java
CounterViewModel vm = new ViewModelProvider(this).get(CounterViewModel.class);
vm.getCount().observe(this, val -> {/* update */});
vm.increment();
```

---

## LiveData (and Flow/Rx interop notes)

- LiveData is lifecycle-aware and works well in Java.
- Kotlin Flow is Kotlin-first; in Java prefer LiveData or RxJava.
- Interop: use LiveDataReactiveStreams or the DataStore RxJava artifact for reactive streams in Java.

Why it matters (real-world):
- UI updates occur only when the UI is active (STARTED/RESUMED), reducing boilerplate and avoiding NPEs.
- Easy to expose from Room DAOs and ViewModels; integrates into XML data binding.

Patterns and tips:
- One-shot events (toasts, navigation): avoid plain LiveData; use SingleLiveEvent pattern or expose an Event wrapper, or prefer RxJava/Channel when appropriate.
- Map/transform: use MediatorLiveData or Transformations.map/switchMap to derive UI-ready data.

LiveDataReactiveStreams example:

```java
LiveData<MyType> live = LiveDataReactiveStreams.fromPublisher(publisher);
Flowable<MyType> flowable = LiveDataReactiveStreams.toPublisher(lifecycleOwner, live);
```

---

## Room (local persistence)

Entities, DAOs, and Database in Java.

Why it matters (real-world):
- Provides a robust local cache for offline-first apps (feeds, inbox, product catalogs). Reactive queries keep UI in sync.
- Compile-time SQL verification prevents runtime errors; migrations keep user data safe across app versions.

Best practices:
- Run queries off the main thread; Room enforces this by default. Use suspend/Rx/Executors.
- Return LiveData/Flow/PagingSource from DAOs for reactive UI updates.
- Plan migrations early; write Migration tests to prevent data loss.

Entity:

```java
@Entity(tableName = "items")
public class ItemEntity {
  @PrimaryKey public long id;
  @NonNull public String name;
  public long createdAt;
}
```

DAO returning LiveData and PagingSource:

```java
@Dao
public interface ItemDao {
  @Query("SELECT * FROM items ORDER BY createdAt DESC")
  LiveData<List<ItemEntity>> observeAll();

  @Query("SELECT * FROM items ORDER BY createdAt DESC")
  PagingSource<Integer, ItemEntity> pagingSource();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insertAll(List<ItemEntity> items);
}
```

Database:

```java
@Database(entities = { ItemEntity.class }, version = 1)
public abstract class AppDatabase extends RoomDatabase {
  public abstract ItemDao itemDao();
}
```

Build database:

```java
AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, "app.db").build();
```

---

## Paging (Paging 3)

Paging 3 works with LiveData/RxJava in Java.

Why it matters (real-world):
- Efficiently renders infinite/long lists (social feeds, search results) with minimal memory and network usage.
- Load states (loading/error/refresh) integrate with UI to show spinners and retry.

Best practices:
- Use cachedIn(viewModelScope) (Kotlin) / getLiveData() (Java) to keep paging stream alive across rotations.
- Handle separators and UI transforms with PagingData transforms.
- Combine Room + RemoteMediator for offline-first and bidirectional sync.

ViewModel exposing LiveData<PagingData<ItemEntity>>:

```java
public class ItemsViewModel extends ViewModel {
  public final LiveData<PagingData<ItemEntity>> pagingData;

  @Inject // optional with Hilt
  public ItemsViewModel(ItemDao dao) {
    Pager<Integer, ItemEntity> pager = new Pager<>(
      new PagingConfig(20),
      () -> dao.pagingSource()
    );
    pagingData = pager.getLiveData();
  }
}
```

Fragment usage with PagingDataAdapter:

```java
ItemsPagingAdapter adapter = new ItemsPagingAdapter();
recyclerView.setAdapter(adapter);

viewModel.pagingData.observe(getViewLifecycleOwner(), data -> {
  adapter.submitData(getLifecycle(), data);
});
```

Adapter skeleton:

```java
public class ItemsPagingAdapter extends PagingDataAdapter<ItemEntity, ItemsPagingAdapter.VH> {
  public ItemsPagingAdapter() { super(DIFF_CALLBACK); }

  static final DiffUtil.ItemCallback<ItemEntity> DIFF_CALLBACK = new DiffUtil.ItemCallback<ItemEntity>() {
    public boolean areItemsTheSame(@NonNull ItemEntity o, @NonNull ItemEntity n) { return o.id == n.id; }
    public boolean areContentsTheSame(@NonNull ItemEntity o, @NonNull ItemEntity n) { return o.equals(n); }
  };

  static class VH extends RecyclerView.ViewHolder { TextView title; VH(View v){ super(v); title = v.findViewById(R.id.title);} }

  @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
    View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false); return new VH(v);
  }
  @Override public void onBindViewHolder(@NonNull VH h, int pos) {
    ItemEntity item = getItem(pos); if (item != null) h.title.setText(item.name);
  }
}
```

---

## Navigation Component (NavHost, Safe Args)

Activity layout container:

```xml
<androidx.fragment.app.FragmentContainerView
  android:id="@+id/nav_host"
  android:name="androidx.navigation.fragment.NavHostFragment"
  app:navGraph="@navigation/nav_graph"
  app:defaultNavHost="true"
  android:layout_width="match_parent"
  android:layout_height="match_parent"/>
```

Navigate with Safe Args (Java):

```java
// From FirstFragment to SecondFragment with an argument
FirstFragmentDirections.ActionFirstToSecond action =
    FirstFragmentDirections.actionFirstToSecond(42);
NavHostFragment.findNavController(this).navigate(action);
```

Receive args in SecondFragment:

```java
int itemId = SecondFragmentArgs.fromBundle(getArguments()).getItemId();
```

Deep links and back stack are handled by the library.

Why it matters (real-world):
- Centralizes navigation logic, simplifies back stack handling, and enables deep links from notifications or web URLs.
- Safe Args generates type-safe argument classes, reducing runtime Bundle key errors.

Tips:
- Use a single-activity architecture with a NavHost and fragments for most apps.
- For multiple bottom navigation stacks, use separate NavHostFragments or the Navigation UI helpers.

---

## WorkManager

Use Worker for background work that should be deferrable and guaranteed.

Why it matters (real-world):
- Guarantees execution even if the app/process is killed or device restarts (e.g., upload logs, sync, backup).
- Supports constraints (Wi‑Fi only, charging) and backoff/retries.

When to use vs Foreground Service:
- WorkManager: deferred, guaranteed background tasks; not user-immediate.
- Foreground Service: user-visible ongoing work (music, location) with a persistent notification.

Worker with progress and output data:

```java
public class FileDownloadWorker extends Worker {
  public static final String KEY_URL = "url";
  public static final String KEY_PROGRESS = "progress";
  public static final String KEY_OUTPUT = "output_path";
  public static final String KEY_ERROR = "error";

  public FileDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  @NonNull @Override public Result doWork() {
    String url = getInputData().getString(KEY_URL);
    if (url == null) return Result.failure(new Data.Builder().putString(KEY_ERROR, "Missing URL").build());
    // ... perform download, periodically call setProgressAsync(new Data.Builder().putInt(KEY_PROGRESS, p).build());
    // On success:
    return Result.success(new Data.Builder().putString(KEY_OUTPUT, "/path/to/file").build());
  }
}
```

Enqueue and observe in Activity:

```java
WorkRequest req = new OneTimeWorkRequest.Builder(FileDownloadWorker.class)
  .setInputData(new Data.Builder().putString(FileDownloadWorker.KEY_URL, "https://...").build())
  .build();
WorkManager wm = WorkManager.getInstance(context);
wm.enqueue(req);
wm.getWorkInfoByIdLiveData(req.getId()).observe(this, info -> {
  if (info == null) return;
  if (info.getState() == WorkInfo.State.RUNNING) {
    int p = info.getProgress().getInt(FileDownloadWorker.KEY_PROGRESS, 0);
    // update progress UI
  } else if (info.getState() == WorkInfo.State.SUCCEEDED) {
    String path = info.getOutputData().getString(FileDownloadWorker.KEY_OUTPUT);
    // show success
  } else if (info.getState() == WorkInfo.State.FAILED) {
    String err = info.getOutputData().getString(FileDownloadWorker.KEY_ERROR);
    // show error
  }
});
```

---

## DataStore (Preferences, RxJava3)

Use the RxJava artifact for Java apps: `androidx.datastore:datastore-preferences-rxjava3`.

Why it matters (real-world):
- Replaces SharedPreferences with a transactional, coroutine/Rx-friendly API that avoids ANRs and supports data consistency.
- Great for small user settings (theme, flags, last sync time) with reactive updates to UI.

Notes:
- Not for large/relational data—use Room. Keep keys/schema versioned to allow future migrations.

Keys and create DataStore:

```java
public class SettingsRepo {
  private final RxDataStore<Preferences> dataStore;
  private static final Preferences.Key<String> THEME_KEY = stringPreferencesKey("theme");

  public SettingsRepo(Context context) {
    dataStore = new RxPreferenceDataStoreBuilder(context, "settings").build();
  }

  public Completable saveTheme(String theme) {
    return dataStore.updateDataAsync(prefsIn -> Single.fromCallable(() -> {
      MutablePreferences prefs = prefsIn.toMutablePreferences();
      prefs.set(THEME_KEY, theme);
      return prefs;
    }));
  }

  public Flowable<String> observeTheme() {
    return dataStore.data().map(prefs -> {
      String v = prefs.get(THEME_KEY);
      return v != null ? v : "light";
    });
  }
}
```

---

## Dependency Injection (Hilt)

Application and Activity setup:

```java
@HiltAndroidApp
public class MyApp extends Application {}

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
  @Inject Repo repo; // field injection
}
```

ViewModel with Hilt:

```java
@HiltViewModel
public class MyViewModel extends ViewModel {
  private final Repo repo;
  @Inject public MyViewModel(Repo repo) { this.repo = repo; }
}
```

Module providing dependencies:

```java
@Module
@InstallIn(SingletonComponent.class)
public abstract class AppModule {
  @Binds public abstract Repo bindRepo(DefaultRepo impl);

  @Provides @Singleton
  public static AppDatabase provideDb(@ApplicationContext Context ctx) {
    return Room.databaseBuilder(ctx, AppDatabase.class, "app.db").build();
  }
}
```

Why it matters (real-world):
- Decouples construction from usage, improving testability and modularity. Swap real implementations with fakes in tests.
- Provides clear scoping (Activity/Fragment/ViewModel/Singleton), avoiding leaks and lifecycle issues.

Pitfalls:
- Scope mismatches cause leaks or crashes (e.g., injecting Activity-scoped object into Singleton). Choose scopes intentionally.
- Avoid over-injecting Context; prefer @ApplicationContext where possible.

---

## Repository pattern & single source of truth

Combine network + Room; expose LiveData to UI.

Why it matters (real-world):
- Centralizes data orchestration and conflict resolution. UI stays simple and observes a single stream of truth.
- Enables offline-first: read from DB, refresh from network, update DB, UI updates automatically.

Tips:
- Keep repository APIs UI-agnostic (no Android types); return LiveData/Rx streams or domain models.
- Handle errors consistently (Result/Either) and surface retry semantics to ViewModel.

```java
public class ItemsRepository {
  private final ItemDao dao; private final Api api; private final Executor io;
  public ItemsRepository(ItemDao dao, Api api, Executor io) { this.dao = dao; this.api = api; this.io = io; }

  public LiveData<List<ItemEntity>> observeAll() { return dao.observeAll(); }

  public LiveData<Result<Unit>> refresh() {
    MutableLiveData<Result<Unit>> result = new MutableLiveData<>();
    io.execute(() -> {
      try {
        List<Item> remote = api.getItems();
        List<ItemEntity> toSave = map(remote);
        dao.insertAll(toSave);
        result.postValue(Result.success(null));
      } catch (Exception e) {
        result.postValue(Result.failure(e));
      }
    });
    return result;
  }
}
```

---

## Testing & best practices

- Keep UI-free logic in ViewModels/Repositories; unit test with JUnit and Robolectric (if needed).
- Use InstantTaskExecutorRule for LiveData tests.
- Use coroutines or Executors consistently; avoid blocking the main thread.
- Prefer LiveData in Java; use RxJava for streams when needed.
- Use WorkManager for deferrable background tasks; Foreground Service for ongoing user-visible work.
- Scope shared ViewModels to NavGraph when using Navigation.

Why it matters (real-world):
- Reliable tests prevent regressions in async flows, DB migrations, and background work.

Testing patterns:
- LiveData: use InstantTaskExecutorRule and observeForTesting helpers.
- Room: use in-memory database for DAO tests and seed data for migrations.
- WorkManager: use WorkManagerTestInitHelper and TestDriver to control constraints and time.


---

This Java-focused primer mirrors the Kotlin guide with idiomatic Java usage and APIs. Consult AndroidX docs for deeper details and latest versions.
