---
name: rive-interactive
description: State machine-based vector animation with runtime interactivity, Android Jetpack Compose, Kotlin Multiplatform, and RML authoring. Use this skill when creating interactive animations, state-driven UI, animated components with logic, designer-created animations with runtime control, or multi-layer state machines. Triggers on tasks involving Rive, state machines, interactive vector animations, animation with input handling, ViewModel data binding, React Rive, Android Rive, Compose Multiplatform, or RML authoring.
metadata:
  mcpmarket-version: 1.0.0
---
# Rive Interactive - State Machine-Based Vector Animation

## Overview

Rive is a state machine-based animation platform that enables designers and developers to create interactive vector animations with complex logic and runtime interactivity. Unlike timeline-only animation tools (like Lottie), Rive supports state machines, input handling, and two-way data binding between application code and animations across Web, Android (Kotlin / Jetpack Compose / KMP), iOS, and Flutter.

**Key Features**:
- State machine system for complex interactive logic
- Multi-layer parallel state machines (e.g. Blink, Breathe, Gaze, Speech)
- 1D/2D BlendStates for smooth procedural transitions (e.g. eye tracking, gestures)
- ViewModel API for two-way data binding
- Input handling (boolean, number, trigger inputs)
- Custom events for animation-to-code communication
- Cross-platform support (Web, React, React Native, iOS, Android Jetpack Compose / KMP, Flutter)
- RML (Rive Markup Language) declarative authoring and Luau scripting
- Small file sizes with crisp, resolution-independent vector graphics

**When to Use This Skill**:
- Creating UI animations with complex state transitions
- Building interactive animated components (buttons, toggles, loaders)
- Implementing game-like UI with state-driven animations
- Binding real-time data to animated visualizations
- Creating animations that respond to user input
- Working with designer-created animations requiring runtime control

**Alternatives**:
- **Lottie** (lottie-animations): For simpler timeline-based animations without state machines
- **Framer Motion** (motion-framer): For code-first React animations with spring physics
- **GSAP** (gsap-scrolltrigger): For timeline-based web animations with precise control

## Core Concepts

### 1. State Machines

State machines define animation behavior with states and transitions:
- **States**: Different animation states (e.g., idle, hover, pressed)
- **Inputs**: Variables that control transitions (boolean, number, trigger)
- **Transitions**: Rules for moving between states
- **Listeners**: React hooks to respond to state changes

### 2. Inputs

Three input types control state machine behavior:
- **Boolean**: On/off states (e.g., isHovered, isActive)
- **Number**: Numeric values (e.g., progress, volume)
- **Trigger**: One-time events (e.g., click, submit)

### 3. ViewModels

Data binding system for dynamic properties:
- **String Properties**: Text content (e.g., username, title)
- **Number Properties**: Numeric data (e.g., stock price, score)
- **Color Properties**: Dynamic colors (hex values)
- **Enum Properties**: Selection from predefined options
- **Trigger Properties**: Animation events

### 4. Events

Custom events emitted from animations:
- **General Events**: Custom named events
- **Event Properties**: Data attached to events
- **Event Listeners**: React hooks to handle events

## Common Patterns

### Pattern 1: Basic Rive Animation

**Use Case**: Display a simple Rive animation in React

**Implementation**:

```bash
# Installation
npm install rive-react
```

```jsx
import Rive from 'rive-react';

export default function SimpleAnimation() {
  return (
    <Rive
      src="animation.riv"
      artboard="Main"
      animations="idle"
      layout={{ fit: "contain", alignment: "center" }}
      style={{ width: '400px', height: '400px' }}
    />
  );
}
```

**Key Points**:
- `src`: Path to .riv file
- `artboard`: Which artboard to display
- `animations`: Which animation timeline to play
- `layout`: How animation fits in container

### Pattern 2: State Machine Control with Inputs

**Use Case**: Control animation states based on user interaction

**Implementation**:

```jsx
import { useRive, useStateMachineInput } from 'rive-react';

export default function InteractiveButton() {
  const { rive, RiveComponent } = useRive({
    src: 'button.riv',
    stateMachines: 'Button State Machine',
    autoplay: true,
  });

  // Get state machine inputs
  const hoverInput = useStateMachineInput(
    rive,
    'Button State Machine',
    'isHovered',
    false
  );

  const clickInput = useStateMachineInput(
    rive,
    'Button State Machine',
    'isClicked',
    false
  );

  return (
    <div
      onMouseEnter={() => hoverInput && (hoverInput.value = true)}
      onMouseLeave={() => hoverInput && (hoverInput.value = false)}
      onClick={() => clickInput && clickInput.fire()} // Trigger input
      style={{ cursor: 'pointer' }}
    >
      <RiveComponent style={{ width: '200px', height: '100px' }} />
    </div>
  );
}
```

**Input Types**:
- Boolean: `input.value = true/false`
- Number: `input.value = 50`
- Trigger: `input.fire()`

### Pattern 3: ViewModel Data Binding

**Use Case**: Bind application data to animation properties

**Implementation**:

```jsx
import { useRive, useViewModel, useViewModelInstance,
         useViewModelInstanceString, useViewModelInstanceNumber } from 'rive-react';
import { useEffect, useState } from 'react';

export default function Dashboard() {
  const [stockPrice, setStockPrice] = useState(150.0);

  const { rive, RiveComponent } = useRive({
    src: 'dashboard.riv',
    autoplay: true,
    autoBind: false, // Manual binding for ViewModels
  });

  // Get ViewModel and instance
  const viewModel = useViewModel(rive, { name: 'Dashboard' });
  const viewModelInstance = useViewModelInstance(viewModel, { rive });

  // Bind properties
  const { setValue: setTitle } = useViewModelInstanceString(
    'title',
    viewModelInstance
  );

  const { setValue: setPrice } = useViewModelInstanceNumber(
    'stockPrice',
    viewModelInstance
  );

  useEffect(() => {
    if (setTitle) setTitle('Stock Dashboard');
  }, [setTitle]);

  useEffect(() => {
    if (setPrice) setPrice(stockPrice);
  }, [setPrice, stockPrice]);

  // Simulate real-time updates
  useEffect(() => {
    const interval = setInterval(() => {
      setStockPrice((prev) => prev + (Math.random() - 0.5) * 10);
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  return <RiveComponent style={{ width: '800px', height: '600px' }} />;
}
```

**ViewModel Property Hooks**:
- `useViewModelInstanceString` - Text properties
- `useViewModelInstanceNumber` - Numeric properties
- `useViewModelInstanceColor` - Color properties (hex)
- `useViewModelInstanceEnum` - Enum selection
- `useViewModelInstanceTrigger` - Animation triggers

### Pattern 4: Handling Rive Events

**Use Case**: React to events emitted from Rive animation

**Implementation**:

```jsx
import { useRive, EventType, RiveEventType } from 'rive-react';
import { useEffect } from 'react';

export default function InteractiveRating() {
  const { rive, RiveComponent } = useRive({
    src: 'rating.riv',
    stateMachines: 'State Machine 1',
    autoplay: true,
    automaticallyHandleEvents: true,
  });

  useEffect(() => {
    if (!rive) return;

    const onRiveEvent = (event) => {
      const eventData = event.data;

      if (eventData.type === RiveEventType.General) {
        console.log('Event:', eventData.name);

        // Access event properties
        const rating = eventData.properties.rating;
        const message = eventData.properties.message;

        if (rating >= 4) {
          alert(`Thanks for ${rating} stars: ${message}`);
        }
      }
    };

    rive.on(EventType.RiveEvent, onRiveEvent);

    return () => {
      rive.off(EventType.RiveEvent, onRiveEvent);
    };
  }, [rive]);

  return <RiveComponent style={{ width: '400px', height: '300px' }} />;
}
```

### Pattern 5: Preloading Rive Files

**Use Case**: Optimize load times by preloading animations

**Implementation**:

```jsx
import { useRiveFile, useRive } from 'rive-react';

export default function PreloadedAnimation() {
  const { riveFile, status } = useRiveFile({
    src: 'large-animation.riv',
  });

  const { RiveComponent } = useRive({
    riveFile: riveFile,
    artboard: 'Main',
    autoplay: true,
  });

  if (status === 'loading') {
    return <div>Loading animation...</div>;
  }

  if (status === 'failed') {
    return <div>Failed to load animation</div>;
  }

  return <RiveComponent style={{ width: '600px', height: '400px' }} />;
}
```

### Pattern 6: Controlled Animation with Refs

**Use Case**: Control animation from parent component

**Implementation**:

```jsx
import { useRive, useViewModel, useViewModelInstance,
         useViewModelInstanceTrigger } from 'rive-react';
import { useImperativeHandle, forwardRef } from 'react';

const AnimatedComponent = forwardRef((props, ref) => {
  const { rive, RiveComponent } = useRive({
    src: 'logo.riv',
    autoplay: true,
    autoBind: false,
  });

  const viewModel = useViewModel(rive, { useDefault: true });
  const viewModelInstance = useViewModelInstance(viewModel, { rive });

  const { trigger: spinTrigger } = useViewModelInstanceTrigger(
    'triggerSpin',
    viewModelInstance
  );

  // Expose methods to parent
  useImperativeHandle(ref, () => ({
    spin: () => spinTrigger && spinTrigger(),
    pause: () => rive && rive.pause(),
    play: () => rive && rive.play(),
  }));

  return <RiveComponent style={{ width: '200px', height: '200px' }} />;
});

export default function App() {
  const animationRef = useRef();

  return (
    <div>
      <AnimatedComponent ref={animationRef} />
      <button onClick={() => animationRef.current?.spin()}>Spin</button>
      <button onClick={() => animationRef.current?.pause()}>Pause</button>
    </div>
  );
}
```

### Pattern 7: Multi-Property ViewModel Updates

**Use Case**: Update multiple animation properties from complex data

**Implementation**:

```jsx
import { useRive, useViewModel, useViewModelInstance,
         useViewModelInstanceString, useViewModelInstanceNumber,
         useViewModelInstanceColor } from 'rive-react';
import { useEffect } from 'react';

export default function UserProfile({ user }) {
  const { rive, RiveComponent } = useRive({
    src: 'profile.riv',
    autoplay: true,
    autoBind: false,
  });

  const viewModel = useViewModel(rive, { useDefault: true });
  const viewModelInstance = useViewModelInstance(viewModel, { rive });

  // Bind all properties
  const { setValue: setName } = useViewModelInstanceString('name', viewModelInstance);
  const { setValue: setScore } = useViewModelInstanceNumber('score', viewModelInstance);
  const { setValue: setColor } = useViewModelInstanceColor('avatarColor', viewModelInstance);

  useEffect(() => {
    if (user && setName && setScore && setColor) {
      setName(user.name);
      setScore(user.score);
      setColor(parseInt(user.color.substring(1), 16)); // Convert hex to number
    }
  }, [user, setName, setScore, setColor]);

  return <RiveComponent style={{ width: '300px', height: '300px' }} />;
}
```

## Integration Patterns

### With Framer Motion (motion-framer)

Animate container while Rive handles interactive content:

```jsx
import { motion } from 'framer-motion';
import Rive from 'rive-react';

export default function AnimatedCard() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      whileHover={{ scale: 1.05 }}
    >
      <Rive
        src="card.riv"
        stateMachines="Card State Machine"
        style={{ width: '300px', height: '400px' }}
      />
    </motion.div>
  );
}
```

### With GSAP ScrollTrigger (gsap-scrolltrigger)

Trigger Rive animations on scroll:

```jsx
import { useRive, useStateMachineInput } from 'rive-react';
import { useEffect, useRef } from 'react';
import gsap from 'gsap';
import ScrollTrigger from 'gsap/ScrollTrigger';

gsap.registerPlugin(ScrollTrigger);

export default function ScrollRive() {
  const containerRef = useRef();
  const { rive, RiveComponent } = useRive({
    src: 'scroll-animation.riv',
    stateMachines: 'State Machine 1',
    autoplay: true,
  });

  const trigger = useStateMachineInput(rive, 'State Machine 1', 'trigger');

  useEffect(() => {
    if (!trigger) return;

    ScrollTrigger.create({
      trigger: containerRef.current,
      start: 'top center',
      onEnter: () => trigger.fire(),
    });
  }, [trigger]);

  return (
    <div ref={containerRef}>
      <RiveComponent style={{ width: '100%', height: '600px' }} />
    </div>
  );
}
```

### With Android Jetpack Compose & Compose Multiplatform

In Android Kotlin Multiplatform (KMP) or Jetpack Compose, use `AndroidView` wrapping `app.rive.runtime.kotlin.RiveAnimationView`:

```kotlin
// build.gradle.kts (androidMain dependencies)
implementation("app.rive:rive-android:3.1.5") // or latest stable

// 1. Early JNI Initialization in MainActivity.onCreate()
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            System.loadLibrary("rive-android")
            app.rive.runtime.kotlin.core.Rive.init(applicationContext)
        } catch (t: Throwable) {
            Log.e("Rive", "Failed early JNI load: ${t.message}")
        }
    }
}

// 2. Safe Compose AndroidView with Error Boundary and Canvas Fallback
@Composable
fun RiveAvatarView(
    modifier: Modifier = Modifier,
    gazeX: Float = 0f, // [-1.0f, 1.0f]
    gazeY: Float = 0f, // [-1.0f, 1.0f]
    isSpeaking: Boolean = false,
    resId: Int = R.raw.avatar
) {
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
        // Fallback gracefully (e.g. Compose Canvas Avatar)
        ComposeCanvasAvatarFallback(modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            try {
                app.rive.runtime.kotlin.core.Rive.init(ctx.applicationContext)
                RiveAnimationView(ctx).apply {
                    setRiveResource(
                        resId = resId,
                        stateMachineName = "State Machine 1",
                        autoplay = true
                    )
                }
            } catch (t: Throwable) {
                isRiveReady = false
                View(ctx)
            }
        },
        update = { view ->
            if (view !is RiveAnimationView) return@AndroidView
            try {
                // Ensure render loop remains active
                if (!view.isPlaying) view.play()

                // Guard inputs to prevent StateMachineInputException on background thread
                val sm = view.stateMachines.firstOrNull() ?: view.playingStateMachines.firstOrNull()
                val validInputs = sm?.inputNames ?: emptyList()

                // Map [-1.0, 1.0] to [0.0, 100.0] for 1D BlendStates
                if (validInputs.contains("gazeX")) {
                    val normalizedGazeX = ((gazeX.coerceIn(-1f, 1f) + 1f) / 2f) * 100f
                    view.setNumberState("State Machine 1", "gazeX", normalizedGazeX)
                }
                if (validInputs.contains("gazeY")) {
                    val normalizedGazeY = ((gazeY.coerceIn(-1f, 1f) + 1f) / 2f) * 100f
                    view.setNumberState("State Machine 1", "gazeY", normalizedGazeY)
                }
                if (validInputs.contains("isSpeaking")) {
                    view.setBooleanState("State Machine 1", "isSpeaking", isSpeaking)
                }
            } catch (t: Throwable) {
                Log.w("Rive", "Update error: ${t.message}")
            }
        }
    )
}
```

### With RML Multi-Layer Parallel State Machines & 1D BlendStates

RML (Rive Markup Language) allows declarative definition of rich, living characters with multi-layer state machines:

```xml
<!-- RML Draw Order Rule: "The first sibling draws on top" -->
<!-- Always declare foreground elements FIRST, background elements LAST -->
<Node name="LeftEyeContainer">
  <!-- 1. Topmost highlight -->
  <Shape name="LeftHighlight"><Circle x="5" y="-5" radius="3"/><SolidColor color="#FFFFFF"/></Shape>
  <!-- 2. Middle pupil -->
  <Shape name="LeftPupil"><Circle radius="12"/><SolidColor color="#0A1128"/></Shape>
  <!-- 3. Bottom eye socket -->
  <Shape name="LeftEyeSocket"><Rectangle width="44" height="44" cornerRadius="14"/><SolidColor color="#00F0FF"/></Shape>
</Node>

<!-- 6-Layer Parallel Native State Machine Architecture -->
<StateMachine name="State Machine 1">
  <!-- Layer 1: Natural Periodic Blink -->
  <StateMachineLayer name="BlinkLayer">
    <EntryState><StateTransition stateToId="blinkAnim"/></EntryState>
    <AnimationState animationId="AnimBlink" loopValue="loop" id="blinkAnim"/>
  </StateMachineLayer>

  <!-- Layer 2: Cyber-Breathing Idle Pulse -->
  <StateMachineLayer name="BreatheLayer">
    <EntryState><StateTransition stateToId="breatheAnim"/></EntryState>
    <AnimationState animationId="AnimBreathe" loopValue="loop" id="breatheAnim"/>
  </StateMachineLayer>

  <!-- Layer 3: Organic Micro-Saccades (Eye Jitter) -->
  <StateMachineLayer name="MicroSaccadesLayer">
    <EntryState><StateTransition stateToId="saccadeAnim"/></EntryState>
    <AnimationState animationId="AnimMicroSaccade" loopValue="loop" id="saccadeAnim"/>
  </StateMachineLayer>

  <!-- Layer 4: 1D BlendState Horizontal Gaze (0 = Left, 50 = Center, 100 = Right) -->
  <StateMachineLayer name="GazeHorizontalLayer">
    <EntryState><StateTransition stateToId="gazeH"/></EntryState>
    <BlendState1D inputName="gazeX" id="gazeH">
      <BlendState1DInput animationId="LookLeft" value="0"/>
      <BlendState1DInput animationId="LookCenterH" value="50"/>
      <BlendState1DInput animationId="LookRight" value="100"/>
    </BlendState1D>
  </StateMachineLayer>

  <!-- Layer 5: 1D BlendState Vertical Gaze (0 = Up, 50 = Center, 100 = Down) -->
  <StateMachineLayer name="GazeVerticalLayer">
    <EntryState><StateTransition stateToId="gazeV"/></EntryState>
    <BlendState1D inputName="gazeY" id="gazeV">
      <BlendState1DInput animationId="LookUp" value="0"/>
      <BlendState1DInput animationId="LookCenterV" value="50"/>
      <BlendState1DInput animationId="LookDown" value="100"/>
    </BlendState1D>
  </StateMachineLayer>

  <!-- Layer 6: Dynamic Speech & Lip Sync -->
  <StateMachineLayer name="SpeechLayer">
    <EntryState><StateTransition stateToId="mouthRest"/></EntryState>
    <AnimationState animationId="MouthRest" id="mouthRest">
      <StateTransition stateToId="mouthTalking">
        <TransitionBoolCondition inputName="isSpeaking" op="equal" value="true"/>
      </StateTransition>
    </AnimationState>
    <AnimationState animationId="Talking" id="mouthTalking">
      <StateTransition stateToId="mouthRest">
        <TransitionBoolCondition inputName="isSpeaking" op="equal" value="false"/>
      </StateTransition>
    </AnimationState>
  </StateMachineLayer>
</StateMachine>
```

## Performance Optimization

### 1. Use Off-Screen Renderer

```jsx
<Rive
  src="animation.riv"
  useOffscreenRenderer={true} // Better performance
/>
```

### 2. Optimize Rive Files

**In Rive Editor**:
- Keep artboards under 2MB
- Use vector graphics (avoid raster images when possible)
- Minimize number of bones in skeletal animations
- Reduce complexity of state machines

### 3. Preload Critical Animations

```jsx
const { riveFile } = useRiveFile({ src: 'critical.riv' });
// Preload during app initialization
```

### 4. Disable Automatic Event Handling

```jsx
<Rive
  src="animation.riv"
  automaticallyHandleEvents={false} // Manual control
/>
```

## Common Pitfalls and Solutions

### Pitfall 1: State Machine Input Not Found

**Problem**: `useStateMachineInput` returns null

**Solution**:
```jsx
// ❌ Wrong: Incorrect input name
const input = useStateMachineInput(rive, 'State Machine', 'wrongName');

// ✅ Correct: Match exact name from Rive editor
const input = useStateMachineInput(rive, 'State Machine', 'isHovered');

// Always check if input exists before using
if (input) {
  input.value = true;
}
```

### Pitfall 2: ViewModel Property Not Updating

**Problem**: ViewModel property doesn't update animation

**Solution**:
```jsx
// ❌ Wrong: autoBind enabled
const { rive } = useRive({
  src: 'dashboard.riv',
  autoplay: true,
  // autoBind: true (default)
});

// ✅ Correct: Disable autoBind for ViewModels
const { rive } = useRive({
  src: 'dashboard.riv',
  autoplay: true,
  autoBind: false, // Required for manual ViewModel control
});
```

### Pitfall 3: Event Listener Not Firing

**Problem**: Rive events not triggering callback

**Solution**:
```jsx
// ❌ Wrong: Missing automaticallyHandleEvents
const { rive } = useRive({
  src: 'rating.riv',
  stateMachines: 'State Machine 1',
  autoplay: true,
});

// ✅ Correct: Enable event handling
const { rive } = useRive({
  src: 'rating.riv',
  stateMachines: 'State Machine 1',
  autoplay: true,
  automaticallyHandleEvents: true, // Required for events
});
```

### Pitfall 4: Android JNI UnsatisfiedLinkError on FileAssetLoader

**Problem**: Crash on launch with `No implementation found for long app.rive.runtime.kotlin.core.FileAssetLoader.constructor() (tried Java_app_rive_runtime_kotlin_core_FileAssetLoader_constructor... - is the library loaded, e.g. System.loadLibrary?)`

**Cause**: In Jetpack Compose, `AndroidView.factory` runs synchronously before asynchronous `LaunchedEffect` hooks, invoking native constructors before `librive-android.so` is loaded.

**Solution**:
```kotlin
// In MainActivity.onCreate():
System.loadLibrary("rive-android")
Rive.init(applicationContext)

// In Composable: Initialize synchronously in remember, and provide a Compose fallback:
var isReady by remember {
    mutableStateOf(
        try {
            System.loadLibrary("rive-android")
            Rive.init(context.applicationContext)
            true
        } catch (t: Throwable) { false }
    )
}
if (!isReady) ComposeCanvasFallback()
```

### Pitfall 5: Android Fatal Background Thread Crash: StateMachineInputException

**Problem**: App crashes on background render thread: `FATAL EXCEPTION: Thread-XX app.rive.runtime.kotlin.core.errors.StateMachineInputException: No StateMachineInput found with name gazeX.`

**Cause**: Calling `view.setNumberState("State Machine 1", "gazeX", ...)` queues inputs into `RiveFileController`. When the background render thread executes `processAllInputs()`, it throws an uncaught exception if that input is missing in the `.riv` file.

**Solution**:
```kotlin
// Always check input names before dispatching:
val sm = view.stateMachines.firstOrNull() ?: view.playingStateMachines.firstOrNull()
val validInputs = sm?.inputNames ?: emptyList()

if (validInputs.contains("gazeX")) {
    view.setNumberState("State Machine 1", "gazeX", value)
}
```

### Pitfall 6: Freeze at Frame 0 (Renderer Stops Loop)

**Problem**: Rive animation displays fine at first, but never animates and remains completely static.

**Cause**: When a State Machine starts with no active looping animation, `stateMachineInstance.advance()` returns `false`. `RiveFileController` then stops the rendering loop to conserve battery.

**Solution**:
1. Ensure the State Machine connects from `EntryState` to an active loop (e.g. `IdleLoop`, `Breathe`, or BlendState).
2. In Compose `update`, call `if (!view.isPlaying) view.play()` to wake up the render loop whenever dynamic parameters change.

### Pitfall 7: RML Draw Order Occlusion (First Sibling Draws on Top)

**Problem**: Pupils, eyes, or foreground highlights disappear behind background shapes.

**Cause**: In RML / Rive scene trees, **the first sibling draws on top of subsequent siblings** (unlike HTML/CSS z-index or standard canvas painters where subsequent draws go on top).

**Solution**:
```xml
<!-- Declare topmost/foreground elements FIRST -->
<Node name="Eye">
  <Shape name="Highlight"/> <!-- 1. Drawn on top (Front) -->
  <Shape name="Pupil"/>     <!-- 2. Drawn in middle -->
  <Shape name="Socket"/>    <!-- 3. Drawn on bottom (Back) -->
</Node>
```

## Resources

### Official Documentation
- **Rive Docs**: https://rive.app/docs
- **React Rive GitHub**: https://github.com/rive-app/rive-react
- **Rive Community**: https://rive.app/community

### Rive Editor
- **Web Editor**: https://rive.app/community
- **Desktop App**: Available for macOS, Windows

### Learning Resources
- **Tutorials**: https://rive.app/learn
- **Examples**: https://rive.app/community/files
- **State Machine Guide**: https://rive.app/docs/state-machine

## Related Skills

- **lottie-animations**: For simpler timeline-based animations without state machines
- **motion-framer**: For code-first React animations with gestures
- **gsap-scrolltrigger**: For scroll-driven animations
- **spline-interactive**: For 3D interactive animations

## Scripts

This skill includes utility scripts:
- `component_generator.py` - Generate Rive React component boilerplate
- `viewmodel_builder.py` - Build ViewModel property bindings

Run scripts from the skill directory:
```bash
./scripts/component_generator.py
./scripts/viewmodel_builder.py
```

## Assets

Starter templates and examples:
- `starter_rive/` - Complete React + Rive template
- `examples/` - Real-world integration patterns
