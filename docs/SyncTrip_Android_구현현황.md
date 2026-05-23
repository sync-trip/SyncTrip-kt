# SyncTrip Android 클라이언트 구현 현황
**인수인계 문서 기준:** v6 | **최신 업데이트:** 2026-05-24

> 이 문서는 기능이 구현되거나 수정될 때마다 업데이트합니다.  
> 기준: `SyncTrip_인수인계문서_v6.md` USR-001 ~ USR-031 + 기존 SyncTrip-Android 앱 기능 동등성

---

## 범례

| 표시 | 의미 |
|---|---|
| ✅ 구현 | 완전히 구현되어 동작하는 기능 |
| ⚠️ 부분 구현 | UI/로직 일부만 구현, 백엔드 연결 미완 |
| ❌ 미구현 | 코드가 없는 기능 |
| ➕ 추가 구현 | 인수인계 문서에 없었으나 추가로 구현한 기능 |

---

## 아키텍처 현황 (전체 공통)

| 레이어 | 상태 | 비고 |
|---|---|---|
| UI (Jetpack Compose) | ⚠️ 부분 구현 | 전체 화면 UI 골격 완성. 일부 화면 API 연결 완료 |
| Navigation (NavGraph) | ✅ 구현 | `SyncTripNavGraph` — Splash 자동로그인 포함 전체 라우트 |
| ViewModel 레이어 | ✅ 구현 | `AuthViewModel`, `BandViewModel`, `VoteViewModel` 완성 |
| Repository 레이어 | ✅ 구현 | `AuthRepository`, `BandRepository`, `VoteRepository`, `ScheduleRepository` |
| 네트워크 (Retrofit) | ✅ 구현 | `ApiClient` Bearer 자동첨부 + 401 자동 갱신 Authenticator |
| JWT 토큰 저장소 | ✅ 구현 | `core/TokenDataStore.kt` — DataStore<Preferences> 기반 |
| WebSocket (STOMP) | ❌ 미구현 | 클라이언트 없음 |
| 지도 SDK (Kakao / Google) | ❌ 미구현 | 의존성 추가됨, 화면 미연결 |
| FCM 클라이언트 | ❌ 미구현 | `FirebaseMessagingService` 없음, API 엔드포인트만 추가됨 |

---

## 1. 인증 / 회원 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-001 | 카카오 로그인 | ✅ 구현 | `auth/KakaoAuthManager.kt` + `AuthViewModel` | SDK → 서버 JWT → DataStore 저장 완전 연결 |
| USR-001 | 구글 로그인 | ✅ 구현 | `auth/GoogleAuthManager.kt` + `AuthViewModel` | Credential Manager → 서버 JWT → DataStore 저장 |
| USR-001 | 로그인 화면 UI | ✅ 구현 | `ui/screens/LoginScreen.kt` | 카카오(노란버튼) / 구글 / 이메일(개발용) 버튼 |
| USR-001 | JWT DataStore 저장 | ✅ 구현 | `core/TokenDataStore.kt` | access + refresh + userId 영속 저장 |
| USR-001 | 401 토큰 자동 갱신 | ✅ 구현 | `network/ApiClient.kt` | OkHttp Authenticator — refresh 후 재시도 |
| USR-001 | 앱 시작 자동 로그인 | ✅ 구현 | `navigation/NavGraph.kt` Splash | DataStore 토큰 복구 → 토큰 있으면 Home 직행 |
| USR-001 | 백엔드 로그인 API | ✅ 구현 | `AuthRepository` | `kakaoLogin()` / `googleLogin()` 연결 완료 |
| USR-002 | 프로필 수정 화면 | ❌ 미구현 | — | 화면 없음 |
| USR-002 | 회원 탈퇴 | ❌ 미구현 | — | API 정의됨, UI 없음 |
| USR-029 | 로그아웃 | ✅ 구현 | `HomeScreen.kt` 사이드 드로어 + `AuthViewModel.logout()` | 드로어 로그아웃 버튼 → 확인 다이얼로그 → 토큰 삭제 → 로그인 이동 완전 연결 |

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-003 | 밴드 목록 조회 | ✅ 구현 | `HomeScreen` + `BandViewModel` | `GET api/bands` 실 데이터 연결 |
| USR-003 | 그룹 생성 UI (다단계) | ⚠️ 부분 구현 | `ui/screens/TripCreationScreens.kt` | UI 완성, `createBand()` API 연결됨. 여행지 검색 연결. 트리플 스타일 커스텀 캘린더 범위 선택 적용 |
| USR-003 | 숙소 입력 | ❌ 미구현 | — | CreateTripScreen에 필드 없음 |
| USR-004 | 초대 코드 참여 UI | ❌ 미구현 | — | HomeScreen에 참여 버튼/BottomSheet 없음 |
| USR-004 | 딥링크 (`synctrip://`) 처리 | ❌ 미구현 | — | AndroidManifest intent-filter 없음 |
| USR-004 | 밴드 참여 API | ✅ 구현 | `BandRepository.joinBand()` | API 연결됨, UI 미완 |
| USR-005 | 최대 인원 제한 표시 | ❌ 미구현 | — | 409 에러 UI 없음 |
| USR-006 | 초대 코드 생성 API | ✅ 구현 | `BandViewModel.getInviteCode()` | 로비에서 버튼 클릭 시 발급, 시스템 공유 시트로 공유 |
| USR-009 | Ready 상태 전환 버튼 | ✅ 구현 | `BandViewModel.setReady()` | TripLobbyScreen MyStatusSection — 장바구니 1개 이상 조건 적용 |
| USR-014 | 상태 전환 (방장) | ✅ 구현 | `BandViewModel.advanceBandStatus()` | TripLobbyScreen 방장 전용 버튼, 미ready 경고 AlertDialog |
| USR-028 | 밴드 삭제 | ❌ 미구현 | — | `BandRepository.deleteBand()` 추가됨, UI 없음 |
| USR-028 | 여행 종료 처리 표시 | ❌ 미구현 | — | DONE 전환 시 UI 없음 |

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-007 | 장소 검색 UI | ✅ 구현 | `ui/screens/PassportAndSearchScreens.kt` + `NavGraph.kt` | 진입 시 자동 로드, 카테고리·키워드 변경 시 재호출. 탭 6개(전체/음식점/관광지/액티비티/쇼핑/자연) 백엔드 기준으로 정렬 |
| USR-007 | 해외 장소 검색 API | ✅ 구현 | `BandRepository.searchPlaces()` + `BandViewModel.searchPlaces()` | 백엔드가 isOverseas 기준으로 카카오/구글 자동 분기 — Android 별도 분기 불필요 |
| USR-007 | 여행지 인기/검색 API | ✅ 구현 | `NavGraph.kt` createTrip composable | `GET api/destinations/popular` 진입 시 로드. `GET api/destinations/search` 키보드 검색 버튼 클릭 시에만 호출(비용 절감). 실패/결과없음 스낵바 처리 |
| USR-007 | 지도 뷰 | ❌ 미구현 | — | 지도 SDK 미연동 |
| USR-008 | 장바구니 담기 API | ✅ 구현 | `BandRepository.addPick()` + `BandViewModel.togglePick()` | 낙관적 업데이트 — UI 선반영 후 API, 실패 시 롤백 |
| USR-008 | 장바구니 목록 API | ✅ 구현 | `BandRepository.getPicks()` + `BandViewModel.loadPicks()` | 진입 시 로드, 담기/삭제 후 갱신 |
| USR-008 | 장바구니 삭제 API | ✅ 구현 | `BandRepository.deletePick()` + `BandViewModel.togglePick()` | externalId로 pick 존재 여부 확인 후 분기 |
| USR-008 | 장바구니 UI | ✅ 구현 | `PlaceSearchScreen` | 북마크 버튼 상태(isBookmarked) + 카운터 배지 + BandViewModel 완전 연결 |

---

## 4. 투표

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-010 | 스와이프 투표 UI | ✅ 구현 | `VotingAndSettlementScreens.kt` → `SwipeVotingScreen` | 카드 1장씩 표시, 좋아요/싫어요 버튼, 카드 이탈 애니메이션, 진행률 배지 |
| USR-010 | 투표 API 연결 | ✅ 구현 | `VoteViewModel.voteForPlace()` + `NavGraph blindVoting/{bandId}` | VoteViewModel 완전 연결. placeId 기반 투표 함수 추가. 완료 시 aiLoading 자동 이동 |
| USR-010 | 투표 화면 진입 라우트 | ✅ 구현 | `NavGraph.kt` `blindVoting/{bandId}` | 누락된 NavGraph 라우트 추가. 크래시 수정. |
| USR-010 | 내가 담은 장소 자동 좋아요 | ❌ 미구현 | — | 구 앱에서 구현됨, 미이식 |
| USR-011 | 카테고리별 순위 풀 표시 | ❌ 미구현 | — | 투표 결과 목록 UI 없음 |
| USR-012 | Density 기반 슬롯 편입 | — | 백엔드 전담 | Android 클라이언트 별도 구현 불필요 |
| USR-013 | 최종 결과 확인 | ⚠️ 부분 구현 | `BlindVotingScreen` (레거시) | 결과 바 UI 있음, 실 데이터 미연결 |
| USR-010 | WebSocket 실시간 투표 | ❌ 미구현 | — | STOMP 클라이언트 없음 |

---

## 5. 알고리즘 (백엔드 전담)

| 단계 | Android 담당 | 상태 | 비고 |
|---|---|---|---|
| Step 1~3 | 결과 화면 표시 | ⚠️ 부분 구현 | `ScheduleScreen.kt` UI 있음, `getSchedule()` 미연결 |
| AI 생성 로딩 화면 | `AiLoadingScreen` + `AiLoadingSimulated` | ⚠️ 부분 구현 | UI 완성. 실제 폴링 대신 단계별 시뮬레이션(~5초) 적용. 투표→로딩→일정 화면 네비게이션 연결. `generateSchedule()` 실 폴링은 미구현 |

---

## 6. 일정 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-015 | 일자별 동선 일정 UI | ⚠️ 부분 구현 | `ui/screens/ScheduleScreen.kt` | 타임라인 UI 완성, API 미연결 |
| USR-015 | 일정 조회 API | ⚠️ 부분 구현 | `ScheduleRepository.getSchedule()` | Repository 완성, NavGraph 미연결 |
| USR-016 | 이상치 배지 표시 | ❌ 미구현 | — | 배지 UI 없음 |
| USR-017 | Drag & Drop 순서 변경 | ❌ 미구현 | — | 구 앱도 미구현 |
| USR-018 | Plan B 대안 팝업 | ❌ 미구현 | — | 구 앱도 미구현 |
| USR-031 | 실시간 Plan B 추천 | ❌ 미구현 | — | 구 앱도 미구현 |

---

## 7. 가계부 / 정산

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-019 | 영수증 OCR | ❌ 미구현 | — | 백엔드 완성, Android UI 없음 |
| USR-020 | 지출 입력/수정 UI | ❌ 미구현 | — | 구 앱도 더미 데이터만 |
| USR-021 | 다통화 환율 표시 | ❌ 미구현 | — | 구 앱도 미구현 |
| USR-022 | 더치페이 정산 UI | ⚠️ 부분 구현 | `SettlementScreen` | UI 있음, `getSettlement()` 미연결 |

---

## 8. 알림

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-026 | 알림 목록 화면 UI | ⚠️ 부분 구현 | `NotificationScreen` | UI 완성, API 미연결 |
| USR-026 | FCM 토큰 등록 API | ⚠️ 부분 구현 | `SyncTripApiService.registerFcmToken()` | API 정의됨, 서비스 미구현 |
| USR-026 | FCM 푸시 알림 수신 | ❌ 미구현 | — | `FirebaseMessagingService` 없음 |
| USR-027 | 알림 토글 설정 | ❌ 미구현 | — | — |
| USR-030 | 공휴일 알림 | ❌ 미구현 | — | 백엔드도 미구현 |

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-023 | 공유 앨범 | ❌ 미구현 | — | 백엔드도 미구현 |
| USR-024 | 여권 스탬프 UI | ⚠️ 부분 구현 | `MyPassportScreen` | 스탬프 그리드 UI 있음, DONE 밴드 필터 미연결 |
| USR-025 | 과거 여행 기록 | ❌ 미구현 | — | 전용 화면 없음 |

---

## 10. 화면별 구현 현황 요약

| 화면 (Composable) | 파일 | UI 완성 | API 연결 | 비고 |
|---|---|---|---|---|
| `SplashScreen` | `SplashScreen.kt` | ✅ | ✅ | DataStore 토큰 복구 → 자동 로그인 |
| `LoginScreen` | `LoginScreen.kt` | ✅ | ✅ | 카카오/구글 AuthViewModel 연결 완료 |
| `HomeScreen` | `HomeScreen.kt` | ✅ | ✅ | BandViewModel 밴드 목록 + 우측 사이드 드로어(여권/알림/로그아웃) + 로그아웃 확인 다이얼로그 + BackHandler 완성 |
| `CreateTripScreen` | `TripCreationScreens.kt` | ✅ | ⚠️ | 2단계 플로우. 여행지 인기/검색 API 연결. 트리플 스타일 커스텀 캘린더(일/토 빨간색·범위선택·오늘 라벨). createBand() 연결됨, 로비 이동 완료 |
| `AiLoadingScreen` | `TripCreationScreens.kt` + `NavGraph.kt` | ✅ | ⚠️ | 시뮬레이션 진행률 적용(단계별 ~5초). 투표 완료 시 bandId 전달, 완료 후 ScheduleScreen 이동. 실 폴링 미연결 |
| `ItineraryScreen` | `TripCreationScreens.kt` | ✅ | ❌ | 완성된 일정 표시 미연결 |
| `TripLobbyScreen` | `TripLobbyScreen.kt` | ✅ | ✅ | BandViewModel 연결 완료 — 멤버/Ready/초대코드/상태전환/picks 실 API |
| `ScheduleScreen` | `ScheduleScreen.kt` | ✅ | ❌ | getSchedule() 미연결 |
| `SwipeVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ✅ | VoteViewModel 완전 연결. 카드 이탈 애니메이션, 카테고리/별점 배지, 진행률 표시 |
| `BlindVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | 레거시 — 현재 미사용. VoteViewModel 미연결 |
| `SettlementScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | getSettlement() 미연결 |
| `PlaceSearchScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | BandViewModel 연결 완료 — 진입 자동 로드, 카테고리/키워드 필터, 장바구니 토글 (낙관적 업데이트) |
| `MyPassportScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | DONE 밴드 필터 미연결 |
| `NotificationScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | getNotifications() 미연결 |
| 가계부 입력 화면 | ❌ 없음 | ❌ | ❌ | 구 앱도 더미 |
| 프로필 편집 화면 | ❌ 없음 | ❌ | ❌ | — |

---

## 11. 누락된 API 엔드포인트 (SyncTripApiService에 추가 필요)

| 엔드포인트 | 용도 | 우선순위 |
|---|---|---|
| `DELETE api/bands/{bandId}` | 밴드 삭제 (방장) | 🟠 |

---

## 12. 작업 우선순위 (기존 앱 기능 동등성 기준)

| 순서 | 작업 | 관련 화면 |
|---|---|---|
| 1 | 누락 API 엔드포인트 추가 | SyncTripApiService, DataModels |
| 2 | CreateTripScreen → createBand API | CreateTripScreen |
| 3 | ~~TripLobbyScreen 전체 연결~~ ✅ | NavGraph에 BandViewModel 연결, 멤버/Ready/초대코드/상태전환/picks 완성 |
| 4 | ~~PlaceSearchScreen 검색 + 장바구니~~ ✅ | BandViewModel 연결, 카테고리 필터, 낙관적 장바구니 완성 |
| 5 | ~~SwipeVotingScreen + VoteViewModel 연결~~ ✅ | `blindVoting/{bandId}` 라우트, SwipeVotingScreen, VoteViewModel |
| 6 | ~~AiLoadingScreen 진행률 시뮬레이션 + 투표→일정 네비게이션~~ ✅ | NavGraph aiLoading/{bandId} |
| 7 | ScheduleScreen → getSchedule | ScheduleScreen |
| 8 | AiLoadingScreen → generateSchedule + 실 폴링 | AiLoadingScreen |
| 8 | MyPassportScreen → DONE 밴드 | MyPassportScreen |
| 9 | HomeScreen 밴드 참여 UI (초대코드) | HomeScreen |
| 10 | FCM 서비스 + 토큰 등록 | SyncTripFirebaseService |
| 11 | STOMP WebSocket (투표 실시간) | BlindVotingScreen |
| 12 | 딥링크 (synctrip://) | AndroidManifest + MainActivity |

---

## 13. 인수인계 문서와 다르게 결정된 사항 (Android)

| 항목 | 인수인계 문서 v6 | 실제 구현 방향 |
|---|---|---|
| FCM 알림 | "In-App 알림만 (FCM 미사용)" | 백엔드에서 FCM 추가됨 → Android도 FCM 수신 구현 필요 |
| Vision AI | "추후 결정" | 백엔드 Gemini Vision 1.5 Flash 확정 → Android는 이미지 촬영 후 base64 전송 |
| UI 프레임워크 | Kotlin (명세 없음) | Jetpack Compose + Material 3 선택 |
| 네비게이션 | 명세 없음 | Navigation Compose (`NavHost`) 적용 |
| 기존 앱 기능 기준 | — | 가계부(MoneyFragment)는 구 앱도 더미 → 낮은 우선순위 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-23 | 문서 최초 작성. USR-001~031 전체 Android 구현 현황 정리 |
| 2026-05-23 | 아키텍처 레이어 전체 구현. TokenDataStore, Repository 4개, ViewModel 3개 추가. ApiClient 401 자동갱신 인터셉터. Splash 자동로그인, Login 실 Auth 연결, HomeScreen BandViewModel 연결. 누락 API 목록 추가. |
| 2026-05-23 | HomeScreen 우측 사이드 드로어 구현 (RTL 트릭). 드로어에 내 여권/알림/로그아웃 메뉴 추가. 로그아웃 확인 AlertDialog 추가. BackHandler로 드로어 열린 상태 뒤로가기 닫기 처리. BASE_URL 수정 (test-api.synctrip.com → test.sync-trip.app). USR-029 로그아웃 ✅ 완성. |
| 2026-05-23 | 누락 API 14개 추가(DataModels + SyncTripApiService). DestinationResponse/PlacePickRequest/PlacePickListResponse 백엔드 DTO 기준으로 수정. CreateTripScreen → createBand API 연결 (여행지 검색, 날짜 피커, RELAXED/PACKED 스타일 토글). TripLobbyScreen 완전 재작성 (실 BandResponse 모델, 상태별 바텀바, 멤버 뱃지, 초대코드, MyStatusSection). NavGraph tripLobby 라우트 BandViewModel 연결 완료 (TokenDataStore userIdFlow, 시스템 공유 Intent). USR-006/009/014 ✅ 완성. |
| 2026-05-23 | CreateTripScreen 2단계 플로우 재설계. 1단계: 여행지 목록(LazyColumn) + 검색 + 해외/국내 탭 + 카테고리 필터(인기/일본/동남아시아/유럽/미주-오세아니아). 2단계: 선택 여행지 확인 카드 + 밴드 이름 입력 + 날짜 선택 행 + 여행 스타일(이모지 카드). 하단 버튼 구 앱과 동일하게 "계속하기" / "이전"+"방 만들기" 로 변경. NavGraph bandName 상태 추가. |
| 2026-05-23 | PlaceSearchScreen BandViewModel 완전 연결. PlaceCategory 탭 백엔드 ApiPlaceCategory 기준으로 정렬(전체/음식점/관광지/액티비티/쇼핑/자연). PlaceSearchScreen이 ApiPlaceSearchResult 직접 사용(중간 UI 모델 제거). isLoading 파라미터 + CircularProgressIndicator 추가. 키보드 Search 액션 onSearch 콜백 연결. NavGraph placeSearch/{bandId} 진입 시 자동 로드 + 카테고리·키워드 변경 시 재호출. 장바구니 토글 낙관적 업데이트(성공→loadPicks, 실패→롤백). USR-007/008 ✅ 완성. |
| 2026-05-24 | 투표 화면 크래시 수정 — `blindVoting/{bandId}` NavGraph 라우트 누락 추가. SwipeVotingScreen 신규 구현 (카드 이탈 애니메이션, 카테고리/별점 배지, 좋아요/싫어요 버튼, 진행률 배지). VoteViewModel에 `voteForPlace(placeId, result)` 추가. USR-010 ✅ 완성. |
| 2026-05-24 | AiLoadingScreen 진행률 0% 고정 버그 수정 — `AiLoadingSimulated` 헬퍼 추가 (6단계 시뮬레이션 ~5초). `aiLoading/{bandId}` 라우트 추가 (완료 후 `schedule/$bandId` 이동). 투표 완료 → aiLoading 자동 이동 연결. |
| 2026-05-24 | HomeScreen 상단 대형 SyncTrip 타이틀 제거. TripTicketCard 썸네일: `BandResponse.thumbnailUrl` 연동 (없으면 여행지명 기반 그라디언트+비행기 아이콘 플레이스홀더). `PlaneLoadingIndicator` 구 앱(`PlaneLoadingView`) 수치 일치 — 2500ms·2.5dp·7dp 점선. `BandCreateRequest.thumbnailUrl` 추가로 여행 생성 시 썸네일 서버 전달. |

---

**마지막 수정:** 2026-05-24 (밴드 썸네일 연동 + 로딩 인디케이터 개선) | **참조 문서:** `SyncTrip_인수인계문서_v6.md`, `SyncTrip_구현현황.md`
