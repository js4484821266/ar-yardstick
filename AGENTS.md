# AGENTS.md

## Project
This repository contains an Android/Kotlin app named AR Yardstick.

## Build
The project must be buildable without Android Studio.

Required commands:
- ./gradlew clean
- ./gradlew assembleDebug
- ./gradlew installDebug

Windows equivalents:
- .\gradlew.bat clean
- .\gradlew.bat assembleDebug
- .\gradlew.bat installDebug

## Android requirements
- Kotlin.
- minSdk 31.
- Android 12 or newer.
- Prefer ARCore for AR measurement.
- Use runtime camera permission.
- Use MediaStore/scoped storage for saving captures.

## Measurement rules
- Never calculate real-world measurements from only 2D pixel distances.
- Use AR hit tests, camera pose, view/projection matrices, and world coordinates.
- Internal unit is meters.
- Display unit may be mm, cm, or m.
- Reference object calibration must apply a correction factor to displayed/exported measurements.

## Reference objects
Supported manual calibration objects:
- Credit card: 85.60mm x 53.98mm.
- A4 paper: 210mm x 297mm.

Automatic detection is optional. Do not fake it.

## Public repo safety
Do not commit:
- local.properties
- signing keys
- keystores
- google-services.json
- API keys
- tokens
- secrets
- .env files
- local build notes
- generated build artifacts

Keep Gradle Wrapper files committed.

## Done means
Before finishing a task:
- Update README.md if behavior changed.
- Keep LOCAL_BUILD.md ignored.
- Attempt ./gradlew assembleDebug.
- Report build/test results honestly.
