# Data Storage on Android (Java)

A practical, Java-focused guide to Android data storage with real-world rationale and runnable-style code: SharedPreferences, DataStore (Preferences with RxJava3), Room (SQLite abstraction), raw SQLite, files/cache/MediaStore, encryption, migrations, and best practices.

---

## Table of contents

- Overview & when to use each storage
- SharedPreferences (legacy key-value)
- DataStore (Preferences, RxJava3)
- Room (SQLite via Jetpack)
- Raw SQLite (SQLiteOpenHelper)
- Files, cache, and MediaStore
- Encryption (EncryptedSharedPreferences, EncryptedFile)
- Migrations (SharedPreferences → DataStore, Room migrations)
- Best practices

---

## Overview & when to use each storage

Why it matters:
- Choose the right tool to avoid ANRs, corruption, and complexity; ensure offline support and good UX.

Guidance:
- Small key-value settings: DataStore Preferences (async, transactional). SharedPreferences is legacy and synchronous.
- Structured relational data: Room (recommended) with entities/DAOs, LiveData/Paging integration.
- Binary files (images, downloads): files (internal/external) or MediaStore; store URIs/paths in DB, not blobs.
- Sensitive small data (tokens): EncryptedSharedPreferences or Keystore-backed solutions.

---

## SharedPreferences (Java usage)

Why it matters:
- Simple, ubiquitous key-value store for small, infrequent writes (feature flags, last screen).

Limitations:
- Synchronous I/O on main thread; no type-safety; race conditions with multiple processes.

Example:

```java
SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

void saveName(String name) {
  prefs.edit().putString("key_name", name).apply(); // apply() is async commit on disk
}

@Nullable String getName() {
  return prefs.getString("key_name", null);
}
```

---

## DataStore (Preferences, RxJava3)

Why it matters:
- Modern, asynchronous, transactional replacement for SharedPreferences with reactive streams.

Setup (Gradle):

```gradle
implementation "androidx.datastore:datastore-preferences-rxjava3:1.1.1"
```

Create and use DataStore (RxJava3):

```java
public final class SettingsDataStore {
  private final RxDataStore<Preferences> dataStore;
  private static final Preferences.Key<String> THEME_KEY = PreferencesKeys.stringKey("theme");

  public SettingsDataStore(@NonNull Context context) {
    dataStore = new RxPreferenceDataStoreBuilder(context.getApplicationContext(), "settings").build();
  }

  public Completable saveTheme(@NonNull String theme) {
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

Note on Proto DataStore:
- Proto DataStore is Kotlin/coroutines-first. In Java projects, prefer Preferences DataStore (Rx) or use Room for strongly-typed persisted models.

---

## Room (recommended for relational data)

Why it matters:
- Compile-time SQL checks, LiveData/Paging integration, easy migrations; ideal for offline-first apps.

Entity, DAO, Database:

```java
@Entity(tableName = "users")
public class User {
  @PrimaryKey public long id;
  @NonNull public String name;
  public String email;
}

@Dao
public interface UserDao {
  @Query("SELECT * FROM users ORDER BY name")
  LiveData<List<User>> observeAll();

  @Query("SELECT * FROM users WHERE id = :id")
  User getById(long id); // call on background thread

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(User... users);
}

@Database(entities = {User.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
  public abstract UserDao userDao();
}
```

Build singleton database:

```java
AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "app-db")
  .fallbackToDestructiveMigration() // use proper migrations in production
  .build();
```

Migrations:

```java
static final Migration MIGRATION_1_2 = new Migration(1, 2) {
  @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE users ADD COLUMN age INTEGER NOT NULL DEFAULT 0");
  }
};

AppDatabase db = Room.databaseBuilder(ctx, AppDatabase.class, "app-db")
  .addMigrations(MIGRATION_1_2)
  .build();
```

---

## Raw SQLite (SQLiteOpenHelper)

Why it matters:
- Full control or legacy codebases. Prefer Room unless you need custom behavior.

Example:

```java
public class MyOpenHelper extends SQLiteOpenHelper {
  public MyOpenHelper(Context context) { super(context, "my.db", null, 1); }
  @Override public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)");
  }
  @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}
}

MyOpenHelper helper = new MyOpenHelper(context);
SQLiteDatabase db = helper.getWritableDatabase();
ContentValues cv = new ContentValues();
cv.put("name", "hello");
db.insert("items", null, cv);
```

---

## Files, cache, and MediaStore

Why it matters:
- Store big/binary data as files; keep DB for metadata. Respect scoped storage and user privacy.

Internal storage (private to app):

```java
try (FileOutputStream fos = openFileOutput("notes.txt", Context.MODE_PRIVATE)) {
  fos.write("Hello".getBytes(StandardCharsets.UTF_8));
}
```

Cache directory (temporary):

```java
File cache = new File(getCacheDir(), "tmp.bin");
try (FileOutputStream out = new FileOutputStream(cache)) { /* write */ }
```

MediaStore (save an image to Pictures):

```java
ContentValues values = new ContentValues();
values.put(MediaStore.Images.Media.DISPLAY_NAME, "demo.jpg");
values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyApp");
Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
if (uri != null) {
  try (OutputStream os = getContentResolver().openOutputStream(uri)) {
    // write JPEG bytes
  }
}
```

Permissions:
- Scoped storage reduces need for WRITE_EXTERNAL_STORAGE on Android 10+. Request READ_MEDIA_IMAGES (Android 13+) or READ_EXTERNAL_STORAGE (older) when needed.

---

## Encryption (EncryptedSharedPreferences & EncryptedFile)

Why it matters:
- Protects secrets at rest (tokens, PII). Use AndroidX Security library.

Gradle:

```gradle
implementation "androidx.security:security-crypto:1.1.0-alpha06"
```

EncryptedSharedPreferences:

```java
MasterKey masterKey = new MasterKey.Builder(context)
  .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
  .build();

SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
  context,
  "secret_prefs",
  masterKey,
  EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
  EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
);

encryptedPrefs.edit().putString("token", "abc").apply();
```

EncryptedFile:

```java
File file = new File(getFilesDir(), "secret.bin");
EncryptedFile enc = new EncryptedFile.Builder(
  context,
  file,
  masterKey,
  EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
).build();

try (FileOutputStream os = enc.openFileOutput()) {
  os.write("sensitive".getBytes(StandardCharsets.UTF_8));
}
```

For database encryption consider SQLCipher with Room via third-party integration.

---

## Migrations (SharedPreferences → DataStore, Room)

SharedPreferences → DataStore (manual one-time copy):

```java
public Completable migratePrefsToDataStore(Context context) {
  SharedPreferences old = context.getSharedPreferences("app_prefs", MODE_PRIVATE);
  SettingsDataStore ds = new SettingsDataStore(context);
  String theme = old.getString("theme", null);
  if (theme != null) {
    return ds.saveTheme(theme)
      .andThen(Completable.fromAction(() -> old.edit().remove("theme").apply()));
  }
  return Completable.complete();
}
```

Room migrations: define `Migration` objects and add via `addMigrations(...)`. Test with MigrationTestHelper to ensure data safety.

---

## Best practices

- Prefer DataStore over SharedPreferences for new key-value storage.
- Prefer Room for relational data; avoid storing large blobs in the DB.
- Use files/MediaStore for media and large binaries; store references (URIs) in DB.
- Keep I/O off the main thread (Executors, WorkManager). LiveData from Room updates UI reactively.
- Encrypt sensitive data; don’t store secrets in plain text.
- Expose storage via repositories; keep UI/ViewModel decoupled and testable.
- Use singletons for Room and DataStore instances (application context).
- Write migration tests (Room) and verify DataStore copy-once logic.

---

This Java guide mirrors the Kotlin version with idiomatic Java examples. Consult AndroidX docs for Room, DataStore, and Security for full APIs.
