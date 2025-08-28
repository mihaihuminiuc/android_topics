# Jetpack Compose (Kotlin)

This document is a concise but thorough primer on Jetpack Compose: building UI with composables, state management, Modifier, layout primitives, navigation, animations, testing, and integration. Examples are Kotlin-focused and use modern best practices.

---

## Table of contents

- Introduction
- Composables & recomposition
- State management (remember, rememberSaveable, State, StateFlow)
- State hoisting & unidirectional data flow
- Modifier (order, usage, semantics)
- Layout primitives: Row, Column, Box, ConstraintLayout, Lazy lists
- Navigation (Navigation Compose)
- Theming & Material
- Animations
- Interop with Views and XML
- Testing & tooling
- Performance tips & common pitfalls

---

## Introduction

Jetpack Compose is Android’s modern toolkit for building native UI declaratively. Instead of XML UI files, UI is built with @Composable Kotlin functions. Compose handles updating the UI by recomposing functions when observed state changes.

---

## Composables & recomposition

- A composable is a Kotlin function annotated with @Composable.
- Composables should be pure and side-effect free: given the same inputs, they should emit the same UI. Side effects (launching coroutines, registering listeners) should be done using effect APIs (LaunchedEffect, DisposableEffect, SideEffect).
- Recomposition occurs whenever state read by a composable changes.

Example simple composable:

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}
```

Recomposition notes:
- Compose only recomposes the smallest possible subtree when state changes.
- Use stable types for parameters to avoid unnecessary recompositions.

---

## State management

Compose has several primitives for state:

- remember { mutableStateOf(...) } — keeps state across recompositions but not process death.
- rememberSaveable { mutableStateOf(...) } — persists state across configuration changes and process recreation (for types supported by SavedInstanceState or via custom saver).
- MutableState / State — observable types that trigger recomposition when their value changes.
- Use ViewModel and StateFlow/Flow for app-level or business state; collect in Compose with collectAsState().

Example: local state

```kotlin
@Composable
fun Counter() {
    var count by rememberSaveable { mutableStateOf(0) }
    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

Example: ViewModel + StateFlow

```kotlin
class MyViewModel : ViewModel() {
    private val _text = MutableStateFlow("Hello")
    val text: StateFlow<String> = _text
    fun update(new: String) { _text.value = new }
}

@Composable
fun Screen(viewModel: MyViewModel = viewModel()) {
    val text by viewModel.text.collectAsState()
    Text(text)
}
```

---

## State hoisting & unidirectional data flow

State hoisting means moving state up to the caller and making a composable stateless. This enables reuse and testability.

Stateless composable:

```kotlin
@Composable
fun NameInput(name: String, onNameChange: (String) -> Unit) {
    TextField(value = name, onValueChange = onNameChange, label = { Text("Name") })
}
```

Parent manages state:

```kotlin
@Composable
fun Parent() {
    var name by rememberSaveable { mutableStateOf("") }
    NameInput(name = name, onNameChange = { name = it })
}
```

This pattern enforces single source of truth and easier testing.

---

## Modifier

- Modifier is Compose’s way to describe element decoration, layout, click handling, accessibility, drawing, etc.
- Modifiers are immutable and chainable; order matters.

Examples:

```kotlin
Text(
    "Click me",
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .clickable { /* ... */ }
)
```

Notes on order:
- .padding().background(color) vs .background(color).padding(): the padding is inside or outside the background respectively.
- Place layout-affecting modifiers (size, padding) before drawing modifiers when appropriate.

Accessibility & testing:
- Use Modifier.semantics { contentDescription = "..." } or built-in parameters like contentDescription on Image.
- Use Modifier.testTag("tag") to identify components in UI tests.

---

## Layout primitives

- Column, Row, Box are the basic building blocks. For large lists, use LazyColumn / LazyRow to keep memory usage low.
- ConstraintLayout is available in Compose via androidx.constraintlayout.compose.

Lazy list example:

```kotlin
@Composable
fun ItemsList(items: List<Item>) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            Text(item.name, modifier = Modifier.padding(16.dp))
        }
    }
}
```

Remember scroll state:

```kotlin
val listState = rememberLazyListState()
LazyColumn(state = listState) { ... }
```

---

## Navigation (Navigation Compose)

- Navigation Compose provides NavController, NavHost, and composable destinations.
- Use Safe Args like patterns by passing strongly-typed data (or using the kotlin-args library) or use Parcelable/Json.

Example:

```kotlin
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "list") {
        composable("list") { ListScreen(onItemClick = { id -> navController.navigate("detail/$id") }) }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { backstack ->
            val id = backstack.arguments?.getInt("id")
            DetailScreen(id = id)
        }
    }
}
```

Use nested graphs for scoping ViewModels and separation of concerns.

---

## Theming & Material

- Compose has Material and Material3 libraries. Use MaterialTheme for colors, typography, and shapes.

Example app setup in Activity:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTheme {
                AppNavHost()
            }
        }
    }
}
```

Define theme colors and typography and apply them via MaterialTheme.

---

## Animations

- Compose provides high-level animation APIs: animate*AsState, updateTransition, AnimatedVisibility, and low-level APIs for custom animations.

Simple animated visibility:

```kotlin
@Composable
fun FadeInOut(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible) {
        content()
    }
}
```

Animate value example:

```kotlin
val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f)
Box(Modifier.alpha(alpha)) { /* ... */ }
```

---

## Interop with Views and XML

- Use AndroidView to embed traditional Android Views inside Compose.
- Use ComposeView to add Compose content to existing XML-based screens.

AndroidView example:

```kotlin
AndroidView(factory = { context ->
    MapView(context).apply { // configure
    }
}, update = { mapView -> /* update */ })
```

---

## Testing & tooling

- Unit test composables with createComposeRule() and ComposeTestRule.
- Use testTag modifiers and semantics to assert UI state.
- Use @Preview for fast iteration and visual inspection.

Basic UI test snippet:

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun counterIncrements() {
    composeTestRule.setContent { Counter() }
    composeTestRule.onNodeWithText("Count: 0").assertExists()
    composeTestRule.onNodeWithText("Count: 0").performClick()
    composeTestRule.onNodeWithText("Count: 1").assertExists()
}
```

---

## Performance tips & common pitfalls

- Keep composables small and focused to limit recomposition scope.
- Prefer derivedStateOf for expensive derived computations.
- Avoid capturing mutable objects without stable identity as parameters; use immutable data classes or remember.
- Use keys in Lazy lists to maintain item identity and avoid flicker.
- Avoid long-running work in composition; use side-effects.
- Do not call suspend functions directly in @Composable bodies; use producedState/LaunchedEffect.

---

## Quick reference

- Local state: remember { mutableStateOf(...) } / rememberSaveable
- ViewModel state: StateFlow / LiveData -> collectAsState() / observeAsState()
- Event handling: callbacks (onClick, onValueChange) passed down by hoisting state
- Lists: LazyColumn / LazyRow with keys
- Navigation: rememberNavController() + NavHost
- UI tests: createComposeRule, Modifier.testTag

---

Compose is a rich, evolving toolkit. Use unidirectional data flow, prefer ViewModel + StateFlow for app state, and keep UI logic in composables minimal and declarative. For deep dives, consult the official Jetpack Compose documentation and Android Studio tooling guides.
