# CLAUDE.md — SyncTrip Android

> These rules are absolute. Nothing overrides them.

---

## 1. Code Comments — Korean Required

Write all code comments in Korean. Every class, function, and Composable must have a KDoc block explaining its role. Add inline comments on business logic, API calls, and state handling. Explain *why*, not just what.

---

## 2. Docs to Read Before Implementing

Always read in this order before starting any new feature:
1. `docs/SyncTrip_인수인계문서_v6.md` — full spec & finalized design decisions (Section 4 is mandatory)
2. `docs/SyncTrip_구현현황.md` — Spring Boot backend implementation status
3. `docs/SyncTrip_Android_구현현황.md` — Android implementation status

---

## 3. Update Implementation Status Doc

After every implementation or fix, update `docs/SyncTrip_Android_구현현황.md`:
- Implemented: `❌` → `✅`, add date
- New (not in spec): add row marked `➕`
- Differs from spec: record reason

---

## 4. Tech Stack

**Android** (`C:\projects\SyncTrip-kt`): Kotlin + Jetpack Compose + Material 3, state hoisting (all state in ViewModel), Retrofit2 + OkHttp (`ApiClient.api`), Navigation Compose (`navigation/NavGraph.kt`)

**Backend** (`C:\projects\SyncTrip-Spring`): Java + Spring Boot, MySQL 8.0.16+, JWT, WebSocket (STOMP) — see `docs/SyncTrip_구현현황.md`

---

## 5. API Rules

- All calls via `ApiClient.api`
- Store token in `ApiClient.accessToken` — auto-injected into all requests
- Token refresh: `POST auth/kakao/refresh` or `POST auth/google/refresh`
- debug: `https://test-api.synctrip.com/` / release: `https://api.synctrip.com/`

---

## 6. File Structure

```
com/synctrip/app/
├── MainActivity.kt / SyncTripApplication.kt
├── auth/         KakaoAuthManager.kt, GoogleAuthManager.kt
├── data/models/  DataModels.kt  (all data classes)
├── navigation/   NavGraph.kt
├── network/      ApiClient.kt, SyncTripApiService.kt
├── ui/components/CommonComponents.kt
├── ui/screens/   (one file per screen)
├── ui/theme/
└── docs/
```

---

## 7. Common Mistakes

| Wrong | Correct |
|---|---|
| `coil.compose.AsyncImage` | `coil3.compose.AsyncImage` |
| `SyncTripTheme {}` (in Preview) | `SynctripTheme {}` |
| `PlaceSearchResult` | `ApiPlaceSearchResult` |
| `NotificationType` | `ApiNotificationType` |
| `Icons.Outlined.NightlifeSharp` | `Icons.Outlined.Nightlife` |
| `PlaceCategory.ATTRACTION / .CAFE / .ACCOMMODATION` | `CULTURE / FOOD / NATURE` |
| `place.imageUrl / place.isInCart` | `place.thumbnailUrl / place.isBookmarked` |

---

## 8. PlaceSearchScreen Rules

- `places` param must be `List<ApiPlaceSearchResult>` — never use `PlaceSearchResult`
- Card fields: `thumbnailUrl`, `isBookmarked`, `externalId`
- Category tab order (do not change): ALL → FOOD → CULTURE → ACTIVITY → SHOPPING → NATURE
    - ALL sends `null`; others send their `.name` string
- On enter: call `bandViewModel.loadPicks(bandId)` only — `searchPlaces` is NOT called on enter (user must type keyword and press search)
- **keyword 필수**: `onCategoryChange` / `onSearch` 모두 `query.isBlank()`이면 `searchPlaces` 호출 생략. 빈 keyword로 호출 시 백엔드 400 반환
- Cart toggle: `bandViewModel.togglePick(bandId, externalId)` — includes optimistic update
- 바텀시트 지도 버튼: `geo:` URI (Intent.ACTION_VIEW) — 기기에 설치된 지도 앱(카카오/네이버/구글) 선택 팝업. 버튼 텍스트 "지도에서 보기"

---

## 9. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

---

## 10. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

---

## 11. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

---

## 12. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Fix the bug" → identify the repro steps first, then confirm fixed on emulator/device
- "Add UI feature" → define expected screen behavior before implementing, verify visually after
- "Refactor X" → confirm behavior is identical before and after

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```