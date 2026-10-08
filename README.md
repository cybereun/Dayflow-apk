# Dayflow Android

휴대폰과 갤럭시 탭을 하나의 APK로 지원하는 별도 Android 프로젝트.
Windows Dayflow 저장소와 분리되어 있다.

## 현재 단계: 1.0.15 Android APK

구현: 데스크톱 Dayflow v1.0.16 렌더러를 Android에 번들해 같은 페이지·도구·탭·
설정·동기화 UI와 동작을 사용한다. 휴대폰은 세로 일간 화면, 태블릿은 세로 한 장,
가로 펼친 양면을 렌더러의 반응형 배치로 표시한다. 데스크톱 JSON 기록은 Room에
저장하고 AES-256-GCM 암호화 동기화와 연결하며, 새 기기는 내용이 있는 플래너를
자동으로 선택한다. ZIP 백업과 데스크톱과 같은 런처 아이콘도 유지한다.

자유 필기·필기 인식은 사용자 요청으로 보류한다.

동기화를 시작하거나 연결할 때만 인터넷 권한을 사용한다. 동기화 데이터는
AES-256-GCM으로 암호화되어 Dayflow 동기화 서버에 저장되며, 동기화를 쓰지
않으면 기록은 기기에만 남는다. 앱 삭제 시 로컬 데이터가 삭제될 수 있으므로
중요한 기록은 ZIP 백업으로 보관한다.

## 빌드

JDK17, Android SDK35, Gradle8.9. Android8.0(API26) 이상을 목표로 한다.
표준 ASCII 로컬 경로에서는 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

Google Drive/한글 경로에서는 `scripts/Build-Android.ps1`로 로컬 NTFS에
복사하여 빌드한다. `-Toolchain`과 `-StageDirectory`는 환경에 맞게 지정한다.
이 스크립트 기본 툴체인 경로는 개발자 PC 환경용이며 저장소에 비밀은 없다.
수정은 항상 원본 저장소에서 한다. staging 복사본을 직접 수정하지 않는다.

디버그 산출물은 Git에서 제외한다. 정식 배포는
`scripts/Build-Release.ps1`가 만드는 `artifacts/Dayflow-1.0.15.apk`를 쓴다.
서명 키는 Git에 넣지 않는다.

## 검증

- 단위 테스트: `:app:testDebugUnitTest` (13개).
- 디바이스 테스트: `:app:connectedDebugAndroidTest` 또는 adb instrumentation.
  데이터베이스 재오픈, 손상 레코드 보존, 할 일 입력/Activity 재생성,
  밝은 종이 위 시스템 아이콘 가독성, 인터넷 권한, 런처 아이콘, 태블릿 양면
  배치 (8개).
- API35 에뮬레이터 휴대폰 및1600x1000/density160 탭형 화면에서 검증한다.
  실물 갤럭시/S펜 검증은 아직 하지 않았다.

문서: `docs/superpowers/specs/2026-10-07-dayflow-android-design.md`,
`docs/superpowers/plans/2026-10-07-android-foundation.md`, `docs/progress.md`.
글꼴 라이선스: `licenses/PoorStory-OFL.txt`.
