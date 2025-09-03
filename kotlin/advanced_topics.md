# Advanced Android Topics (Kotlin)

This document covers advanced material useful for Android developers: Custom Views, JNI/NDK, AIDL (IPC), and related patterns such as dynamic features and modularization. Each section includes practical Kotlin examples and best practices.

---

## Table of contents

- Custom Views
  - constructors, attrs, measurement, drawing
  - saving state
  - compound views
  - performance tips
- JNI & NDK
  - calling native code from Kotlin
  - CMake/Gradle integration
  - memory & safety considerations
  - when to use NDK
- AIDL (Android Interface Definition Language)
  - defining an AIDL interface
  - implementing a remote Service
  - client binding and usage
  - callbacks and RemoteCallbackList
- IPC alternatives (Messenger, ContentProvider)
- Dynamic features & module architecture
- Tools & debugging tips

---

## Custom Views

When to create a custom view:
- Need custom drawing or touch behavior not supported by existing widgets.
- Combine multiple widgets into a reusable component (compound view).

Core APIs: override constructors, onMeasure, onLayout (for ViewGroup), onDraw, onTouchEvent.

Kotlin custom view skeleton:

```kotlin
class CounterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var count = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
    }

    init {
        // read custom attrs
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.CounterView, defStyleAttr, 0)
            // val some = ta.getInt(R.styleable.CounterView_someAttr, 0)
            ta.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (paint.measureText("$count") + paddingLeft + paddingRight).toInt()
        val desiredHeight = (paint.fontMetrics.run { bottom - top } + paddingTop + paddingBottom).toInt()

        val measuredW = resolveSize(desiredWidth, widthMeasureSpec)
        val measuredH = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(measuredW, measuredH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = paddingLeft.toFloat()
        val y = paddingTop - paint.ascent()
        canvas.drawText(count.toString(), x, y, paint)
    }

    fun increment() {
        count++
        invalidate() // redraw
        requestLayout() // if size may change
    }
}
```

Custom attributes (res/values/attrs.xml):

```xml
<declare-styleable name="CounterView">
    <attr name="startValue" format="integer" />
</declare-styleable>
```

Saving view state across configuration changes:

```kotlin
override fun onSaveInstanceState(): Parcelable? {
    val superState = super.onSaveInstanceState()
    return SavedState(superState).also { it.count = this.count }
}

override fun onRestoreInstanceState(state: Parcelable?) {
    if (state is SavedState) {
        super.onRestoreInstanceState(state.superState)
        this.count = state.count
    } else {
        super.onRestoreInstanceState(state)
    }
}

private class SavedState : BaseSavedState {
    var count: Int = 0
    constructor(superState: Parcelable?) : super(superState)
    private constructor(inParcel: Parcel) : super(inParcel) { count = inParcel.readInt() }
    override fun writeToParcel(out: Parcel, flags: Int) {
        super.writeToParcel(out, flags)
        out.writeInt(count)
    }
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SavedState> {
            override fun createFromParcel(p: Parcel) = SavedState(p)
            override fun newArray(size: Int) = arrayOfNulls<SavedState?>(size)
        }
    }
}
```

Compound views (inflate XML inside a custom view):

```kotlin
class UserHeader(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private val binding: ViewUserHeaderBinding = ViewUserHeaderBinding.inflate(LayoutInflater.from(context), this)
    init {
        // customize
    }
}
```

Performance tips:
- Cache Paint, Path, Rect objects; avoid allocating in onDraw.
- Avoid expensive operations in onMeasure; compute once when possible.
- Use hardware layers (`setLayerType(LAYER_TYPE_HARDWARE, null)`) only when beneficial.
- Keep draw operations minimal and profile with Hierarchy Viewer / Layout Inspector.

---

## JNI & NDK (native code)

Why use NDK/ JNI?
- CPU-intensive tasks (image processing, codecs) or using native libraries.
- Access to existing C/C++ codebases.

When to avoid:
- Business logic or UI should remain in Kotlin unless there is a clear performance or legacy reason.

Kotlin side: declare native functions and load the native library:

```kotlin
class NativeLib {
    companion object {
        init { System.loadLibrary("native-lib") }
    }

    external fun stringFromJNI(): String
}
```

C++ implementation (example `native-lib.cpp`):

```cpp
#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_app_NativeLib_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
```

Build integration (Gradle + CMake):

```groovy
android {
    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
        }
    }
}
```

CMakeLists.txt declares sources and ABI settings. Use `ndk-build` or CMake depending on preference.

Safety & performance:
- Avoid excessive JNI crossings; batch data when possible.
- Use `DirectByteBuffer` for large binary data transfer to minimize copying.
- Carefully manage native memory — leaks are not detected by Android heap tools.
- Prefer standard libraries and stable APIs; use sanitizers during native development.

---

## AIDL (Android Interface Definition Language)

AIDL allows IPC between processes using Binder.

Define AIDL file (`IMyAidlService.aidl`):

```aidl
package com.example.aidl;

interface IMyAidlService {
    String getData();
    void registerCallback(IMyCallback cb);
}
```

Generate Parcelable types if needed. AIDL supports primitive types, String, List, Map, and Parcelable.

Service implementation in Kotlin:

```kotlin
class MyService : Service() {
    private val binder = object : IMyAidlService.Stub() {
        override fun getData(): String = "data"
        override fun registerCallback(cb: IMyCallback?) {
            cb?.let { callbacks.register(it) }
        }
    }

    private val callbacks = RemoteCallbackList<IMyCallback>()

    override fun onBind(intent: Intent): IBinder = binder
}
```

Client binding in Activity:

```kotlin
private var service: IMyAidlService? = null
private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = IMyAidlService.Stub.asInterface(binder)
    }
    override fun onServiceDisconnected(name: ComponentName) { service = null }
}

// bind
bindService(Intent(this, MyService::class.java), connection, Context.BIND_AUTO_CREATE)
```

Notes & best practices:
- AIDL methods are blocking and executed on Binder threads — avoid long work in AIDL methods; offload to background threads.
- Use `oneway` to mark asynchronous one-way calls.
- Use `RemoteCallbackList` to manage callbacks across processes safely.
- Use `Parcelize` for Parcelable classes in Kotlin (apply kotlin-parcelize plugin).

---

## IPC alternatives

- Messenger: simpler than AIDL for passing `Message` objects; good for basic IPC.
- ContentProvider: use for structured data sharing via URIs between apps.
- Shared memory (ashmem / mmap) and FileDescriptors for very high-performance IPC (advanced).

---

## Dynamic features & modularization

- Dynamic Feature Modules (Android App Bundle) allow on-demand download of features using Play Feature Delivery.
- Modularize by layer (core, domain, feature) to improve build times and enforce boundaries.

Gradle example for dynamic feature:

```groovy
// settings.gradle include ":app", ":feature:chat"
```

Best practices:
- Keep feature modules decoupled and small.
- Share common code via `:core` module.
- Use Hilt or DI to injection across module boundaries.

---

## Tools & debugging tips

- Use StrictMode to surface UI-thread disk/network access during development.
- For native code: use ASAN/TSAN and native debuggers (ndk-gdb) and systrace for performance.
- For AIDL/IPC: use `dumpsys activity services` and binder diagnostics for debugging.
- Use Layout Inspector and Profile GPU Rendering for view-related issues.

---

Advanced topics require careful trade-offs. Prefer high-level Kotlin solutions unless native or IPC capabilities are necessary. When you do use advanced primitives, invest in robust testing, profiling, and lifecycle management.
