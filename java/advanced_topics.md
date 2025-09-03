# Advanced Android Topics (Java)

This guide covers advanced Android topics using Java: Custom Views, JNI/NDK, AIDL (IPC), IPC alternatives, dynamic features, and tools/debugging. Each section explains why it matters, real-life use cases, and includes concise Java-centric examples.

---

## Table of contents

- Custom Views
  - Constructors, attrs, measurement, drawing
  - Saving/restoring state
  - Compound views
  - Performance tips
- JNI & NDK
  - Calling native code from Java
  - CMake/Gradle integration
  - Memory & safety considerations
  - When to use the NDK
- AIDL (Android Interface Definition Language)
  - Defining an AIDL interface
  - Implementing a remote Service
  - Client binding and usage
  - Callbacks and RemoteCallbackList
- IPC alternatives (Messenger, ContentProvider)
- Dynamic features & modularization
- Tools & debugging tips

---

## Custom Views

Why it matters:
- When standard widgets can’t achieve your design or performance goals, custom views provide control over rendering and input.

Real-life:
- Badges, charts, progress indicators, and special animations.

Java custom view skeleton:

```java
public class CounterView extends View {
  private int count = 0;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public CounterView(Context context) { this(context, null); }
  public CounterView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
  public CounterView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    paint.setColor(Color.BLACK);
    paint.setTextSize(48f);

    if (attrs != null) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CounterView, defStyleAttr, 0);
      count = a.getInt(R.styleable.CounterView_startValue, 0);
      a.recycle();
    }
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    Rect bounds = new Rect();
    String text = String.valueOf(count);
    paint.getTextBounds(text, 0, text.length(), bounds);
    int desiredW = bounds.width() + getPaddingLeft() + getPaddingRight();
    int desiredH = bounds.height() + getPaddingTop() + getPaddingBottom();
    int w = resolveSize(desiredW, widthMeasureSpec);
    int h = resolveSize(desiredH, heightMeasureSpec);
    setMeasuredDimension(w, h);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    String text = String.valueOf(count);
    float x = getPaddingLeft();
    float y = getPaddingTop() - paint.ascent();
    canvas.drawText(text, x, y, paint);
  }

  public void increment() { count++; invalidate(); requestLayout(); }

  @Override protected Parcelable onSaveInstanceState() {
    Parcelable superState = super.onSaveInstanceState();
    SavedState ss = new SavedState(superState);
    ss.count = this.count; return ss;
  }

  @Override protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof SavedState) {
      SavedState ss = (SavedState) state;
      super.onRestoreInstanceState(ss.getSuperState());
      this.count = ss.count; invalidate();
    } else { super.onRestoreInstanceState(state); }
  }

  public static class SavedState extends BaseSavedState {
    int count;
    SavedState(Parcelable superState) { super(superState); }
    private SavedState(Parcel in) { super(in); count = in.readInt(); }
    @Override public void writeToParcel(Parcel out, int flags) { super.writeToParcel(out, flags); out.writeInt(count); }
    public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
      @Override public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
      @Override public SavedState[] newArray(int size) { return new SavedState[size]; }
    };
  }
}
```

attrs (res/values/attrs.xml):

```xml
<declare-styleable name="CounterView">
  <attr name="startValue" format="integer"/>
</declare-styleable>
```

Compound view (inflate XML inside a custom container):

```java
public class UserHeader extends FrameLayout {
  public UserHeader(Context c, AttributeSet a) { super(c, a); init(); }
  private void init(){ LayoutInflater.from(getContext()).inflate(R.layout.view_user_header, this, true); }
}
```

Performance tips:
- Cache Paint/Path/Rect; avoid allocating in onDraw.
- Keep onMeasure cheap; precompute where possible.
- Minimize overdraw; prefer simple shapes; profile with Layout Inspector.

---

## JNI & NDK (native code)

Why it matters:
- Native code can unlock performance for compute-heavy tasks or let you reuse C/C++ libraries.

Real-life:
- Image/audio processing, ML inferences with existing native libs.

Java side:

```java
public class NativeLib {
  static { System.loadLibrary("native-lib"); }
  public static native String stringFromJNI();
}
```

C++ implementation (native-lib.cpp):

```cpp
#include <jni.h>
#include <string>
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_app_NativeLib_stringFromJNI(JNIEnv* env, jclass) {
  std::string hello = "Hello from C++";
  return env->NewStringUTF(hello.c_str());
}
```

Gradle + CMake sketch:

```groovy
android {
  defaultConfig { externalNativeBuild { cmake { cppFlags "-std=c++17" } } }
  externalNativeBuild { cmake { path "CMakeLists.txt" } }
}
```

Safety & performance:
- Batch JNI calls; avoid excessive crossings.
- Manage native memory; use sanitizers; guard against buffer overflows.

---

## AIDL (Android Interface Definition Language)

Why it matters:
- Define stable IPC contracts across processes/modules.

Real-life:
- Media services, background compute service shared by multiple apps.

Define AIDL (IMyAidlService.aidl):

```aidl
package com.example.aidl;
interface IMyAidlService {
  String getData();
  void registerCallback(IMyCallback cb);
}
```

Service implementation (Java):

```java
public class MyService extends Service {
  private final RemoteCallbackList<IMyCallback> callbacks = new RemoteCallbackList<>();
  private final IMyAidlService.Stub binder = new IMyAidlService.Stub() {
    @Override public String getData() { return "data"; }
    @Override public void registerCallback(IMyCallback cb) { if (cb != null) callbacks.register(cb); }
  };
  @Override public IBinder onBind(Intent intent) { return binder; }
}
```

Client binding:

```java
private IMyAidlService service;
private final ServiceConnection conn = new ServiceConnection() {
  @Override public void onServiceConnected(ComponentName name, IBinder b) { service = IMyAidlService.Stub.asInterface(b); }
  @Override public void onServiceDisconnected(ComponentName name) { service = null; }
};
// bind
bindService(new Intent(this, MyService.class), conn, Context.BIND_AUTO_CREATE);
```

Notes:
- AIDL calls are blocking on Binder threads—avoid heavy work inside; offload to background threads.
- Use RemoteCallbackList for managing remote callbacks safely.

---

## IPC alternatives (Messenger, ContentProvider)

Why it matters:
- Many use cases don’t need AIDL complexity.

Messenger:

```java
Handler handler = new Handler(Looper.getMainLooper()) {
  @Override public void handleMessage(Message msg) { /* handle */ }
};
Messenger messenger = new Messenger(handler);
```

ContentProvider: share structured data via URIs and grant URI permissions with FileProvider for files.

Real-life:
- Messenger fits simple command passing; Providers fit cross-app data sharing with permissions.

---

## Dynamic features & modularization

Why it matters:
- Reduce initial APK size; ship features on demand; speed up builds by modularizing.

settings.gradle:

```groovy
include ":app", ":feature:chat"
```

Tips:
- Keep feature modules decoupled; share code via :core module.
- Use Hilt/DI to bridge dependencies across modules.

Real-life:
- Large apps (e-commerce, media) often deliver seldom-used flows as on-demand modules.

---

## Tools & debugging tips

Why it matters:
- Advanced features need careful instrumentation, profiling, and diagnostics.

Tips:
- StrictMode to catch disk/network on main thread.
- For native code: use ASAN/TSAN, ndk-gdb, and Perfetto/Systrace.
- For IPC: dumpsys activity services, Binderized logs.
- View tools: Layout Inspector, Profile GPU Rendering.

Example StrictMode (debug only):

```java
if (BuildConfig.DEBUG) {
  StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
  StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
}
```

---

Advanced topics carry complexity. Prefer higher-level APIs where possible; when you need low-level power, pair it with tests, profiling, and disciplined lifecycle management.
