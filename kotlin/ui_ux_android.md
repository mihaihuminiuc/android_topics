# UI / UX Design for Android (Kotlin)

A practical guide to Android UI/UX: Material Design principles, theming, ConstraintLayout, Compose layout patterns, accessibility, motion, responsive layouts, and implementation tips with Kotlin examples.

---

## Table of contents

- Material Design principles
- Theming & Material Components (View + Compose)
- ConstraintLayout (XML + Kotlin)
- Responsive layouts & multi-window
- Motion & micro-interactions (MotionLayout & Compose)
- Accessibility (a11y)
- Typography, spacing, density (dp / sp)
- Icons, images & asset best practices
- Lists, navigation patterns, and scaffold patterns
- Tools & testing for UI/UX
- Checklist / Best practices

---

## Material Design principles

Core principles:
- Material is tactile and inspired by physical surfaces: use elevation, shadows, and motion to convey hierarchy.
- Consistency: use a coherent color, shape, and typography system so UI feels unified.
- Motion: animated transitions should be meaningful and not distracting.
- Clarity: content-first design — make UI readable and accessible.

Key Material building blocks: AppBar (TopAppBar), FAB, BottomNavigation, Navigation Drawer, Cards, Buttons, TextFields, Surfaces.

Design tokens to maintain consistency:
- Colors (primary, secondary, surface, background, error)
- Typography scale (headline, body, caption)
- Shapes & corner radii
- Spacing system (multiples of 4 or 8dp)

---

## Theming & Material Components

View system (XML + Styles)

- Use MaterialComponents theme in styles.xml as parent to get Material widgets:

```xml
<style name="Theme.MyApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="colorPrimary">@color/seed_primary</item>
    <item name="colorSecondary">@color/seed_secondary</item>
</style>
```

- Use Material components in layouts: com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputLayout, etc.

Dark mode & dynamic colors

- Support day/night themes with Theme.MaterialComponents.DayNight parent and use `values-night` resources.
- On Android 12+, consider dynamic color (Material You) to use system palette.

Compose theming (Material3 recommended)

```kotlin
@Composable
fun MyTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

- Use `Scaffold` in Compose to place TopAppBar, FAB, Drawer, and BottomBar consistently.

---

## ConstraintLayout (XML + Kotlin)

ConstraintLayout lets you build complex, flat layouts without deep nesting.

Basic XML example with constraints and a guideline:

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/guideline"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintGuide_begin="16dp" />

    <TextView
        android:id="@+id/title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        android:padding="16dp"
        android:text="Title" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

Chains and bias:
- Chains (`app:layout_constraintHorizontal_chainStyle="packed"`) help align groups of views with different chain styles (spread, spread_inside, packed).
- Use `layout_constraintHorizontal_bias` and `layout_constraintVertical_bias` to position views within constraints.

ConstraintLayout in Kotlin (ConstraintSet & MotionLayout) enables runtime constraints and animated transitions.

---

## Responsive layouts & multi-window

- Use `dp` for layout dimensions and `sp` for text; use resource qualifiers (layout-sw600dp, values-w820dp) for tablets/foldables.
- Use `BoxWithConstraints` in Compose to adapt UI based on available space.

Compose example for responsive layout:

```kotlin
@Composable
fun ResponsiveLayout() {
    BoxWithConstraints {
        if (maxWidth < 600.dp) {
            // phone layout
        } else {
            // tablet / two-pane layout
        }
    }
}
```

- For multi-window and foldables, use Jetpack WindowManager to detect posture and adjust UI to two-pane layouts.

---

## Motion & micro-interactions

- Use MotionLayout in ConstraintLayout to create rich animated transitions defined in a MotionScene XML.
- In Compose use `animate*AsState`, `updateTransition`, `AnimatedVisibility` and `rememberInfiniteTransition`.

MotionLayout example (concept):

- Define start and end ConstraintSets and transitions in a MotionScene XML, then use MotionLayout to interpolate.

Compose example (simple animated elevation):

```kotlin
val elevated by animateDpAsState(if (elevatedState) 8.dp else 0.dp)
Surface(elevation = elevated) { /* ... */ }
```

Design motion guidelines:
- Keep motion purposeful and short (100–300 ms typical)
- Use easing curves and staggered children for clarity
- Avoid excessive motion; respect system Reduce Motion setting if user enabled

---

## Accessibility (a11y)

Accessibility is critical for UX.

Key practices:
- Provide `contentDescription` for images and icons.
- Use `android:labelFor` to associate labels and inputs.
- Respect font scaling: use `sp` units and allow large-font testing.
- Ensure tap targets are at least 48dp.
- Support RTL by using `start`/`end` attributes instead of `left`/`right`.
- Test with TalkBack and Android Accessibility Scanner.

Compose a11y example:

```kotlin
Image(
    painter = painterResource(R.drawable.icon),
    contentDescription = stringResource(R.string.icon_desc),
    modifier = Modifier.size(48.dp)
)
```

Use semantics modifiers to expose accessibility properties:

```kotlin
Modifier.semantics { contentDescription = "Close" }
```

---

## Typography, spacing, density

- Use consistent typography scale: headline, subtitle, body, caption.
- Use `sp` for text sizes (scales with accessibility settings) and `dp` for UI measurements.
- Define spacing tokens (4dp/8dp grid) and reuse them via dimens or a spacing object in Compose.

Compose typography example:

```kotlin
val Typography = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp)
)
```

---

## Icons, images & asset best practices

- Use vector drawables for simple icons (reduce APK bloat). For complex images prefer WebP or AVIF for smaller size.
- Provide density-specific raster images only when necessary.
- Prefer adaptive icons for the app launcher.
- Optimize PNGs and remove unused assets.

---

## Lists, navigation patterns & scaffold

Classic patterns:
- Single Activity with fragments / navigation component or single-activity Compose navigation.
- Use NavHost/NavController for declarative navigation in Compose or Navigation Component for fragments.
- Scaffold pattern: header-bar (TopAppBar), content, floating action button, bottom bar.

Compose Scaffold example:

```kotlin
Scaffold(
    topBar = { TopAppBar(title = { Text("App") }) },
    floatingActionButton = { FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, null) } },
    bottomBar = { BottomAppBar { /* nav */ } }
) { padding ->
    // screen content with padding
}
```

---

## Tools & testing for UI/UX

- Layout Inspector and Layout Validation in Android Studio
- Accessibility Scanner and TalkBack testing
- Espresso and Compose testing for UI interaction tests
- Preview annotations in Compose for fast iteration
- Use design specs and golden image testing to validate visuals

---

## Checklist / Best practices

- [ ] Use theme tokens (colors, typography, spacing) centrally
- [ ] Support dark mode and night resources
- [ ] Respect font scaling and accessibility settings
- [ ] Use ConstraintLayout / Compose responsive patterns for complex layouts
- [ ] Keep touch targets at least 48dp
- [ ] Test on multiple screen sizes and densities
- [ ] Optimize images and use vector drawables when appropriate
- [ ] Animate purposefully and respect reduce motion
- [ ] Use contentDescription and test with TalkBack

---

Design is more than visuals: prioritize accessibility, responsive behavior, and coherent motion. Use Material guidelines as a foundation and adapt them to your brand through consistent tokens and theming. Implement with architecture that keeps UI code declarative, testable, and reusable.
