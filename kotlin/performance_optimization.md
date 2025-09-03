# Performance Optimization (Android, Kotlin)

This guide presents practical strategies and examples to optimize Android apps for memory, CPU, battery, and APK size. It covers profiling tools, common problems, code patterns, and build-time optimizations.

---

## Table of contents

- Measure first (profiling tools)
- Memory management
- CPU (threading & algorithmic improvements)
- UI & rendering (layout, overdraw, draw calls)
- Lists & RecyclerView optimizations
- Image loading & bitmaps
- Startup time (cold/warm start)
- APK size reduction
- Networking & battery
- Database & storage optimizations
- Build & Gradle tips
- Checklist & common pitfalls

---

## Measure first (profiling tools)

Always measure before optimizing. Useful tools:
- Android Profiler (CPU, Memory, Network)
- Memory Profiler / Heap dumps
- LeakCanary for memory leaks
- Systrace / Perfetto for system-level traces
- Traceview & VM trace
- Benchmark library for microbenchmarks

Collect representative workloads and reproduce performance issues before fixing.

---

## Memory management

Minimize allocations and leaks:
- Avoid retaining Activity/Fragment contexts in static fields. Use applicationContext when needed.
- Use viewLifecycleOwner in Fragments for LiveData/Flow observers to avoid leaking view references.
- Cancel coroutines and callbacks in onDestroy/onDestroyView.
- Prefer immutable data classes and avoid creating temporary objects in hot paths (e.g., inside draw or onBindViewHolder).

Example: LruCache for bitmaps

```kotlin
val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
    override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount / 1024
}

fun getBitmap(key: String): Bitmap? = bitmapCache.get(key)
fun putBitmap(key: String, bmp: Bitmap) = bitmapCache.put(key, bmp)
```

Detect leaks:
- Use LeakCanary to find retained instances and fix references.
- Inspect heap dumps in Memory Profiler to find large objects.

Reduce object churn:
- Reuse objects (StringBuilder, reuse arrays) in performance-critical loops.
- Use primitive arrays instead of boxed types when appropriate.

---

## CPU usage & threading

- Keep heavy work off the main thread. Use coroutines with Dispatchers.IO or background threads.
- Use structured concurrency to ensure work is cancelled when lifecycle ends.

Avoid expensive operations in UI callbacks and drawing code.

Profiling CPU hotspots:
- Use CPU Profiler to capture method traces and identify hotspots.
- Optimize algorithmic complexity (O(n^2) -> O(n log n) etc.) before micro-optimizing.

Example (coroutines off UI):

```kotlin
lifecycleScope.launch {
    withContext(Dispatchers.IO) {
        val data = repository.loadLargeData()
        // heavy processing done off-main
    }
}
```

---

## UI & rendering optimizations

Reduce overdraw and unnecessary invalidations:
- Flatten view hierarchies: prefer ConstraintLayout or Compose which reduce nesting.
- Use tools: "Show GPU overdraw" in Developer Options, Layout Inspector.
- Avoid expensive custom drawing in onDraw; cache bitmaps where possible.

Use ViewStub for infrequently used layouts and inflate lazily.
Use android:hardwareAccelerated="true" (default on modern devices) for GPU acceleration.

Modifiers in Compose: minimize recompositions by using stable inputs, remember, and derivedStateOf.

---

## Lists & RecyclerView

- Use RecyclerView with ViewHolders for large lists.
- Use DiffUtil to compute changes off the main thread.
- Call setHasFixedSize(true) when item size is fixed for more optimizations.
- Use paging (Paging 3) for large datasets to load items incrementally.

RecyclerView tips:
- Use payloads in notifyItemChanged to avoid rebinding the entire view.
- Use view caches and setItemViewCacheSize when necessary.
- Preload images for smoother scrolls if needed.

Example: DiffUtil

```kotlin
class MyDiffCallback : DiffUtil.ItemCallback<Item>() {
    override fun areItemsTheSame(old: Item, new: Item) = old.id == new.id
    override fun areContentsTheSame(old: Item, new: Item) = old == new
}
```

---

## Image loading & bitmaps

Images are a common source of OOM and jank.

Best practices:
- Use a proven image library (Glide, Coil, Picasso) that handles caching and memory.
- Request images at the exact target size (.override(width, height)) to reduce memory use.
- Use placeholders and image decoding options with inSampleSize for large images.
- Prefer vector drawables for simple icons to reduce APK size.

Bitmap sampling example:

```kotlin
fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
```

Image loading with Glide (example):

```kotlin
Glide.with(imageView)
    .load(url)
    .override(200, 200)
    .centerCrop()
    .into(imageView)
```

---

## Startup time (cold/warm)

Minimize work in Application.onCreate() and Activity.onCreate().
- Defer heavy initialization to background threads or to when the feature is actually needed.
- Use lazy initialization or WorkManager for non-urgent background tasks.
- Use the Android Studio Startup Profiler and baseline profiles to improve ART JIT/AOT usage.

Cold start tips:
- Reduce initialization work and number of dex methods executed at startup.
- Use app bundles and baseline profiles for optimizing code used during startup.

---

## APK size reduction

Strategies to reduce APK size:
- Use Android App Bundle (AAB) and dynamic features.
- Enable R8 (minifyEnabled true) and resource shrinking (shrinkResources true) in release builds.
- Remove unused resources and code; avoid heavy transitive libraries.
- Use vector drawables for icons and convert images to WebP/AVIF.
- Split APKs by ABI to avoid bundling native libs for all architectures.

Gradle config example (Groovy):

```groovy
android {
  buildTypes {
    release {
      minifyEnabled true
      proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
      shrinkResources true
    }
  }
  splits {
    abi {
      enable true
      reset()
      include 'armeabi-v7a', 'arm64-v8a'
      universalApk false
    }
  }
}
```

Also consider removing debug-only libraries from release builds and using ProGuard rules to strip unused code.

---

## Networking & battery

- Batch network calls when possible and use server-side compression (gzip).
- Use caching headers and OkHttp caching.
- Avoid frequent wake-ups, prefer WorkManager for deferrable work and set appropriate constraints.
- Use location updates sparingly and choose appropriate priority (e.g., balanced or passive).

---

## Database & storage optimizations

- Use indexes on frequently queried columns in SQLite/Room.
- Batch inserts/updates inside transactions to speed up operations.

Example transaction with Room:

```kotlin
@Transaction
suspend fun insertAll(items: List<Item>) {
    dao.insert(items)
}
```

- Use paging to reduce memory usage when loading large datasets.

---

## Build & Gradle tips

- Use AAPT2 and enable resource shrinking.
- Use R8 to reduce method count.
- Keep dependencies up to date; modern libraries are often smaller and faster.
- Use ProGuard/R8 rules to keep required reflection-based classes.

---

## Checklist & common pitfalls

- [ ] Profile first—don’t guess
- [ ] Avoid blocking the main thread
- [ ] Cancel background jobs when appropriate
- [ ] Reuse expensive objects (caches, pools)
- [ ] Use lazy init for heavy components
- [ ] Use paging for large lists
- [ ] Use vector drawables and compress images
- [ ] Trim unused libraries and enable R8/shrinking
- [ ] Use LeakCanary and Memory Profiler
- [ ] Test on low-end devices and with background constraints

---

Optimization is iterative: measure, change, and re-measure. Use the right tool for each problem (profilers, LeakCanary, Benchmark library), and prefer algorithmic improvements and architectural changes over micro-optimizations.
