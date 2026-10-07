# Dayflow Android

휴대폰과 갤럭시 탭을 하나의 APK로 지원하는 별도 Android 프로젝트.
Windows Dayflow 저장소와 분리되어 있다.

## 현재 단계: 0.1.0 개발 기반 APK

구현: 종이/파스텔 화면, 기존 Poor Story 한글 글꼴,9색 포인트, 오늘의
할 일 작성·수정·상태·삭제, 코멘트·메모,144개10분 시간 칸, 주간 목표·평가,
기본 통계, Room 로컬 자동 저장, 큰 화면의 일간2단 배치.

아직 미구현: Cloudflare 동기화, 복수 플래너 관리, D-day 편집,
백업 파일 가져오기/내보내기, 데스크톱의 전체 통계/주간 시간표 디자인 동등성,
정식 앱 아이콘·배포 서명. 자유 필기·필기 인식은 사용자 요청으로 보류.
개발 APK를 전체 기능 완성본으로 취급하지 않는다.

현재 APK에는 인터넷 권한이 없고 기록을 서버에 전송하지 않는다.
동기화 기능이 준비되면 별도 단계에서 권한과 개인정보 안내를 갱신한다.
앱 삭제 시 로컬 데이터도 삭제되므로 중요한 실기록은 아직 넣지 않는 것을 권장한다.

## 빌드

JDK17, Android SDK35, Gradle8.9. Android8.0(API26) 이상을 목표로 한다.
표준 ASCII 로컬 경로에서는 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

Google Drive/한글 경로에서는 `scripts/Build-Android.ps1`로 로컬 NTFS에
복사하여 빌드한다. `-Toolchain`과 `-StageDirectory`는 환경에 맞게 지정한다.
이 스크립트 기본 툴체인 경로는 개발자 PC 환경용이며 저장소에 비밀은 없다.
수정은 항상 원본 저장소에서 한다. staging 복사본을 직접 수정하지 않는다.

빌드 산출물: `artifacts/Dayflow-0.1.0-debug.apk` (Git 제외).
정식 배포용이 아닌 debug 서명 테스트 APK이다. 서명 키는 Git에 넣지 않는다.

## 검증

- 단위 테스트: `:app:testDebugUnitTest` (6개).
- 디바이스 테스트: `:app:connectedDebugAndroidTest` 또는 adb instrumentation.
  데이터베이스 재오픈, 손상 레코드 보존, 할 일 입력/Activity 재생성,
  밝은 종이 위 시스템 아이콘 가독성 (4개).
- API35 에뮬레이터 휴대폰 및1600x1000/density160 탭형 화면에서 검증한다.
  실물 갤럭시/S펜 검증은 아직 하지 않았다.

문서: `docs/superpowers/specs/2026-10-07-dayflow-android-design.md`,
`docs/superpowers/plans/2026-10-07-android-foundation.md`, `docs/progress.md`.
글꼴 라이선스: `licenses/PoorStory-OFL.txt`.
