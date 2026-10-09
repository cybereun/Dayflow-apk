# Dayflow Cross-platform Handwriting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execution proposed: Native, in this session, without parallel agents.

**Goal:** Android에서 손글씨를 텍스트 또는 펜 획으로 저장하고 Windows에 필기 원본을 표시하며, 기존 기록을 보존해 두 앱을 릴리즈한다.

**Architecture:** 펜 획은 기존 플래너 JSON의 임의 필드에 의존하지 않는 별도 저장 영역을 사용한다. 같은 동기화 그룹의 승인 기기만 접근하는 필기 전용 암호화 경로를 추가하고, Android는 편집·Windows는 읽기 전용 렌더링을 제공한다. 기존 동기화 엔진은 그대로 유지한다.

**Tech Stack:** Android Kotlin/WebView, ML Kit Digital Ink Recognition, JavaScript SVG/Pointer Events, Electron, Cloudflare Worker/Durable Objects, 기존 기기 인증 및 기기 내 암호화 라이브러리.

**Spec:** `docs/superpowers/specs/2026-10-09-cross-platform-handwriting-design.md` (2026-10-09 사용자 승인).

## Global Constraints

- Android 작업은 `Y:\내 드라이브\AI\App_Bulid\Dayflow-apk`에서만 한다.
- Windows 작업은 `Y:\내 드라이브\AI\App_Bulid\Dayflow`에서 한다.
- Android: v1.0.16 / versionCode 16. Windows: v1.0.18.
- 기존 기록·기기 승인·그룹·암호화 키·복구 코드를 삭제하거나 초기화하지 않는다.
- 다른 기능과 디자인은 변경하지 않는다. 기존 dirty 변경은 보존·구분한다.
- 서버는 기존 Cloudflare를 사용한다. 필기 평문·인증 토큰을 로그나 GitHub에 노출하지 않는다.
- 테스트는 별도 테스트 플래너·그룹을 사용한다. 실제 사용자 데이터로 삭제/충돌 시험을 하지 않는다.
- Android 업데이트는 사용자 동의 다운로드와 Android 설치 승인을 요구한다.
- 패키지 빌드만으로 동기화 성공이나 배포 완료를 주장하지 않는다.

## Review Focus

1. 구버전 클라이언트 저장 후에도 새로운 필기 원본이 사라지지 않는가: Task 1/2 호환성 시험.
2. IME 조합 중·모델 미다운로드 상태에서 인식 버튼을 눌러 기존 텍스트가 덮어써지지 않는가: Task 4.
3. 회전·확대·태블릿 양면에서 다른 날짜에 획을 저장하지 않는가: Task 3.
4. 기기 해제·그룹 불일치·재전송 중에도 데이터가 노출되거나 삭제된 획이 부활하지 않는가: Task 2.
5. 업데이트 취소·출처 허용 거절·서명 불일치에서 기록·연결이 유지되는가: Task 6.

## File map / interfaces

새 공통 JS 모듈은 Android `app/src/main/assets/desktop/assets/`와 Windows `Dayflow.Desktop/dist/assets/`에 동일 사본으로 둔다. 테스트로 두 사본의 해시 일치를 확인한다. 기존 대형 번들은 모듈 호출을 연결하는 최소 수정만 한다.

- `handwriting-model.js`: `validateStroke(value)`, `mergeStrokes(local, remote)`, `pagePoint(clientPoint, rect, logicalSize)`; 획 데이터·검증·좌표·병합.
- `handwriting-storage.js`: `loadPage(bookId, kind, date)`, `applyStrokes(records)`, `pendingRecords()`, `ackRecords(ids)`; IndexedDB 별도 저장 및 전송 대기 큐.
- `handwriting-sync.js`: `startInkSync({bridge,storage,onChanged}) -> dispose`; 보호된 인증·암호화 경계, 오프라인/포커스 복귀 재동기화.
- `handwriting-overlay.js`: `mountInkOverlay({page,bookId,kind,date,editable,storage}) -> dispose`; 페이지 SVG와 Android 편집 도구.
- Android `HandwritingRecognizer.kt`: 모델 준비·획 인식·후보 반환. JS bridge는 요청 ID로 결과를 반환한다.
- Windows `dist-electron/ink-sync-ipc.cjs`: 렌더러가 비밀 키/토큰을 직접 받지 않도록 인증·암호화·HTTP 작업을 분리한다.
- Android `InkSyncBridge.kt`: 동일 역할을 Android 보호 저장소 경계 안에서 제공한다.
- Windows `sync-server/src/original-protocol.mjs`: 기존 승인 기기 검사 뒤 필기 API 경로를 분기한다.
- Windows `sync-server/src/ink-records.mjs`: 필기 암호문 저장·검증·버전 커서. 기존 record/head를 변경하지 않는다.

## Task 1: 데이터 보존 경계와 획 모델

**Files:** 공통 `handwriting-model.js`, `handwriting-storage.js`; Android `scripts/Test-Handwriting.cjs`; Windows `Dayflow.Desktop/tests/handwriting.test.cjs`.

**Interfaces:** 위 file map의 모델·저장 함수. 획은 `{v:1,id,bookId,kind,date,points,color,width,stamp,deleted}`. kind는 `daily|weekly`, points는 논리 좌표 `{x,y,p,t}` 배열. 생성 ID는 UUID, stamp는 단조 증가 논리 시계와 device ID 쌍이다.

- [ ] 기존 앱의 백업 API와 IndexedDB/기본 저장소 위치를 확인하고, 비밀/본문을 출력하지 않고 백업 성공 여부를 기록한다.
- [ ] 실패 테스트 작성: 두 기기의 서로 다른 ID 획 병합은 둘 다 남음; 최신 삭제 표시는 이전 획보다 우선; NaN/무한대/잘못된 색상/범위 밖 좌표 거절.
- [ ] `node scripts/Test-Handwriting.cjs`로 새 기능 부재 실패를 확인한다.
- [ ] 페이지당 논리 좌표와 검증을 구현한다. 획당 최대 8192점, 단일 전송 레코드 평문 최대 256KiB를 사용한다. 초과는 안내하고 원본 초안을 유지한다.
- [ ] 저장 테스트: 미전송 획 재시작 복원, 미지원 버전 보존, 저장 실패 시 대기 큐를 삭제하지 않음.
- [ ] 구버전 엔진의 flatten/build 왕복 시험에 필기 필드를 넣어 보존 여부를 기록한다. 결과와 무관하게 필기 전용 저장 영역은 구버전 저장과 분리한다.
- [ ] 양쪽 사본 해시 일치와 테스트 성공 후 이 작업 파일만 커밋한다.

## Task 2: 승인 기기 전용 암호화 필기 동기화

**Files:** 공통 `handwriting-sync.js`, Android `InkSyncBridge.kt`/`AndroidDayflowBridge.kt`, Windows `ink-sync-ipc.cjs`/기존 preload·host 연결, `sync-server/src/ink-records.mjs`/`original-protocol.mjs`, `sync-server/tests/ink-records.test.mjs`.

**Interfaces:** 기존 `/original/v1/groups/{gid}` 경로 아래 `GET /ink?since={cursor}`, `PUT /ink/{recordId}`. 응답은 암호문·커서만 제공한다. `recordId`는 그룹 키로 계산한 불투명 ID이고 암호화 AAD에 그룹·recordId·v1을 묶는다. 브리지는 `inkCall(action,payload)`만 노출하고 원시 키/토큰은 반환하지 않는다.

- [ ] 실패 테스트: 인증 없는 요청 401, 다른 그룹 404/403, 해제된 기기 401, 기존 플래너 record/head가 필기 요청 전후 동일함.
- [ ] 기존 그룹 키의 실제 타입·라이브러리 API를 확인한다. 원본 crypto API로 도메인 분리 키를 파생한다. 임의 문자열 변환이나 직접 암호 알고리즘 재구현은 하지 않는다.
- [ ] 서버는 기존 기기 토큰 해시 검증을 재사용한다. 기존 플래너 레코드 삭제/정리 코드에서 필기 네임스페이스를 접근하지 않는다.
- [ ] 실패 테스트: 동일 레코드 재전송은 중복 없음, 역순 전달 시 최신 stamp 유지, 256KiB 초과 거절, 새 데이터 검증 실패 시 기존 암호문 유지.
- [ ] 네이티브 양쪽 브리지와 큐 재시도를 구현한다. 온라인 복귀·앱 포커스·수신 폴링으로 동기화하며 중복 요청은 직렬화한다.
- [ ] `node --test sync-server/tests/*.test.mjs`, Windows `npm test`, Android `node scripts/Test-Handwriting.cjs` 성공을 확인한다.
- [ ] 서버 배포 전 기존 그룹/record 수를 변경하지 않는 테스트 증거를 기록하고 호환 경로만 배포한다. 실그룹 내용은 출력하지 않는다.
- [ ] 별도 테스트 그룹으로 Android→Windows, 오프라인 추가, 지우기 전파, 동시 추가, 그룹 격리를 확인하고 커밋한다.

## Task 3: Android 필기와 Windows 읽기 전용 표시

**Files:** 공통 `handwriting-overlay.js`, 양쪽 renderer 번들 최소 연결, Android `android-layout.css`의 새 필기 도구 스타일, 양쪽 DOM 회귀 테스트.

**Interfaces:** `mountInkOverlay`는 페이지 내부 논리 크기의 SVG를 생성한다. 비활성/Windows는 pointer-events none; 활성 Android는 pen 입력만 수집하고 손가락 입력은 사용자가 켠 경우에만 수집한다.

- [ ] 실패 테스트: 확대·회전 전후 같은 논리 점, 양면 왼쪽/오른쪽 날짜 분리, 홈·설정에 편집 overlay 없음.
- [ ] 일간/주간 페이지를 mount/unmount할 때 overlay를 연결한다. 페이지 변경 중 활성 획은 기존 페이지에 종료 저장하고 다른 날짜로 옮기지 않는다.
- [ ] 펜 모드, 색/굵기, 획 지우개, undo/redo, 손가락 필기 토글을 추가한다. 기존 형광펜 모드와 상호 배타적으로 동작한다.
- [ ] Windows 읽기 전용 SVG 표시를 연결한다. 수신만으로 수정/삭제/송신 이벤트를 만들지 않는다.
- [ ] 테스트 태블릿에서 실제 펜 입력·손바닥 접촉·회전·확대·양면·재실행 보존을 확인한다. 사용자 펜 입력이 필요하면 요청하고 검증 대기 상태를 명시한다.
- [ ] 같은 테스트 플래너를 Windows에서 열어 위치·색·굵기 일치를 확인하고 커밋한다.

## Task 4: 한글 손글씨 → 기존 입력칸

**사용자 변경으로 취소 (2026-10-09):** 별도 인식창과 ML Kit는 제거한다. 기본 삼성 키보드 입력은 기존 텍스트 입력 경로를 사용한다. 자유 필기 저장과 동기화는 계속 구현한다.

**Files:** Android `HandwritingRecognizer.kt`, `AndroidDayflowBridge.kt`, 새 `handwriting-input.js`, `app/build.gradle.kts`, 인식 결과 전달 회귀 테스트.

**Interfaces:** `recognizeInk(requestId, strokesJson)` / `window.__dayflowInkResult(requestId,{candidates,error})`. 한국어 모델 `ko`를 기본으로 사용한다. 입력칸에 넣을 때 원래 selection 범위 뒤/선택 영역에 사용자 확인 후 삽입하고 input 이벤트를 전달한다.

- [ ] 실패 테스트: 대상 없는 인식은 기존 필드 수정 없음, 날짜 input 제외, 한글 IME 조합 중 삽입 지연, 모델 실패 시 초안 획 유지.
- [ ] 공식 ML Kit API/지원 API 수준을 확인해 버전을 고정한다. 최초 모델 다운로드와 기기 내 인식은 분리한다.
- [ ] 네이티브 인식 서비스·bridge·후보 선택 UI를 구현한다. Android 9에서도 OS 직접 필기에 의존하지 않는다.
- [ ] 태스크·코멘트·메모·목표·D-day 제목에 테스트 글자 삽입 후 저장·재시작·Windows 텍스트 수신을 확인한다.
- [ ] 네트워크 없음/다운로드 거부/빈 획/취소 시나리오를 검증하고 커밋한다.

## Task 5: 비파괴 통합 검증

**Files:** Android/Windows의 새 필기 테스트와 테스트 결과 문서만.

- [ ] 기존 Android bridge/weekly/update policy 테스트, Android Gradle unit tests, Windows 전체 npm test, Worker 전체 테스트를 실행한다. 기존 실패도 이름과 원인을 기록한다.
- [ ] 테스트 플래너의 기존 텍스트/시간표 JSON이 필기 전후 동일한지 비교한다. 실데이터를 테스트 목적으로 편집하지 않는다.
- [ ] 구버전 앱이 같은 그룹에서 텍스트를 저장한 뒤 필기 기록이 보존되는지 검증한다.
- [ ] Windows↔Android 재연결·키보드 포커스·백그라운드 복귀 시 최신 필기 표시와 텍스트 보존을 확인한다.
- [ ] 새 IPC 입력 검증, 페이지 좌표 정규화, 익명 서버 접근 거부를 점검한다. 미검증 중요 항목이 있으면 릴리즈하지 않고 보고한다.

## Task 6: 버전·업데이트·푸시·릴리즈

**Files:** Android `app/build.gradle.kts`, Windows `Dayflow.Desktop/package.json` 및 lock/표시 버전, 양쪽 릴리즈 설명, Android `docs/android-updates.md`.

- [ ] Android 버전 1.0.16/code16, Windows 버전 1.0.18을 설정한다. 기존 서명 키와 앱 ID는 바꾸지 않는다.
- [ ] Android 업데이트 규칙 테스트: 동일버전·낮은버전 제외, 높은버전 선택, 서명 불일치 거절, 취소/출처 거절 시 데이터 유지.
- [ ] Android `scripts/Build-Release.ps1`, Windows `scripts/Build-Dayflow-Release.ps1 -Version 1.0.18`을 실행한다. APK 이름은 `Dayflow-1.0.16.apk`로 한다.
- [ ] Android APK 설치 및 Windows 설치/업데이트 재실행을 검증한다. 테스트 목적으로 앱 삭제나 downgrade는 하지 않는다.
- [ ] 파일 버전·SHA-256·서명·Windows latest.yml을 확인한다. diff에서 관련 없는 변경이 포함되지 않았는지 확인한다.
- [ ] 양쪽 저장소의 요청 범위 커밋을 푸시한다. 각 저장소에 새 태그 릴리즈를 생성하고 검증한 파일과 사용 안내를 업로드한다.
- [ ] 익명 GitHub API로 Android v1.0.16 메타데이터와 다운로드 주소를 확인한다. Windows 피드의 v1.0.18 버전/해시를 확인한다.
- [ ] 최종 보고는 릴리즈 링크·검증 범위·필기 모델 최초 다운로드·Android 15→16 최초 수동 설치 필요를 포함한다. 더 높은 미래 버전 전체 업데이트 시험은 별도 검증임을 명시한다.

## Approval / execution

설계 승인은 받았다. 이 구현 계획은 아직 사용자 검토 전이다. 권장 실행 방식은 Native(현재 대화에서 직접 순서대로 작업)이며, 계획 검토 확인 후 시작한다. 사용자가 명시적으로 요청하기 전에는 별도 에이전트를 생성하지 않는다.
