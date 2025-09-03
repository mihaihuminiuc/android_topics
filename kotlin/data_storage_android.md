# Data Storage on Android (Kotlin)

This document summarizes data storage options on Android and shows practical Kotlin examples for SharedPreferences, DataStore (Preferences and Proto), Room (SQLite abstraction), raw SQLite, file storage, encryption, and migration strategies.

---

## Table of contents

- Overview & when to use each storage
- SharedPreferences (legacy key-value)
- DataStore (Preferences & Proto)
- Room (SQLite via Jetpack)
- Raw SQLite (SQLiteOpenHelper)
- Files, cache, and MediaStore
- Encryption (EncryptedSharedPreferences, EncryptedFile, SQLCipher)
- Migrations (SharedPreferences -> DataStore, Room migrations)
- Best practices

---

## Overview & when to use each storage

- Small key-value pairs (settings): DataStore (Preferences) — modern, async, Flow-based. SharedPreferences still works but DataStore is recommended.
- Structured relational data: Room (recommended). Uses SQLite under the hood and offers compile-time checks, Flow integration, and migrations.
- Binary files (images, downloads): internal/external files or MediaStore, not DB blobs.
- Secure small data (tokens): EncryptedSharedPreferences or Android Keystore-backed solutions.

---

## SharedPreferences (Kotlin usage)

Simple usage (KTX extension edit):

```kotlin
val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

fun saveName(name: String) {
    prefs.edit { putString("key_name", name) }
}

fun getName(): String? = prefs.getString("key_name", null)
```

Limitations:
- Synchronous (can block), not ideal for frequent updates.
- Not recommended for large data.

---

## DataStore

DataStore is the recommended replacement for SharedPreferences. It comes in two flavors:
- Preferences DataStore — key-value, similar to SharedPreferences but asynchronous and Flow-based.
- Proto DataStore — structured, strongly-typed via protobuf.

Preferences DataStore example:

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "app_prefs")) }
)

val THEME_KEY = stringPreferencesKey("theme")

suspend fun saveTheme(context: Context, theme: String) {
    context.dataStore.edit { prefs -> prefs[THEME_KEY] = theme }
}

val themeFlow: Flow<String> = context.dataStore.data
    .map { prefs -> prefs[THEME_KEY] ?: "light" }
```

Proto DataStore example (requires a .proto schema and generated Kotlin classes):

```protobuf
// settings.proto
syntax = "proto3";
package example;
message Settings {
    string theme = 1;
}
```

Serializer and declaration:

```kotlin
object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): Settings = Settings.parseFrom(input)
    override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
}

val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer
)

val themeFlow = context.settingsDataStore.data.map { it.theme }
```

Advantages of DataStore:
- Asynchronous and coroutine-friendly
- Uses Flow for updates
- Safe APIs for handling data corruption and migrations

---

## Room (recommended for relational data)

Room provides compile-time checked SQL, entities, DAOs, and integration with Kotlin coroutines and Flow.

Entity, DAO, Database example:

```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: Long,
    val name: String,
    val email: String
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name")
    fun observeAll(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg user: User)
}

@Database(entities = [User::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

// build database (singleton)
val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app-db")
    .fallbackToDestructiveMigration() // use proper migrations in production
    .build()
```

Room features:
- Support for Flow and LiveData return types.
- Paging integration via PagingSource in DAOs.
- TypeConverters for complex types (dates, lists, enums).
- Compile-time verification of SQL queries.

Migrations example:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE users ADD COLUMN age INTEGER DEFAULT 0 NOT NULL")
    }
}

Room.databaseBuilder(context, AppDatabase::class.java, "app-db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

---

## Raw SQLite (SQLiteOpenHelper)

Use raw SQLite only when you need direct control or for legacy apps. Prefer Room for new projects.

```kotlin
class MyOpenHelper(context: Context) : SQLiteOpenHelper(context, "my.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

val helper = MyOpenHelper(context)
val db = helper.writableDatabase
db.insert("items", null, ContentValues().apply { put("name", "hello") })
```

---

## Files, cache, and MediaStore

- Use internal storage (context.filesDir) for private app files.
- Use cacheDir for temporary data; system can clear cache when low on storage.
- Use external storage with scoped storage or MediaStore for shared media files. Request runtime permissions if required.

Saving a file to internal storage:

```kotlin
context.openFileOutput("notes.txt", Context.MODE_PRIVATE).use { stream ->
    stream.write("Hello".toByteArray())
}
```

MediaStore example (saving an image) uses ContentValues and context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).

---

## Encryption: EncryptedSharedPreferences & EncryptedFile

AndroidX Security library offers simple encryption APIs.

EncryptedSharedPreferences example:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secret_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

encryptedPrefs.edit { putString("token", "abc") }
```

EncryptedFile example:

```kotlin
val file = File(context.filesDir, "secret.bin")
val encryptedFile = EncryptedFile.Builder(
    context,
    file,
    masterKey,
    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
).build()

encryptedFile.openFileOutput().use { it.write("sensitive".toByteArray()) }
```

For database encryption consider SQLCipher + Room (third-party integration).

---

## Migrations (SharedPreferences -> DataStore)

Migrate SharedPreferences to Preferences DataStore using SharedPreferencesMigration:

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "app_prefs")) }
)
```

For Room, provide Migration objects and test them.

---

## Best practices

- Prefer DataStore over SharedPreferences for new key-value storage.
- Prefer Room for relational data; avoid storing large blobs in the DB.
- Use files or MediaStore for media and large binary data; store URIs in the DB.
- Use encryption for sensitive data and avoid storing secrets in plain text.
- Expose storage through repositories and abstract implementation details for testability.
- Use Flow/LiveData for reactive UI updates instead of polling.
- Keep DB and IO operations off the main thread (use coroutines/Dispatchers.IO).
- Use singletons for Room database and DataStore instances (application context).

---

This guide provides practical Kotlin snippets and guidance to choose the right storage for Android apps. For full API details consult the AndroidX docs for Room, DataStore, and Security libraries.
