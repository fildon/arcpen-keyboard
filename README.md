# ArcPen Keyboard

A gesture keyboard for Android. Instead of a grid of keys, ArcPen uses a circular gesture interface — you draw short arcs to select characters, keeping your thumb in one place.

---

## Contents

- [How it works](#how-it-works)
- [Character map](#character-map)
- [Special inputs](#special-inputs)
- [Installing on your phone](#installing-on-your-phone)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Modifying the character layout](#modifying-the-character-layout)

---

## How it works

The keyboard is a circle divided into four quadrants: **North** (top), **East** (right), **South** (bottom), **West** (left).

Every character is identified by three properties of a gesture:

| Property | Meaning |
|---|---|
| **Starting quadrant** | The first quadrant your finger enters after leaving the centre |
| **Direction** | Clockwise (CW) or counterclockwise (CCW) — set by the first quadrant boundary you cross |
| **Depth** | How many quadrant boundaries you cross before returning to centre (1–4) |

**Step by step:**

1. **Touch** the red centre disc to begin
2. **Sweep** into a quadrant — this sets the starting quadrant, but no character is selected yet
3. **Arc** CW or CCW across one or more quadrant boundaries — this sets direction and depth
4. **Return** to the centre disc — the character commits on release

Returning to centre mid-drag commits the current character and immediately starts a new gesture, so you can type continuously without lifting your finger.

**Correcting an overshoot:** if you arc too far, simply reverse direction — each boundary crossed in reverse decrements the depth by one. Reversing all the way back to the starting quadrant (depth 0) also resets the direction lock, letting you choose CW or CCW again.

```
              North
               (0)
                │
    CCW ◄───────┼───────► CW
                │
   West        (●)        East
   (3)       centre        (1)
                │
                │
              South
               (2)

  CW rotation on screen: North → East → South → West → North
```

---

## Character map

Characters are placed in strict frequency order: the most common characters require the shallowest arcs. Depth-1 characters cover the eight most common English letters; depth-4 characters cover the rarest letters and punctuation.

```
         CCW  │  CW
       ───────┼───────
North    e d1 │ t d1
         r d2 │ d d2
         y d3 │ g d3
         q d4 │ z d4

East     a d1 │ o d1
         l d2 │ c d2
         p d3 │ b d3
         j d4 │ x d4

South    i d1 │ n d1
         u d2 │ m d2
         v d3 │ k d3
         ! d4 │ ? d4

West     s d1 │ h d1
         f d2 │ w d2
         , d3 │ . d3   ← punctuation sector
         - d4 │ ' d4
```

**Example gestures:**

| Character | Gesture |
|---|---|
| `e` | Centre → North → return (CCW, depth 1) |
| `t` | Centre → North → return (CW, depth 1) |
| `h` | Centre → West → arc CW into North → return |
| `.` | Centre → West → arc CW into North → arc CW into East → return |
| `'` | Centre → West → arc CW all the way round → return (depth 4) |

---

## Special inputs

| Action | How |
|---|---|
| **Space** | Tap the centre disc (no arc) |
| **Period + space** | Double-tap the centre disc within 300 ms — also auto-capitalises the next letter |
| **Backspace** | Long-press the centre disc, or tap DEL in the top-right corner |
| **Enter** | Tap ENT in the bottom-right corner |
| **Shift (once)** | Tap SHF in the top-left corner — capitalises the next letter only |
| **Caps lock** | Tap SHF twice — all letters uppercase (shown as CAP) until tapped again |
| **Numeric mode** | Tap 123 in the bottom-left corner — shows a dial-pad; tap ABC to return |

---

## Installing on your phone

### Prerequisites

- An Android phone running Android 5.0 (API 21) or later
- USB debugging enabled (see below) **or** a way to transfer the APK file

### Enable USB debugging (one-time)

1. Settings → About phone → tap **Build number** seven times
2. Settings → System → Developer options → enable **USB debugging**
3. Connect via USB and tap **Allow** on the authorisation prompt that appears on your phone

### Install via ADB

```powershell
# Check device is recognised
adb devices

# Install (replace path with wherever you built the APK)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Activate the keyboard

1. Settings → General management → Keyboard list and default (exact path varies by Android version)
2. Toggle **ArcPen** on
3. Open any text field, long-press the keyboard switcher icon in the navigation bar, and select **ArcPen**

Or open the **ArcPen** app on your phone — it has buttons that take you directly to the relevant settings screens.

---

## Building from source

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Hedgehog 2023.1 or later) — this also installs the Android SDK and a bundled JDK

### Build in Android Studio

1. Open Android Studio → **Open** → select the `arcpen-keyboard` directory
2. Wait for Gradle sync to complete
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Build from the command line (Windows)

Android Studio's bundled JDK and the Gradle distribution downloaded during sync can be used directly:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.6-bin\*\gradle-8.6\bin\gradle.bat"
& (Resolve-Path $gradle) -p . assembleDebug
```

### One-liner: build + install to connected device

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = (Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.6-bin\*\gradle-8.6\bin\gradle.bat")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $gradle -p . assembleDebug && & $adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project structure

```
app/src/main/
├── AndroidManifest.xml          # Declares the IME service and launcher activity
├── java/com/arcpen/keyboard/
│   ├── CharacterLayout.kt       # Maps (sector, direction, depth) → character
│   ├── GestureTracker.kt        # Pure gesture state machine (no Android View dependency)
│   ├── ArcPenView.kt            # Custom View: renders the circle and handles touch
│   ├── ArcPenIMEService.kt      # InputMethodService: bridges view events to the editor
│   └── MainActivity.kt          # Setup activity with enable/select buttons
└── res/
    ├── xml/method.xml            # IME metadata (subtype declaration)
    └── values/                   # strings, colors, themes
```

### Key design decisions

**`GestureTracker` is a pure state machine.** It has no Android View imports and takes only `(x, y)` floats as input. This makes it straightforward to unit test in isolation.

**Shift state lives in `ArcPenView`.** The view owns the shift toggle and applies the transformation before calling `KeyListener.onCharacter`. The service receives already-shifted characters and does not need to track capitalisation itself.

**Mid-drag commits.** Returning to the centre disc mid-drag commits the current character via `GestureTracker.onMove` returning a non-null `Char`. The gesture state is soft-reset (active but cleared) so the next outward sweep starts a fresh gesture without requiring a new touch-down.

---

## Modifying the character layout

All 32 character assignments live in a single `mapOf` in `CharacterLayout.kt`. Each entry is:

```kotlin
GestureKey(sector, clockwise, depth) to 'character'
```

- `sector`: `0` = North, `1` = East, `2` = South, `3` = West
- `clockwise`: `true` = CW arc, `false` = CCW arc
- `depth`: `1`–`4` (number of quadrant boundaries crossed)

To swap two characters, change their target values. To verify your changes make sense, cross-reference with the frequency ordering documented in the `CharacterLayout.kt` header comment.
