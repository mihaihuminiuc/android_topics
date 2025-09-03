# Android UI/UX (Java)

A practical guide to building Android UI with the classic View system in Java. Includes real-world rationale, XML + Java code for components, patterns, theming, accessibility, performance, and UX polish.

---

## Table of contents

- UI foundations (layouts, views, resources)
- ConstraintLayout essentials
- RecyclerView (adapters, DiffUtil, item decoration)
- Material Components (TextInputLayout, MaterialButton, Snackbar)
- Theming and styles (colors, typography, night mode)
- Dimensions & density (dp/sp), drawables
- Custom views (drawing, attributes)
- Animations & transitions (Property animations, Motion)
- Accessibility (content descriptions, labels, touch targets)
- Internationalization & RTL
- Forms & input handling (validation, TextWatcher)
- Gestures & touch (GestureDetector)
- Navigation UI (Toolbar, AppBar, back handling)
- State & configuration changes (onSaveInstanceState)
- Performance tips (jank, overdraw, large lists)

---

## UI foundations (layouts, views, resources)

Why it’s used:
- Views render UI; XML keeps layout declarative and consistent. Resources allow theming, localization, and reuse across screens.

Example: simple layout and Activity binding

```xml
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello"/>

    <Button
        android:id="@+id/cta"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Tap"/>
</LinearLayout>
```

```java
public class MainActivity extends AppCompatActivity {
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    TextView title = findViewById(R.id.title);
    Button cta = findViewById(R.id.cta);
    cta.setOnClickListener(v -> title.setText("Tapped"));
  }
}
```

Real-life:
- Keep XML lean; move repeated styles to styles.xml; use resources (strings/dimens) for reuse.

---

## ConstraintLayout essentials

Why it’s used:
- Single, flat layout with constraints reduces nested views and improves performance compared to deep LinearLayouts.

```xml
<!-- res/layout/item_profile.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp">

    <ImageView
        android:id="@+id/avatar"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="@string/avatar_cd"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"/>

    <TextView
        android:id="@+id/name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintStart_toEndOf="@id/avatar"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/avatar"
        android:layout_marginStart="12dp"
        android:textStyle="bold"/>

    <TextView
        android:id="@+id/subtitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintStart_toStartOf="@id/name"
        app:layout_constraintTop_toBottomOf="@id/name"
        app:layout_constraintEnd_toEndOf="parent"/>
</androidx.constraintlayout.widget.ConstraintLayout>
```

Real-life:
- Use 0dp and constraints to distribute space; prefer guidelines and chains over nested weights.

---

## RecyclerView (adapters, DiffUtil, item decoration)

Why it’s used:
- Efficiently renders large/variable lists with view recycling and fine-grained updates.

Adapter using ListAdapter + DiffUtil (Java):

```java
public class User {
  public final int id; public final String name; public User(int id, String name){this.id=id; this.name=name;}
}

public class UserDiff extends DiffUtil.ItemCallback<User> {
  @Override public boolean areItemsTheSame(@NonNull User a, @NonNull User b){ return a.id == b.id; }
  @Override public boolean areContentsTheSame(@NonNull User a, @NonNull User b){ return a.name.equals(b.name); }
}

public class UserVH extends RecyclerView.ViewHolder {
  TextView name;
  public UserVH(@NonNull View itemView){ super(itemView); name = itemView.findViewById(R.id.name); }
  public void bind(User u){ name.setText(u.name); }
}

public class UserAdapter extends ListAdapter<User, UserVH> {
  protected UserAdapter(){ super(new UserDiff()); }
  @NonNull @Override public UserVH onCreateViewHolder(@NonNull ViewGroup p, int vt){
    View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_user, p, false);
    return new UserVH(v);
  }
  @Override public void onBindViewHolder(@NonNull UserVH h, int pos){ h.bind(getItem(pos)); }
}
```

Setup in Activity:

```java
RecyclerView rv = findViewById(R.id.list);
rv.setLayoutManager(new LinearLayoutManager(this));
rv.setHasFixedSize(true);
UserAdapter adapter = new UserAdapter();
rv.setAdapter(adapter);
adapter.submitList(Arrays.asList(new User(1, "Ada"), new User(2, "Linus")));
```

ItemDecoration (divider):

```java
rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
```

Real-life:
- Use stable IDs, paging, and DiffUtil for smooth updates; avoid heavy work in onBind.

---

## Material Components (TextInputLayout, MaterialButton, Snackbar)

Why it’s used:
- Consistent look-and-feel, accessibility, and gestures following Material Design.

Gradle:

```gradle
implementation "com.google.android.material:material:1.12.0"
```

Layout example:

```xml
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
  <com.google.android.material.textfield.TextInputEditText
      android:id="@+id/email"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:hint="Email"/>
</com.google.android.material.textfield.TextInputLayout>

<com.google.android.material.button.MaterialButton
    android:id="@+id/submit"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    style="@style/Widget.Material3.Button.Filled"
    android:text="Continue"/>
```

Show error and Snackbar:

```java
TextInputLayout til = findViewById(R.id.textInputLayout);
TextInputEditText email = findViewById(R.id.email);
MaterialButton submit = findViewById(R.id.submit);
submit.setOnClickListener(v -> {
  if (TextUtils.isEmpty(email.getText())) {
    til.setError("Email required");
  } else {
    til.setError(null);
    Snackbar.make(v, "Saved", Snackbar.LENGTH_SHORT).show();
  }
});
```

Real-life:
- Use TextInputLayout for validation hints; prefer MaterialButton variants for elevation and semantics.

---

## Theming and styles (colors, typography, night mode)

Why it’s used:
- Centralizes UI look; enables dark mode and brand consistency without touching each view.

colors.xml and theme:

```xml
<!-- res/values/colors.xml -->
<color name="brandPrimary">#6750A4</color>

<!-- res/values/themes.xml -->
<style name="Theme.App" parent="Theme.Material3.Light.NoActionBar">
  <item name="colorPrimary">@color/brandPrimary</item>
</style>
```

Enable dark theme resources in res/values-night and runtime switch:

```java
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
```

Real-life:
- Switch themes per brand/tenant; dark mode improves battery on OLED and user comfort.

---

## Dimensions & density (dp/sp), drawables

Why it’s used:
- Consistent sizing across devices; sp for text respects user font scale.

```xml
<!-- res/values/dimens.xml -->
<dimen name="space_m">16dp</dimen>
<TextView
  android:padding="@dimen/space_m"
  android:textSize="16sp"/>
```

Vector drawables scale crisply and reduce APK size.

Real-life:
- Avoid hardcoded px; use dp/sp and dimens for responsive UI.

---

## Custom views (drawing, attributes)

Why it’s used:
- When built-in widgets don’t fit the need; encapsulate complex UI behavior.

attrs and view class:

```xml
<!-- res/values/attrs.xml -->
<resources>
  <declare-styleable name="CircleView">
    <attr name="circleColor" format="color"/>
  </declare-styleable>
</resources>
```

```java
public class CircleView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  public CircleView(Context c, AttributeSet as){ super(c, as);
    TypedArray a = c.obtainStyledAttributes(as, R.styleable.CircleView);
    int color = a.getColor(R.styleable.CircleView_circleColor, Color.RED);
    a.recycle(); paint.setColor(color);
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    float r = Math.min(getWidth(), getHeight())/2f;
    canvas.drawCircle(getWidth()/2f, getHeight()/2f, r, paint);
  }
}
```

Real-life:
- Visualizations, charts, badges; exposing attributes makes them reusable in XML.

---

## Animations & transitions (Property animations, Motion)

Why it’s used:
- Guides attention, provides continuity, improves perceived performance.

Property animation:

```java
View card = findViewById(R.id.card);
ObjectAnimator fade = ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f);
fade.setDuration(200);
fade.start();
```

Shared element transition between Activities:

```java
ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(this, card, "card");
startActivity(new Intent(this, DetailActivity.class), options.toBundle());
```

MotionLayout (XML-driven):

```xml
<!-- res/layout/scene.xml -->
<MotionLayout
  xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:app="http://schemas.android.com/apk/res-auto"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  app:layoutDescription="@xml/scene_motion">
  <!-- children -->
</MotionLayout>
```

Real-life:
- Animate list changes, button presses, and screen transitions to feel responsive and modern.

---

## Accessibility (content descriptions, labels, touch targets)

Why it’s used:
- Inclusive design; mandatory for many markets. Improves usability for everyone.

Basics:

- Provide `android:contentDescription` for non-text icons.
- Associate labels with fields via `android:labelFor`.
- Ensure minimum 48dp touch targets; avoid tiny tap areas.
- High contrast text and sufficient color contrast.

Programmatic announcement:

```java
View v = findViewById(R.id.status);
v.announceForAccessibility("Saved");
```

Real-life:
- Screen reader users depend on proper labels; larger targets reduce mis-taps.

---

## Internationalization & RTL

Why it’s used:
- Reach global users; avoid hardcoded strings; support RTL scripts.

strings and plurals:

```xml
<string name="greeting">Hello, %1$s</string>
<plurals name="item_count">
  <item quantity="one">%d item</item>
  <item quantity="other">%d items</item>
</plurals>
```

Use bidi-friendly attributes and allow RTL mirroring in manifest:

```xml
<application android:supportsRtl="true" />
```

Real-life:
- Correct pluralization and date/number formats prevent confusion and support localization vendors.

---

## Forms & input handling (validation, TextWatcher)

Why it’s used:
- Validate data early and provide instant feedback.

```java
EditText email = findViewById(R.id.email);
Button submit = findViewById(R.id.submit);
submit.setEnabled(false);
email.addTextChangedListener(new TextWatcher() {
  @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
  @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
  @Override public void afterTextChanged(Editable s) {
    boolean ok = Patterns.EMAIL_ADDRESS.matcher(s).matches();
    submit.setEnabled(ok);
  }
});
```

Real-life:
- Reduce server errors and abandonment by catching mistakes on-device.

---

## Gestures & touch (GestureDetector)

Why it’s used:
- Natural interactions like swipes, flings, and double-taps.

```java
GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
  @Override public boolean onDoubleTap(MotionEvent e){ /* zoom */ return true; }
});
View canvas = findViewById(R.id.canvas);
canvas.setOnTouchListener((v, ev) -> detector.onTouchEvent(ev));
```

Real-life:
- Enhance media viewers, maps, and carousels with intuitive gestures.

---

## Navigation UI (Toolbar, AppBar, back handling)

Why it’s used:
- Consistent app bars, titles, and Up navigation improve discoverability.

```xml
<!-- res/layout/activity_home.xml -->
<com.google.android.material.appbar.MaterialToolbar
  android:id="@+id/toolbar"
  android:layout_width="match_parent"
  android:layout_height="wrap_content"
  android:theme="@style/ThemeOverlay.Material3.ActionBar"/>
```

```java
MaterialToolbar toolbar = findViewById(R.id.toolbar);
setSupportActionBar(toolbar);
getSupportActionBar().setTitle("Home");
```

With Navigation component (setup action bar):

```java
NavController nav = Navigation.findNavController(this, R.id.nav_host_fragment);
AppBarConfiguration cfg = new AppBarConfiguration.Builder(nav.getGraph()).build();
NavigationUI.setupActionBarWithNavController(this, nav, cfg);
```

Real-life:
- Standardizes navigation affordances across screens and deep links.

---

## State & configuration changes (onSaveInstanceState)

Why it’s used:
- Preserve transient UI state across rotation/process death when ViewModel/DB isn’t appropriate.

```java
public class ComposeEmailActivity extends AppCompatActivity {
  private EditText subject;
  @Override protected void onCreate(Bundle saved){
    super.onCreate(saved); setContentView(R.layout.activity_compose);
    subject = findViewById(R.id.subject);
    if (saved != null) subject.setText(saved.getString("subject"));
  }
  @Override protected void onSaveInstanceState(@NonNull Bundle out){
    super.onSaveInstanceState(out); out.putString("subject", subject.getText().toString());
  }
}
```

Real-life:
- Prevents data loss in forms when device rotates or app is backgrounded.

---

## Performance tips (jank, overdraw, large lists)

Why it’s used:
- Smooth 60fps UX reduces churn; users abandon janky apps.

Tips:
- Prefer ConstraintLayout; avoid deeply nested hierarchies.
- Use RecyclerView with DiffUtil; avoid notifyDataSetChanged.
- Debounce text changes; avoid heavy work on main thread; use background threads.
- Use image libraries (Glide/Picasso) with placeholders, caching, and resizing.
- Enable Profile GPU Rendering and Layout Inspector to spot jank and overdraw.
- Use setHasFixedSize(true) and item view recycling effectively.

Real-life:
- Faster scrolling lists, reduced ANRs, better battery use.

---

This Java UI/UX guide focuses on pragmatic patterns and examples you can drop into classic View-based apps. Pair it with the Testing and Performance docs for a solid foundation.
