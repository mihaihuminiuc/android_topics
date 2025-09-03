# Android Testing (Java)

A practical, Java-focused guide for testing Android apps. Covers unit tests, ViewModel/LiveData, Room, WorkManager, networking with MockWebServer, UI tests with Espresso, Robolectric, Hilt DI tests, Navigation tests, IdlingResources, CI, and best practices—with real-world rationale and runnable-style snippets.

---

## Table of contents

- Testing pyramid & setup
- Unit tests (JUnit4, Mockito)
- ViewModel & LiveData testing
- Room DAO & Migration tests
- WorkManager tests
- Networking tests (Retrofit/OkHttp + MockWebServer)
- UI tests (Espresso)
- Robolectric tests (JVM)
- Hilt dependency injection tests
- Navigation tests
- IdlingResources & flaky-test handling
- CI basics (Gradle)
- Best practices checklist

---

## Testing pyramid & setup

Why it matters:
- Most tests should be fast unit tests; fewer integration/UI tests. Keeps feedback loop quick and reliable.

Gradle (common deps):

```gradle
// Unit test
testImplementation "junit:junit:4.13.2"
testImplementation "org.mockito:mockito-core:5.12.0"
testImplementation "org.robolectric:robolectric:4.12.2"
testImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"
testImplementation "androidx.arch.core:core-testing:2.2.0" // InstantTaskExecutorRule

// Android instrumented test
androidTestImplementation "androidx.test:runner:1.6.2"
androidTestImplementation "androidx.test:rules:1.6.1"
androidTestImplementation "androidx.test.ext:junit:1.2.1"
androidTestImplementation "androidx.test.espresso:espresso-core:3.6.1"
androidTestImplementation "androidx.test.espresso:espresso-intents:3.6.1"
androidTestImplementation "androidx.test.espresso:espresso-idling-resource:3.6.1"
androidTestImplementation "androidx.room:room-testing:2.6.1"
androidTestImplementation "androidx.work:work-testing:2.9.0"
androidTestImplementation "com.google.dagger:hilt-android-testing:2.51.1"
kaptAndroidTest "com.google.dagger:hilt-android-compiler:2.51.1"
androidTestImplementation "androidx.navigation:navigation-testing:2.7.7"
```

---

## Unit tests (JUnit4, Mockito)

Why it matters:
- Fast, deterministic tests for business logic; no device/emulator required.

Real-life:
- Validate pricing/formatting algorithms, mapping between DTOs and domain models, small utility classes.

Example service test:

```java
public class Calculator {
  public int add(int a, int b) { return a + b; }
}

public class CalculatorTest {
  @Test public void add_twoNumbers_returnsSum() {
    Calculator c = new Calculator();
    assertEquals(7, c.add(3, 4));
  }
}
```

Mockito example:

```java
public interface Repo { String getUserName(int id); }

public class Greeter {
  private final Repo repo; public Greeter(Repo repo) { this.repo = repo; }
  public String greet(int id){ return "Hello, " + repo.getUserName(id); }
}

public class GreeterTest {
  @Test public void greet_usesRepo() {
    Repo repo = Mockito.mock(Repo.class);
    Mockito.when(repo.getUserName(42)).thenReturn("Ada");
    Greeter g = new Greeter(repo);
    assertEquals("Hello, Ada", g.greet(42));
    Mockito.verify(repo).getUserName(42);
  }
}
```

---

## ViewModel & LiveData testing

Why it matters:
- Verify UI state logic without Android framework dependencies.

Real-life:
- Ensure complex UI state transitions (loading, success, error) are correct and resilient to edge cases.

InstantTaskExecutorRule and LiveData test helper:

```java
public class LiveDataTestUtil {
  public static <T> T getOrAwaitValue(LiveData<T> liveData, long time, TimeUnit unit) throws InterruptedException {
    final Object[] data = new Object[1];
    CountDownLatch latch = new CountDownLatch(1);
    Observer<T> o = new Observer<T>() {
      @Override public void onChanged(T t) {
        data[0] = t;
        latch.countDown();
        liveData.removeObserver(this);
      }
    };
    // With InstantTaskExecutorRule, observeForever can be used from the test thread
    liveData.observeForever(o);
    if (!latch.await(time, unit)) throw new RuntimeException("Timeout waiting for LiveData value");
    //noinspection unchecked
    return (T) data[0];
  }
}
```

ViewModel example:

```java
public class TimeViewModel extends ViewModel {
  private final MutableLiveData<String> state = new MutableLiveData<>();
  public LiveData<String> getState() { return state; }
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  public void load(){ io.execute(() -> state.postValue("OK")); }
  @Override protected void onCleared(){ io.shutdownNow(); }
}

public class TimeViewModelTest {
  @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();
  @Test public void load_emitsOk() throws Exception {
    TimeViewModel vm = new TimeViewModel();
    vm.load();
    String v = LiveDataTestUtil.getOrAwaitValue(vm.getState(), 2, TimeUnit.SECONDS);
    assertEquals("OK", v);
  }
}
```

---

## Room DAO & Migration tests

Why it matters:
- Verify queries and migrations to avoid data loss.

Real-life:
- Catch breaking schema changes early; ensure WHERE clauses and JOINs return what the UI expects.

In-memory DAO test (instrumented or Robolectric):

```java
@RunWith(AndroidJUnit4.class)
public class UserDaoTest {
  private AppDatabase db; private UserDao dao; private Context ctx;
  @Before public void setup(){ ctx = ApplicationProvider.getApplicationContext();
    db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).allowMainThreadQueries().build();
    dao = db.userDao();
  }
  @After public void tearDown(){ db.close(); }
  @Test public void insertAndQuery(){
    User u = new User(); u.id = 1; u.name = "Ada"; dao.insert(u);
    List<User> all = LiveDataTestUtil.getOrAwaitValue(dao.observeAll(), 2, TimeUnit.SECONDS);
    assertEquals(1, all.size());
  }
}
```

MigrationTestHelper example (instrumented):

```java
@Rule public MigrationTestHelper helper = new MigrationTestHelper(
  InstrumentationRegistry.getInstrumentation(), AppDatabase.class.getCanonicalName());

@Test public void migrate1To2() throws IOException {
  SupportSQLiteDatabase db = helper.createDatabase("test-db", 1);
  db.execSQL("INSERT INTO users(id, name, email) VALUES(1,'Ada','a@example.com')");
  db.close();
  AppDatabase migrated = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class, "test-db")
    .addMigrations(MIGRATION_1_2)
    .build();
  migrated.getOpenHelper().getWritableDatabase().close();
}
```

---

## WorkManager tests

Why it matters:
- Deterministic testing of deferred background work with constraints and backoff.

Real-life:
- Validate periodic syncs, file uploads, and retry/backoff logic without waiting for real time or network.

```java
@RunWith(AndroidJUnit4.class)
public class UploadWorkerTest {
  @Before public void setup() {
    Context ctx = ApplicationProvider.getApplicationContext();
    Configuration config = new Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).build();
    WorkManagerTestInitHelper.initializeTestWorkManager(ctx, config);
  }

  @Test public void run_success() throws Exception {
    OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(UploadWorker.class).build();
    WorkManager wm = WorkManager.getInstance(ApplicationProvider.getApplicationContext());
    wm.enqueue(req).result.get();
    WorkInfo info = wm.getWorkInfoById(req.getId()).get();
    assertEquals(WorkInfo.State.SUCCEEDED, info.getState());
  }
}
```

Use TestDriver to control constraints/timeouts.

---

## Networking tests (Retrofit/OkHttp + MockWebServer)

Why it matters:
- Reliable tests without hitting real servers or the network.

Real-life:
- Lock down API contracts, handle error bodies and edge HTTP codes (401/500), and keep tests fast/offline.

```java
public class ApiTest {
  private MockWebServer server; private ApiService api;
  @Before public void setUp() throws IOException {
    server = new MockWebServer(); server.start();
    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(server.url("/").toString())
      .addConverterFactory(GsonConverterFactory.create())
      .build();
    api = retrofit.create(ApiService.class);
  }
  @After public void tearDown() throws IOException { server.shutdown(); }
  @Test public void listItems_ok() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("[{\"id\":1,\"name\":\"A\"}]"));
    Response<List<Item>> resp = api.listItems().execute();
    assertTrue(resp.isSuccessful()); assertEquals(1, resp.body().size());
    RecordedRequest rr = server.takeRequest(); assertEquals("/items", rr.getPath());
  }
}
```

---

## UI tests (Espresso)

Why it matters:
- Validate user flows and view interactions on real devices/emulators.

Real-life:
- Smoke test critical journeys: login, checkout, settings changes, deep links.

Basic test with ActivityScenarioRule:

```java
@RunWith(AndroidJUnit4.class)
public class LoginTest {
  @Rule public ActivityScenarioRule<LoginActivity> rule = new ActivityScenarioRule<>(LoginActivity.class);

  @Test public void loginFlow() {
    onView(withId(R.id.username)).perform(typeText("user"), closeSoftKeyboard());
    onView(withId(R.id.password)).perform(typeText("pass"), closeSoftKeyboard());
    onView(withId(R.id.login)).perform(click());
    onView(withText("Welcome")).check(matches(isDisplayed()));
  }
}
```

Intents test:

```java
@RunWith(AndroidJUnit4.class)
public class ShareIntentTest {
  @Rule public IntentsRule intents = new IntentsRule();
  @Test public void sendsShareIntent() {
    intending(hasAction(Intent.ACTION_SEND)).respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, null));
    // trigger share
    intended(hasAction(Intent.ACTION_SEND));
  }
}
```

RecyclerViewActions:

```java
onView(withId(R.id.list)).perform(RecyclerViewActions.actionOnItemAtPosition(5, click()));
```

---

## Robolectric tests (JVM)

Why it matters:
- Fast tests that run on the JVM while providing many Android APIs.

Real-life:
- Cover Activity/Fragment logic quickly in CI without an emulator.

```java
@RunWith(RobolectricTestRunner.class)
public class MainActivityRoboTest {
  @Test public void clickingButton_showsText() {
    ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
    MainActivity activity = controller.get();
    activity.findViewById(R.id.button).performClick();
    TextView tv = activity.findViewById(R.id.message);
    assertEquals("Clicked", tv.getText().toString());
  }
}
```

---

## Hilt dependency injection tests

Why it matters:
- Replace real dependencies with fakes for isolated tests.

Real-life:
- Swap network/DB with fakes to make tests deterministic and fast.

```java
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class RepoTest {
  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Module @InstallIn(SingletonComponent.class)
  public static class TestModule {
    @Provides @Singleton public Repo provideRepo() { return new FakeRepo(); }
  }

  @Test public void usesFakeRepo() {
    hiltRule.inject();
    // launch and verify behavior using FakeRepo
  }
}
```

Note: Use @UninstallModules to replace production modules when needed.

---

## Navigation tests

Why it matters:
- Verify navigation actions and arguments.

Real-life:
- Prevent regressions in back stack and argument passing between screens.

```java
@Test public void navigateToDetail() {
  TestNavHostController nav = new TestNavHostController(ApplicationProvider.getApplicationContext());
  nav.setGraph(R.navigation.nav_graph);
  FragmentScenario<MyFragment> scenario = FragmentScenario.launchInContainer(MyFragment.class);
  scenario.onFragment(f -> Navigation.setViewNavController(f.requireView(), nav));
  onView(withId(R.id.toDetail)).perform(click());
  assertEquals(R.id.detailFragment, nav.getCurrentDestination().getId());
}
```

---

## IdlingResources & flaky-test handling

Why it matters:
- Synchronize Espresso with async work; reduce flakiness.

Real-life:
- Stabilize tests that rely on network or background threads by exposing app idleness.

Simple CountingIdlingResource:

```java
public class BusyIdler {
  public static final CountingIdlingResource IDLER = new CountingIdlingResource("GLOBAL");
  public static void busy(){ IDLER.increment(); }
  public static void idle(){ if (!IDLER.isIdleNow()) IDLER.decrement(); }
}
```

Register in tests:

```java
IdlingRegistry.getInstance().register(BusyIdler.IDLER);
// in code: BusyIdler.busy(); ... BusyIdler.idle();
```

Other tips:
- Disable animations on test devices.
- Use retry rules sparingly for flaky external deps.

---

## CI basics (Gradle)

Run tests:

```bash
./gradlew test
./gradlew connectedAndroidTest
```

Split jobs by variant and shard UI tests across devices for speed.

Real-life:
- Gate pull requests with fast unit tests and nightly full connected test runs.

---

## Best practices checklist

- Keep most tests as unit tests; minimize slow UI tests.
- Make network deterministic with MockWebServer; no real servers in tests.
- Use InstantTaskExecutorRule for LiveData; use in-memory Room for DAOs; test migrations.
- Use WorkManager test utilities and TestDriver to control constraints.
- Use Espresso with IdlingResources; avoid Thread.sleep.
- Seed test data; isolate external services with fakes/mocks.
- Run tests in CI on every PR with clear reports and flaky test quarantine.

---

This Java testing primer focuses on pragmatic, reliable patterns. Adapt to your architecture (MVVM/MVI) and aim for fast feedback with deterministic tests.
