# Dependency Injection (Android Kotlin)

This document explains dependency injection on Android with Dagger, Hilt, and Koin. It covers concepts, examples, scoping, testing, and best practices in Kotlin.

---

## Table of contents

- Why dependency injection?
- Core DI concepts: providers, singletons, scopes, qualifiers
- Hilt (recommended for Android)
  - setup and basics
  - modules, provides vs binds
  - scoping and components
  - ViewModel injection
  - qualifiers and context injection
  - testing with Hilt
- Dagger (manual configuration)
  - components & modules
  - subcomponents and component dependencies
  - assisted injection
- Koin (runtime DI)
  - setup and DSL
  - usage in Activities/Fragments/ViewModels
- Choosing a DI framework
- Best practices and caveats

---

## Why dependency injection?

- Decouples construction from usage
- Improves testability by allowing easy replacement of dependencies with fakes/mocks
- Centralizes wiring of graph and lifecycle of shared resources

Core terms:
- Provider / Factory: creates instances
- Singleton: single instance per component
- Scope: lifetime of instances (app, activity, viewmodel)
- Qualifier: disambiguates multiple bindings of same type

---

## Hilt (opinionated Dagger for Android)

Hilt builds on Dagger and provides pre-defined components tied to Android lifecycles (Application, Activity, Fragment, ViewModel, Service, etc.). It reduces boilerplate for Android apps.

Setup (Gradle):
- Add Hilt Gradle plugin and dependencies. Apply plugin and add `com.google.dagger:hilt-android` and `kapt` for annotation processing.

Minimal usage:

```kotlin
@HiltAndroidApp
class MyApp : Application()

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var repo: Repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // repo is injected
    }
}
```

ViewModel injection with Hilt:

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(private val repo: Repo) : ViewModel()

// in Activity/Fragment
private val viewModel: MainViewModel by viewModels()
```

Modules & providers:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit = Retrofit.Builder()...build()

    @Provides
    fun provideApi(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
```

@Binds vs @Provides:
- `@Binds` is preferred when you provide an interface implementation via an abstract function. It's more efficient (no factory method generated).

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {
    @Binds
    abstract fun bindRepo(impl: RepoImpl): Repo
}
```

Qualifiers and context injection:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Remote

@Module
@InstallIn(SingletonComponent::class)
object RepoBindings {
    @Provides
    @Remote
    fun provideRemoteRepo(...): Repo = RemoteRepo()

    @Provides
    fun provideLocalRepo(...): Repo = LocalRepo()
}

class UseCase @Inject constructor(@Remote private val repo: Repo) { }

// Inject contexts
class SomeClass @Inject constructor(@ApplicationContext val appContext: Context)
```

Scoping & components (Hilt common components):
- SingletonComponent (application)
- ActivityRetainedComponent
- ActivityComponent
- FragmentComponent
- ViewModelComponent
- ServiceComponent

Testing with Hilt:
- Use `@HiltAndroidTest` and `HiltAndroidRule` for instrumentation tests.
- Replace modules with `@TestInstallIn` or `@UninstallModules` to provide fakes/mocks.

Example test module replacement:

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule { /* test providers */ }
```

---

## Dagger (manual)

Dagger is the underlying DI framework. Hilt simplifies Dagger but understanding Dagger helps for advanced cases.

Component & Module example:

```kotlin
@Module
class NetworkModule {
    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit = Retrofit.Builder()...build()
}

@Singleton
@Component(modules = [NetworkModule::class])
interface AppComponent {
    fun inject(app: MyApp)
}

// Create component (usually in Application)
val appComponent = DaggerAppComponent.create()
appComponent.inject(this)
```

Subcomponents & component dependencies allow scoping objects to lifecycles and building graphs in pieces.

Assisted injection (for runtime parameters):
- Use `@AssistedInject` and `@Assisted` in Dagger when a factory is needed for objects that require runtime values.

---

## Koin (runtime DI, Kotlin first)

Koin is a lightweight, runtime DI library with a Kotlin DSL. No code generation; fast to iterate but may have a small runtime cost.

Setup & example:

```kotlin
// define module
val appModule = module {
    single<Repo> { RepoImpl(get()) }
    single { ApiService.create() }
    viewModel { MainViewModel(get()) }
}

// start in Application
startKoin {
    androidContext(this@MyApp)
    modules(appModule)
}

// inject in Activity/Fragment
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModel()
}
```

Koin pros/cons:
- Pros: easy setup, fast iteration, readable DSL.
- Cons: runtime lookup, less compile-time safety than Dagger/Hilt.

---

## Choosing a DI framework

- Hilt: best-in-class for Android apps that want static analysis, performance, lifecycle-aware components, and test support.
- Dagger: use when you need finer control or are already invested in Dagger without Hilt.
- Koin: great for small apps or prototypes where quick iteration is valued over compile-time guarantees.

---

## Best practices & caveats

- Prefer constructor injection (clear dependencies) over field injection when possible.
- Keep modules small and focused; separate network, database, domain modules.
- Use qualifiers or named annotations to disambiguate similar bindings.
- Do not inject Android framework types (Context) via constructors for classes that outlive lifecycle—use @ApplicationContext for singletons.
- Use `@Provides` for complex construction and `@Binds` for simple interface bindings to reduce generated code.
- Avoid service locator anti-pattern; prefer explicit injection.
- For testing, provide replacement modules and keep test doubles simple.

---

Dependency injection improves architecture and testability. On Android, Hilt provides a pragmatic, lifecycle-aware solution with less boilerplate; Dagger is for more manual control and Koin offers ease-of-use for rapid development. Choose based on app scale, team preferences, and testing needs.
