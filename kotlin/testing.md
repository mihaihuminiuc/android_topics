# Testing on Android (Kotlin)

This document explains unit testing, instrumentation testing, Espresso, Robolectric, Compose testing, and test utilities commonly used in Android Kotlin projects. Includes practical examples and recommended libraries.

---

## Table of contents

- Test types: local vs instrumentation vs UI
- Common frameworks & libraries
- Unit testing (JUnit + Mockito/MockK)
- Testing coroutines and Flow
- Testing LiveData
- Room database tests (in-memory)
- Networking tests with MockWebServer
- Instrumentation tests with Espresso
- Jetpack Compose testing
- Robolectric (JVM-based Android tests)
- Hilt + DI testing
- Test tooling & CI tips

---

## Test types

- Local unit tests (JVM): run on the developer machine JVM, fast, use JUnit, Mockito/MockK, Robolectric when Android APIs required.
- Instrumentation tests (on device/emulator): run on Android runtime; use Espresso for UI assertions.
- UI tests (Espresso / UIAutomator / Compose tests) for interactions and full-stack verification.

---

## Common libraries

- JUnit (4 or 5)
- Mockito or MockK (mocking frameworks) — MockK is Kotlin-friendly
- Truth / AssertJ for fluent assertions
- kotlinx-coroutines-test for coroutines
- Robolectric for JVM Android tests
- Espresso & AndroidX Test for instrumentation tests
- MockWebServer for HTTP mocking
- AndroidX Test (ActivityScenario, FragmentScenario)
- Hilt testing utilities (@HiltAndroidTest, HiltAndroidRule)
- Compose testing: createComposeRule

---

## Unit testing (example)

ViewModel unit test using MockK and kotlinx-coroutines-test:

```kotlin
class MyViewModelTest {
    private val repo: Repo = mockk()
    private lateinit var viewModel: MyViewModel

    @Before
    fun setup() {
        viewModel = MyViewModel(repo)
    }

    @Test
    fun `load returns success`() = runBlockingTest {
        coEvery { repo.fetchItems() } returns listOf(Item(1, "a"))

        viewModel.load()

        assertThat(viewModel.state.value).isInstanceOf(UiState.Success::class.java)
    }
}
```

Notes:
- Use MockK for Kotlin-friendly mocking (final classes, coroutines). For Mockito use mockito-inline or Mockito-Kotlin.
- Prefer `runTest` / `runBlockingTest` from kotlinx-coroutines-test for coroutine tests.

---

## Testing coroutines & Flow

Use kotlinx-coroutines-test to control dispatchers and virtual time. Provide a JUnit rule to set Dispatchers.Main:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description?) { Dispatchers.resetMain() }
}
```

Then use runTest and advanceUntilIdle / advanceTimeBy to control time-based operators (debounce, delay).

Collecting Flow in tests:

```kotlin
@Test
fun `flow emits values`() = runTest {
    val flow = flowOf(1,2,3)
    val results = mutableListOf<Int>()
    flow.toList(results)
    assertThat(results).containsExactly(1,2,3)
}
```

---

## Testing LiveData

Use InstantTaskExecutorRule to execute LiveData synchronously.

```kotlin
@get:Rule
val instantExecutorRule = InstantTaskExecutorRule()

@Test
fun liveDataTest() {
    val ld = MutableLiveData<String>()
    ld.value = "hello"
    assertThat(ld.getOrAwaitValue()).isEqualTo("hello")
}
```

Helper extension getOrAwaitValue:

```kotlin
fun <T> LiveData<T>.getOrAwaitValue(
    time: Long = 2,
    timeUnit: TimeUnit = TimeUnit.SECONDS
): T {
    var data: T? = null
    val latch = CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(o: T?) {
            data = o
            latch.countDown()
            this@getOrAwaitValue.removeObserver(this)
        }
    }
    this.observeForever(observer)
    if (!latch.await(time, timeUnit)) throw TimeoutException("LiveData value was never set.")
    @Suppress("UNCHECKED_CAST")
    return data as T
}
```

---

## Room database tests (in-memory)

Use Room.inMemoryDatabaseBuilder with an instrumentation Context or Robolectric context for JVM tests.

```kotlin
@get:Rule
val instantTaskExecutorRule = InstantTaskExecutorRule()

private lateinit var db: AppDatabase
private lateinit var dao: UserDao

@Before
fun setup() {
    db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    dao = db.userDao()
}

@After
fun tearDown() {
    db.close()
}

@Test
fun insertAndGet() = runBlockingTest {
    val user = User(1, "a")
    dao.insert(user)
    val loaded = dao.getById(1)
    assertThat(loaded).isEqualTo(user)
}
```

---

## Networking tests with MockWebServer

MockWebServer lets you enqueue canned HTTP responses to test networking code deterministically.

```kotlin
val server = MockWebServer()
server.enqueue(MockResponse().setBody("{\"id\":1,\"name\":\"foo\"}").setResponseCode(200))
server.start()

val retrofit = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(MoshiConverterFactory.create())
    .build()

val api = retrofit.create(ApiService::class.java)
val user = runBlocking { api.getUser(1) }

server.shutdown()
```

---

## Instrumentation tests with Espresso

Espresso is used for UI interactions on device/emulator.

Example:

```kotlin
@get:Rule
val activityRule = ActivityScenarioRule(MainActivity::class.java)

@Test
fun clickButton_showsText() {
    onView(withId(R.id.button)).perform(click())
    onView(withText("Clicked")).check(matches(isDisplayed()))
}
```

Idling resources:
- Espresso waits for main looper and AsyncTasks by default but not for custom background threads or coroutines.
- Use CountingIdlingResource or register idling resources to synchronize asynchronous work with Espresso.

Example with CountingIdlingResource:

```kotlin
val idlingResource = CountingIdlingResource("DataLoader")
// increment before background work, decrement after
IdlingRegistry.getInstance().register(idlingResource)
// unregister in @After
```

Use ActivityScenario instead of deprecated ActivityTestRule.

---

## Jetpack Compose testing

Compose provides createComposeRule for unit-style UI tests on the JVM or instrumentation.

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun greetingDisplaysName() {
    composeTestRule.setContent { Greeting("Android") }
    composeTestRule.onNodeWithText("Hello Android").assertExists()
}
```

Use testTag with Modifier.testTag("myTag") to locate nodes; use semantics to assert accessibility properties.

---

## Robolectric (JVM-based Android tests)

Robolectric runs a simulated Android environment on the JVM for faster tests than instrumentation.

```kotlin
@RunWith(RobolectricTestRunner::class)
class ActivityTest {
    @Test
    fun activityCreates() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertThat(activity).isNotNull()
    }
}
```

Robolectric is useful for testing Activities, resources, and many platform behaviors without an emulator.

---

## Hilt + DI testing

Use @HiltAndroidTest and HiltAndroidRule to initialize Hilt in instrumentation tests. Replace modules with test doubles using @TestInstallIn or @UninstallModules.

```kotlin
@HiltAndroidTest
class MyHiltTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() { hiltRule.inject() }
}
```

---

## Test tooling & CI tips

- Run unit tests locally and run instrumentation tests on CI using Firebase Test Lab or GitHub Actions matrix of device images.
- Use build variants to run tests against staging/backends.
- Collect code coverage with JaCoCo and fail builds on regressions if desired.
- Keep tests fast and deterministic; avoid flaky tests by using idling resources and mocking remote dependencies.

---

## Summary & best practices

- Prefer small, focused unit tests; use instrumentation tests for integration and UI flows.
- Mock network and heavy dependencies (MockWebServer, fake repositories).
- Use coroutine test utilities and set Dispatchers.Main when testing code that uses Dispatchers.Main.
- Use in-memory Room DB for database tests.
- Use Compose test rule for Compose UIs and Espresso for classic Views.
- Use Robolectric when Android platform behavior is needed but you want JVM speed.

Testing is essential for reliability and maintainability. Start with unit tests for business logic, add integration tests for repositories, and UI tests for critical user flows.
