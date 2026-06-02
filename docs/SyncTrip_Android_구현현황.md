# SyncTrip Android 클라이언트 구현 현황
**인수인계 문서 기준:** v6 | **최신 업데이트:** 2026-05-27 (숙소 입력 구현)

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
| WebSocket (STOMP) | ✅ 구현 | `network/VoteStompClient.kt` — OkHttp3 STOMP 직접 구현, VoteViewModel 통합. URL `ApiClient.wsUrl` 동적화(BuildConfig 기반). 지수 백오프 재연결(최대 5회, 최대 30s) |
| 지도 SDK (Google Maps Compose) | ✅ 구현 | 앨범 지도 탭 + 일정 지도(ScheduleDayMapView) 두 곳에서 활용. `maps-compose` 의존성 활용 |
| FCM 클라이언트 | ✅ 구현 | `SyncTripFirebaseService.kt` — 토큰 등록 + 헤드업 푸시 알림(IMPORTANCE_HIGH) + 알림 탭 시 화면 이동(VOTE_STARTED→투표창, 나머지→밴드 로비) |

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
| USR-002 | 프로필 수정 화면 | ✅ 구현 | `ProfileAndSettingsScreens.kt` `ProfileEditScreen` + `NavGraph "profileEdit"` | 이름 텍스트필드 + 갤러리 사진 선택(Base64 인코딩) + PUT /api/users/me 연결 |
| USR-002 | 회원 탈퇴 | ✅ 구현 | `HomeScreen.kt` 드로어 하단 | "회원탈퇴" 텍스트 버튼 + 확인 다이얼로그 + API 연결 |
| USR-029 | 로그아웃 | ✅ 구현 | `HomeScreen.kt` 드로어 + `AuthViewModel.logout()` | 확인 다이얼로그 → 토큰 삭제 → 로그인 이동 |

---

## 2. 그룹(밴드) 관리

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-003 | 밴드 목록 조회 | ✅ 구현 | `HomeScreen` + `BandViewModel` | `GET api/bands` 최신순 정렬 |
| USR-003 | 그룹 생성 UI (다단계) | ✅ 구현 | `ui/screens/TripCreationScreens.kt` | 2단계 플로우. 여행지 인기/검색 API 연결. 트리플 스타일 커스텀 캘린더 범위 선택. createBand() 연결 |
| USR-003 | 숙소 입력 | ✅ 구현 (2026-05-27) | `TripCreationScreens.kt` + `TripLobbyScreen.kt` + `BandViewModel` | 생성 시: 3페이지 플로우(여행지→여행정보→숙소선택). `AccommodationSearchPage` — 상단 Google Map 핀 표시 + 검색창 + 결과 목록. 우상단 "건너뛰기" 회색 버튼. 숙소 선택 시 위도·경도를 `BandCreateRequest`에 전달, 미선택 시 목적지 좌표 사용. 로비 밴드 탭: `AccommodationSection` 카드 + 방장 수정 다이얼로그. `PATCH /api/bands/{bandId}/accommodation` 연결. VOTING/GENERATING 중 편집 불가. 백엔드: `GET /api/places/search?keyword=&lat=&lng=` 엔드포인트 추가 필요 |
| USR-004 | 초대 코드 참여 UI | ✅ 구현 | `HomeScreen` BottomSheet | "코드로 참여" 버튼 → 8자리 코드 입력 BottomSheet → joinBand API |
| USR-004 | 딥링크 (`synctrip://`) 처리 | ✅ 구현 | `AndroidManifest.xml` + `MainActivity.kt` | synctrip://band/join + https://test.sync-trip.app/invite 두 scheme. onNewIntent + AlertDialog 확인 후 참여 |
| USR-005 | 최대 인원 제한 표시 | ⚠️ 부분 구현 | 스낵바 에러 메시지 | 409 → 스낵바. 별도 UI 없음 |
| USR-006 | 초대 코드 생성 + 공유 | ✅ 구현 | `InviteScreen.kt` + `invite/{bandId}` 라우트 | 코드 자동 발급, 클립보드 복사, 링크 공유 Intent |
| USR-009 | Ready 상태 전환 버튼 | ✅ 구현 | `TripBandHubScreen` + `BandViewModel.setReady()` | 장바구니 1개 이상 조건 적용. `PlaceSearchScreen` 하단 장바구니 바에도 "준비 완료" 버튼 추가 (장바구니 화면에서 바로 제출 가능) |
| USR-014 | 상태 전환 (방장) | ✅ 구현 | `BandViewModel.advanceBandStatus()` | 투표 시작 전 픽 수 부족 시 에러 팝업(멤버 수 × 2 기준). 미Ready 경고 AlertDialog |
| USR-014 | 방장 수동 투표 마감 | ✅ 구현 | `SwipeVotingScreen` TopBar + `BandViewModel.advanceBandStatus()` 재활용 | 방장 전용 "마감하기" TextButton — 확인 다이얼로그 → GENERATING 전환 → aiLoading 이동 |
| USR-028 | 밴드 삭제 (방장) | ✅ 구현 | `TripBandHubScreen` 우측 상단 + `BandViewModel.deleteBand()` | 방장만 노출되는 빨간 "방 삭제" TextButton → 확인 AlertDialog("방을 삭제하시겠습니까?" + "7일 후 영구 삭제, 7일 이내 복구 요청 가능" 안내) → 삭제 후 홈 이동. 백엔드 소프트 딜리트 → 7일 후 하드 딜리트 구조 |
| USR-028 | 여행 종료 처리 표시 | ⚠️ 부분 구현 | `TripBandHubScreen` 밴드 탭 | DONE 상태 일정/정산 탭 표시. 별도 종료 화면 없음 |

---

## 3. 장소 탐색 / 장바구니

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-007 | 장소 검색 UI | ✅ 구현 | `PassportAndSearchScreens.kt` | 카테고리 탭 6개(전체/음식점/관광지/액티비티/쇼핑/자연). 키워드 입력 후 검색 버튼 클릭 시에만 API 호출. 카테고리 탭 전환 시에도 keyword 있을 때만 API 호출(빈 값이면 생략). "지도에서 보기" geo: URI로 기기 설치 지도 앱 선택 |
| USR-007 | 장소 검색 API | ✅ 구현 | `BandRepository.searchPlaces()` | 국내/해외 모두 Google Places Text Search. keyword 필수(빈 값이면 API 호출 생략). `radiusMeters` 파라미터 제거 |
| USR-007 | 여행지 인기/검색 API | ✅ 구현 | `NavGraph.kt` createTrip composable | `GET api/destinations/popular` 진입 시 로드. `GET api/destinations/search` 키보드 검색 시에만 호출 |
| USR-007 | 지도 뷰 (장소 탐색 화면) | ❌ 미구현 | — | PlaceSearchScreen 전용 독립 지도 미연동. 일정 화면 지도(`ScheduleDayMapView`)는 별도로 구현됨 |
| USR-008 | 장바구니 담기/삭제/목록 | ✅ 구현 | `BandViewModel.togglePick()` | 낙관적 업데이트 — UI 선반영 후 API, 실패 시 롤백. 5개 초과 시 다이얼로그 |
| USR-008 | 장바구니 인터랙션 애니메이션 | ➕ 구현 | `PassportAndSearchScreens.kt` `PlaceCard` | 담기: 아이콘 1.45× 스프링 바운스 + 버튼 배경 Primary 컬러 전환. 삭제: 아이콘 0.75× 축소 바운스. 카운트 슬라이드 애니메이션(`AnimatedContent`) |

---

## 4. 투표

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-010 | 스와이프 투표 UI | ✅ 구현 | `VotingAndSettlementScreens.kt` `SwipeVotingScreen` | 카드 1장씩 표시, 좋아요/싫어요 버튼, 카드 이탈 애니메이션, 진행률 배지. 투표 실패 시 Snackbar 에러 표시 |
| USR-010 | 투표 API 연결 | ✅ 구현 | `VoteViewModel.voteForPlace()` | placeId 기반 투표. 내 투표 완료 시 대기 UI, 전원 완료(`isAllComplete`) 시 aiLoading 이동. `isMyComplete` 타이밍 버그 수정(isLoading 가드 추가) |
| USR-010 | WebSocket 실시간 투표 | ✅ 구현 | `network/VoteStompClient.kt` + `VoteViewModel.connectWebSocket()` | 투표 화면 진입 시 자동 연결. 이벤트 수신 시 groupStatus만 재조회(`refreshGroupStatus`) — 불필요한 내 상태 API 재호출 제거 |
| USR-010 | 내가 담은 장소 자동 좋아요 | ✅ 구현 | `VoteViewModel.loadVotePlaces()` | 투표 화면 진입 시 myBookmark=true 장소를 pending에서 제외 + result=1 순차 자동 제출(백엔드에서 0으로 저장). 화면 재진입 시 CONFLICT 무시. autoLike 장소를 `votedPlaces`에도 포함 → `totalCount = votedPlaces.size + pendingPlaces.size` 가 전체 장소 수 정확히 반영 (2026-05-31 버그 수정) |
| ➕ | 투표 카드 — 내가 담은 장소 배지 | ✅ 구현 | `VotingPlaceCard` | `myBookmark=true`이면 이미지 우상단에 Primary 색 "내가 담은 곳" 배지 표시 |
| USR-011 | 투표 결과 화면 | ✅ 구현 (2026-05-26) | `VoteResultScreen` + `VoteViewModel.loadVoteResults()` + `GET /api/bands/{bandId}/votes/results` (백엔드 신규) | 장소별 좋아요/싫어요 집계 표시, 통과/탈락 배지, likeCount 내림차순 정렬. 투표 완료 시 blindVoting → voteResults 자동 이동 후 "일정 만들기" → aiLoading 이동 |
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
| USR-016 | 이상치/경고 플래그 배지 표시 | ✅ 구현 (2026-05-30) | `ScheduleScreen.kt` `SlotWarningBadges` | `ScheduleSlotResponse`에 플래그 5종(isOutlierCandidate/openingHoursViolation/mealWindowViolation/lateSchedule/openingHoursUnverified) 추가. `SlotWarningBadges` 컴포넌트 → 슬롯 카드에 ⚠ 영업시간/🌙 심야/🍽 식사시간/📍 동선이탈/❓ 영업미확인 배지 표시. `@SerializedName(alternate=[...])` 으로 백엔드 isXxx 변형 대응 |
| ➕ | 편집자 게이팅 (canEdit / 편집 오버레이) | ✅ 구현 (2026-05-30) | `ScheduleScreen.kt` `EditingByOtherBanner` + `DataModels.kt` `ScheduleResponse` | `ScheduleResponse`에 `editingUserId/editingUserName/canEdit` 추가. 타인 편집 락 보유 시 "○○님이 편집 중입니다" 배너 표시(ScheduleScreen·ScheduleContent 양쪽). 편집 버튼을 백엔드 `canEdit` 값으로 게이팅(DONE/후합류/락 시 자동 숨김) |
| USR-017 | Drag & Drop 순서 변경 | ✅ 구현 (2026-05-31) | `ScheduleEditScreen`(`ScheduleScreen.kt`) | `sh.calvin.reorderable` 라이브러리. 전용 편집 화면 내 `LazyColumn` + `ReorderableItem`. 드롭 완료 시 1회 `PATCH /schedule/reorder` 호출. 실패 시 서버 순서로 자동 롤백 |
| ➕ | 크로스 Day 드래그 이동 | ✅ 구현 (2026-05-31) | `ScheduleEditScreen`(`ScheduleScreen.kt`) + `ScheduleViewModel.moveSlot()` | `ScheduleEditScreen` 전면 개편: Day 탭 제거, 전체 Day 플랫 리스트(`FlatItem.DayHeader/SlotItem`). Day 헤더 넘는 드래그 허용. 드래그 완료 후 같은 Day면 `reorderSlots`, 다른 Day면 `POST /schedule/move` 호출. `ScheduleMoveRequest` DataModel 추가. 실패 시 서버 상태로 자동 롤백 |
| USR-018 | Plan B 대안 팝업 | ✅ 구현 | 2026-05-26 | `ScheduleScreen.kt` `PlanBBottomSheet` — 각 슬롯 카드 아래 "Plan B 추천받기" 버튼, 선택 시 락 획득·교체·락 반환 원자 처리 |
| USR-031 | 실시간 Plan B 추천 | ✅ 구현 | 2026-05-26 | `ScheduleViewModel.loadPlanB()` → `POST /schedule/plan-b` → `PlanBBottomSheet` 결과 표시 (최대 7개, 거리 표시) |

---

## 7. 가계부 / 정산

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-019 | 영수증 OCR | ✅ 구현 | `ExpenseInputSheet` (갤러리 선택 → multipart POST → 자동 채우기) | `POST /api/bands/{bandId}/expenses/ocr` 연동. 항목명·금액·통화 자동 채우기. OCR 실패 시 수동 입력 유지 |
| USR-020 | 지출 입력/삭제 UI | ✅ 구현 | `SettlementContent` 내 `ExpenseInputSheet` + `ExpenseCard` | 지출 추가 FAB → BottomSheet. 실제 지출 목록(ExpenseResponse) 표시. 본인 지출만 삭제 버튼 노출. 낙관적 삭제. CRUD API 연결 |
| USR-021 | 다통화 환율 표시 | ❌ 미구현 | — | 구 앱도 미구현 |
| USR-022 | 더치페이 정산 UI | ✅ 구현 | `SettlementContent` (hub SETTLEMENT 탭) + `BandViewModel.loadSettlement()` | `SettlementResponse → Settlement` 변환. hub 탭 진입 시 자동 로드. `myBalance` 현재 userId 기준 수정 |

---

## 8. 알림

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-026 | 알림 목록 화면 UI | ✅ 구현 | `NotificationScreen` + `NotificationViewModel` | 날짜별 그룹핑. loadNotifications/markAllRead/markRead/deleteNotification API 연결 |
| USR-026 | FCM 토큰 등록 API | ✅ 구현 | `SyncTripFirebaseService.onNewToken()` | 로그인 상태 시 자동 서버 등록 |
| USR-026 | FCM 푸시 알림 수신 | ✅ 구현 | `SyncTripFirebaseService` | 알림 채널 IMPORTANCE_HIGH(헤드업). PRIORITY_HIGH. 포그라운드/백그라운드 모두 data 페이로드 key 통일(`bandId`, `type`)로 탭 이동 처리 |
| USR-026 | FCM 알림 탭 → 화면 이동 | ✅ 구현 (2026-05-27) | `SyncTripFirebaseService` + `MainActivity` + `NavGraph` | `VOTE_STARTED` → `blindVoting/{bandId}`, 나머지 bandId 있는 타입 → `tripLobby/{bandId}`. 앱 실행 중/종료 상태 모두 처리. `HOLIDAY_WARNING` 타입 `ApiNotificationType`에 추가 |
| USR-027 | 알림 토글 설정 | ✅ 구현 | `ProfileAndSettingsScreens.kt` `NotificationSettingsScreen` + `NavGraph "notificationSettings"` | GET /api/users/notification-settings 조회 + PATCH 개별 토글. 낙관적 업데이트. 드로어 "알림 설정" 퀵 아이템으로 진입 |
| USR-030 | 공휴일 달력 표시 | ✅ 구현 | `TripCreationScreens.kt` `DateRangePickerDialog` | `GET /api/holidays?countryCode=JP&year=2026` 연동. 달력 진입 시 자동 fetch(다중 연도 지원). `CalendarDay`에 공휴일 날짜 빨간색 + 현지어명 최대 4자 표시. 날짜 선택 후 확인 버튼 위에 "여행 기간 내 공휴일 N개" 주황 배너 + 날짜/공휴일명 목록 표시(최대 3건 + "외 N개 더"). 공휴일 알림(Push) 자체는 백엔드 미구현 |

---

## 9. 아카이빙

| USR | 기능명 | 상태 | 구현 위치 | 비고 |
|---|---|---|---|---|
| USR-023 | 공유 앨범 | ✅ 구현 | `ui/screens/AlbumScreen.kt` + `AlbumViewModel` + `AlbumRepository` | 인스타그램 피드 형식. 피드/지도 탭 전환. EXIF(위도·경도·촬영시각) 추출. Base64 업로드. 낙관적 삭제. 지도 핀 클릭 → 피드 스크롤. **(2026-06-02 수정)** Android 10+ 위치 EXIF redact 대응: `ACCESS_MEDIA_LOCATION` 권한 + `MediaStore.setRequireOriginal()` + PhotoPicker(`PickVisualMedia`) 전환으로 GPS 좌표 추출 정상화 |
| USR-024 | 여권 스탬프 UI + API 연동 | ✅ 구현 | `MyPassportScreen` + `BandViewModel.loadPassportStamps()` | `GET /api/users/me/stamps` 연동. DESC→ASC 역순 정렬(오래된 스탬프 먼저). ISO/배열 두 날짜 포맷 모두 파싱. 로딩 중 CircularProgressIndicator 표시 |
| USR-025 | 과거 여행 기록 | ✅ 구현 | `HomeScreen.kt` `PastTripsScreen` + `NavGraph "pastTrips"` | DONE 밴드 필터링. 홈 드로어 "지난 여행" 항목으로 진입. 썸네일·기간·인원수 카드 표시 |

---

## 10. 화면별 구현 현황 요약

| 화면 (Composable) | 파일 | UI 완성 | API 연결 | 비고 |
|---|---|---|---|---|
| `AlbumContent` | `AlbumScreen.kt` | ✅ | ✅ | hub PHOTO 탭 임베드용. AlbumViewModel 연결. 피드(인스타 스타일)/지도 탭 전환. EXIF 추출. 업로드 다이얼로그 |
| `SplashScreen` | `SplashScreen.kt` | ✅ | ✅ | DataStore 토큰 복구 → 자동 로그인 |
| `LoginScreen` | `LoginScreen.kt` | ✅ | ✅ | 카카오/구글 AuthViewModel 연결 완료 |
| `HomeScreen` | `HomeScreen.kt` | ✅ | ✅ | 밴드 목록 최신순 + 사이드 드로어(프로필·D-day배너·퀵액션·로그아웃·회원탈퇴) |
| `CreateTripScreen` | `TripCreationScreens.kt` | ✅ | ✅ | 3단계 플로우(여행지→여행정보→숙소). 여행지 검색 + 캘린더 범위 선택 + createBand() 완료. 공휴일 API 연동. 홈 추천 카드에서 진입 시 `initialPage=2`로 Step 1 건너뜀 |
| `AiLoadingScreen` | `TripCreationScreens.kt` | ✅ | ✅ | 3초 폴링 + 일정 API 백업 폴링. 완료 시 `popBackStack()` → hub SCHEDULE 탭 |
| `TripBandHubScreen` | `TripLobbyScreen.kt` | ✅ | ✅ | 하단 NavigationBar 4탭(밴드/일정/정산/사진). 상태별 액션 버튼. 방장 전용 "방 삭제" 버튼 + 삭제 확인 AlertDialog |
| `InviteScreen` | `InviteScreen.kt` | ✅ | ✅ | 코드 자동 발급, 클립보드 복사, 링크 공유 |
| `ScheduleContent` | `ScheduleScreen.kt` | ✅ | ✅ | hub SCHEDULE 탭 임베드용. ScheduleViewModel 연결. altOptions 카테고리 필터 |
| `SettlementContent` | `VotingAndSettlementScreens.kt` | ✅ | ✅ | hub SETTLEMENT 탭 임베드용. BandViewModel.loadSettlement() 연결 |
| `SwipeVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ✅ | VoteViewModel 완전 연결. WebSocket 실시간 groupStatus 갱신 |
| `PlaceSearchScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | 장바구니 토글(낙관적 업데이트) + 스프링 바운스/색상/카운트 슬라이드 애니메이션 |
| `NotificationScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | NotificationViewModel 연결. 읽음/삭제 처리 |
| `MyPassportScreen` | `PassportAndSearchScreens.kt` | ✅ | ✅ | `BandViewModel.loadPassportStamps()` + `getMyStamps()` API 연결 완료 (2026-05-26) |
| `BlindVotingScreen` | `VotingAndSettlementScreens.kt` | ✅ | ❌ | 레거시 — 현재 미사용 |
| 가계부 입력 화면 | ❌ 없음 | ❌ | ❌ | 구 앱도 더미 |
| `ProfileEditScreen` | `ProfileAndSettingsScreens.kt` | ✅ | ✅ | 이름 텍스트필드 + 갤러리 이미지 + PUT /api/users/me (2026-05-26 구현) |

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
| 2026-06-02 | **공유 앨범 버그 수정 2건** — ① 업로드 후 새 사진이 피드 최상단에 추가돼도 스크롤이 안 따라가던 문제: `photos[0].id` 변화 감지 `LaunchedEffect`로 최상단 자동 스크롤. ② "9시간 전" 오표기: 서버 `uploadedAt`(타임존 없는 UTC)을 기기 로컬로 파싱해 9h 어긋나던 것을 `formatRelativeTime`에서 UTC 파싱으로 수정 |
| 2026-06-02 | **공유 앨범 업로드 이미지 압축** — 원본 그대로 Base64 전송(수~십 MB, 업로드 1분+)하던 것을 최대 변 1080px 리사이즈 + JPEG 품질 80% + Base64 NO_WRAP으로 압축(수백 KB). `decodeDownscaledBitmap` 헬퍼 추가. 좌표·촬영시각은 원본 EXIF에서 별도 추출하므로 재인코딩 영향 없음 |
| 2026-06-02 | **공유 앨범 지도 사진 마커 + 좌표 0,0 진단** — 지도 핀을 기본 마커에서 흰 틀 안 사진 썸네일 마커(`PhotoMarker`+`MarkerComposable`)로 교체(피드 photoData를 핀 id로 매칭해 디코딩). 업로드 시 좌표가 (0.0,0.0)으로 저장되던 문제 대응: 포토피커 위치 redact로 GPS 태그가 0,0으로 비워지는 케이스를 "위치 없음"으로 처리(`hasValidGps`), `AlbumService`에 업로드 좌표 로그 + `extractPhotoData`에 EXIF 진단 로그(`AlbumExif`) 추가 |
| 2026-06-02 | **공유 앨범 EXIF GPS 추출 수정** — Android 10+ 위치 메타데이터 redact로 사진 GPS가 항상 null이던 문제 해결. `AndroidManifest`에 `ACCESS_MEDIA_LOCATION` 권한 추가, `AlbumScreen` 사진 선택을 `GetContent()`→PhotoPicker(`PickVisualMedia`)로 전환, `extractPhotoData()`에 `MediaStore.setRequireOriginal()` + 권한 런타임 요청 적용. 좌표 추출 정상화로 지도 핀 표시 및 핀→피드 연결 동작 |
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
| 2026-05-26 | **정산 백엔드-Android 불일치 수정** — `MemberSettlementSummary` 필드명 `name`→`userName`, `balance`→`netAmount`, `profileImageUrl` 제거. `SettlementTransaction` 필드명 `fromName`→`fromUserName`, `toName`→`toUserName`. `toUiSettlement()` `myBalance` 현재 userId 기준으로 수정 |
| 2026-05-26 | **지출 입력/삭제 (USR-020)** — `SettlementContent`에 지출 추가 FAB + `ExpenseInputSheet` BottomSheet 추가. 항목명·금액·통화·분담자 입력. `ExpenseCard` 실제 지출 목록 표시(본인 것만 삭제). `BandViewModel`에 `loadExpenses·createExpense·deleteExpense·updateExpense` 추가. `SyncTripApiService`에 `updateExpense·deleteExpense` API 추가. SETTLEMENT 탭 진입 시 지출 목록 자동 로드 |
| 2026-05-26 | **영수증 OCR (USR-019)** — `ExpenseInputSheet`에 "영수증 스캔" 버튼 추가. 갤러리 이미지 선택 → multipart POST → 항목명·금액·통화 자동 채우기. `SyncTripApiService.scanReceipt` 추가. `OcrReceiptResponse·OcrItemResult` DataModel 추가 |
| 2026-05-26 | **홈 화면 추천 여행지 API 연동** — `BandViewModel.loadRecommendedDestinations()` 추가. 계절 가중치(봄=일본·유럽, 여름=국내·미주오세아니아, 가을=일본·유럽·동남아, 겨울=동남아·미주오세아니아·일본) 기반 그룹 분리 후 각 그룹 내 셔플 → 6개 선택. `HomeScreen` `recommendedContent` 실 데이터 연결 |
| 2026-05-26 | **여권 스탬프 API 연동 (USR-024)** — `ApiPassportStampResponse` DataModel 추가. `SyncTripApiService.getMyStamps()` 추가. `BandViewModel.loadPassportStamps()` + `toPassportStamp()` + `parseStampDate()` 추가(ISO/배열 두 포맷 처리). `BandUiState`에 `passportStamps`·`isPassportLoading` 추가. NavGraph `"passport"` composable에서 실 데이터 로드. `MyPassportScreen`에 `isLoading` 파라미터 추가 + 로딩 스피너 |
| 2026-05-26 | **홈 추천 여행지 UX 강화** — 섹션 헤더 아래 계절별 서브타이틀 문구 표시(봄/여름/가을/겨울 4종). 카드 클릭 시 여행지 상세 ModalBottomSheet 표시(이미지·지역 카테고리 칩·계절별 추천 문구·"여행 계획 만들기" CTA 버튼). 버튼 클릭 시 createTrip 화면으로 진입 |
| 2026-05-26 | **여권 스탬프 도장 찍기 애니메이션** — `PassportStampGrid` 를 순차 stagger(190ms 간격) + spring 바운스로 교체. 각 스탬프가 1.4× 크기에서 튕기며 찍히는 rubber stamp 효과. 신규 스탬프(`newestStampId`)는 1.7× 스케일 + DampingRatioMediumBouncy + 찍힌 직후 잉크 번짐 링(8dp 테두리) fade-out. `LazyVerticalGrid` → `Column+Row` chunked 수동 그리드로 교체(AnimatedVisibility 지원). `AnimatedVisibility`, `scaleIn`, `tween`, `delay` import 추가 |
| 2026-05-26 | **사이드 드로어 메뉴 확장 + 설정 화면 신규 구현** — 드로어 퀵 액션을 2×2 그리드로 확장(내 여권·코드 참여·알림 설정·프로필 편집). `ProfileAndSettingsScreens.kt` 신규 파일에 `NotificationSettingsScreen`(알림 5종 Switch + PATCH API 낙관적 업데이트) + `ProfileEditScreen`(이름 텍스트필드 + 갤러리 이미지 선택·Base64 인코딩·PUT API). `BandViewModel`에 `loadNotificationSettings·updateNotificationSetting·updateProfile` 추가. NavGraph `notificationSettings`, `profileEdit` 라우트 추가. USR-002, USR-027 완료 |

| 2026-05-26 | **과거 여행 기록 (USR-025)** — `PastTripsScreen` 신규 (`HomeScreen.kt`). 홈 드로어에 "지난 여행" `NavigationDrawerItem` 추가. NavGraph `"pastTrips"` 라우트 추가. DONE 상태 밴드를 최신순으로 표시. 썸네일(없으면 그라디언트 플레이스홀더)·여행기간·인원수·완료 뱃지 카드 구성 |
| 2026-05-26 | **장소 검색 Google 통일 반영** — `BandRepository.searchPlaces()` `radiusMeters` 파라미터 제거. `SyncTripApiService.searchPlaces()` `@Query("radiusMeters")` 제거. `NavGraph.kt` `onCategoryChange` / `onSearch` keyword 빈 값 가드 추가(빈 상태에서 API 호출 생략). `PassportAndSearchScreens.kt` 바텀시트 "Google 지도에서 보기" → "지도에서 보기". |

| 2026-05-26 | **Plan B (USR-018/031)** — `ScheduleViewModel`에 `planBResults`·`isPlanBLoading` 상태 + `loadPlanB()`·`executePlanBSwap()` 함수 추가. `ScheduleScreen`/`ScheduleContent`에 슬롯별 "Plan B 추천받기" 버튼(항상 노출) + `PlanBBottomSheet` + `PlanBOptionCard` 추가. `TripBandHubScreen`·NavGraph(두 call site) Plan B 파라미터 연결. |
| 2026-05-26 | **일정 지도 뷰 (Option B 분할화면)** — `ScheduleScreen`/`ScheduleContent` else 브랜치를 지도(240dp 고정)·타임라인 분할 레이아웃으로 변경. `ScheduleDayMapView`(Google Maps + 번호 마커), `NumberedMarker`(카테고리 색 원+흰 숫자), `MapPlaceBottomSheet`(썸네일·정보·길찾기 버튼), `openDirections()`(국내=geo: URI 선택기, 해외=Google Maps) 추가. `isOverseas: Boolean` 파라미터 `ScheduleScreen`·`ScheduleContent`·`TripBandHubScreen`·NavGraph 두 call site 전파. |

| 2026-05-26 | **홈 추천 여행지 랜드마크 지도 연동** — 상세 팝업 "이런 곳이 있어요" 칩 클릭 시 `geo:0,0?q=장소명,여행지` Intent.ACTION_VIEW 실행. 기기 설치 지도 앱(구글/카카오/네이버) 선택 팝업 표시. `HomeScreen.kt` Surface onClick + LocalContext import 추가. |

| 2026-05-26 | **밴드홈 장소 탐색 버튼 중복 제거** — `MyStatusSection` 내 "장소 탐색" OutlinedButton 제거. 하단 BandActionArea의 "장소 탐색하기" 버튼으로 단일화. "준비 완료" 버튼을 `MyStatusSection` 카드 내 전체 너비 버튼으로 정리 |
| 2026-05-26 | **장소 탐색 화면 "준비 완료" 버튼 추가 (USR-009)** — `PlaceSearchScreen` 하단 CartBottomBar에 "준비 완료" 버튼 추가(장바구니 1개 이상 활성화). NavGraph `placeSearch/{bandId}` 라우트에서 `setReady` 호출 후 뒤로가기 |
| 2026-05-26 | **투표 시작 전 픽 수 검증 (USR-014)** — `TripBandHubScreen`에서 투표 시작 버튼 클릭 시 `members.sumOf { it.bookmarkCount } < members.size * 2` 조건 미충족 시 에러 다이얼로그 표시 후 차단. 조건 충족 시 기존 미Ready 경고 다이얼로그 표시 |
| 2026-05-26 | **isMyVoteComplete 타이밍 버그 수정** — `NavGraph.kt` blindVoting 라우트에서 `isMyComplete` 계산 시 `!uiState.isLoading` 가드 추가. 로딩 직후 pendingPlaces 빈 상태를 완료로 잘못 판단하던 오판 제거 |
| 2026-05-26 | **방장 수동 투표 마감 (USR-014)** — `SwipeVotingScreen` TopBar에 방장 전용 "마감하기" TextButton 추가. 확인 AlertDialog 경유 → `advanceBandStatus` 호출 → GENERATING 전환 시 aiLoading 화면 이동. NavGraph `blindVoting` 라우트에 `BandViewModel` 추가 연결 |
| 2026-05-26 | **WebSocket URL 하드코딩 제거** — `ApiClient`에 `wsUrl`/`wsHost` 속성 추가(BuildConfig.BASE_URL 기반 동적 변환). `VoteStompClient`의 `wss://test.sync-trip.app/ws` 하드코딩 제거 |
| 2026-05-26 | **WebSocket 재연결 로직 추가** — `VoteStompClient` `onFailure`/`onClosed`(비정상 코드)에서 지수 백오프 재연결(2s~30s, 최대 5회). `disconnect()` 호출 시 재연결 억제(`intentionalDisconnect`). `onOpen`에서 카운터 리셋 |
| 2026-05-26 | **투표 카드 내가 담은 장소 배지 추가** — `VotingPlaceCard` 이미지 우상단에 `myBookmark=true` 시 Primary 색 "내가 담은 곳" 배지 표시(Bookmark 아이콘 + 라벨) |
| 2026-05-26 | **WebSocket 이벤트 처리 최적화** — `VoteViewModel.connectWebSocket()` `onEvent` 콜백에서 `refreshStatus()`(내 상태+그룹 상태 2 API) → `refreshGroupStatus()`(그룹 상태만 1 API)로 교체. 불필요한 내 상태 재조회 제거. `refreshGroupStatus()` private 메서드 분리 |
| 2026-05-26 | **투표 실패 에러 UI 추가** — `SwipeVotingScreen`에 `snackbarHostState` 파라미터 + `Scaffold` `snackbarHost` 추가. NavGraph에서 `uiState.error` LaunchedEffect 감지 → 스낵바 표시 후 `clearError()` 호출 |
| 2026-05-27 | **Pull-to-Refresh 전 화면 구현 (➕)** — `HomeScreen`, `NotificationScreen`, `MyPassportScreen`, `VoteResultScreen`, `TripBandHubScreen` 전 탭에 스와이프 새로고침 추가. `PullToRefreshBox` (material3.pulltorefresh 서브패키지) 사용. 탭별 새로고침 로직 분기: BAND·SETTLEMENT는 `TripBandHubScreen` 레벨 `PullToRefreshBox`로 처리, SCHEDULE은 `ScheduleContent` 내부 타임라인(`SlotTimeline`) 영역에 직접 `PullToRefreshBox` 배치(구글맵이 제스처 소비하므로 지도 아래 LazyColumn만 감쌈), PHOTO는 `AlbumContent` 내부 FEED 탭 `AlbumFeedList`에 직접 `PullToRefreshBox` 배치. NavGraph 각 라우트에 `isRefreshing` 상태 + `LaunchedEffect(isLoading)` 리셋 + `onRefresh` 콜백 연결 |

| 2026-05-27 | **FCM 알림 신뢰성 + 탭 이동 구현** — 알림 채널 중요도 `IMPORTANCE_DEFAULT` → `IMPORTANCE_HIGH`(헤드업 표시, 재설치 필요), `NotificationCompat.PRIORITY_HIGH` 추가. `HOLIDAY_WARNING` 타입 `ApiNotificationType`에 추가(없으면 알림 목록 역직렬화 NPE 위험). FCM 알림 탭 시 화면 이동: `VOTE_STARTED` → `blindVoting/{bandId}`, 나머지 → `tripLobby/{bandId}`. 포그라운드(`showNotification`) + 백그라운드(FCM SDK 자동) 양쪽에서 data 페이로드 key(`bandId`, `type`) 통일. `PendingIntent` requestCode 고유화(`System.currentTimeMillis().toInt()`). `NavGraph` 알림 route 처리: 앱 실행 중 → 즉시 이동, 앱 종료 상태 → splash 완료 후 이동 |

| 2026-05-27 | **숙소 입력 (USR-003)** — `CreateTripScreen`을 3페이지 플로우로 확장(여행지→여행정보→숙소선택). 3단계 `AccommodationSearchPage` 신규: 상단 Google Map(선택 숙소 Hotel 아이콘 마커, 미선택 시 목적지 중심) + 검색창(`ImeAction.Search`) + 결과 목록(`AccommodationResultCard`). 우상단 "건너뛰기" 회색 `TextButton`. 숙소 선택 시 `ApiPlaceSearchResult.latitude/longitude`를 `BandCreateRequest.accommodationLat/Lng`에 전달, 미선택·건너뛰기 시 null(목적지 위치 기본). 로비 밴드 탭 `AccommodationSection` 카드 + 방장 `AlertDialog` 수정. `PATCH /api/bands/{bandId}/accommodation` + `GET /api/places/search` 엔드포인트 추가(후자 백엔드 구현 필요). `AccommodationUpdateRequest` DataModel, `BandViewModel.updateAccommodation()` 추가 |

| 2026-05-31 | **B-3 숙소 좌표 + 지도 핀** — `BandResponse`에 `accommodationLat/Lng` 추가. `ScheduleDayMapView`에 amber "숙" 호텔 마커 추가(탭 시 숙소명 AlertDialog). `AccommodationSection`에 "지도에서 보기" TextButton 추가(geo: URI 기기 지도 앱). Spring `BandResponse.java` + `toBandResponse()`에 숙소 좌표 포함. `ScheduleContent`/`ScheduleScreen`/`NavGraph`/`TripLobbyScreen` 파라미터 전파 |
| 2026-05-31 | **전용 편집 화면 (A-3/B-4)** — `ScheduleEditScreen` 신규(`ScheduleScreen.kt` 하단). 진입 시 편집 락 획득(`startEditing`), `DisposableEffect`로 이탈 시 자동 해제. 락 실패 시 "○○님이 편집 중" AlertDialog + 자동 복귀. 드래그앤드랍 슬롯 순서 변경(`sh.calvin.reorderable`, 드롭 시 1회 API 호출). swap/Plan B 선택 시 확인 AlertDialog(바텀시트 유지, 취소 가능). 락 만료 에러 스낵바 처리. NavGraph `scheduleEdit/{bandId}` 라우트 추가. `ScheduleReorderRequest` DataModel + `reorderSchedule` API + `reorderSlots` ViewModel 함수 신규 |
| 2026-05-30 | **경고 플래그 배지 (USR-016/A-1)** — `DataModels.kt` `ScheduleSlotResponse`에 플래그 5종 추가(`isOutlierCandidate`/`openingHoursViolation`/`mealWindowViolation`/`lateSchedule`/`openingHoursUnverified`). `@SerializedName(alternate=[...])` 으로 백엔드 isXxx 네이밍 변형 대응. `ScheduleScreen.kt` `SlotWarningBadges` 컴포넌트 신규 → 슬롯 카드에 ⚠ 영업시간위반 / 🌙 심야 / 🍽 식사시간 / 📍 동선이탈 / ❓ 영업미확인 배지 표시. ScheduleScreen·ScheduleContent 공용 `ScheduleSlotCard` 경유로 양쪽 화면 자동 적용 |
| 2026-05-30 | **편집자 게이팅 (A-2)** — `DataModels.kt` `ScheduleResponse`에 `editingUserId: Long?`·`editingUserName: String?`·`canEdit: Boolean` 추가. `ScheduleScreen.kt` `EditingByOtherBanner` 컴포넌트 신규 → 타인이 편집 락 보유 시 "○○님이 편집 중입니다" 배너 표시(ScheduleScreen·ScheduleContent 양쪽). 편집 버튼을 백엔드 `canEdit` 값으로 게이팅. `NavGraph.kt` `schedule/{bandId}` 라우트 `canEdit` 배선을 백엔드 응답 값으로 교체 |

| 2026-05-31 | **HTTP 에러 메시지 사용자 친화적 처리 (➕)** — `util/ErrorUtils.kt` 신규. `Throwable.toUserMessage()` 확장 함수: `IOException`→네트워크 안내, `HttpException 5xx`→서버 오류 안내, `401/403` 등 상태별 한국어 메시지. `BandViewModel`, `ScheduleViewModel`, `VoteViewModel`, `AlbumViewModel`, `NotificationViewModel`, `AuthViewModel`, `NavGraph.kt` 전체 `.message` → `.toUserMessage()` 교체 |
| 2026-05-31 | **일정 편집 버튼 연결 (➕)** — `TripBandHubScreen` 일정 탭에서 "방 삭제" 옆 EditNote 아이콘 버튼 추가. 클릭 시 `scheduleEdit/{bandId}` 화면으로 이동. `TripBandHubScreen.onGoToScheduleEdit` 파라미터 추가. NavGraph 연결 |
| 2026-05-31 | **영업시간 미확인 뱃지 제거 (➕)** — `ScheduleScreen.kt` `SlotWarningBadges`에서 "❓ 영업시간 미확인" 항목 제거. 지도 API가 영업시간을 미제공하는 경우가 많아 노이즈로 판단 |
| 2026-05-31 | **타임라인 숙소 출발 행 추가 (➕)** — `SlotTimeline`에 `accommodationName` 파라미터 추가. 숙소가 설정된 경우 타임라인 최상단에 `AccommodationDepartureRow`(호텔 아이콘 + 숙소명 + "09:00 출발") 표시. 이후 첫 슬롯까지 `TravelTimeConnector`로 연결. 이동시간 원인 시각화 |
| 2026-05-31 | **TravelTimeConnector 이동시간 텍스트 표시 (➕)** — `TravelTimeConnector`의 기존 미사용 `minutes` 파라미터를 실제 표시에 활용. 연결선 옆에 "Xmin" 텍스트 추가. 슬롯 간 이동시간 가시화 |
| 2026-05-31 | **지도 마커 Day 전환 버그 수정 (➕)** — `ScheduleDayMapView` `forEachIndexed` 내 `MarkerComposable`에 `key(slot.scheduleId)` 래퍼 추가. 기존 코드는 Day 전환 시 `rememberMarkerState`가 위치 기반으로 재사용돼 이전 Day 좌표가 유지되는 버그 발생. `key()`로 슬롯 ID 변경 시 강제 재생성 |
| 2026-05-31 | **크로스 Day 드래그 이동 구현 (➕)** — `ScheduleEditScreen` 전면 개편: Day 탭 → 전체 Day 플랫 리스트(`FlatItem` sealed class). `EditDayHeaderRow` 신규(Day 헤더, 비드래그). 드래그 완료 후 `movedIndex` 앞 `DayHeader`로 목적지 Day 판별. 같은 Day: `reorderSlots`, 다른 Day: `moveSlot(POST /schedule/move)`. `ScheduleMoveRequest` DataModel + `SyncTripApiService.moveSchedule` + `ScheduleRepository.moveSlot` + `ScheduleViewModel.moveSlot()` 추가 |

| 2026-05-31 | **투표 진행률 표시 버그 수정 (➕)** — `VoteViewModel.loadVotePlaces()`에서 autoLike 장소 제출 후 `votedPlaces`에도 추가. 북마크 장소가 있을 때 `totalCount(= votedPlaces.size + pendingPlaces.size)`가 전체 장소 수보다 작게 표시되던 문제 해결. 백엔드 `BandMember.voteCompleted` 플래그 도입(DDL v14) 대응 — `groupStatus.isAllComplete`이 `voteCompleted` DB 플래그 기반으로 판정됨 |

| 2026-06-01 | **ScheduleEditScreen 버그 수정 9건 (➕)** — ① `BackHandler` 추가(시스템 뒤로가기 미저장 확인). ② `FlatItem.EmptyDayPlaceholder` 신규 — 빈 Day에 드롭존 제공(`buildFlatItems` 수정). ③ `onMove` DayHeader·EmptyDayPlaceholder 가드 추가 — 헤더 위치로 슬롯 이동 차단. ④ `LaunchedEffect(schedule)`에 `hasPendingChanges = false` 추가 — swap 후 저장 버튼 오잔류 해결. ⑤ `onSelectAlt` / `onSelect`에 `showSwapSheet/showPlanBSheet = false` 추가 — 바텀시트+다이얼로그 중첩 해결. ⑥ `detailSlot` 상태 + `PlaceDetailBottomSheet` 연결 — 슬롯 카드 탭 시 상세정보 표시. ⑦ `saveScheduleChanges`에서 마지막 API 호출에만 `notify=true` — 알림 중복 1건으로 집약. ⑧ 실패 에러 메시지 구체화. ⑨ 백엔드 `ScheduleMoveRequest` / `ScheduleReorderRequest` DTO에 `notify` 필드 추가, `ScheduleService`에 `shouldNotify()` 체크 |

| 2026-06-01 | **일정 화면 UX 개선 (➕)** — ① 슬롯 카드 탭 시 `cameraPositionState.animate()` (zoom 16f, 500ms)로 지도 마커 포커스. ② 선택된 마커 32dp→44dp 확대·테두리 강조. ③ `HotelMarker` "숙" 텍스트→`Icons.Outlined.Hotel` 아이콘 교체. ④ 숙소 마커/타임라인 숙소 행 탭 시 `AlertDialog`→`HotelBottomSheet`(숙소 길찾기 버튼) 교체. ⑤ 지도 마커 간 순서대로 대시 점선(`Dash(20f)+Gap(12f)`) 연결 — 숙소 포함. ⑥ `PlaceDetailBottomSheet`에 길찾기 버튼 추가·`durationMinutes` MetaBadge 제거. ⑦ 카테고리 마커 색상 고정 hex 전환 — CULTURE `#1E88E5`(파랑)·ETC `#546E7A`(블루그레이)로 회색 탈피. ⑧ `AccommodationDepartureRow` 클릭 시 HotelBottomSheet 표시. ⑨ Plan B fallback — altPool 3km 초과 시 거리 무관 카테고리 매칭 반환 (백엔드 연동). |

| 2026-06-01 | **별표(Plan B) → 화살표(swap) 통합 (➕)** — 슬롯 카드 옆 두 버튼을 하나로 통합. 화살표 탭 시 기존 `loadAlts`(altPool 조회) 대신 `loadPlanB`(거리+점수 기반 추천) 호출. `SlotSwapBottomSheet`, `AltOptionCard`, `AddSlotBottomSheet` composable 제거. `ScheduleAltResponse`, `ScheduleAddRequest` DataModel 제거. `getAlts`/`addToSchedule` API·Repository·ViewModel 함수 제거. `executePlanBSwap` 제거(편집 락 이미 보유 시 `swapSlot` 직접 호출). `ScheduleContent`·`ScheduleEditScreen`·`TripLobbyScreen`·`NavGraph`·Preview 연쇄 파라미터 정리. `PlanBBottomSheet`의 `onSelect` → `onSwapSlot` 연결로 동일 결과. `SlotTimeline`에서 "Plan B 추천받기" OutlinedButton 및 `DayEmptyContent` "장소 추가" 버튼 제거. 바텀시트 제목 "Plan B 추천" → "근처 대안 장소 추천" 변경 |
| 2026-06-01 | **일정 편집 Day 헤더 장소 추가 기능 (➕)** — `EditDayHeaderRow`에 `+` `IconButton` 추가(`onAddClick` 파라미터). `ScheduleEditScreen`에 `onNavigateToAddPlace(dayNumber)` 파라미터 추가. 탭 시 `scheduleAddPlace/{bandId}/{dayNumber}` 신규 NavGraph 라우트로 이동. `PlaceSearchScreen`에 `onAddToSchedule: ((ApiPlaceSearchResult) -> Unit)?` 파라미터 추가 — 비null이면 픽 바 숨기고 카드 북마크 버튼 → "+" 추가 버튼으로 전환. 선택 시 `ScheduleViewModel.addSlotFromSearch()` → `POST /schedule/add-search` 호출. `ScheduleAddFromSearchRequest` DataModel 추가. `SyncTripApiService.addSlotFromSearch` + `ScheduleRepository.addSlotFromSearch` 추가. 백엔드 `POST /schedule/add-search` 연동 — externalId 기반 Place upsert + Day 말미 슬롯 삽입 + `assignTimesInOrder` 재계산 |
| 2026-06-01 | **일정 탭 지도 초기 위치 버그 수정 (➕)** — `ScheduleDayMapView`의 `rememberCameraPositionState()`에 첫 슬롯 좌표를 초기값으로 설정. 기존: 지도 기본값(0,0)에서 animate()로 이동해 렉처럼 보임. 수정 후: 진입 시 첫 슬롯 위치에서 바로 렌더링 |
| 2026-06-01 | **ScheduleEditScreen 편집 락 버그 수정 3건 (➕)** — ① **무한 로딩 버그**: "+" 탭 후 뒤로가기 시 `DisposableEffect.onDispose`가 `finishEditing`을 호출해 락 해제 → `startEditing` 재진입 레이스 컨디션으로 무한 로딩. `isNavigatingToChild: MutableState<Boolean>` 플래그 추가 — "+" 탭 시 `true` 설정, `onDispose`에서 `finishEditing` 억제. ② **배너 지속 버그**: 편집 후 돌아오면 "편집 모드" 배너가 계속 표시됨 — `finishEditing`이 `onBack`에만 있어 시스템 제스처 뒤로가기(hasPendingChanges=false)에서 미호출. `onDispose`에 `finishEditing` 복구(isNavigatingToChild 플래그로 자식 화면 이동 시 억제). ③ **addSlotFromSearch 락 설계 오류**: `addSlotFromSearch`가 불필요하게 `startEditing`/`finishEditing` 호출 — `isNavigatingToChild`로 락이 이미 유지되므로 제거. `onFailure`에서 보유 중인 락을 실수로 해제하는 코드도 제거. `scheduleAddPlace` 에러 메시지 표시 — `scheduleViewModel.uiState.error` 관찰 추가(`NavGraph`) |
| 2026-06-01 | **백엔드 addSlotFromSearch DB 충돌 버그 수정 (➕)** — 기존 슬롯 있는 Day에 추가 시 `Duplicate entry '(bandId)-(day)-(1)'` 오류. 원인: 새 슬롯을 `slotOrder=1`로 먼저 INSERT한 뒤 기존 슬롯을 같은 순서로 UPDATE → unique constraint 충돌. 수정: 새 슬롯을 미리 저장하지 않고 `existingSlots` 리스트에 추가 후 `assignTimesInOrder`로 최종 순서 결정, `saveAll`로 INSERT+UPDATE 일괄 처리 (`C:\SyncTrip-Spring\...\ScheduleService.java`) |
| 2026-06-01 | **편집 락 누수 수정 — 1분 타임아웃 + 하트비트 (➕)** — 앱 강제 종료·네트워크 단절·여행 일괄 이탈 등으로 `finishEditing`이 누락되면 다른 멤버가 최대 5분간 락에 걸리던 문제. ① **백엔드** `Band.EDIT_LOCK_TIMEOUT_MINUTES` 5→1분 — `lastEditingAt` 이후 1분 지나면 `isEditingByOther`가 락을 무효 처리(자동 해제). ② **프론트 하트비트** `ScheduleViewModel.heartbeatEditing()` 신규(isEditing/error 비변경, 실패 무시) — `ScheduleEditScreen`과 `scheduleAddPlace`(NavGraph)에서 30초마다 `/edit/start` 재호출로 `lastEditingAt` 갱신. 액션 없이 화면만 봐도 락 유지, 화면 이탈 시 하트비트 정지로 1분 내 자동 만료. ③ `finishEditing` `onFailure`에서도 `isEditing=false`로 로컬 상태 정리 — 호출 실패 후 재진입 시 `startEditing`이 다시 호출되도록 (`Band.java`, `ScheduleViewModel.kt`, `ScheduleScreen.kt`, `NavGraph.kt`) |

| 2026-06-01 | **편집 락 배너/canEdit 영구 지속 버그 수정 (➕)** — 편집 중 사용자가 finishEditing 없이 이탈(앱 강제 종료·밤새 방치)하면 다른 멤버에게 "○○님이 편집 중" 배너와 `canEdit=false`가 **영구히** 남던 버그. 원인: `toScheduleResponse`가 `band.getCurrentlyEditingUserId()`(만료 미적용 raw getter)로 `editingUserId`를 계산 — `isEditingByOther`만 타임아웃을 적용하고 배너/canEdit 경로는 미적용이라 분리됨. 수정: `Band.getActiveEditingUserId()` 신규(EDIT_LOCK_TIMEOUT_MINUTES 만료 적용) + `isEditingByOther`도 이를 공유하도록 리팩터, `ScheduleService`에서 해당 메서드 사용. 이제 타임아웃 경과 후 일정 새로고침 시 배너 자동 소멸·canEdit 복구 (`Band.java`, `ScheduleService.java`) |
| 2026-06-01 | **편집 진입 시 락 항상 재획득 (➕)** — `ScheduleEditScreen` 진입 `LaunchedEffect`의 `if(!isEditing) startEditing` 가드 제거. 프론트 `isEditing=true`인데 백엔드 락 미보유(상태 불일치) 시 `startEditing`을 건너뛰어 장소 추가가 FORBIDDEN("편집 시작 버튼을 눌러주세요")으로 실패하던 문제. 진입(최초·"+" 복귀)마다 `startEditing` 호출 — 본인 보유 시 idempotent 갱신, 타인 보유 시 CONFLICT 즉시 안내. `isNavigatingToChild`가 onDispose finishEditing을 억제하므로 finish/start 레이스(무한 로딩) 재발 없음 (`ScheduleScreen.kt`) |

| 2026-06-01 | **히어로 배지 상태별 분기 (➕)** — `BandHeroSection` 우하단 배지를 밴드 상태에 따라 전환. PLANNING: "👥 N/M 준비", VOTING: "🗳 투표 중", GENERATING/TRAVELLING/DONE: 배지 숨김. 투표 완료 후 합류한 멤버(`joinedAfterVoting=true`)는 `isReady`가 영구 false여서 "3/4 준비"가 절대 해소되지 않던 UX 문제 해결. (`TripLobbyScreen.kt`) |

| 2026-06-01 | **홈 추천 여행지 → 여행 생성 Step 2 직행 (➕)** — 홈화면 추천 카드 "이 여행지로 계획 세우기" 클릭 시 선택 여행지명을 `createTrip?destination={name}` 쿼리 파라미터로 전달. `CreateTripScreen`에 `initialPage: Int = 1` 파라미터 추가 — `destinationArg` 있으면 `page=2` 초기화해 Step 1(여행지 선택) 건너뛰고 Step 2(여행 정보)부터 진입. `getPopularDestinations()` 응답에서 name 매칭으로 `selectedDestination` 자동 설정 + `bandName` 자동 입력. `selectedDestination` 로딩 중 `CircularProgressIndicator` 표시(null 크래시 방지). FAB·EmptyTripsPlaceholder 진입은 `initialPage=1` 유지. `HomeScreen.onCreateTripClick: (String) -> Unit`으로 시그니처 변경. (`HomeScreen.kt`, `NavGraph.kt`, `TripCreationScreens.kt`) |

| 2026-06-02 | **일정 탭 드래그 분할 패널 (➕)** — `DraggableMapTimelinePanel` 신규 컴포저블 추가(`ScheduleScreen.kt`). 지도(상단)와 타임라인(하단) 사이 pill 드래그 핸들 삽입. 세로 드래그로 3개 스냅 지점 전환: ① 핸들 아래로 → 목록 전체(지도 0dp), ② 기본 240dp, ③ 핸들 위로 → 지도 최대(목록 최소 60dp). `Animatable` + `spring<Float>()` 애니메이션. `ScheduleScreen`·`ScheduleContent` 양쪽에서 `DraggableMapTimelinePanel` 호출로 교체 |

**마지막 수정:** 2026-06-02 (일정 탭 드래그 분할 패널) | **참조 문서:** `SyncTrip_인수인계문서_v6.md`, `SyncTrip_구현현황.md`
