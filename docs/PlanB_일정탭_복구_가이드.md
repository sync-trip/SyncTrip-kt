# Plan B 추천 버튼 복구 가이드 — 일정 탭

## 배경

2026-06-01 작업 중 **별표(AutoAwesome) → 화살표(SwapHoriz) 통합** 과정에서
일정 편집 전용 화면(ScheduleEditScreen)의 별표만 제거하면 됐는데,
`SlotTimeline` 컴포저블 내부의 **"Plan B 추천받기" OutlinedButton**까지
함께 제거됨.

이 버튼은 **일정 탭(TripLobbyScreen > ScheduleContent)** 에서 편집 모드가 아닌
**일반 조회 상태에서도** 동작하는 별개 기능이었음.

---

## 제거된 기능 동작 방식

```
[일정 탭 - 일반 조회 상태]

슬롯 카드 아래 "Plan B 추천받기" 버튼 탭
  → POST /schedule/plan-b (현재 장소 기준 반경 1~3km, 최대 7개 추천)
  → PlanBBottomSheet 오픈 (추천 목록 표시)
  → 장소 선택
  → startEditing(락 획득) → POST /schedule/swap → finishEditing(락 반환)
     (= executePlanBSwap — 편집 모드 진입 없이 원자적 처리)
```

편집 모드(isEditing=true)의 화살표와 차이:
- 편집 모드 화살표: 이미 락 보유 → `swapSlot` 직접 호출
- 일정 탭 Plan B 버튼: 락 없음 → `executePlanBSwap`이 락을 직접 획득

---

## 현재 코드에 이미 살아있는 것 (재사용 가능)

아래는 제거되지 않고 현재도 존재함:

| 항목 | 파일 | 위치 |
|---|---|---|
| `PlanBRequest` data class | `DataModels.kt` | `data class PlanBRequest(val targetPlaceId: Long)` |
| `PlanBResponse` data class | `DataModels.kt` | 약 471줄 |
| `SyncTripApiService.getPlanB()` | `SyncTripApiService.kt` | `@POST("api/bands/{bandId}/schedule/plan-b")` |
| `ScheduleRepository.getPlanB()` | `ScheduleRepository.kt` | `ApiClient.api.getPlanB(...)` |
| `ScheduleUiState.planBResults` | `ScheduleViewModel.kt` | `val planBResults: List<PlanBResponse>` |
| `ScheduleUiState.isPlanBLoading` | `ScheduleViewModel.kt` | `val isPlanBLoading: Boolean` |
| `ScheduleViewModel.loadPlanB()` | `ScheduleViewModel.kt` | 108번째 줄 |
| `PlanBBottomSheet` composable | `ScheduleScreen.kt` | Plan B Bottom Sheet 섹션 |
| `PlanBOptionCard` composable | `ScheduleScreen.kt` | PlanBBottomSheet 내부 |

---

## 복구 시 추가해야 할 코드

### 1. `ScheduleViewModel.kt` — `executePlanBSwap()` 함수 재추가

`saveScheduleChanges()` 함수 위에 삽입:

```kotlin
/**
 * Plan B 교체 — 편집 락 획득 → 교체 → 락 반환을 순서대로 원자적으로 실행.
 * 일정 탭에서 편집 모드 진입 없이 호출 가능.
 */
fun executePlanBSwap(bandId: Long, scheduleId: Long, newPlaceId: Long) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, planBResults = emptyList()) }
        runCatching {
            ScheduleRepository.startEditing(bandId)
            ScheduleRepository.swapSlot(bandId, scheduleId, newPlaceId)
            ScheduleRepository.finishEditing(bandId)
        }
        .onSuccess { loadSchedule(bandId) }
        .onFailure { e ->
            // 교체 실패 시에도 락 반환 시도
            runCatching { ScheduleRepository.finishEditing(bandId) }
            _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
        }
    }
}
```

---

### 2. `ScheduleScreen.kt` — `ScheduleSlotItem` 파라미터 및 UI 복구

현재 코드 (`ScheduleSlotItem`):
```kotlin
@Composable
private fun ScheduleSlotItem(
    slot: ScheduleSlotResponse,
    isEditing: Boolean,
    onClick: () -> Unit,
    onSwapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(...) {
        TimelineNode(...)
        Spacer(Modifier.width(10.dp))
        ScheduleSlotCard(
            slot        = slot,
            isEditing   = isEditing,
            onClick     = onClick,
            onSwapClick = onSwapClick,
            modifier    = Modifier.weight(1f),
        )
    }
}
```

복구 후 (`isPlanBLoading`, `onPlanBClick` 파라미터 + OutlinedButton 추가):
```kotlin
@Composable
private fun ScheduleSlotItem(
    slot: ScheduleSlotResponse,
    isEditing: Boolean,
    isPlanBLoading: Boolean,        // ← 추가
    onClick: () -> Unit,
    onSwapClick: () -> Unit,
    onPlanBClick: () -> Unit,       // ← 추가
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimelineNode(category = slot.place.category, time = slot.startTime)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            ScheduleSlotCard(
                slot = slot,
                isEditing = isEditing,
                onClick = onClick,
                onSwapClick = onSwapClick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            // 슬롯 카드 아래 Plan B 추천 버튼 — 항상 노출
            OutlinedButton(
                onClick = onPlanBClick,
                enabled = !isPlanBLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (isPlanBLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "근처 대안 장소 추천",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
```

> 버튼 텍스트는 원래 "Plan B 추천받기"였으나, 팀 협의에 따라 변경 가능.

---

### 3. `ScheduleScreen.kt` — `SlotTimeline` 파라미터 복구

현재 코드:
```kotlin
private fun SlotTimeline(
    slots: List<ScheduleSlotResponse>,
    isEditing: Boolean,
    onSlotClick: (ScheduleSlotResponse) -> Unit,
    onSwapClick: (ScheduleSlotResponse) -> Unit,
    accommodationName: String? = null,
    onAccommodationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

복구 후:
```kotlin
private fun SlotTimeline(
    slots: List<ScheduleSlotResponse>,
    isEditing: Boolean,
    isPlanBLoading: Boolean,                    // ← 추가
    onSlotClick: (ScheduleSlotResponse) -> Unit,
    onSwapClick: (ScheduleSlotResponse) -> Unit,
    onPlanBClick: (ScheduleSlotResponse) -> Unit, // ← 추가
    accommodationName: String? = null,
    onAccommodationClick: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

`itemsIndexed` 내 `ScheduleSlotItem` 호출부:
```kotlin
ScheduleSlotItem(
    slot           = slot,
    isEditing      = isEditing,
    isPlanBLoading = isPlanBLoading,   // ← 추가
    onClick        = { onSlotClick(slot) },
    onSwapClick    = { onSwapClick(slot) },
    onPlanBClick   = { onPlanBClick(slot) }, // ← 추가
)
```

빈 Day 처리:
```kotlin
if (slots.isEmpty()) {
    DayEmptyContent(modifier = modifier)  // 변경 없음
    return
}
```

---

### 4. `ScheduleScreen.kt` — `ScheduleContent` 파라미터 및 핸들러 복구

현재 파라미터 목록에 추가:
```kotlin
onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,  // ← 추가
```

현재 `SlotTimeline` 호출부:
```kotlin
SlotTimeline(
    slots                = currentSlots,
    isEditing            = isEditing,
    accommodationName    = accommodationName,
    onAccommodationClick = { showHotelSheet = true },
    onSlotClick          = { slot -> detailSlot = slot },
    onSwapClick          = { slot ->
        planBTargetSlot = slot
        showPlanBSheet  = true
        onRequestPlanB(slot.place.placeId)
    },
    modifier             = Modifier.fillMaxSize(),
)
```

복구 후:
```kotlin
SlotTimeline(
    slots                = currentSlots,
    isEditing            = isEditing,
    isPlanBLoading       = isPlanBLoading,   // ← 추가
    accommodationName    = accommodationName,
    onAccommodationClick = { showHotelSheet = true },
    onSlotClick          = { slot -> detailSlot = slot },
    onSwapClick          = { slot ->         // 편집 모드 화살표 — 변경 없음
        planBTargetSlot = slot
        showPlanBSheet  = true
        onRequestPlanB(slot.place.placeId)
    },
    onPlanBClick         = { slot ->         // ← 추가 (일반 모드 버튼)
        planBTargetSlot = slot
        showPlanBSheet  = true
        onRequestPlanB(slot.place.placeId)
    },
    modifier             = Modifier.fillMaxSize(),
)
```

`PlanBBottomSheet`의 `onSelect` 콜백 분기:
```kotlin
onSelect = { newPlaceId ->
    if (isEditing) {
        // 편집 모드: 이미 락 보유 → 직접 교체
        onSwapSlot(planBSlot.scheduleId, newPlaceId)
    } else {
        // 일반 모드: 락 없음 → executePlanBSwap이 획득·교체·반환
        onExecutePlanBSwap(planBSlot.scheduleId, newPlaceId)
    }
    showPlanBSheet  = false
    planBTargetSlot = null
    detailSlot      = null
},
```

> **중요**: 현재 코드는 `onSelect` → `onSwapSlot`(편집 모드 전제)으로 하드코딩되어 있음.
> 일정 탭에서는 `isEditing`이 false이므로 `onExecutePlanBSwap`으로 분기 필요.

---

### 5. `TripLobbyScreen.kt` — 파라미터 추가

```kotlin
onExecutePlanBSwap: (scheduleId: Long, newPlaceId: Long) -> Unit,  // ← 추가
```

`ScheduleContent` 호출부에도 전달:
```kotlin
ScheduleContent(
    ...
    onExecutePlanBSwap = onExecutePlanBSwap,  // ← 추가
    ...
)
```

---

### 6. `NavGraph.kt` — TripLobbyScreen 호출부

```kotlin
TripBandHubScreen(
    ...
    onExecutePlanBSwap = { sid, pid ->
        scheduleViewModel.executePlanBSwap(bandIdLong, sid, pid)
    },  // ← 추가
    ...
)
```

---

## 연결 흐름 요약

```
NavGraph
  └── TripBandHubScreen (onExecutePlanBSwap)
        └── ScheduleContent (onExecutePlanBSwap, isPlanBLoading, onRequestPlanB)
              └── SlotTimeline (isPlanBLoading, onPlanBClick)
                    └── ScheduleSlotItem (isPlanBLoading, onPlanBClick)
                          └── OutlinedButton "근처 대안 장소 추천"
                                ↓ 탭
                              onRequestPlanB(slot.place.placeId)
                                ↓
                              ScheduleViewModel.loadPlanB()
                                ↓
                              POST /schedule/plan-b → planBResults
                                ↓
                              PlanBBottomSheet 오픈
                                ↓ 장소 선택
                              isEditing? onSwapSlot : onExecutePlanBSwap
                                ↓ (비편집 모드)
                              ScheduleViewModel.executePlanBSwap()
                              → startEditing → swapSlot → finishEditing
```

---

## 주의사항

- `ScheduleEditScreen`(드래그 전용 편집 화면)에는 이 버튼을 추가하지 않아도 됨
  → 해당 화면은 이미 편집 모드이므로 화살표로 충분
- 복구 시 `isEditing` 분기를 **반드시** 추가해야 함
  → 편집 모드에서 `executePlanBSwap`을 호출하면 이미 락이 있으므로 `startEditing`이 실패할 수 있음
- `PlanBBottomSheet` KDoc의 "락 획득·교체·반환" 주석도 함께 복구

---

*작성일: 2026-06-01 | 담당: 팀 협의 후 복구 여부 결정*
