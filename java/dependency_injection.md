# Android Dependency Injection (Java)

A practical Java guide to DI on Android. Covers why DI matters, manual DI, Dagger fundamentals, Hilt for Android, scopes, qualifiers, bindings, ViewModel/Activity/Fragment injection, WorkManager integration, networking/database modules, testing with Hilt, and best practices—with concise Java examples and real-life rationale.

---

## Table of contents

- Why DI (and Service Locator vs DI)
- Manual DI (composition root)
- Dagger fundamentals (@Inject, @Module, @Provides, @Component)
- Hilt for Android (setup, @HiltAndroidApp, @AndroidEntryPoint, @InstallIn)
- Injecting into Activities, Fragments, ViewModels
- Qualifiers (@Named, custom @Qualifier)
- Bindings (@Binds vs @Provides) and multibindings
- Scoping & lifetimes (Singleton, ActivityRetained, ViewModel)
- Assisted injection (runtime params) and WorkManager
- Networking and database modules (Retrofit/OkHttp/Room)
- Testing with Hilt (@HiltAndroidTest, @UninstallModules)
- Migration notes (Dagger → Hilt) and when to keep plain Dagger
- Best practices checklist

---

## Why DI (and Service Locator vs DI)

Why it matters:
- DI decouples construction from usage, improving testability and reuse. You can swap real dependencies for fakes in tests without changing production code.

Real-life:
- Replace a network API with a MockWebServer-backed fake in UI tests; inject an in-memory Room DB in instrumented tests.

Notes:
- Service Locator hides dependencies and can encourage global state. Prefer DI with explicit constructor parameters.

---

## Manual DI (composition root)

Why it matters:
- Simple apps don’t need a framework. A composition root creates and wires objects in one place (e.g., Application) and passes them down.

Example:

```java
// Composition root in Application
public class App extends Application {
  private Api api; private Repository repo;
  @Override public void onCreate() {
    super.onCreate();
    api = new Api(new OkHttpClient());
    repo = new Repository(api);
  }
  public Repository repo() { return repo; }
}

// Use in Activity
public class MainActivity extends AppCompatActivity {
  private Repository repo;
  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    setContentView(R.layout.activity_main);
    repo = ((App) getApplication()).repo();
  }
}
```

Real-life:
- Great for small apps or prototyping. As complexity grows, moving to Hilt simplifies lifecycle scoping and testing.

---

## Dagger fundamentals (@Inject, @Module, @Provides, @Component)

Why it matters:
- Dagger generates factories and graphs at compile-time—fast DI with clear scoping.

Example:

```java
// Dependency and usage
class Api { final OkHttpClient client; @Inject Api(OkHttpClient client){ this.client = client; } }
class Repository { final Api api; @Inject Repository(Api api){ this.api = api; } }

@Module
class AppModule {
  @Provides @Singleton OkHttpClient provideClient(){ return new OkHttpClient.Builder().build(); }
}

@Singleton
@Component(modules = {AppModule.class})
interface AppComponent {
  void inject(MainActivity activity);
}

public class App extends Application {
  public static AppComponent appComponent;
  @Override public void onCreate() {
    super.onCreate();
    appComponent = DaggerAppComponent.create();
  }
}

public class MainActivity extends AppCompatActivity {
  @Inject Repository repo;
  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    App.appComponent.inject(this);
  }
}
```

Real-life:
- Predictable, performant DI for large apps; more boilerplate on Android compared to Hilt’s conveniences.

---

## Hilt for Android (setup, @HiltAndroidApp, @AndroidEntryPoint, @InstallIn)

Why it matters:
- Hilt is Dagger on Android with batteries included (generated components, Android class injection, testing support).

Setup sketch (Gradle):

```gradle
plugins { id 'com.google.dagger.hilt.android' }
dependencies {
  implementation "com.google.dagger:hilt-android:2.51.1"
  annotationProcessor "com.google.dagger:hilt-android-compiler:2.51.1"
}
```

Basic wiring:

```java
@HiltAndroidApp
public class App extends Application {}

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {
  @Provides @Singleton OkHttpClient provideClient(){ return new OkHttpClient(); }
  @Provides @Singleton Retrofit provideRetrofit(OkHttpClient c){
    return new Retrofit.Builder().baseUrl("https://api.example.com/")
      .client(c).addConverterFactory(GsonConverterFactory.create()).build();
  }
}

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
  @Inject Retrofit retrofit;
}
```

Real-life:
- Inject Activities, Fragments, Services, BroadcastReceivers, ViewModels, and Workers with minimal glue code.

---

## Injecting into Activities, Fragments, ViewModels

Why it matters:
- UI classes get dependencies without manual plumbing; ViewModels hold business logic while being testable.

Activity/Fragment:

```java
@AndroidEntryPoint
public class DetailFragment extends Fragment {
  @Inject Repository repo; /* use in onViewCreated */
}
```

ViewModel (Hilt):

```java
@HiltViewModel
public class UserViewModel extends ViewModel {
  private final Repository repo;
  @Inject public UserViewModel(Repository repo) { this.repo = repo; }
  public LiveData<User> load(long id){ MutableLiveData<User> d = new MutableLiveData<>(); /* fetch */ return d; }
}

@AndroidEntryPoint
public class UserActivity extends AppCompatActivity {
  private UserViewModel vm;
  @Override protected void onCreate(Bundle saved){
    super.onCreate(saved);
    setContentView(R.layout.activity_user);
    vm = new ViewModelProvider(this).get(UserViewModel.class);
  }
}
```

Real-life:
- Business logic lives in ViewModels that receive repositories/UseCases via DI, simplifying tests.

---

## Qualifiers (@Named, custom @Qualifier)

Why it matters:
- Disambiguate multiple bindings of the same type (e.g., two Retrofit instances).

Examples:

```java
@Qualifier @Retention(RetentionPolicy.RUNTIME) @interface Auth {}

@Module @InstallIn(SingletonComponent.class)
class ApiModule {
  @Provides @Singleton @Named("public") Retrofit publicApi(OkHttpClient c){ /* ... */ return retrofit; }
  @Provides @Singleton @Auth Retrofit authApi(OkHttpClient c){ /* ... */ return retrofit; }
}

public class Repo {
  private final Retrofit publicApi; private final Retrofit authApi;
  @Inject public Repo(@Named("public") Retrofit publicApi, @Auth Retrofit authApi){
    this.publicApi = publicApi; this.authApi = authApi;
  }
}
```

Real-life:
- Separate unauthenticated vs authenticated clients; split logging-only clients from pinned-security clients.

---

## Bindings (@Binds vs @Provides) and multibindings

Why it matters:
- @Binds ties an interface to an implementation without a factory method body; multibindings build sets/maps of dependencies.

```java
interface Analytics { void log(String event); }
class FirebaseAnalyticsImpl implements Analytics { /* ... */ }

@Module @InstallIn(SingletonComponent.class)
abstract class AnalyticsModule {
  @Binds @Singleton abstract Analytics bindAnalytics(FirebaseAnalyticsImpl impl);
}
```

Multibindings:

```java
@Module @InstallIn(SingletonComponent.class)
abstract class HandlersModule {
  @Binds @IntoSet abstract Handler bindSyncHandler(SyncHandler impl);
  @Binds @IntoSet abstract Handler bindUploadHandler(UploadHandler impl);
}
```

Real-life:
- Swap concrete implementations in tests; register multiple handlers/plugins without manual lists.

---

## Scoping & lifetimes (Singleton, ActivityRetained, ViewModel)

Why it matters:
- Right lifetimes prevent leaks and recreate cost. Hilt offers Android-tailored components.

Common scopes:
- @Singleton (SingletonComponent): app-wide singletons (Retrofit, Room, Repos).
- @ActivityRetainedScoped: survives configuration changes; ideal for UseCases used by ViewModels.
- @ActivityScoped / @FragmentScoped: per-UI lifecycle objects.
- @ViewModelScoped (in ViewModelComponent): one instance per ViewModel.

Example:

```java
@Module @InstallIn(ViewModelComponent.class)
class UseCaseModule {
  @Provides @ViewModelScoped UseCase provideUseCase(Repository repo){ return new UseCase(repo); }
}
```

Real-life:
- Avoid re-creating heavy clients; keep per-screen stateful helpers correctly scoped.

---

## Assisted injection (runtime params) and WorkManager

Why it matters:
- Some classes need runtime parameters (IDs, URLs) that DI can’t provide at compile-time.

WorkManager + Hilt:

```java
@HiltWorker
public class UploadWorker extends Worker {
  private final Uploader uploader;
  @Inject public UploadWorker(@Assisted @NonNull Context ctx,
                              @Assisted @NonNull WorkerParameters params,
                              Uploader uploader) {
    super(ctx, params); this.uploader = uploader;
  }
  @NonNull @Override public Result doWork(){ uploader.upload(); return Result.success(); }
}
```

Hilt sets up the WorkerFactory; just add Hilt dependency and annotate Application with @HiltAndroidApp.

Real-life:
- Inject uploaders or repositories into Workers while still receiving runtime input Data.

---

## Networking and database modules (Retrofit/OkHttp/Room)

Why it matters:
- Centralize client configuration and reuse singletons.

```java
@Module @InstallIn(SingletonComponent.class)
public class DataModule {
  @Provides @Singleton OkHttpClient okHttp(){
    return new OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build();
  }
  @Provides @Singleton Retrofit retrofit(OkHttpClient c){
    return new Retrofit.Builder().baseUrl("https://api.example.com/")
      .client(c).addConverterFactory(GsonConverterFactory.create()).build();
  }
  @Provides @Singleton AppDatabase db(@ApplicationContext Context context){
    return Room.databaseBuilder(context, AppDatabase.class, "app.db").build();
  }
  @Provides @Singleton UserDao userDao(AppDatabase db){ return db.userDao(); }
}
```

Real-life:
- Consistent TLS/interceptors and DB singletons shared across repositories.

---

## Testing with Hilt (@HiltAndroidTest, @UninstallModules)

Why it matters:
- Replace production modules with fakes for deterministic tests.

```java
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@UninstallModules({DataModule.class})
public class RepoTest {
  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Module @InstallIn(SingletonComponent.class)
  public static class FakeDataModule {
    @Provides @Singleton Retrofit retrofit(){
      return new Retrofit.Builder().baseUrl("http://localhost/")
        .addConverterFactory(GsonConverterFactory.create()).build();
    }
  }

  @Test public void usesFakeRetrofit() {
    hiltRule.inject();
    // launch scenario and assert using fake backend
  }
}
```

Real-life:
- Speed up tests, remove network flakiness, and validate integration boundaries.

---

## Migration notes (Dagger → Hilt) and when to keep plain Dagger

Why it matters:
- Hilt reduces boilerplate, but some libraries/apps rely on custom Dagger setups.

Guidance:
- Keep Dagger if you need custom components/subcomponents or pure JVM usage.
- Adopt Hilt gradually: wrap existing components with @DefineComponent or migrate modules stepwise.

Real-life:
- Large apps often migrate feature-by-feature to Hilt while retaining some legacy Dagger code.

---

## Best practices checklist

- Prefer constructor injection; avoid field injection except in Android entry points.
- Keep modules small and cohesive (network, database, feature-level).
- Use qualifiers to disambiguate same-type bindings; avoid @Named strings for long term, prefer custom @Qualifier.
- Scope correctly: singletons for heavy clients, ViewModel/Activity scopes for UI helpers.
- Avoid static singletons; let DI manage lifetimes.
- For runtime params, use assisted injection patterns (Workers) or factories.
- Test with @UninstallModules and fake bindings; keep tests deterministic.
- Document your graph (readme/di package) and keep constructors small.

---

This Java DI primer focuses on Hilt-first patterns with Dagger fundamentals when needed. Pair it with the Testing and Networking guides for a robust, testable architecture.
