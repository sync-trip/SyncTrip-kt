# SyncTrip Android 클라이언트 구현 현황
**인수인계 문서 기준:** v6 | **최신 업데이트:** 2026-05-25

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
| UI (Jetpack Compose) | ✅ 구현 | 전체 화면 UI 완성. 주요 화면 API 연결 완료 |
| Navigation (NavGraph) | ✅ 구현 | `SyncTripNavGraph` — Splash 자동로그인 포함 전체 라우트 |
| ViewModel 레이어 | ✅ 구현 | `AuthViewModel`, `BandViewModel`, `VoteViewModel`, `ScheduleViewModel`, `NotificationViewModel`, `AlbumViewModel` |
| Repository 레이어 | ✅ 구현 | `AuthRepository`, `BandRepository`, `VoteRepository`, `ScheduleRepository`, `AlbumRepository` |
| 네트워크 (Retrofit) | ✅ 구현 | `ApiClient` Bearer 자동첨부 + 401 자동 갱신 Authenticator |
| JWT 토큰 저장소 | ✅ 구현 | `core/TokenDataStore.kt` — DataStore<Preferences> 기반 |
| WebSocket (STOMP) | ✅ 구현 | `network/VoteStompClient.kt` — OkHttp3 STOMP 직접 구현, VoteViewModel 통합 |
| 지도 SDK (Google Maps Compose) | ✅ 구현 | 앨범 지도 탭에서 핀 표시용으로 연결. `maps-compose` 의존성 활용 |
| FCM 클라이언트 | ✅ 구현 | `SyncTripFirebaseService.kt` — 토큰 등록 + 푸시 알림 표시 |

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
| USR-002 | 프로필 수정 화면 | ❌ 미구현 | — | 화면 없음 |
| USR-002 | 회원 탈퇴 | ✅ 구현 | `HomeScreen.kt` 드로어 하단 | "회원탈퇴" 텍스트 버튼 + 확인 다이얼로그 + API 연결 |
| USR-029 | 로그아웃 | ✅ 구현 | `HomeScreen.kt` 드로어 + `AuthViewModel.logout()` | 확인 다이얼로그 → 토큰 삭제 → 로그인 이동 |

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-003 | 밴드 목록 조회 | ✅ 구현 | `HomeScreen` + `BandViewModel` | `GET api/bands` 최신순 정렬 |
| USR-003 | 그룹 생성 UI (다단계) | ✅ 구현 | `ui/screens/TripCreationScreens.kt` | 2단계 플로우. 여행지 인기/검색 API 연결. 트리플 스타일 커스텀 캘린더 범위 선택. createBand() 연결 |
| USR-003 | 숙소 입력 | ❌ 미구현 | — | CreateTripScreen에 필드 없음 |
| USR-004 | 초대 코드 참여 UI | ✅ 구현 | `HomeScreen` BottomSheet | "코드로 참여" 버튼 → 8자리 코드 입력 BottomSheet → joinBand API |
| USR-004 | 딥링크 (`synctrip://`) 처리 | ✅ 구현 | `AndroidManifest.xml` + `MainActivity.kt` | synctrip://band/join + https://test.sync-trip.app/invite 두 scheme. onNewIntent + AlertDialog 확인 후 참여 |
| USR-005 | 최대 인원 제한 표시 | ⚠️ 부분 구현 | 스낵바 에러 메시지 | 409 → 스낵바. 별도 UI 없음 |
| USR-006 | 초대 코드 생성 + 공유 | ✅ 구현 | `InviteScreen.kt` + `invite/{bandId}` 라우트 | 코드 자동 발급, 클립보드 복사, 링크 공유 Intent |
| USR-009 | Ready 상태 전환 버튼 | ✅ 구현 | `TripBandHubScreen` + `BandViewModel.setReady()` | 장바구니 1개 이상 조건 적용 |
| USR-014 | 상태 전환 (방장) | ✅ 구현 | `BandViewModel.advanceBandStatus()` | `BandStatusTransitionResponse` 반환 — 전/후 status 모두 포함. 미Ready 경고 AlertDialog |
| USR-028 | 밴드 삭제 (방장) | ✅ 구현 | `TripBandHubScreen` 우측 상단 + `BandViewModel.deleteBand()` | 방장만 노출되는 빨간 "방 삭제" TextButton → 확인 AlertDialog("방을 삭제하시겠습니까?" + "7일 후 영구 삭제, 7일 이내 복구 요청 가능" 안내) → 삭제 후 홈 이동. 백엔드 소프트 딜리트 → 7일 후 하드 딜리트 구조 |
| USR-028 | 여행 종료 처리 표시 | ⚠️ 부분 구현 | `TripBandHubScreen` 밴드 탭 | DONE 상태 일정/정산 탭 표시. 별도 종료 화면 없음 |

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-007 | 장소 검색 UI | ✅ 구현 | `PassportAndSearchScreens.kt` | 카테고리 탭 6개(전체/음식점/관광지/액티비티/쇼핑/자연). 키워드 입력 후 검색 버튼 클릭 시에만 API 호출. 카테고리 탭 전환 시에도 keyword 있을 때만 API 호출(빈 값이면 생략). "지도에서 보기" geo: URI로 기기 설치 지도 앱 선택 |
| USR-007 | 장소 검색 API | ✅ 구현 | `BandRepository.searchPlaces()` | 국내/해외 모두 Google Places Text Search. keyword 필수(빈 값이면 API 호출 생략). `radiusMeters` 파라미터 제거 |
| USR-007 | 여행지 인기/검색 API | ✅ 구현 | `NavGraph.kt` createTrip composable | `GET api/destinations/popular` 진입 시 로드. `GET api/destinations/search` 키보드 검색 시에만 호출 |
| USR-007 | 지도 뷰 | ❌ 미구현 | — | 지도 SDK 미연동 |
| USR-008 | 장바구니 담기/삭제/목록 | ✅ 구현 | `BandViewModel.togglePick()` | 낙관적 업데이트 — UI 선반영 후 API, 실패 시 롤백. 5개 초과 시 다이얼로그 |
| USR-008 | 장바구니 인터랙션 애니메이션 | ➕ 구현 | `PassportAndSearchScreens.kt` `PlaceCard` | 담기: 아이콘 1.45× 스프링 바운스 + 버튼 배경 Primary 컬러 전환. 삭제: 아이콘 0.75× 축소 바운스. 카운트 슬라이드 애니메이션(`AnimatedContent`) |

---

## 4. 투표

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-010 | 스와이프 투표 UI | ✅ 구현 | `VotingAndSettlementScreens.kt` `SwipeVotingScreen` | 카드 1장씩 표시, 좋아요/싫어요 버튼, 카드 이탈 애니메이션, 진행률 배지 |
| USR-010 | 투표 API 연결 | ✅ 구현 | `VoteViewModel.voteForPlace()` | placeId 기반 투표. 내 투표 완료 시 대기 UI, 전원 완료(`isAllComplete`) 시 aiLoading 이동 |
| USR-010 | WebSocket 실시간 투표 | ✅ 구현 | `network/VoteStompClient.kt` + `VoteViewModel.connectWebSocket()` | 투표 화면 진입 시 자동 연결, 이벤트 수신 시 groupStatus 갱신 |
| USR-010 | 내가 담은 장소 자동 좋아요 | ❌ 미구현 | — | 구 앱에서 구현됨, 미이식 |
| USR-011 | 카테고리별 순위 풀 표시 | ❌ 미구현 | — | 투표 결과 목록 UI 없음 |
| USR-012 | Density 기반 슬롯 편입 | — | 백엔드 전담 | Android 클라이언트 별도 구현 불필요 |
| USR-013 | 최종 결과 확인 | ⚠️ 부분 구현 | `BlindVotingScreen` (레거시) | 결과 바 UI 있음, 실 데이터 미연결 |

---

## 5. 알고리즘 (백엔드 전담)

| 단계 | Android 담당 | 상태 | 비고 |
|---|---|---|---|
| AI 생성 로딩 화면 | `AiLoadingScreen` + 폴링 | ✅ 구현 | `aiLoading/{bandId}` 라우트. `BandViewModel.startGeneratingPoll()` — 3초 간격 밴드 상태 폴링. GENERATING→전환 감지 시 `scheduleReadyEvent` 발행 → hub SCHEDULE 탭 자동 이동. 일정 API 백업 폴링 병행 |

---

## 6. 일정 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-015 | 일자별 동선 일정 UI | ✅ 구현 | `ui/screens/ScheduleScreen.kt` | 타임라인 + 날짜 탭. `TripBandHubScreen` SCHEDULE 탭에 `ScheduleContent`로 임베드 |
| USR-015 | 일정 조회 API | ✅ 구현 | `ScheduleViewModel` + `ScheduleRepository.getSchedule()` | hub SCHEDULE 탭 진입 시 자동 로드. `startTime: String?` nullable 처리(자유 시간 슬롯) |
| USR-015 | 대체 장소(alts) 카테고리 필터 | ✅ 구현 | `ScheduleScreen.kt` `SlotSwapBottomSheet` | `altOptions`를 `swapSlot.place.category`로 필터링하여 동일 카테고리만 표시 |
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
| USR-022 | 더치페이 정산 UI | ✅ 구현 | `SettlementContent` (hub SETTLEMENT 탭) + `BandViewModel.loadSettlement()` | `SettlementResponse → Settlement` 변환. hub 탭 진입 시 자동 로드 |

---

## 8. 알림

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-026 | 알림 목록 화면 UI | ✅ 구현 | `NotificationScreen` + `NotificationViewModel` | 날짜별 그룹핑. loadNotifications/markAllRead/markRead/deleteNotification API 연결 |
| USR-026 | FCM 토큰 등록 API | ✅ 구현 | `SyncTripFirebaseService.onNewToken()` | 로그인 상태 시 자동 서버 등록 |
| USR-026 | FCM 푸시 알림 수신 | ✅ 구현 | `SyncTripFirebaseService` | 시스템 알림 채널 생성 + 표시 |
| USR-027 | 알림 토글 설정 | ❌ 미구현 | — | — |
| USR-030 | 공휴일 달력 표시 | ✅ 구현 | `TripCreationScreens.kt` `DateRangePickerDialog` | `GET /api/holidays?countryCode=JP&year=2026` 연동. 달력 진입 시 자동 fetch(다중 연도 지원). `CalendarDay`에 공휴일 날짜 빨간색 + 현지어명 최대 4자 표시. 날짜 선택 후 확인 버튼 위에 "여행 기간 내 공휴일 N개" 주황 배너 + 날짜/공휴일명 목록 표시(최대 3건 + "외 N개 더"). 공휴일 알림(Push) 자체는 백엔드 미구현 |

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-023 | 공유 앨범 | ✅ 구현 | `ui/screens/AlbumScreen.kt` + `AlbumViewModel` + `AlbumRepository` | 인스타그램 피드 형식. 피드/지도 탭 전환. EXIF(위도·경도·촬영시각) 추출. Base64 업로드. 낙관적 삭제. 지도 핀 클릭 → 피드 스크롤 |
| USR-024 | 여권 스탬프 UI | ⚠️ 부분 구현 | `MyPassportScreen` | 스탬프 그리드 UI 있음, DONE 밴드 필터 미연결 |
| USR-025 | 과거 여행 기록 | ❌ 미구현 | — | 전용 화면 없음 |

---

## 10. 화면별 구현 현황 요약

| 화면 (Composable) | 파일 | UI 완성 | API 연결 | 비고 |
|---|---|---|---|---|
| `AlbumContent` | `AlbumScreen.kt` | ✅ | ✅ | hub PHOTO 탭 임베드용. AlbumViewModel 연결. 피드(인스타 스타일)/지도 탭 전환. EXIF 추출. 업로드 다이얼로그 |
| `SplashScreen` | `SplashScreen.kt` | ✅ | ✅ | DataStore 토큰 복구 → 자동 로그인 |
| `LoginScreen` | `LoginScreen.kt` | ✅ | ✅ | 카카오/구글 AuthViewModel 연결 완료 |
| `HomeScreen` | `HomeScreen.kt` | ✅ | ✅ | 밴드 목록 최신순 + 사이드 드로어(프로필·D-day배너·퀵액션·로그아웃·회원탈퇴) |
| `CreateTripScreen` | `TripCreationScreens.kt` | ✅ | ✅ | 2단계 플로우. 여행지 검색 + 캘린더 범위 선택 + createBand() 완료. 공휴일 API 연동(국가 코드 기반 달력 마킹) |
| `AiLoadingScreen` | `TripCreationScreens.kt` | ✅ | ✅ | 3초 폴링 + 일정 API 백업 폴링. 완료 시 `popBackStack()` → hub SCHEDULE 탭 |
| `TripBandHubScreen` | `TripLobbyScreen.kt` | ✅ | ✅ | 하단 NavigationBar 4탭(밴드/일정/정산/사진). 상태별 액션 버튼. 방장 전용 "방 삭제" 버튼 + 삭제 확인 AlertDialog |
| `InviteScreen` | `InviteScreen.kt` | ✅ | ✅ | 코드 자동 발급, 클립보드 복사, 링크 공유 |
| `ScheduleContent` | `ScheduleScreen.kt` | ✅ | ✅ | hub SCHEDULE 탭 임베드용. ScheduleViewModel 연결. altOptions 카테고리 필터 |
| `SettlementContent` | `VotingAndSettlementScreens.kt` | ✅ | ✅ | hub SETTLEMENT 탭 임베드용. BandViewModel.loadSettlement() 연결 |
| `SwipeVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ✅ | VoteViewModel 완전 연결. WebSocket 실시간 groupStatus 갱신 |
| `PlaceSearchScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | 장바구니 토글(낙관적 업데이트) + 스프링 바운스/색상/카운트 슬라이드 애니메이션 |
| `NotificationScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | NotificationViewModel 연결. 읽음/삭제 처리 |
| `MyPassportScreen` | `PassportAndSearchScreens.kt` | ✅ | ❌ | DONE 밴드 필터 미연결 |
| `BlindVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | 레거시 — 현재 미사용 |
| 가계부 입력 화면 | ❌ 없음 | ❌ | ❌ | 구 앱도 더미 |
| 프로필 편집 화면 | ❌ 없음 | ❌ | ❌ | — |

---

## 11. 백엔드-프론트 불일치 수정 이력

| 항목 | 문제 | 수정 내용 |
|---|---|---|
| `advanceBandStatus` 반환 타입 | 백엔드는 `BandStatusTransitionResponse` 반환, 프론트는 `BandResponse`로 파싱 → 모든 필드 null → 상태 전환 네비게이션 무음 실패 | `SyncTripApiService`, `BandRepository`, `BandViewModel`, `NavGraph` 모두 `BandStatusTransitionResponse`로 교체. `transition.currentStatus`로 밴드 상태 업데이트 |
| `ScheduleSlotResponse.startTime` | 자유 시간 슬롯은 백엔드가 null 반환, 프론트는 non-null `String`으로 선언 → 역직렬화 크래시 | `startTime: String?` nullable로 변경. `TimelineNode`에서 null → `"--:--"` 표시. `DetailRow`는 null 시 미노출 |
| `SlotSwapBottomSheet` 후보 필터 | altOptions가 전체 카테고리 혼재, 슬롯 카테고리와 무관한 장소도 표시 | `it.category == swapSlot.place.category` 필터링 추가 |

---

## 12. 인수인계 문서와 다르게 결정된 사항 (Android)

| 항목 | 인수인계 문서 v6 | 실제 구현 방향 |
|---|---|---|
| FCM 알림 | "In-App 알림만 (FCM 미사용)" | 백엔드에서 FCM 추가됨 → Android도 FCM 수신 구현 |
| Vision AI | "추후 결정" | 백엔드 Gemini Vision 1.5 Flash 확정 → Android는 이미지 촬영 후 base64 전송 예정 |
| 밴드 로비 구조 | 단일 화면 | 하단 NavigationBar 4탭 허브 구조 (`TripBandHubScreen`) — 일정/정산을 별도 라우트 대신 탭으로 임베드. NavigationBar는 밴드 화면 전용 (HomeScreen에서 제거) |
| 일정 화면 | 별도 `schedule/{bandId}` 라우트 | hub SCHEDULE 탭으로 통합. `ScheduleContent` (internal composable) 사용 |
| 정산 화면 | 별도 `settlement/{bandId}` 라우트 | hub SETTLEMENT 탭으로 통합. `SettlementContent` (internal composable) 사용 |
| 기존 앱 기능 기준 | — | 가계부(MoneyFragment)는 구 앱도 더미 → 낮은 우선순위 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|---|---|
| 2026-05-23 | 문서 최초 작성. USR-001~031 전체 Android 구현 현황 정리 |
| 2026-05-23 | 아키텍처 레이어 전체 구현. TokenDataStore, Repository 4개, ViewModel 3개 추가. ApiClient 401 자동갱신 인터셉터. Splash 자동로그인, Login 실 Auth 연결, HomeScreen BandViewModel 연결 |
| 2026-05-23 | HomeScreen 우측 사이드 드로어 구현 (RTL 트릭). 로그아웃 확인 AlertDialog. BackHandler 닫기. BASE_URL 수정 |
| 2026-05-23 | 누락 API 14개 추가. CreateTripScreen → createBand API 연결. TripLobbyScreen 완전 재작성. NavGraph tripLobby 라우트 BandViewModel 연결. USR-006/009/014 완성 |
| 2026-05-23 | CreateTripScreen 2단계 플로우 재설계. 여행지 검색 + 트리플 스타일 캘린더. PlaceSearchScreen BandViewModel 연결. 장바구니 낙관적 업데이트. USR-007/008 완성 |
| 2026-05-24 | SwipeVotingScreen 신규 구현 + VoteViewModel 연결. `blindVoting/{bandId}` NavGraph 라우트 추가. USR-010 완성 |
| 2026-05-24 | AiLoadingScreen 진행률 시뮬레이션 + `aiLoading/{bandId}` 라우트 연결. 투표 완료 → aiLoading 자동 이동 |
| 2026-05-26 | 투표 완료 조건 수정 — 내 투표 완료 시 즉시 이동하던 버그 수정. 내 투표 완료→대기 UI(CircularProgressIndicator), groupStatus.isAllComplete==true 시에만 aiLoading 이동. USR-010 스펙 준수 |
| 2026-05-24 | 홈 밴드 카드 최신순 정렬. InviteScreen 신규 분리 + `invite/{bandId}` 라우트. 딥링크 AlertDialog NavHost 밖으로 이동 |
| 2026-05-24 | 사이드 드로어 전면 리디자인 — 프로필 섹션, D-day 배너, 퀵액션 카드, 회원탈퇴. `GET api/users/me` 연결 |
| 2026-05-24 | ScheduleViewModel 신규 생성. VoteStompClient 이식 + VoteViewModel WebSocket 통합. NotificationViewModel 신규 생성. BandViewModel.loadSettlement() 추가. Firebase FCM 구현 |
| 2026-05-24 | **TripBandHubScreen** — 밴드 로비를 하단 NavigationBar 4탭 허브 구조로 전면 재설계 (밴드/일정/정산/사진). `ScheduleContent`·`SettlementContent` internal composable 추가. AI 생성 완료 시 `scheduleReadyEvent` 수신 → SCHEDULE 탭 자동 전환. `AiLoadingScreen` 완료 후 `popBackStack()` 복귀 |
| 2026-05-24 | **장바구니 애니메이션** — PlaceCard 담기/삭제 시 아이콘 스프링 바운스(Animatable), 버튼 배경 Primary 컬러 전환(animateColorAsState), 카운트 숫자 슬라이드(AnimatedContent) |
| 2026-05-24 | **방 삭제** — TripBandHubScreen 방장 전용 빨간 "방 삭제" TextButton 추가. BandViewModel.deleteBand() 구현 |
| 2026-05-24 | **백엔드 불일치 수정** — `advanceBandStatus` 반환 타입 `BandResponse` → `BandStatusTransitionResponse` (API/Repository/ViewModel/NavGraph 전체). `startTime: String?` nullable 처리. `SlotSwapBottomSheet` altOptions 동일 카테고리 필터링 |
| 2026-05-25 | **하단 NavigationBar 밴드 전용화** — HomeScreen에서 `SyncTripBottomNav`(홈/내여행/탐색/여권/프로필) 제거. `TripBandHubScreen` `HubNavigationBar` 앱 테마 컬러 적용(primary/primaryContainer/onSurfaceVariant). NavGraph `selectedNavItem` 상태 및 `BottomNavDestination` import 정리 |
| 2026-05-25 | **방 삭제 확인 다이얼로그** — `TripBandHubScreen`에 `showDeleteDialog` 상태 추가. "방을 삭제하시겠습니까?" AlertDialog + "7일 후 영구 삭제 / 7일 이내 복구 요청 가능" 안내 문구(소프트 딜리트 → 7일 후 하드 딜리트 구조 반영). HubTopBar 버튼 → 다이얼로그 경유 후 삭제 실행 |
| 2026-05-25 | **공휴일 달력 연동 (USR-030 부분 구현)** — `HolidayInfo` DataModel 추가. `SyncTripApiService.getHolidays(countryCode, year)` 추가. `DateRangePickerDialog`에 `countryCode` 파라미터 추가 + `LaunchedEffect`로 공휴일 자동 fetch. `CalendarDay`에 공휴일 빨간색 숫자 + 현지어명(최대 4자) 표시. 달력 범위가 2개 연도 걸칠 경우 모두 fetch |
| 2026-05-25 | **밴드 탭 히어로 이미지 UI (USR-030 확장)** — `TripLobbyScreen.kt`에 `BandHeroSection` composable 추가. 기존 `LobbyInfoCard` 대신 풀와이드(260dp) 이미지 + 상태 칩 오버레이 + 목적지·날짜 텍스트 + 우하단 준비 현황 뱃지. `thumbnailUrl` 없으면 파란 그라디언트 폴백. `MembersSection` 헤더에 "👥 N명" 멤버 수 뱃지 추가. `BandHubTabContent` 레이아웃 패딩 구조 재편(히어로 풀와이드 → 하위 콘텐츠 20dp 패딩) |
| 2026-05-25 | **여행 기간 공휴일 요약 배너** — `DateRangePickerDialog` 확인 버튼 위에 선택 범위 내 공휴일 주황 배너(FFF8E1) 추가. 최대 3건 날짜+현지어명 표시, 초과 시 "외 N개 더" 안내. 날짜/범위 변경 시 `remember(startDate, endDate, holidays)`로 실시간 갱신 |

---

| 2026-05-26 | **공유 앨범 (USR-023)** — `AlbumScreen.kt` 신규 (인스타그램 피드 + Google Maps 지도 핀 탭). `AlbumViewModel`, `AlbumRepository` 신규. `DataModels` 앨범 4개 모델 추가. `SyncTripApiService` 앨범 API 6개 추가. EXIF 메타데이터(위도·경도·촬영시각) 추출. Base64 이미지 업로드. 낙관적 삭제. 지도 핀 클릭 시 피드 탭으로 전환 + 스크롤. `TripBandHubScreen` PHOTO 탭 `AlbumContent` 연결. Google Maps SDK 첫 연결(지도 SDK 미구현 → 구현) |
| 2026-05-26 | **장소 검색 Google 통일 반영** — `BandRepository.searchPlaces()` `radiusMeters` 파라미터 제거. `SyncTripApiService.searchPlaces()` `@Query("radiusMeters")` 제거. `NavGraph.kt` `onCategoryChange` / `onSearch` keyword 빈 값 가드 추가(빈 상태에서 API 호출 생략). `PassportAndSearchScreens.kt` 바텀시트 "Google 지도에서 보기" → "지도에서 보기". |

**마지막 수정:** 2026-05-26 (장소 검색 Google 통일, radiusMeters 제거, NavGraph 빈 keyword 가드) | **참조 문서:** `SyncTrip_인수인계문서_v6.md`, `SyncTrip_구현현황.md`
