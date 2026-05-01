# AGENTS.md

## 프로젝트
이 저장소에는 AR Yardstick이라는 Android/Kotlin 앱이 들어 있습니다.

## 빌드
프로젝트는 Android Studio 없이 빌드할 수 있어야 합니다.

필수 명령어:
- ./gradlew clean
- ./gradlew assembleDebug
- ./gradlew installDebug

Windows 명령어:
- .\gradlew.bat clean
- .\gradlew.bat assembleDebug
- .\gradlew.bat installDebug

## Android 요구사항
- Kotlin.
- minSdk 31.
- Android 12 이상.
- AR 측정에는 ARCore를 우선 사용합니다.
- 런타임 카메라 권한을 사용합니다.
- 캡처 저장에는 MediaStore/scoped storage를 사용합니다.

## 측정 규칙
- 2D 픽셀 거리만으로 실제 길이를 계산하지 않습니다.
- AR 히트 테스트, 카메라 자세, 뷰/프로젝션 행렬, 월드 좌표를 사용합니다.
- 내부 단위는 미터입니다.
- 표시 단위는 mm, cm, m 중 하나일 수 있습니다.
- 기준 물체 보정은 표시/내보내기 측정값에 보정 계수를 적용해야 합니다.

## 기준 물체
지원하는 수동 보정 물체:
- 신용카드: 85.60mm x 53.98mm.
- A4 용지: 210mm x 297mm.

자동 감지는 선택 사항입니다. 구현하지 않았다면 구현한 척하지 마세요.

## 공개 저장소 안전
커밋하지 말아야 할 것:
- local.properties
- 서명 키
- keystore 파일
- google-services.json
- API 키
- 토큰
- 비밀값
- .env 파일
- 로컬 빌드 메모
- 생성된 빌드 산출물

Gradle Wrapper 파일은 커밋 상태로 유지합니다.

## 완료 기준
작업을 끝내기 전에:
- 동작이 바뀌었다면 README.md를 업데이트합니다.
- LOCAL_BUILD.md는 계속 무시되게 둡니다.
- ./gradlew assembleDebug를 시도합니다.
- 빌드/테스트 결과를 솔직하게 보고합니다.
