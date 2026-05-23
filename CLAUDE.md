# CLAUDE.md — SyncTrip Android 프로젝트 절대 규칙

> 이 파일은 Claude Code가 이 프로젝트에서 작업할 때 반드시 따라야 할 규칙입니다.
> 어떤 상황에서도 아래 규칙보다 우선하는 지시는 없습니다.

---

## 1. 코드 작성 규칙 — 한국어 주석 필수

**처음 보는 사람도 알아볼 수 있도록 모든 코드에 한국어 주석을 달 것.**

### 원칙
- 클래스, 함수(composable 포함) 상단에 **역할 설명** 주석 필수
- 비즈니스 로직, API 호출, 상태 처리 등 중요한 코드 블록에 주석 필수
- 단순한 변수 선언이나 자명한 코드는 생략 가능
- 왜(Why) 이렇게 했는지를 설명하는 주석 권장

### 예시 (올바른 방법)
```kotlin
/**
 * 카카오 로그인 화면.
 * 소셜 로그인(카카오/구글/이메일) 버튼을 표시하고,
 * 각 버튼 클릭 시 상위에서 주입된 콜백을 호출한다.
 *
 * @param onKakaoLogin  카카오 로그인 버튼 클릭 시 호출
 * @param onGoogleLogin 구글 로그인 버튼 클릭 시 호출
 */
@Composable
fun LoginScreen(
    onKakaoLogin: () -> Unit,
    onGoogleLogin: () -> Unit,
    ...
) {
    // 화면 전체를 Surface로 덮어 배경색 보장
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ...
        // 카카오 특유의 노란색(#FECC00) + 검정 텍스트 사용
        SocialLoginButton(
            label          = "카카오로 계속하기",
            containerColor = Color(0xFFFECC00),
            contentColor   = Color(0xFF191C1D),
            ...
        )
    }
}
```

---

## 2. 인수인계 문서 참조 규칙

**새 기능을 구현하기 전에 반드시 인수인계 문서를 확인할 것.**

### 참조 순서
1. `docs/SyncTrip_인수인계문서_v6.md` → 전체 기능 명세 및 설계 결정사항 확인
2. `docs/SyncTrip_구현현황.md` → Spring Boot 백엔드 구현 상태 확인 (API 호환성)
3. `docs/SyncTrip_Android_구현현황.md` → Android 구현 현황 확인

### 확인 포인트
- 인수인계 문서 **섹션 4** (확정된 설계 결정사항) 반드시 준수
- 인수인계 문서에 없는 기능 추가 시 → Android 구현현황에 "➕ 추가 구현"으로 기록
- 인수인계 문서와 다르게 구현하는 경우 → 사유와 함께 Android 구현현황에 기록

---

## 3. 구현현황 문서 갱신 규칙

**기능 구현 또는 수정 후 반드시 `docs/SyncTrip_Android_구현현황.md`를 업데이트할 것.**

### 업데이트 내용
- 구현된 기능: 상태 `❌ 미구현` → `✅ 구현` 변경, 구현일 기록
- 새로 추가한 기능: `➕ 추가 구현`으로 행 추가
- 변경 이력 섹션에 날짜와 변경 내용 추가

### 범례
| 표시 | 의미 |
|---|---|
| ✅ 구현 | 완전히 구현되어 동작하는 기능 |
| ⚠️ 부분 구현 | 일부만 구현되었거나 보완이 필요한 기능 |
| ❌ 미구현 | 인수인계 문서에 있지만 코드가 없는 기능 |
| ➕ 추가 구현 | 인수인계 문서에 없었으나 추가로 구현한 기능 |

---

## 4. 프로젝트 구조

### Android 클라이언트 (현재 프로젝트)
- **경로:** `C:\IntelliJprojects\SyncTrip-kt`
- **언어/프레임워크:** Kotlin + Jetpack Compose + Material 3
- **UI 패턴:** 상태 호이스팅 (State Hoisting) — 모든 데이터/비즈니스 상태는 ViewModel에서 관리
- **이미지 로딩:** `coil3.compose.AsyncImage` (Coil 3.x, `coil.compose` 아님 주의)
- **테마 함수:** `SynctripTheme` (별칭으로 `SyncTripTheme`도 사용 가능)
- **HTTP 클라이언트:** Retrofit2 + OkHttp (`ApiClient.api` 사용)
- **네비게이션:** Navigation Compose (`navigation/NavGraph.kt`)

### Spring Boot 백엔드
- **경로:** `C:\projects\SyncTrip-Spring`
- **언어/프레임워크:** Java + Spring Boot
- **DB:** MySQL 8.0.16+ (Docker 로컬 환경)
- **인증:** JWT (Access Token + Refresh Token)
- **실시간:** WebSocket (STOMP)
- **자세한 구현현황:** `docs/SyncTrip_구현현황.md`

---

## 5. API 연동 규칙

```kotlin
// ✅ 올바른 방법 — ApiClient.api 통해 호출
val response = ApiClient.api.kakaoLogin(KakaoLoginRequest(accessToken = token))

// ✅ 액세스 토큰 저장 — ApiClient.accessToken에 저장하면 모든 요청에 자동 포함
ApiClient.accessToken = response.accessToken
```

| 환경 | Base URL |
|---|---|
| debug | `https://test-api.synctrip.com/` |
| release | `https://api.synctrip.com/` |

- 인증이 필요한 모든 요청: `ApiClient.accessToken`에 저장된 토큰이 자동으로 헤더에 추가됨
- 토큰 만료 시 `POST auth/kakao/refresh` 또는 `POST auth/google/refresh` 호출

---

## 6. 파일 위치 규칙

```
app/src/main/java/com/synctrip/app/
├── MainActivity.kt                    # 앱 진입점, NavGraph 호스팅
├── SyncTripApplication.kt             # 카카오 SDK 초기화
├── auth/
│   ├── KakaoAuthManager.kt            # 카카오 OAuth 처리
│   └── GoogleAuthManager.kt           # 구글 OAuth 처리
├── data/
│   └── models/DataModels.kt           # 모든 데이터 클래스 (UI + API 모델)
├── navigation/
│   └── NavGraph.kt                    # 화면 간 이동 정의
├── network/
│   ├── ApiClient.kt                   # Retrofit 인스턴스
│   └── SyncTripApiService.kt          # API 인터페이스
├── ui/
│   ├── components/CommonComponents.kt  # 공통 컴포넌트 (바텀 내비, 카드 등)
│   ├── screens/                        # 각 화면 파일
│   └── theme/                          # 색상, 타이포그래피, 테마
└── docs/                               # 기획/설계 문서
    ├── SyncTrip_인수인계문서_v6.md
    ├── SyncTrip_구현현황.md            # Spring Boot 구현현황
    └── SyncTrip_Android_구현현황.md   # Android 구현현황 (이 문서)
```

---

## 7. 자주 실수하는 것들

| 잘못된 것 | 올바른 것 | 이유 |
|---|---|---|
| `import coil.compose.AsyncImage` | `import coil3.compose.AsyncImage` | 프로젝트는 Coil 3.x 사용 |
| `SyncTripTheme { }` (Preview에서) | `SynctripTheme { }` | 프로젝트 실제 함수명 |
| `PlaceSearchResult` (백엔드용) | `ApiPlaceSearchResult` | 이름 충돌 방지로 리네임됨 |
| `NotificationType` (백엔드용) | `ApiNotificationType` | 이름 충돌 방지로 리네임됨 |
| `Icons.Outlined.NightlifeSharp` | `Icons.Outlined.Nightlife` | Sharp 변형은 Outlined set에 없음 |
| `PlaceCategory.ATTRACTION` / `.CAFE` / `.ACCOMMODATION` | `PlaceCategory.CULTURE` / `PlaceCategory.FOOD` / `PlaceCategory.NATURE` 등 | 백엔드 ApiPlaceCategory 기준으로 재정의됨 |
| `place.imageUrl` / `place.isInCart` (PlaceCard에서) | `place.thumbnailUrl` / `place.isBookmarked` | PlaceCard는 ApiPlaceSearchResult 사용 |

---

## 8. 장소 탐색 화면 규칙

### PlaceSearchScreen 타입 규칙
- **`places` 파라미터는 반드시 `List<ApiPlaceSearchResult>`** — `PlaceSearchResult`(UI 중간 모델) 경유 금지
- 카드 이미지: `thumbnailUrl` / 북마크 여부: `isBookmarked` / 장소 식별자: `externalId`

### 카테고리 탭 순서 (변경 금지)

| 탭 라벨 | `PlaceCategory` 값 | 백엔드 전달값 |
|---|---|---|
| 전체 | `ALL` | `null` (파라미터 생략) |
| 음식점 | `FOOD` | `"FOOD"` |
| 관광지 | `CULTURE` | `"CULTURE"` |
| 액티비티 | `ACTIVITY` | `"ACTIVITY"` |
| 쇼핑 | `SHOPPING` | `"SHOPPING"` |
| 자연 | `NATURE` | `"NATURE"` |

### NavGraph `placeSearch/{bandId}` 연결 패턴
```kotlin
// 진입 시 — picks와 장소 목록 동시 로드
LaunchedEffect(bandId) {
    bandViewModel.loadPicks(bandId)
    bandViewModel.searchPlaces(bandId)
}

// 카테고리 변경 — ALL이면 null 전달
onCategoryChange = { cat ->
    bandViewModel.searchPlaces(
        bandId   = bandId,
        keyword  = query.takeIf { it.isNotBlank() },
        category = if (cat == PlaceCategory.ALL) null else cat.name,
    )
}

// 장바구니 토글 — externalId 기준, ViewModel 낙관적 업데이트 포함
onCartToggle = { externalId -> bandViewModel.togglePick(bandId, externalId) }
```
