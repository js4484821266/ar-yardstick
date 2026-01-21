# AR Yardstick - App Architecture

## Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity.kt                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  UI Components                                        │  │
│  │  • Mode Text (Arbitrary/Standard)                     │  │
│  │  • Switch Mode Button                                 │  │
│  │  • Clear Button                                       │  │
│  │  • Instruction Text                                   │  │
│  │  • Measurement Display (16sp, white on black, bold)   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  AR Scene Management                                  │  │
│  │  • ArSceneView (ARCore integration)                   │  │
│  │  • Surface detection                                  │  │
│  │  • Camera tracking                                    │  │
│  │  • Touch event handling                               │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Measurement Logic                                    │  │
│  │  ┌─────────────────┐      ┌─────────────────┐         │  │
│  │  │ Arbitrary Mode  │      │ Standard Mode   │         │  │
│  │  │                 │      │                 │         │  │
│  │  │ • Tap surfaces  │      │ • 30cm ref line │         │  │
│  │  │ • Place points  │      │ • Tap surfaces  │         │  │
│  │  │ • Auto measure  │      │ • Place points  │         │  │
│  │  │ • Green visual  │      │ • Auto measure  │         │  │
│  │  │                 │      │ • Yellow visual │         │  │
│  │  └─────────────────┘      └─────────────────┘         │  │
│  │                                                        │  │
│  │  Common: 3D Euclidean distance calculation            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## User Flow

### Arbitrary Mode (Default)
```
1. App Launch
   ↓
2. Grant Camera Permission
   ↓
3. ARCore initializes, detects surfaces
   ↓
4. User taps on detected surface
   ↓
5. Green sphere appears at tap point
   ↓
6. User taps second point
   ↓
7. Distance calculated and displayed
   ↓
8. Green line drawn between points
   ↓
9. Repeat steps 4-8 for more measurements
```

### Standard Mode
```
1. User clicks "Switch Mode" button
   ↓
2. Mode changes to Standard
   ↓
3. 30cm red reference line appears in AR
   ↓
4. User uses reference to estimate distances
   ↓
5. User taps on surfaces to measure
   ↓
6. Yellow spheres mark tap points
   ↓
7. Distance calculated and displayed
   ↓
8. Green line drawn between points
```

## Data Flow

```
Touch Event
    ↓
Hit Test (ARCore)
    ↓
Create Anchor
    ↓
Create AnchorNode
    ↓
Add to Scene
    ↓
Calculate Distance (if 2+ points)
    ↓
Display Measurement (16sp, white on black)
```

## Key Classes and Methods

### MainActivity
- **onCreate()**: Initialize UI and AR
- **initializeAR()**: Set up ARCore session
- **toggleMode()**: Switch between modes
- **handleTap()**: Process touch events
- **handleArbitraryModeTap()**: Place points in arbitrary mode
- **handleStandardModeTap()**: Place points in standard mode
- **createStandardReference()**: Create 30cm reference line
- **calculateDistance()**: Compute 3D Euclidean distance
- **showMeasurement()**: Display distance on screen
- **drawLine()**: Render line between points
- **clearMeasurements()**: Reset all measurements

## Visual Elements

### Arbitrary Mode
- Points: Green spheres (0.01m radius)
- Lines: Green cylinders (0.003m radius)
- Display: Distance in centimeters

### Standard Mode
- Reference: Red cylinder (30cm, 0.005m radius)
- Points: Yellow spheres (0.01m radius)
- Lines: Green cylinders (0.003m radius)
- Display: Distance in centimeters

## Permissions & Requirements
- Camera permission (requested at runtime)
- ARCore required (declared in manifest)
- Min SDK: 24 (Android 7.0)
- Target SDK: 33 (Android 13)
