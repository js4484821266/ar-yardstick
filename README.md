# AR Yardstick

AR Yardstick은 AR 카메라 화면에서 실제 길이를 추정하는 Kotlin Android 앱입니다. ARCore 히트 테스트와 월드 좌표를 사용해 측정하고, 측정한 도형을 카메라 미리보기 위에 외곽선 오버레이로 다시 투영합니다.

## 기능

- AR 히트 테스트로 얻은 두 점을 탭해 직선 거리를 측정합니다.
- 세 점을 탭해 로컬 3D 평면 위의 원을 추정합니다.
- 선과 원을 외곽선만 있는 오버레이로 그리며, 투명도가 부드럽게 애니메이션됩니다.
- 측정값 텍스트를 측정 도형 근처에 표시합니다.
- 신용카드 또는 A4 용지의 알려진 변을 이용한 수동 기준 물체 보정을 지원합니다.
- Android scoped storage / MediaStore를 통해 카메라 미리보기와 오버레이를 함께 캡처 저장합니다.
- 카메라 권한 누락, ARCore/기기 지원 문제, 히트 테스트 실패, 보정 상태, 캡처 성공/실패를 명확히 표시합니다.

## 측정 방식

내부 거리는 미터 단위로 저장합니다. 표시값은 크기에 따라 mm, cm, m로 변환하며 현재 보정 계수를 반영합니다.

측정값은 2D 화면 픽셀 거리만으로 계산하지 않습니다. 앱은 ARCore 히트 테스트, 카메라 자세/방향, 시야각 확인, 뷰/프로젝션 행렬, 3D 월드 좌표를 사용합니다.

## 제한사항

- 측정에는 ARCore가 필요합니다. 지원되지 않는 기기에서는 지원 불가 화면을 표시합니다.
- ARCore 지원/설치 확인이 통과해도 카메라 기반 AR 세션 시작은 실패할 수 있습니다. 앱은 실패 단계를 표시하고 재시도를 제공합니다.
- 평면 감지 품질은 기기 지원, 조명, 움직임, 표면 질감에 영향을 받습니다.
- 기준 물체 자동 감지는 아직 구현되지 않았습니다. 현재는 수동 변 보정이 지원되는 보정 방식입니다.
- 측정값은 추정치이며, 인증된 정밀도가 필요한 용도에는 사용하면 안 됩니다.

## 빌드

이 프로젝트는 Android Studio 없이 Gradle Wrapper로 빌드할 수 있게 구성되어 있습니다.

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

Windows:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

로컬 Android SDK와 Android Gradle Plugin에 맞는 JDK가 필요합니다. 자세한 로컬 환경 메모는 공개 저장소에 올리지 않는 `LOCAL_BUILD.md`에 남겨 주세요.
