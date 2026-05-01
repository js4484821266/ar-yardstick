# AR Yardstick

AR Yardstick is a Kotlin Android app for estimating physical lengths from an AR camera view. It uses ARCore hit tests and world-space coordinates, then projects measured geometry back onto the camera preview as an outline overlay.

## Features

- Tap two AR hit-test points to measure a straight-line distance.
- Tap three AR hit-test points to estimate a circle from a local 3D plane.
- Draws outline-only line and circle overlays with animated opacity.
- Displays measurement text near the measured geometry.
- Manual reference-object calibration using a credit card or A4 paper edge.
- Saves the camera preview plus overlays through Android scoped storage / MediaStore.
- Shows explicit messages for missing camera permission, ARCore/device support problems, hit-test failures, calibration status, and capture results.

## Measurement Notes

Internal distances are stored in meters. Display values are converted to mm, cm, or m and include the current calibration correction factor.

Measurements are not calculated from 2D pixel distances alone. The app uses ARCore hit tests, camera pose/direction, field-of-view checks, view/projection matrices, and 3D world coordinates.

## Limitations

- ARCore is required for measurement. Unsupported devices show an unsupported-device screen.
- Plane detection quality depends on device support, lighting, motion, and visible surface texture.
- Automatic reference-object detection is not implemented. Manual edge calibration is functional and intentionally exposed as the supported calibration path.
- Measurements are estimates and should not be used where certified precision is required.

## Build

This project is intended to build without Android Studio by using the Gradle Wrapper.

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

On Windows:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

You need a local Android SDK and a JDK compatible with the Android Gradle Plugin. Detailed local notes belong in `LOCAL_BUILD.md`, which is intentionally ignored for public-repo safety.
