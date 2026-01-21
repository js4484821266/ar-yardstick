# AR Yardstick - Android App

An Augmented Reality (AR) yardstick application for Android that allows users to measure distances using their device's camera and ARCore.

## Features

### Dual Measurement Modes

1. **Arbitrary Mode** (Default)
   - Automatically detects surfaces and allows users to place measurement points
   - Tap on detected surfaces to place points
   - Automatically calculates and displays the distance between consecutive points
   - Green measurement line connects points
   - Measurements displayed in centimeters

2. **Standard Mode**
   - Provides a 30cm reference line in AR space
   - Users can use this reference to estimate other distances
   - Red reference line for easy visibility
   - Helps calibrate measurement perception

### User Interface

- **Navigation Bar**: Always visible (not hidden) for quick app exit
- **Measurement Display**: 
  - High contrast text (white on black background)
  - 12-point font size for easy readability
  - Centered on screen
  - Shows distance in centimeters (format: "XX.XX cm")
- **Mode Switching**: Easy toggle between Arbitrary and Standard modes
- **Clear Button**: Reset all measurements and start fresh
- **Instructions**: Context-aware instructions based on current mode

## Requirements

- Android device with ARCore support
- Android 7.0 (API level 24) or higher
- Camera permission

## Project Structure

```
ar-yardstick/
├── app/
│   ├── build.gradle                          # App-level build configuration
│   ├── proguard-rules.pro                    # ProGuard rules
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml           # App manifest with permissions
│           ├── java/com/example/aryardstick/
│           │   └── MainActivity.kt           # Main activity with AR logic
│           └── res/
│               ├── drawable/
│               │   └── ic_launcher_foreground.xml
│               ├── layout/
│               │   └── activity_main.xml     # Main UI layout
│               ├── mipmap-*/
│               │   └── ic_launcher.xml       # App icons
│               └── values/
│                   ├── colors.xml            # Color definitions
│                   ├── strings.xml           # String resources
│                   └── themes.xml            # App themes
├── build.gradle                              # Project-level build configuration
├── gradle.properties                         # Gradle properties
├── settings.gradle                           # Gradle settings
└── gradlew                                   # Gradle wrapper script
```

## Building the App

### Prerequisites

1. Install Android Studio
2. Install Android SDK (API 33)
3. Ensure your device supports ARCore

### Build Steps

```bash
# Clone the repository
git clone https://github.com/js4484821266/ar-yardstick.git
cd ar-yardstick

# Build the app
./gradlew build

# Or open in Android Studio and click "Build > Build Bundle(s) / APK(s) > Build APK(s)"
```

## Usage

1. **Launch the App**: Open AR Yardstick on your ARCore-supported Android device
2. **Grant Camera Permission**: Allow camera access when prompted
3. **Wait for Surface Detection**: Move your device to help ARCore detect surfaces
4. **Choose a Mode**:
   - **Arbitrary Mode**: Tap "Switch Mode" if needed, then tap on surfaces to place measurement points
   - **Standard Mode**: Tap "Switch Mode", a 30cm reference line will appear
5. **View Measurements**: Distance appears in the center of the screen in centimeters
6. **Clear Measurements**: Tap "Clear" to remove all measurements and start over
7. **Exit**: Use the navigation bar (not hidden) to exit the app quickly

## Technical Details

### Dependencies

- **AndroidX Core KTX**: 1.10.1
- **AppCompat**: 1.6.1
- **Material Components**: 1.9.0
- **ConstraintLayout**: 2.1.4
- **ARCore**: 1.38.0
- **Sceneform**: 1.17.1

### Key Components

1. **MainActivity.kt**
   - Handles ARCore session management
   - Implements touch detection and surface placement
   - Manages mode switching between Arbitrary and Standard
   - Calculates distances using 3D vector math
   - Renders measurement points and lines in AR space

2. **activity_main.xml**
   - Responsive layout with AR view container
   - Control panel with mode display and action buttons
   - High-contrast measurement display (12pt, white on black)
   - Context-aware instruction text

3. **AndroidManifest.xml**
   - Camera permission declaration
   - ARCore required feature declaration
   - Portrait orientation lock for better AR experience

### AR Functionality

- **Surface Detection**: Uses ARCore's plane detection to identify surfaces
- **Anchor Points**: Creates anchors at touch points for persistent AR placement
- **Distance Calculation**: Uses 3D Euclidean distance formula
- **Visual Feedback**: 
  - Green spheres for measurement points in Arbitrary mode
  - Yellow spheres for measurement points in Standard mode
  - Green cylinders for measurement lines
  - Red cylinder for 30cm reference line

## Permissions

- **CAMERA**: Required for AR functionality

## Compatibility

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 33 (Android 13)
- Requires ARCore-supported device

## License

See LICENSE file in the repository.

## Acknowledgments

Built using Google ARCore and Sceneform libraries.
