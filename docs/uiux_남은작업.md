# SyncTrip 일정/숙소 — 실제 남은 작업 목록

> 기준: 2026-05-30 현재 코드(`com.sync` 백엔드 + `com.synctrip.app` 프론트) 실측.
> 배경: 0530 "알고리즘에 숙소 TSP 적용" 커밋들로 `uiux_review.md`의 핵심 발견 대부분이
> 이미 구현 완료됨. 이 문서는 **그 이후 진짜로 남은 작업**만 정리한다.
> (검증 완료: 숙소 TSP 반영 / 경고 플래그 저장·노출 / DONE·후합류 편집 차단 / 편집자 정보 노출 = 전부 백엔드 완료)

---

## A. 프론트 갭 — 백엔드는 완료, 프론트가 데이터를 안 받음 (4건)

### ~~A-1. 경고 플래그 5종 수신 + 배지 표시~~ ✅ 완료 (2026-05-30)
- `DataModels.kt` `ScheduleSlotResponse`에 플래그 5종 추가. `SlotWarningBadges` 컴포넌트 → 슬롯 카드 배지 표시.

### ~~A-2. 편집자 정보 + canEdit 수신 → 오버레이/게이팅~~ ✅ 완료 (2026-05-30)
- `DataModels.kt` `ScheduleResponse`에 `editingUserId/editingUserName/canEdit` 추가. `EditingByOtherBanner` + 편집 버튼 게이팅. NavGraph canEdit 배선 교체.

### ~~A-3. REORDER(드래그 순서 변경) 기능 자체가 프론트에 없음~~ ✅ 완료 (2026-05-31)
- `ScheduleEditScreen` 전용 편집 화면(ScheduleScreen.kt 하단) 신규. `sh.calvin.reorderable:2.4.3` 드래그앤드랍. 드롭 완료 시 `reorderSlots()` → `PATCH /schedule/reorder` 1회 호출. 실패 시 서버 순서 자동 롤백. 진입 시 편집 락 획득, 이탈 시 `DisposableEffect`로 자동 해제.

### A-4. WebSocket 일정 토픽 구독 → 자동 재조회
- **현황**: 백엔드는 reorder/swap/숙소재계산 시 `/topic/bands/{bandId}/schedule`로 `ScheduleUpdatedEvent(bandId, editorUserId)` 브로드캐스트(데이터 없는 알림). 프론트 `ScheduleViewModel`은 액션 직후 `loadSchedule`로만 갱신하고 토픽 구독은 없다(`VoteStompClient`만 존재).
- **할 일**
  1. STOMP로 `/topic/bands/{bandId}/schedule` 구독
  2. 수신 시 `loadSchedule(bandId)` 재호출. 단 `editorUserId == 내 userId`면 무시(본인 변경 중복 갱신 방지)
- **난이도**: 중 (프론트)

---

## B. 양쪽 미구현 — 정책 결정 + 구현 필요

### B-1. 빈 Day 표시
- **현황**: `getSchedule:350-366`은 저장된 슬롯만 `groupingBy` → 슬롯 0개인 Day는 응답에서 사라짐. 또 슬롯 전무 시 404(`:346`).
- **선택지**: (a) 백엔드가 startDate~endDate 전체 Day를 빈 슬롯 배열로 내려주기 / (b) 프론트가 날짜 역산해 빈 Day 탭 생성 후 "장소 추가 유도"
- **난이도**: 낮음(프론트 역산) / 중(백엔드 응답 변경)

### B-2. 슬롯 삭제 / 건너뛰기(is_free_time 토글) API
- **현황**: `schedules.is_free_time` 컬럼은 있으나 설정/삭제 엔드포인트가 없다(ScheduleController엔 generate/get/alts/plan-b/reorder/swap/edit만). "오전 통째로 스킵"이 불가.
- **할 일**: 슬롯 삭제 또는 `is_free_time` 토글 엔드포인트 신설 여부 결정 → 백엔드+프론트
- **난이도**: 중

### ~~B-3. 숙소 좌표 BandResponse 노출 (지도 핀)~~ ✅ 완료 (2026-05-31)
- Spring `BandResponse.java` + `toBandResponse()`에 `Double accommodationLat/Lng` 추가. Android `BandResponse` DataModel 동기화. `ScheduleDayMapView` amber "숙" 마커(탭 시 숙소명 AlertDialog). `AccommodationSection` "지도에서 보기" TextButton(geo: URI).

### ~~B-4. swap 확인 팝업 (오탭 방지)~~ ✅ 완료 (2026-05-31)
- `ScheduleEditScreen` 내 swap·Plan B 선택 시 AlertDialog 확인 후 실행. 취소 시 바텀시트 유지. 편집 화면에서 락 이미 보유 중이므로 `swapSlot` 직접 호출(`executePlanBSwap` 사용 금지).

### B-5. [지도 검색] 신규 장소로 교체 (Plan B 범위 확장) — 결정 필요
- **현황**: `swapSchedulePlace:742-743`은 예비목록(ScheduleAlt)에 없는 장소면 400. 즉 "직접 검색해 새 장소로 교체"는 불가.
- **결정**: v1은 "예비목록 내 교체"로 확정 / 확장하려면 신규 Place 등록 + 카테고리 매핑 + estimatedDuration·densityPoint 기본값 정책까지 연쇄 설계
- **난이도**: 높음 (확장 시)

### B-6. Day별 다중 숙소 — v2 이관
- **현황**: Band에 숙소 1세트뿐. v1은 "여행 전체 1숙소"로 명시.
- **난이도**: (v2)

---

## C. 작업 아님 — 의도된 설계, 명문화만

- **자동저장 부재(구 B-3)**: "5분 자동저장"은 없다. `reorder`/`swap`이 호출 즉시 저장하는 구조이고, `Band.isEditingByOther:261-267`이 5분 후 락을 lazy 만료시킬 뿐. → "드래그/교체는 즉시 저장된다"로 UX 문구 확정. 진짜 자동저장이 필요하면 별도 과제.

---

## 우선순위 제안

| 순위 | 작업 | 성격 | 난이도 | 상태 |
|------|------|------|--------|------|
| — | A-2 편집자/canEdit 수신 + 게이팅 | 프론트 | 낮음~중 | ✅ 완료 |
| — | A-1 경고 플래그 배지 | 프론트 | 낮음 | ✅ 완료 |
| — | B-3 숙소 좌표 노출 + 지도 핀 | 백+프 | 낮음 | ✅ 완료 |
| — | A-3 REORDER 드래그 UI + 전용 편집 화면 | 프론트 | 중 | ✅ 완료 |
| — | B-4 swap 확인 팝업 | 프론트 | 낮음 | ✅ 완료 |
| 1 | A-4 WS 자동 재조회 | 프론트 | 중 | ❌ |
| 2 | B-1 빈 Day 표시 | 정책+α | 낮~중 | ❌ |
| 3 | B-2 슬롯 삭제/스킵 API | 백+프 | 중 | ❌ |
| 4 | B-5 지도검색 신규교체 | 결정 후 | 높음 | ❌ |
| 5 | B-6 멀티 숙소 | v2 | — | v2 이관 |

---

*작성일: 2026-05-30 / 근거: 코드 실측(파일:라인 명시). 원본 분석은 `uiux_review.md` 참고(단, 0530 커밋 이전 스냅샷 기준이라 핵심 발견 다수가 현행과 불일치).*
