# Dayflow Android Foundation Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. No delegation requested.

**Goal:** Installable offline-first Android foundation with desktop-like paper design and phone/tablet adaptive layouts.
**Architecture:** Kotlin domain operations retain unknown JSON fields; Room stores whole planner documents transactionally. Compose renders a selected date and responsive daily/weekly/statistics views. Sync stays explicitly unavailable until a separate interoperable integration milestone passes.
**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, Compose BOM 2024.10.01, Room 2.6.1, JDK17, SDK35.
**Spec:** ../specs/2026-10-07-dayflow-android-design.md

## Global Constraints
- Separate Dayflow-apk repository; do not modify Windows Dayflow.
- One APK, minSdk26, desktop paper/pastel design, nine base colors.
- No handwriting/recognition, no paid services, no false sync availability.
- Package com.cybereun.dayflow; version0.1.0; debug builds labelled testing only.

## Review Focus
- Unknown PC fields survive local edits (Task1).
- Invalid timetable indices/colors cannot corrupt stored documents (Task1).
- Cold restart and orientation changes retain edits (Task2/3).
- Long Korean text/large fonts remain scrollable on compact screens (Task3).
- Storage errors preserve originals and show an error rather than silently reset (Task2).

### Task 1: Compatible document operations
Files: app/build.gradle.kts; app/src/main/java/com/cybereun/dayflow/data/PlannerDocument.kt; app/src/test/java/com/cybereun/dayflow/data/PlannerDocumentTest.kt
Interfaces: PlannerDocument.day(date), task(text), editTask(id,text), cycleMark(id), paint(index,category), setText(field,text), minutes(), weekStart(date).
- [ ] Write tests for 144 ten-minute cells, marks0..4, deletion, day boundary06:00, unknown-field retention, Monday week start, invalid index rejection.
- [ ] Run :app:testDebugUnitTest; expect missing domain implementation failure.
- [ ] Implement JSON-preserving operations, nine theme definitions and computed statistics.
- [ ] Run :app:testDebugUnitTest; expect all green; commit.

### Task 2: Durable local planner repository
Files: data/PlannerDatabase.kt; data/PlannerRepository.kt; MainActivity.kt.
Interfaces: Room document table and repository StateFlow<PlannerDocument>, load(), mutate(edit), theme(index).
- [ ] Write persistence instrumentation test for save/reopen and corrupt JSON preservation.
- [ ] Implement Room transaction storage, document snapshots, error state; no seed data presented as user records.
- [ ] Verify repository read/write with instrumentation on emulator; commit.

### Task 3: Desktop-style adaptive screens and APK
Files: ui/DayflowApp.kt; ui/PaperComponents.kt; ui/PlannerScreens.kt; ui/Timetable.kt.
Interfaces: DayflowApp(repository), shared selected date, adaptive width and paper theme.
- [ ] Add UI test assertions for daily heading, text entry persistence and phone/tablet layout.
- [ ] Implement paper canvas, native-like copper rings without duplicate layers, nine-color theme; daily tasks/comment/memo, 24h timetable, weekly goal/review/stars, statistics/settings.
- [ ] Build :app:assembleDebug and run emulator smoke test, inspect screenshots for phone and tablet.
- [ ] Record remaining scope (sync, backup import/export, full stats parity) honestly; commit and push new repository.

## Subsequent milestone (not declared complete by this plan)
PC/Android crypto fixtures → encrypted sync/pairing/recovery/Keystore → device integration tests → backup import/export → signed distribution APK. Do not enable a sync UI button before its actual implementation and interoperability tests pass.
