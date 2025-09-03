# Android Performance Optimization (Java)

A pragmatic, Java-focused guide to speeding up Android apps. Covers UI rendering, layout, RecyclerView, bitmaps, memory, threading, networking, database, app startup, battery, StrictMode/tracing, build optimizations, monitoring/ANRs—with real-world rationale and runnable-style snippets.

---

## Table of contents

- Rendering & UI jank (Choreographer, 60/120fps)
- Layout & overdraw (ConstraintLayout, view hierarchies)
- RecyclerView performance (DiffUtil, stable IDs, prefetch)
- Bitmaps & images (Glide, inBitmap/inSampleSize)
- Memory & leaks (LruCache, Leak patterns, large objects)
- Threading & background work (Executors, HandlerThread, WorkManager)
- Networking performance (timeouts, compression, caching)
- Database performance (Room indexes, transactions, Paging)
- App startup (cold/warm/hot), deferred init (App Startup)
- Battery & Doze/app standby (scheduling, constraints)
- StrictMode, tracing & profiling (Perfetto, Trace API)
- Build & packaging (R8/ProGuard, resources)
- Monitoring & ANR avoidance
- Best practices checklist

---

## Rendering & UI jank

Why it matters:
- Smooth UI requires finishing each frame in ~16ms (60Hz) or ~8ms (120Hz). Missed deadlines cause jank.

Tips:
- Keep work in onDraw minimal; avoid heavy allocations in UI code paths.
- Debounce expensive updates; coalesce UI changes.

Choreographer frame callback (diagnose main thread stalls):

```java
Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
  @Override public void doFrame(long frameTimeNanos) {
    // schedule next frame callback
    Choreographer.getInstance().postFrameCallback(this);
    // lightweight diagnostics
  }
});
```

---

## Layout & overdraw

Why it matters:
- Deep hierarchies increase measure/layout time; overdraw wastes GPU cycles.

Tips:
- Prefer ConstraintLayout and merge/include tags to flatten layout.
- Remove redundant backgrounds; use tools:targetApi and android:foreground for ripple where possible.

Example: use ViewStub for rarely-shown content:

```xml
<ViewStub
  android:id="@+id/detail_stub"
  android:inflatedId="@+id/detail_content"
  android:layout="@layout/content_detail"
  android:layout_width="match_parent"
  android:layout_height="wrap_content"/>
```

```java
ViewStub stub = findViewById(R.id.detail_stub);
View detail = stub.inflate(); // inflate only when needed
```

---

## RecyclerView performance

Why it matters:
- Lists dominate many apps. Efficient binding, diffing, and prefetching avoid jank and battery drain.

Use ListAdapter + DiffUtil:

```java
public class ItemAdapter extends ListAdapter<Item, ItemAdapter.VH> {
  public ItemAdapter() { super(DIFF); }
  static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>(){
    public boolean areItemsTheSame(@NonNull Item o, @NonNull Item n){ return o.id==n.id; }
    public boolean areContentsTheSame(@NonNull Item o, @NonNull Item n){ return o.equals(n); }
  };
  static class VH extends RecyclerView.ViewHolder { TextView title; VH(View v){ super(v); title = v.findViewById(R.id.title);} }
  @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt){
    View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false); return new VH(v);
  }
  @Override public void onBindViewHolder(@NonNull VH h, int pos){ Item it = getItem(pos); if(it!=null) h.title.setText(it.name); }
}
```

Stabilize IDs for animations and change payloads:

```java
adapter.setHasStableIds(true);
@Override public long getItemId(int position){ return getItem(position).id; }
```

Disable expensive item animations when not needed:

```java
((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
```

Prefetch:

```java
RecyclerView.setItemViewCacheSize(20);
LinearLayoutManager lm = new LinearLayoutManager(context);
lm.setInitialPrefetchItemCount(4);
recyclerView.setLayoutManager(lm);
```

---

## Bitmaps & images

Why it matters:
- Images are the top source of OOM and jank.

Use Glide/Picasso (recommended):

```gradle
implementation "com.github.bumptech.glide:glide:4.16.0"
annotationProcessor "com.github.bumptech.glide:compiler:4.16.0"
```

```java
Glide.with(imageView).load(url)
  .thumbnail(0.25f)
  .placeholder(R.drawable.placeholder)
  .error(R.drawable.error)
  .into(imageView);
```

Manual decode with downsampling:

```java
public static Bitmap decodeSampled(Resources res, int resId, int reqW, int reqH){
  BitmapFactory.Options opts = new BitmapFactory.Options();
  opts.inJustDecodeBounds = true; BitmapFactory.decodeResource(res, resId, opts);
  opts.inSampleSize = Math.max(1, Math.min(opts.outWidth/reqW, opts.outHeight/reqH));
  opts.inJustDecodeBounds = false; return BitmapFactory.decodeResource(res, resId, opts);
}
```

Use LruCache for small bitmaps:

```java
int cacheSize = (int)(Runtime.getRuntime().maxMemory()/8);
LruCache<String, Bitmap> cache = new LruCache<>(cacheSize){
  @Override protected int sizeOf(String k, Bitmap b){ return b.getByteCount(); }
};
```

---

## Memory & leaks

Why it matters:
- Leaks cause OOM and jank due to GC churn.

Tips:
- Avoid static references to Context/Views; prefer Application context when needed.
- Clear listeners in onStop/onDestroy; unsubscribe from LiveData/observers properly.
- Prefer SparseArray/SparseIntArray over HashMap<Integer, T> to save memory.

Detect with StrictMode VM policy (debug):

```java
if (BuildConfig.DEBUG) {
  StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
    .detectActivityLeaks()
    .detectLeakedClosableObjects()
    .penaltyLog()
    .build());
}
```

---

## Threading & background work

Why it matters:
- Blocking main thread leads to ANRs. Move IO/CPU work off main and marshal results back.

Executors + main Handler:

```java
ExecutorService io = Executors.newFixedThreadPool(4);
Handler main = new Handler(Looper.getMainLooper());

aio.execute(() -> {
  String result = fetch();
  main.post(() -> render(result));
});
```

HandlerThread for dedicated background Looper:

```java
HandlerThread ht = new HandlerThread("worker"); ht.start();
Handler worker = new Handler(ht.getLooper());
worker.post(() -> { /* background */ });
```

WorkManager for deferrable guaranteed jobs:

```java
Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
  .setConstraints(net).build();
WorkManager.getInstance(context).enqueue(req);
```

---

## Networking performance

Why it matters:
- Slow networks require caching, compression, and fewer round trips.

OkHttp client with cache & gzip:

```java
File cacheDir = new File(context.getCacheDir(), "http");
Cache cache = new Cache(cacheDir, 20L * 1024 * 1024);
OkHttpClient client = new OkHttpClient.Builder()
  .cache(cache)
  .addInterceptor(chain -> chain.proceed(
    chain.request().newBuilder().header("Accept-Encoding", "gzip").build()))
  .build();
```

Batch requests and use HTTP/2 multiplexing via a single OkHttpClient.

---

## Database performance

Why it matters:
- Inefficient queries block threads and waste battery.

Room indexes and transactions:

```java
@Entity(indices = {@Index(value = {"name"})})
public class User { @PrimaryKey public long id; public String name; }

@Dao interface UserDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insertAll(List<User> users);
}

@Transaction
public void refreshUsers(AppDatabase db, List<User> users){ db.userDao().insertAll(users); }
```

Use Paging 3 for large lists and avoid SELECT * without limits in UI flows.

---

## App startup (cold/warm/hot)

Why it matters:
- Faster start improves perceived quality and retention.

Tips:
- Keep Application.onCreate light; defer heavy work to background or App Startup Initializers.
- Use SplashScreen API (Android 12+) instead of custom heavy splash layouts.

Enable App Startup (Initializer):

```gradle
implementation "androidx.startup:startup-runtime:1.1.1"
```

```java
public class LoggerInitializer implements Initializer<Void> {
  @NonNull @Override public Void create(@NonNull Context context) {
    // light setup only
    return null;
  }
  @NonNull @Override public List<Class<? extends Initializer<?>>> dependencies() { return Collections.emptyList(); }
}
```

Manifest (auto-generated by library with provider). Keep initializers cheap.

---

## Battery & Doze/app standby

Why it matters:
- Waking device/radios drains battery. Respect Doze and background limits.

Tips:
- Use WorkManager with constraints (charging, unmetered) for heavy jobs.
- Batch operations; avoid frequent wakeups; leverage FCM for push instead of polling.

---

## StrictMode, tracing & profiling

Why it matters:
- Catch bad patterns in debug; measure performance objectively.

StrictMode in debug builds:

```java
if (BuildConfig.DEBUG) {
  StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
    .detectAll().penaltyLog().build());
  StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
    .detectLeakedSqlLiteObjects().penaltyLog().build());
}
```

Manual trace markers:

```java
Trace.beginSection("binding");
try {
  // bind views
} finally {
  Trace.endSection();
}
```

Profile with Android Studio Profiler/Perfetto: record CPU, memory, and system traces to spot jank and GC churn.

---

## Build & packaging (R8/ProGuard, resources)

Why it matters:
- Smaller APK/fast dex speeds installs/startup.

Tips:
- Enable minifyEnabled true in release; add keep rules for reflection/serialization.
- Remove unused resources (shrinkResources true).
- Use WebP/AVIF for images; vector drawables for icons.

Gradle (release):

```gradle
buildTypes {
  release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
  }
}
```

---

## Monitoring & ANR avoidance

Why it matters:
- ANRs kill UX and ratings. Monitor and fix before users suffer.

Tips:
- Never block main thread (disk/network). Offload and set timeouts.
- Watch for input dispatch stalls (>5s). Log slow operations during debug.
- Use Crashlytics/Play Console ANR reports; add breadcrumbs and custom keys for slow spots.

---

## Best practices checklist

- Keep main thread free; use background executors and post results to main.
- Flatten layouts; avoid overdraw; keep draw passes cheap.
- Optimize RecyclerView: DiffUtil/ListAdapter, stable IDs, disable change animations when necessary.
- Use image libraries; downsample and cache bitmaps; avoid large in-memory images.
- Avoid leaks: no static Context; clear listeners; prefer Application context when appropriate.
- Add indexes and limits to DB queries; batch writes in transactions; use Paging.
- Cache network responses; compress where possible; batch calls; reuse OkHttpClient.
- Defer heavy startup work; use App Startup and WorkManager for deferred tasks.
- Enable StrictMode in debug; trace critical sections; profile regularly.
- Shrink/optimize builds; remove unused resources; prefer modern image formats.

---

This guide focuses on actionable patterns for Java Android apps. Measure first, change one thing at a time, and verify with profiling to avoid regressions.
