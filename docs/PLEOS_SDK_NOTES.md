# Pleos Connect 개발 참고 노트

공식 매뉴얼: https://document.pleos.ai/docs/pleos-only/getting-started
(2026-10-30 기준으로 확인한 내용. 버전이 바뀌면 이 문서를 갱신할 것)

> 함께로(Together-Ro) 프로젝트에서 확인하고 옮겨 온 문서입니다. 본문의 `TripSession`, `trip/TripService` 같은 클래스 이름은 함께로 코드(`/home/oem/AndroidStudioProjects/Together-ro`)를 가리킵니다.

## 개발 환경

- 에뮬레이터: AVD `Pleos_Connect_v2.1.0` (Android 14 / API 34, x86_64), RAM 4GB, Cold boot
  - 시스템 이미지 업데이트 사이트: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
  - 실행: `~/Android/Sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -gpu host`
- Virtual Console (차량 신호 시뮬레이터): `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`
  - Motion: 기어, 브레이크, 조향각, 주행거리 / ADAS: 크루즈 컨트롤 / Safety: 경고등 / Energy, Climate, Convenience
  - 차량 위치(GPS)는 Virtual Console이 아닌 에뮬레이터 Extended Controls > Location(경로 재생)으로 시뮬레이션
  - **CLI 제어: `tools/vconsole.py`** (gear / drive / brake / park / state / replay)
    - HTTP API: `POST /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `POST /vehicle/controls?speed=&brake=&accelerator=&steering=`, `GET /vehicle/state`, `GET /vehicle/catalog`
    - 브라우저가 붙어 있어야만 명령을 받음(`emulator_inactive` 오류). `POST /browser/session?client=<id>` 하트비트를 1초마다 보내면 됨
    - 브레이크 페달을 밟아도 속도가 자동으로 줄지 않음. 급제동을 재현하려면 속도도 함께 낮춰야 함
- 앱 제출 조건: minSdk 28 이상

## Maven

```kotlin
// settings.gradle.kts
maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
```

| SDK | 좌표 | 상태 / 비고 |
|---|---|---|
| Vehicle | `ai.pleos.playground:Vehicle:2.3.2` | 사용 가능 (신청 불필요) |
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | 사용 가능 (신청 불필요) |
| TextToSpeech | `ai.pleos.playground:TextToSpeech:3.3.1` | ON_DEVICE 모드는 등록 불필요, HYBRID/SERVER는 Playground 클라이언트 ID/시크릿 필요 |
| SpeechToText | `ai.pleos.playground:SpeechToText:3.3.1` | 위와 동일 |
| FusedLocation | `ai.pleos.playground:FusedLocation:1.1.3` | Playground에서 "준비 중"으로 표시됨 |

Playground 콘솔 기준(2026-10-30): Vehicle State, NaviHelper는 사용 가능. FusedLocation, TextToSpeech, SpeechToText, Vehicle Control은 "Coming Soon" → TTS/STT는 ON_DEVICE 모드를 기본으로 하고 서버 모드는 열리면 전환.
"API 권한 관리 > Vehicle Data"는 클라우드 REST API(유럽 지역 전용)라 이 앱에서는 사용하지 않음.
Playground 자격 증명(프로젝트 ID, 클라이언트 ID/시크릿)은 `local.properties`에만 보관(git 제외).

주의: nexus-playground 서버 인증서 키가 약해서 시스템 curl은 거부함 (`--ciphers DEFAULT@SECLEVEL=1` 필요). Gradle(JDK)에서 문제가 생기면 이 부분부터 확인.

## Vehicle SDK

```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()
val brake = vehicle.getBrake()      // getXxx()로 도메인 객체 획득
// checkXxxCapabilities / getCurrentXxx / registerXxx / unregisterXxx / setXxx
// 결과는 비동기 onComplete / onFailed 콜백
vehicle.release()                    // 모든 콜백이 끝난 뒤 호출
```

- 패키지: `ai.pleos.playground.vehicle`, `.api`, `.listener`, `.constant`, `.callback.exception`
- 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer, Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper
- **문서에는 없지만 SDK 2.3.2에 `getGear()`가 있음** (AAR을 javap로 확인):
  `getCurrentGearSelectionState(onComplete: (VehicleGear?) -> Unit, onFailed)`, `registerGearSelection(GearSelectionListener)`. 권한 `CAR_DRIVE`
  - **구독 직후 P(값 4)가 한 번 온 뒤 2ms 만에 실제 기어(D=8)가 옴.** 주행 중에 앱을 켜도 잠깐 P로 보이므로, P가 1.5초 유지돼야 정차로 판단함(`TripSession.parked`)
- Brake: `getCurrentBrakePedalStatus`, `registerBrakePedalStatus(BrakePedalStatusListener { onBrakePedalStatusUpdated(Boolean?) })`. 권한 `CAR_DRIVE`
- 모든 리스너는 `VehicleListener.onFailed(Exception)`를 함께 구현해야 함
- Seat 착좌(2026-11-04 확인): `checkSeatOccupancyCapabilities`(지원 좌석 목록) → `getCurrentSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT), …)`, `registerSeatOccupancy(SeatArea, SeatOccupancyListener)`. 권한 `pleos.car.permission.CAR_SEATS`
  - **SDK 2.3.2의 Boolean은 "원시 값 == 1"(javap로 확인)인데, AAOS `SEAT_OCCUPANCY`(0x15400BB0)에서 1은 VACANT, 2가 OCCUPIED.** 에뮬레이터 Virtual Console도 AAOS 값을 보내서 앉으면 false, 비면 true가 옴. 앱은 true를 빈자리로 읽음(`PleosVehicleSource.seated`)
  - Virtual Console: `POST /vehicle/seat?kind=occupied&area=passenger&value=true` (area: driver, passenger, rear_left, rear_right). 좌석별 안전벨트 조작은 없음(경고등만)
- **속도는 Vehicle SDK에 없음** → AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(m/s) 사용
  - `useLibrary("android.car")`(compile only, 기기에서는 bootclasspath)
  - 권한 `android.car.permission.CAR_SPEED`는 **런타임 권한**(위치 그룹, 허용 창에는 "위치 정보"로 표시)
  - API 34라 `registerCallback`(deprecated) 사용. `subscribePropertyEvents`는 API 35부터
- 권한(매니페스트 선언, 런타임 다이얼로그 아님): `pleos.car.permission.CAR_DRIVE`(DrivingMode, Brake, Odometer), `CAR_SAFETY`, `CAR_SPEED`, `READ_ADAS_STATES`, `READ_CAR_STEERING` 등. 제어는 `CONTROL_CAR_*`. 필요한 최소 권한만 선언할 것
- 권한이 없으면 `onFailed`로 `VehicleException`(`ERROR_SECURITY_PERMISSION`)이 옴

## NaviHelper SDK

Drive Mate에서 확인(2026-11-02, NaviHelper 3.0.5 + navi-data 3.0.4):
- `requestRoute(RouteInfo(latitude, longitude, displayLatitude = null, displayLongitude = null, poiName, poiId, poiSubId, address))`. `poiId`·`poiSubId`는 null 불가(내비 POI가 아니면 `""`, `"0"`). 권한 `pleos.car.permission.NAVI_ROUTE` + **`NAVI_ROUTE_SEARCH`**(없으면 SecurityException)
- `requestNavigationScreen(NavigationScreenRequest.Foreground)`: 내비 화면을 앞으로. 권한 **`NAVI_CUSTOM_MAP`**
- 권한 이름은 문서에 없고 AAR 안 문자열에서 찾음(`NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`, `NAVI_CUSTOM_ETC`)
- 내비 앱(`ai.umos.maps.android.navigation.app`)은 서버 `connect-gw-coral.umos.ai`에 HTTPS로 붙어야 준비됨. HTTPS를 가로채는 네트워크에서는 "서비스 연결 중입니다"에 멈추고, 받은 경로 요청은 `navi not ready ... Booting`으로 남음

순서: `NaviHelper` 생성 → `initialize()` → `addListener(NaviHelperEventListener)` → API 호출 → `removeListener()` → `release()`
기능: 목적지 설정/조회/취소, 경로 옵션/재탐색, 경유지 추가/조회/삭제, 경유지 포함 경로 요청, 주행 상태 이벤트
문서: `/docs/pleos-only/connect-sdk-pleos/NaviHelper/{destinations,routes,waypoint-settings,event-listeners}`

## TextToSpeech / SpeechToText

```kotlin
val tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)  // ai.pleos.playground.tts
// initialize() → addEventListener() → setLocale() → speak() / stop() → removeEventListener() → release()
val stt = SpeechToText(applicationContext, Mode.ON_DEVICE)   // ai.pleos.playground.stt
// initialize() → addListener(ResultListener) → setLocale() → request() → stop() → removeListener() → release()
```

## Gleo AI

- 서드파티 앱이 LLM을 직접 호출하는 API는 없음. Gleo Interface(function calling)로 앱이 함수를 노출하는 방식
  - App Schema(JSON)로 함수를 등록 → Gleo가 Action으로 호출 → 앱이 Callback으로 응답
  - 문서: `/docs/pleos-only/app-integration-guide/guide/{core-concepts,app-capability,action_callback,examples,deliver-schema}`
- 따라서 퀴즈 출제나 발화 해석은 앱 자체 로직(문제 은행, 키워드 매칭)이나 외부 서버로 처리하고, Gleo 연동은 "음성 명령 진입점"으로 활용

## 운전 중 제한 (Driving restriction)

- `CarUxRestrictionsManager.registerListener()`로 `onUxRestrictionsChanged()`를 구독하고, `isRequiresDistractionOptimization()`과 `getActiveRestrictions()`에 따라 기능 제한
- 예: `UX_RESTRICTIONS_NO_VIDEO`
- 앱 설계 원칙: 주행 중에는 음성만, 화면이 필요한 게임(노래방, 정산 화면)은 P단에서만
- 에뮬레이터 확인 결과(2026-10-30):
  - 매니페스트 `distractionOptimized=true`를 선언한 일반 Activity(Compose)는 D단 주행 중에도 표시됨. Car App Library 템플릿 앱도 표시됨
  - 주행 중에는 `isRequiresDistractionOptimization=true`, `activeRestrictions=0x50`(NO_VIDEO, NO_SETUP)
  - **시스템 권한 허용 창은 주행 중에 막히고**, 그 창이 위에 있으면 앱 영역 전체가 "주행 중에는 사용할 수 없습니다"로 가려짐 → 권한 요청은 반드시 정차(P단) 중에만
    - D단 40km/h에서 허용 창이 그대로 표시된 적도 있음. 어느 쪽이든 주행 중 조작이 되므로 P단에서만 요청함
  - 다른 앱(내비)이 앞에 오면 우리 앱은 백그라운드가 되므로, 여행 기록은 포그라운드 서비스(`trip/TripService`, 유형 `specialUse`)에서 유지
  - 내비 앱: `ai.umos.maps.android.navigation.app/.MainActivity`
  - 앱 영역(Pleos 홈 화면의 TaskView)은 1682×1210px. 왼쪽에는 Driving view가 고정되어 있음

## 기타

- 실차 플랫폼 식별: `Build.DEVICE` = `CONNECT_S` / `CONNECT_L` / `CONNECT_W` (에뮬레이터에서는 사용 불가)
- 지역 코드: 시스템 프로퍼티 `product.sw.target_region` (KOR/EUR/USA/CAN)
- 샘플 앱(Vehicle SDK Sample)은 일반 Activity 기반 Android 앱 (Car App Library 템플릿 아님)
