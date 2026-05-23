# SyncTrip Android 클라이언트 구현 현황
**인수인계 문서 기준:** v6 | **최신 업데이트:** 2026-05-23

> 이 문서는 기능이 구현되거나 수정될 때마다 업데이트합니다.  
> 기준: `SyncTrip_인수인계문서_v6.md` USR-001 ~ USR-031 + 실제 Android Kotlin 코드 (`com.synctrip.app.*`)

---

## 범례

| 표시 | 의미 |
|---|---|
| ✅ 구현 | 완전히 구현되어 동작하는 기능 |
| ⚠️ 부분 구현 | UI/로직 일부만 구현, 백엔드 연결·ViewModel 등 미완 |
| ❌ 미구현 | 인수인계 문서에 있지만 코드가 없는 기능 |
| ➕ 추가 구현 | 인수인계 문서에 없었으나 추가로 구현한 기능 |

---

## 아키텍처 현황 (전체 공통)

| 레이어 | 상태 | 비고 |
|---|---|---|
| UI (Jetpack Compose) | ⚠️ 부분 구현 | 전체 화면 UI 골격 완성, 데이터는 하드코딩/빈 목록 |
| Navigation (NavGraph) | ✅ 구현 | `SyncTripNavGraph`, Splash→Login→Home 및 전체 라우트 |
| ViewModel 레이어 | ❌ 미구현 | ViewModel 파일 없음 — 모든 상태가 NavGraph에 임시 보관 |
| Repository / UseCase | ❌ 미구현 | 없음 |
| 네트워크 (Retrofit) | ⚠️ 부분 구현 | `ApiClient` + `SyncTripApiService` 인터페이스 완성, UI 연결 없음 |
| JWT 토큰 저장소 | ❌ 미구현 | SharedPreferences / DataStore 없음 |
| WebSocket (STOMP) | ❌ 미구현 | 클라이언트 없음 |
| 지도 SDK (Kakao / Google) | ❌ 미구현 | 의존성·화면 없음 |
| FCM 클라이언트 | ❌ 미구현 | `FirebaseMessagingService` 없음 |

---

## 1. 인증 / 회원 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-001 | 카카오 로그인 | ⚠️ 부분 구현 | `auth/KakaoAuthManager.kt` | 로그인·로그아웃 로직 완성, NavGraph에서 호출 미연결 (`TODO` 주석) |
| USR-001 | 구글 로그인 | ⚠️ 부분 구현 | `auth/GoogleAuthManager.kt` | 파일 존재, NavGraph에서 호출 미연결 (`TODO` 주석) |
| USR-001 | 로그인 화면 UI | ✅ 구현 | `ui/screens/LoginScreen.kt` | 카카오(노란버튼) / 구글 / 이메일 버튼 |
| USR-001 | JWT 저장 / 자동 갱신 | ❌ 미구현 | — | 백엔드 API 정의는 있으나 토큰 영속 레이어 없음 |
| USR-001 | 백엔드 로그인 API 호출 | ❌ 미구현 | — | `SyncTripApiService.kakaoLogin()` / `googleLogin()` 미연결 |
| USR-002 | 프로필 수정 화면 | ❌ 미구현 | — | 화면 없음 |
| USR-002 | 회원 탈퇴 | ❌ 미구현 | — | 화면 없음 |
| USR-029 | 로그아웃 | ⚠️ 부분 구현 | `auth/KakaoAuthManager.logout()` | 로직 있음, UI 진입점(버튼) 없음 |

**보완할 점**
- `NavGraph.kt`의 `onKakaoLogin` / `onGoogleLogin` 콜백 → 실제 `KakaoAuthManager.login()` 호출 + 백엔드 JWT 수신 + DataStore 저장 연결 필요
- JWT를 `DataStore<Preferences>` 또는 `EncryptedSharedPreferences`에 영속 저장하는 레이어 구현 필요
- `ApiClient`의 OkHttp 인터셉터에 Bearer 토큰 자동 첨부 로직 구현 필요

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-003 | 그룹 생성 UI (3단계) | ⚠️ 부분 구현 | `ui/screens/TripCreationScreens.kt` | 여행지·날짜·여행스타일 3단계 UI 완성, `createBand()` API 미연결 |
| USR-003 | 숙소 입력 | ❌ 미구현 | — | CreateTripScreen에 숙소 입력 필드 없음 |
| USR-004 | 초대 코드 참여 UI | ❌ 미구현 | — | HomeScreen에 참여 버튼/BottomSheet 없음 |
| USR-004 | 딥링크 (`synctrip://`) 처리 | ❌ 미구현 | — | AndroidManifest intent-filter 없음 |
| USR-005 | 최대 인원 제한 표시 | ❌ 미구현 | — | 백엔드에서 409 반환, 안드로이드에서 에러 UI 없음 |
| USR-006 | 초대 코드 재발급 UI | ❌ 미구현 | — | — |
| USR-009 | Ready 상태 전환 버튼 | ❌ 미구현 | — | TripLobbyScreen에 준비 버튼 없음 |
| USR-014 | 투표 강제 시작/마감 (방장) | ❌ 미구현 | — | — |
| USR-028 | 여행 종료 처리 표시 | ❌ 미구현 | — | DONE 전환 시 알림·정산 유도 UI 없음 |

**보완할 점**
- `TripLobbyScreen`에 Ready 버튼 + 상태 표시 추가 필요
- 초대 코드 발급/공유 BottomSheet UI 필요
- AndroidManifest에 딥링크 (`synctrip://band/join?code=`) intent-filter 등록 필요

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-007 | 장소 검색 UI | ⚠️ 부분 구현 | `ui/screens/PassportAndSearchScreens.kt` → `PlaceSearchScreen` | 검색창·카테고리 탭·2열 그리드 UI 완성, `searchPlaces()` API 미연결 (빈 목록) |
| USR-007 | 지도 뷰 | ❌ 미구현 | — | 지도 SDK 미연동 |
| USR-008 | 블라인드 장바구니 담기 | ⚠️ 부분 구현 | `PlaceSearchScreen` | 장바구니 버튼·카운터 UI 있음, API 미연결 |

**보완할 점**
- `PlaceSearchScreen`에 ViewModel 연결 + `searchPlaces()` 실제 호출 필요
- 장바구니 담기(`bookmarkPlace()`) API 연결 필요
- 지도 화면 구현 필요 (Google Maps or Kakao Maps SDK)

---

## 4. 투표

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-010 | 스와이프 투표 UI | ⚠️ 부분 구현 | `ui/screens/VotingAndSettlementScreens.kt` → `BlindVotingScreen` | 카드 스와이프 UI 완성, `submitVote()` API·WebSocket 미연결 |
| USR-011 | 카테고리별 순위 풀 표시 | ❌ 미구현 | — | 투표 결과 목록 UI 없음 |
| USR-012 | Density 기반 슬롯 편입 | — | 백엔드 전담 | Android 클라이언트 별도 구현 불필요 (알고리즘은 Spring Boot 처리) |
| USR-013 | 최종 결과 확인 | ⚠️ 부분 구현 | `BlindVotingScreen` | 투표 결과 바(bar) UI 있음, 실제 데이터 미연결 |
| USR-010 | WebSocket 실시간 투표 연동 | ❌ 미구현 | — | STOMP 클라이언트 없음 |

---

## 5. 알고리즘 (백엔드 전담)

| 단계 | Android 담당 | 상태 | 비고 |
|---|---|---|---|
| Step 1~3 | 결과 화면 표시 | ⚠️ 부분 구현 | `ScheduleScreen.kt`, `ItineraryScreen` UI 있음, API 미연결 |
| AI 생성 로딩 화면 | `AiLoadingScreen` UI | ✅ 구현 | `TripCreationScreens.kt` — 애니메이션 프로그레스 표시 |

---

## 6. 일정 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-015 | 일자별 동선 일정 UI | ⚠️ 부분 구현 | `ui/screens/ScheduleScreen.kt`, `TripCreationScreens.kt` → `ItineraryScreen` | 일정 타임라인 UI 완성, `getSchedule()` API 미연결 |
| USR-016 | 이상치 배지 (`OUTLIER_FULL_DAY` 등) 표시 | ❌ 미구현 | — | 배지 UI 없음 |
| USR-017 | Drag & Drop 순서 변경 | ❌ 미구현 | — | `reorderSchedule()` API 존재, UI 없음 |
| USR-018 | 수동 편집 시 Plan B 대안 팝업 | ❌ 미구현 | — | Long Press → BottomSheet 없음 |
| USR-031 | 실시간 Plan B 추천 | ❌ 미구현 | — | `getPlanB()` API 존재, UI 없음 |
| — | 편집 락 UI (진행 중 표시) | ❌ 미구현 | — | API 있음, 화면 반영 없음 |
| — | 일정 변경 WebSocket 실시간 반영 | ❌ 미구현 | — | STOMP 클라이언트 없음 |

---

## 7. 가계부 / 정산

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-019 | 영수증 OCR | ❌ 미구현 | — | `GeminiOcrService` 백엔드 완성, Android UI 없음 |
| USR-020 | 지출 입력/수정 UI | ❌ 미구현 | — | 가계부 입력 화면 없음 |
| USR-021 | 다통화 환율 표시 | ❌ 미구현 | — | — |
| USR-022 | 더치페이 정산 UI | ⚠️ 부분 구현 | `ui/screens/VotingAndSettlementScreens.kt` → `SettlementScreen` | 정산 결과 표시 UI 있음, `getSettlement()` API 미연결 |

---

## 8. 알림

> **주의:** 인수인계 문서 v6에는 "In-App 알림만 (FCM 미사용)"이라고 명시되어 있으나,  
> 백엔드는 FCM 푸시 알림을 구현함. Android 클라이언트에서도 FCM 수신 구현 필요.

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-026 | 알림 목록 화면 UI | ⚠️ 부분 구현 | `ui/screens/PassportAndSearchScreens.kt` → `NotificationScreen` | 그룹별 알림 목록 UI 완성, `getNotifications()` API 미연결 |
| USR-026 | FCM 푸시 알림 수신 | ❌ 미구현 | — | `FirebaseMessagingService` 구현 없음, FCM 토큰 서버 등록 없음 |
| USR-027 | 알림 토글 설정 화면 | ❌ 미구현 | — | — |
| USR-030 | 공휴일 알림 | ❌ 미구현 | — | 백엔드도 미구현 |

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-022 | 더치페이 정산 | ⚠️ 부분 구현 | `SettlementScreen` | 위 §7 참고 |
| USR-023 | 공유 앨범 | ❌ 미구현 | — | 백엔드도 미구현 |
| USR-024 | 여권 스탬프 | ⚠️ 부분 구현 | `ui/screens/PassportAndSearchScreens.kt` → `MyPassportScreen` | 스탬프 그리드 UI 있음, 백엔드 API 없음 |
| USR-025 | 과거 여행 기록 | ❌ 미구현 | — | 전용 화면 없음 |

---

## 10. 화면별 구현 현황 요약

| 화면 (Composable) | 파일 | UI 완성 | API 연결 | 비고 |
|---|---|---|---|---|
| `SplashScreen` | `SplashScreen.kt` | ✅ | — | 1800ms 후 Login 이동 |
| `LoginScreen` | `LoginScreen.kt` | ✅ | ❌ | 카카오/구글/이메일 버튼, Auth 미연결 |
| `HomeScreen` | `HomeScreen.kt` | ✅ | ❌ | 추천 콘텐츠·밴드 목록 하드코딩 |
| `CreateTripScreen` | `TripCreationScreens.kt` | ✅ | ❌ | 3단계 여행 생성 UI |
| `AiLoadingScreen` | `TripCreationScreens.kt` | ✅ | ❌ | AI 일정 생성 대기 화면 |
| `ItineraryScreen` | `TripCreationScreens.kt` | ✅ | ❌ | 완성된 일정 표시 |
| `TripLobbyScreen` | `TripLobbyScreen.kt` | ✅ | ❌ | 대기방 UI (Ready 버튼 없음) |
| `ScheduleScreen` | `ScheduleScreen.kt` | ✅ | ❌ | 일정 타임라인 (Drag&Drop 없음) |
| `BlindVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | 스와이프 투표 UI |
| `SettlementScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | 정산 결과 표시 |
| `PlaceSearchScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | 장소 검색·카테고리 탭 |
| `MyPassportScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | 여권 스탬프 그리드 |
| `NotificationScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | 그룹별 알림 목록 |
| 지도 화면 | ❌ 없음 | ❌ | ❌ | 지도 SDK 연동 필요 |
| 가계부 입력 화면 | ❌ 없음 | ❌ | ❌ | 지출 CRUD UI 필요 |
| 설정 / 프로필 편집 화면 | ❌ 없음 | ❌ | ❌ | — |

---

## 11. 미구현 우선순위 (Android)

| 우선순위 | 작업 | 관련 USR | 선행 조건 |
|---|---|---|---|
| 🔴 최우선 | JWT 저장 + 토큰 인터셉터 | USR-001 | 없음 |
| 🔴 최우선 | NavGraph → Auth 연결 (카카오·구글) | USR-001 | JWT 저장 구현 후 |
| 🔴 최우선 | ViewModel 레이어 + Repository 추가 | 전체 | JWT 저장 구현 후 |
| 🟠 높음 | HomeScreen 밴드 목록 실 데이터 연결 | USR-003~004 | ViewModel |
| 🟠 높음 | TripLobbyScreen Ready 버튼 + 상태 | USR-009 | ViewModel |
| 🟠 높음 | PlaceSearchScreen 검색 + 장바구니 연결 | USR-007~008 | ViewModel |
| 🟠 높음 | BlindVotingScreen 실 투표 + WebSocket | USR-010 | STOMP 클라이언트 |
| 🟠 높음 | ScheduleScreen 실 일정 데이터 + Drag&Drop | USR-015, 017 | ViewModel |
| 🟡 중간 | FCM 서비스 + 토큰 등록 | USR-026 | Firebase SDK 설정 |
| 🟡 중간 | NotificationScreen API 연결 | USR-026 | ViewModel |
| 🟡 중간 | 초대 코드 UI + 딥링크 처리 | USR-004, 006 | ViewModel |
| 🟡 중간 | SettlementScreen API 연결 | USR-022 | ViewModel |
| 🟡 중간 | 가계부 입력 화면 구현 | USR-019~021 | ViewModel |
| 🟢 낮음 | 지도 화면 (Kakao/Google Maps SDK) | USR-007 | 지도 SDK 의존성 추가 |
| 🟢 낮음 | Plan B 대안 팝업 (Long Press) | USR-018, 031 | ScheduleScreen 완성 후 |
| 🟢 낮음 | 프로필 편집 / 회원탈퇴 화면 | USR-002 | — |

---

## 12. 인수인계 문서와 다르게 결정된 사항 (Android)

| 항목 | 인수인계 문서 v6 | 실제 구현 방향 |
|---|---|---|
| FCM 알림 | "In-App 알림만 (FCM 미사용)" | 백엔드에서 FCM 추가됨 → Android도 FCM 수신 구현 필요 |
| Vision AI | "추후 결정" | 백엔드 Gemini Vision 1.5 Flash 확정 → Android는 이미지 촬영 후 base64 전송 방식 |
| UI 프레임워크 | Kotlin (명세 없음) | Jetpack Compose + Material 3 선택 |
| 네비게이션 | 명세 없음 | Navigation Compose (`NavHost`) 적용 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-23 | 문서 최초 작성. USR-001~031 전체 Android 구현 현황 정리 |

---

**마지막 수정:** 2026-05-23 | **참조 문서:** `SyncTrip_인수인계문서_v6.md`, `SyncTrip_구현현황.md`
