# Android & RML Rive Reference

This guide provides deep implementation patterns for integrating Rive into Android Jetpack Compose, Kotlin Multiplatform (KMP), and authoring declarative RML (Rive Markup Language) state machines.

---

## 1. Android Dependencies

In `composeApp/build.gradle.kts` (or Android `build.gradle.kts`):

`kotlin
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation("app.rive:rive-android:3.1.5")
        }
    }
}
`

---

## 2. JNI Initialization Lifecycle

Rive's Android runtime wraps a high-performance C++ core. Attempting to instantiate `RiveAnimationView` or load `.riv` files before `librive-android.so` is loaded will throw an `UnsatisfiedLinkError` on `FileAssetLoader.constructor()`.

### Application / Activity Level (Mandatory)
`kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            System.loadLibrary("rive-android")
            app.rive.runtime.kotlin.core.Rive.init(applicationContext)
        } catch (t: Throwable) {
            Log.e("Rive", "Native JNI init error: ")
        }
    }
}
`

### Compose Guard & Fallback
`kotlin
@Composable
fun RiveContainer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isRiveReady by remember {
        mutableStateOf(
            try {
                System.loadLibrary("rive-android")
                app.rive.runtime.kotlin.core.Rive.init(context.applicationContext)
                true
            } catch (t: Throwable) {
                false
            }
        )
    }

    if (!isRiveReady) {
        FallbackCanvas(modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            try {
                app.rive.runtime.kotlin.core.Rive.init(ctx.applicationContext)
                RiveAnimationView(ctx).apply {
                    setRiveResource(R.raw.avatar, stateMachineName = "State Machine 1", autoplay = true)
                }
            } catch (t: Throwable) {
                isRiveReady = false
                View(ctx)
            }
        },
        update = { view ->
            if (view !is RiveAnimationView) return@AndroidView
            if (!view.isPlaying) view.play()
        }
    )
}
`

---

## 3. Defensive State Machine Inputs

When calling `setNumberState`, `setBooleanState`, or `fireState`, Rive enqueues the operation to a dedicated render thread. If the state machine does not declare that input, an uncaught `StateMachineInputException` crashes the app.

`kotlin
fun updateRiveInputs(
    view: RiveAnimationView,
    gazeX: Float,
    gazeY: Float,
    isSpeaking: Boolean
) {
    val sm = view.stateMachines.firstOrNull() ?: view.playingStateMachines.firstOrNull()
    val validInputs = sm?.inputNames ?: return

    if (validInputs.contains("gazeX")) {
        val normalized = ((gazeX.coerceIn(-1f, 1f) + 1f) / 2f) * 100f
        view.setNumberState("State Machine 1", "gazeX", normalized)
    }
    if (validInputs.contains("isSpeaking")) {
        view.setBooleanState("State Machine 1", "isSpeaking", isSpeaking)
    }
}
`

---

## 4. RML (Rive Markup Language) Declarative Architecture

### Draw Order Rule
In RML: **The first sibling element draws ON TOP of subsequent siblings.**
`xml
<!-- Example: Correct Z-ordering -->
<Node name="Eye">
  <!-- Topmost: Specular white reflection -->
  <Shape name="Highlight"><Circle radius="3" fill="#FFFFFF"/></Shape>
  <!-- Middle: Pupil pupil iris -->
  <Shape name="Pupil"><Circle radius="12" fill="#0A1128"/></Shape>
  <!-- Bottom: Sclera / eye background -->
  <Shape name="Sclera"><Rectangle width="40" height="40" cornerRadius="12" fill="#00F0FF"/></Shape>
</Node>
`

### 1D BlendStates for Continuous Directional Gaze
`xml
<StateMachineLayer name="GazeHorizontalLayer">
  <EntryState><StateTransition stateToId="gazeH"/></EntryState>
  <BlendState1D inputName="gazeX" id="gazeH">
    <BlendState1DInput animationId="LookLeft" value="0"/>
    <BlendState1DInput animationId="LookCenterH" value="50"/>
    <BlendState1DInput animationId="LookRight" value="100"/>
  </BlendState1D>
</StateMachineLayer>
`

### Multi-Layer Parallel Execution
A single State Machine can run multiple independent layers concurrently:
- Layer 1: Looping blink cycle (`loopValue="loop"`)
- Layer 2: Breathing scale oscillation
- Layer 3: Micro-saccades jitter
- Layer 4: Horizontal gaze blend state
- Layer 5: Vertical gaze blend state
- Layer 6: Lip sync / speech state transitions
