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

**Android** (`C:\IntelliJprojects\SyncTrip-kt`): Kotlin + Jetpack Compose + Material 3, state hoisting (all state in ViewModel), Retrofit2 + OkHttp (`ApiClient.api`), Navigation Compose (`navigation/NavGraph.kt`)

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
- On enter: call `bandViewModel.loadPicks(bandId)` + `searchPlaces(bandId)` together
- Cart toggle: `bandViewModel.togglePick(bandId, externalId)` — includes optimistic update