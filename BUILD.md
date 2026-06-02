# Building 8pen

## Requirements
- Android Studio Hedgehog (2023.1) or later
- Android SDK 34

## Steps

1. Open `C:\work\8pen-keyboard` in Android Studio.
   Android Studio will download Gradle and sync automatically.

2. Build → Generate Signed APK (or just run to a connected device / emulator).

3. Install the APK on your phone, then:
   - Open the **8pen** app
   - Tap **Step 1: Enable 8pen** → turn on "8pen" in the keyboard list
   - Tap **Step 2: Select 8pen** → choose it as your current keyboard

## How to type

Each character is a short circular gesture:

1. **Touch** the red centre disc
2. **Sweep** into one of the four quadrants (N / E / S / W)
3. Optionally **arc** clockwise or counterclockwise through additional quadrants
4. **Return** to the centre disc — the character commits on release

Depth 1 = single quadrant (closest characters, most common letters)
Depth 2 = cross one boundary before returning
Depth 3 = cross two boundaries
Depth 4 = nearly full rotation

**Tap** centre → space  
**Long-press** centre → backspace  
**Bottom strip**: ⌫ | space | ↵

## Character map

```
          CCW │ CW
         ─────┼─────
North d1:  e  │  t
      d2:  r  │  d
      d3:  y  │  g
      d4:  q  │  z

East  d1:  a  │  o
      d2:  l  │  c
      d3:  p  │  b
      d4:  ,  │  .

South d1:  i  │  n
      d2:  u  │  m
      d3:  v  │  k
      d4:  !  │  ?

West  d1:  s  │  h
      d2:  f  │  w
      d3:  j  │  x
      d4:  -  │  /
```
