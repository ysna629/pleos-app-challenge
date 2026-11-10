# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Pleos 차량용 앱 개발 공모전 프로젝트

현대자동차그룹 "차량용 앱 개발 공모전 — Code your App into Pleos App Market" 출품 프로젝트.
이 파일은 이전 세션(2026-10-23)에서 조사한 내용을 이어가기 위한 핸드오프 문서다.

> **릴리스 APK (2026-11-10)**: `apk/개척자들_Route94_release.apk`(1.0, v2 서명, 카카오 키 포함). 서명 키는 `~/.android/route94-release.jks`(alias `route94`), 비밀번호는 로컬 Gradle 속성 `route94*` 4개 — **저장소 밖, 팀 공유 드라이브에 백업 필요**(잃으면 같은 서명으로 업데이트 불가). `build.gradle.kts`는 그 속성이 있을 때만 release 서명. 릴리스는 `clean assembleRelease`로(카카오 키 인라인 문제). 에뮬레이터에 release를 깔면 디버그와 서명이 달라 `installDebug` 전에 `adb uninstall` 필요(기록 초기화됨).

> **현재 상태 (2026-11-01)**: 아이디어 확정(운행일지 + 주차 위치 음성 메모, 아래 "팀 회의 결과"). 기술 검증 완료, `mockup/`에 동작하는 목업 앱 있음. **앱 이름 Route94(2026-11-10 확정, 미국 Route 66에서 따옴), 팀명 개척자들.** 이전 이름 "도착노트". 첫 커밋 완료(`sample/`·빌드 산출물은 `.gitignore`로 제외). 다음: 실사용 테스트 → 제출 준비(앱 이름·팀명, 기획서, 시연 영상, README·릴리스 APK).

## 팀 / 진행 방식
- 부문: **일반(스타트업 포함)**
- 인원: 2명 (회사 동료, 둘 다 개발자, 안드로이드 경험 있음)
- 개발은 **AI 코딩 중심**으로 진행 (규정상 제한 없음 확인. 단 PT에서 코드·SDK 활용을 직접 설명할 수 있어야 함)
- 관심 분야: 전기차 충전·배터리, 가족·아이·반려동물, 출퇴근·업무, 휴식·웰니스

## 공모전 핵심
- 공식 사이트: https://appdevchallenge.auto-hmg.io/
- 접수: 2026-10-21 ~ **2026-11-15**, 이메일 `appdevchallenge@hyundai-partners.com`
  - 제목: `[차량용 앱 개발 공모전] 팀명_대표자명`
  - 필수: 접수양식(기획서, DOCX) + 서명 동의서(전원)
  - 선택(가산점): 시연 영상, GitHub 저장소(소스 + `apk/팀명_앱이름_release.apk`, README에 앱 소개·실행 방법·테스트 환경)
- 온라인 설명회: **2026-10-28(수) 오후** (다시보기 제공, 공지사항에서 링크 확인)
- 1차 발표 11-23 (20팀 통과) → **2차 PT 12-03~04, 실제 동작하는 앱 필수** (에뮬레이터 시연 인정) → 최종 발표 12-18 → 2027-02 전시·시상
- 일정상 1차 발표 후 PT까지 열흘뿐이므로 **접수 전부터 개발 병행** 필요. MVP 목표: 화면 3개, SDK 도메인 3개 이내
- 상금: 대상 1,000만 / 최우수 600만 / 우수 400만(부문별 2팀) / 아이디어 특별상 150만

### 1차 심사 기준 (배점 비공개)
| 항목 | 내용 |
|---|---|
| 차량특화성(필수) | 모바일 대비 차량 환경만의 가치, 주행·정차 맥락 및 차량 기능과 결합 |
| 창의성(필수) | 독창성, Pain Point 명확성 |
| 기술 활용성(필수) | Pleos Playground SDK·API를 핵심 기능에 활용 |
| 사업성(필수) | 타깃, 예상 MAU, Retention, 확대 계획 |
| 개발 산출물(가산점) | 시연 영상, GitHub(소스+APK) |

- 사이트 예시 아이디어(음성 메모, 안전운전 점수, 배달 기사 동선)는 중복 우려로 피한다
- IP는 출품자 보유. 수상작은 주최 측 이용 허락·4개월 우선협상권. 타 공모전 수상작 출품 불가

## 기술 환경 (Pleos Connect)
- 문서: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
- Playground: https://pleos.ai/playground (가입 → Workspace > My Project에서 프로젝트 생성 → SDK 메뉴에서 사용 신청)
- 요구: Android API 28+, Gradle 8.0+, Java/Kotlin
- 공모전 지정 SDK: Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation

### SDK 제공 상태 (에뮬레이터 검증 2026-10-30, `smoketest/`)
- **Vehicle 조회·제어 모두 에뮬레이터에서 동작.** `CONTROL_CAR_*`는 `protectionLevel="normal"`이라 별도 승인 없이 매니페스트 선언만으로 됨 (실차 출시 시 App Market 승인은 별개)
- TextToSpeech / SpeechToText / FusedLocation: 현재 문서에 "Coming Soon" 표기 없음(TTS·STT 3.3.1, FusedLocation 1.1.3). **미검증**
- Vehicle SDK 버전: 2.3.2 (nexus에는 2.0.3, 2.3.2만 존재)

검증 결과 (P 기어 기준):
| 동작 | 결과 |
|---|---|
| `setHvacTemperature`, `setHvacSeatTemperature`, `setWindowPosition`, `setEvChargePercentLimit` | 성공 (D에서도 성공) |
| `setAmbientLightSwitch`, `setAmbientLightBrightnessColor` | 성공 (D에서도 성공). 설정값 read-back만 확인, 화면 변화는 미확인 |
| `moveSeatBackrestAngle`, `setDoorLock` | **P에서만 성공. D에서는 `IllegalArgumentException: 50001`** (주행 중 잠금) |
| `moveSeatBackrestAngle` 후 위치값 | 명령은 수락, `getCurrentSeatBackrestAnglePosition`은 0 그대로 (위치 변화 미확인) |
| `getTripCurrentDrivingTime` | `ERROR_NOT_SUPPORTED_PROPERTY` (에뮬레이터). 다른 Odometer getter 미검증 |

운행일지용 검증 (`smoketest` logbook suite: `am start --user 10 -n ai.pleos.challenge.smoketest/.MainActivity --es suite logbook`):
| 동작 | 결과 |
|---|---|
| `NaviHelper.initialize`, `getCurrentLocationInfo` | 성공. 위도·경도·주소("수정구 시흥동") 수신 |
| `getDestinationInfo`, `getRecentDestinationInfo` | 응답은 오지만 경로가 없어 빈 값 |
| `requestRoute(RouteInfo(서울역 좌표, poiId=""))` | `onError(CAUSE_SEARCH_ROUTE)`. 경로 탐색 실패 원인 미확인 (poiId 필요 또는 에뮬레이터 제약) |
| `FusedLocation.getLastKnownLocation` | `pleos.car.permission.FUSED_LOCATION` 런타임 grant 후 성공. 단 에뮬레이터 기본 GPS(구글 본사 좌표)라 내비 위치와 다름 |
| `getCurrentDistanceDriven`, `registerDistanceDriven` | **동작.** Virtual Console `POST /vehicle/odometer?traveled_distance_km=...` 값이 리스너로 들어옴. 처음 콜백이 안 오던 건 에뮬레이터를 새로 부팅하자 해결(서비스 상태 문제로 추정). 누적 거리는 줄어드는 값을 무시하는 듯하니 테스트 시 증가하는 값 사용 |
| Trip 거리·시간 getter 전부 | `ERROR_NOT_SUPPORTED_PROPERTY` (capability도 미지원). 운행 거리는 출발·도착 시 누적 거리 차이로 앱이 계산 |
| `CarInfo.getCRNInfo/getVINInfo` | `"invalid"` (에뮬레이터) → 차량번호는 사용자 입력 |
| TTS `Mode.ON_DEVICE` | **동작**(앱 등록 불필요). `addEventListener`는 `initialize` 성공 후에 호출해야 함(먼저 하면 SDK 내부 lateinit 예외) |
| STT `Mode.ON_DEVICE` | **동작**. 에뮬레이터 마이크는 무음이라 `request()` 후 `sendAudio(16kHz mono PCM)`+`completeAudioSend()`로 검증. "지하 5층 에이 4" → "지하 오층에 사회/의사"처럼 숫자·영문 오인식 → 후처리(`ParkingText`)와 수정 UI 필요. 가끔 부분 결과 후 `ON_DEVICE_ENGINE_ERROR` |
- **기어 감지**: Virtual Console 기어 변경은 AAOS `GEAR_SELECTION` 콜백으로 오지 않음 → Pleos `getGear().registerGearSelection` 사용(deprecated지만 동작)
- **Virtual Console은 브라우저 세션이 살아 있어야 동작**: 콘솔 페이지를 열어 두거나 `POST /browser/session?client=<id>`를 1초마다 보내야 함. 아니면 `{"error":"emulator_inactive"}`
- **area 하드코딩 금지**: Light·Door에 `ROW_1_LEFT`를 넣으면 "area ID not supported". `check...Capabilities`가 돌려주는 area를 써야 함
- **SDK에 없는 값은 AAOS `CarPropertyManager`로** (`useLibrary("android.car")`): `GEAR_SELECTION`, `PARKING_BRAKE_ON`, `EV_CHARGE_PORT_CONNECTED`는 바로 읽힘. `PERF_VEHICLE_SPEED`(`android.car.permission.CAR_SPEED`), `EV_CHARGE_STATE`·`EV_CHARGE_TIME_REMAINING`·`RANGE_REMAINING`(`CAR_ENERGY`)은 dangerous 퍼미션이라 앱에서 런타임 요청 필요
- 에뮬레이터에 문서화 안 된 `ai.pleos.playground.service.adas`(퍼미션 `ADAS_DRIVING_WARN`) 존재. 공개 여부 미확인

### Vehicle SDK 도메인 (19개 + deprecated Gear)
- 조회만: Brake, CarInfo, DrivingMode, Fuel, Odometer, Safety, Tire, TurnSignal
- 조회+제어: Display, Door, EvBattery, HVAC, Light, Seat, SideMirror, SteeringWheel, Toll, Window, Wiper
- 확인된 주요 메서드:
  - EvBattery: `getEvBatteryStateOfCharge`/`register...`(SOC), `getEvChargeSwitchState`, `setEvChargePercentLimit`, `setEvChargeSwitch`, `setEvChargePortOpen`. 충전 상태·남은 시간은 SDK에 없음 → AAOS `EV_CHARGE_STATE`/`EV_CHARGE_TIME_REMAINING`
  - Odometer: `getCurrentDistanceDriven`, `getTripCurrentDrivingTime`, `getTripCurrentDrivingDistance` 등
  - Seat: `moveSeatBackrestAngle`, `moveSeatForeAft`, `setHvacSeatTemperature/Ventilation`, `getCurrentSeatOccupancy` (마사지 없음)
  - Light: `setAmbientLightBrightnessColor`, `setCabinLightSwitch`
  - HVAC: `setHvacTemperature`, `setHvacFanSpeed`, `setHvacPower`, `getHvacTemperatureCurrent`
  - Safety: 오일·워셔액·안전벨트·텔테일 경고만. **졸음/차선이탈/충돌 경고 없음**
- 모든 기능은 `check...Capabilities`로 지원 여부 확인 후 사용 (차량마다 다름)
- 퍼미션: 조회 `pleos.car.permission.CAR_*`, 제어 `CONTROL_CAR_*`. 실제 출시 시 App Market 사전 승인 필요
- NaviHelper: 목적지 설정/취소, 경유지 추가/제거, 경로 재탐색, 내비 정보 조회, 이벤트 수신, 충전 정보 조회

## 개발 환경 (이 맥에 세팅 완료, 2026-10-30)
- Android Studio 2026.2 (brew cask), SDK `~/Library/Android/sdk` (platform-tools, emulator, platforms;android-34, build-tools;35.0.0)
- Pleos 이미지: `~/.android/repositories.cfg`에 `crp-arm64-sys-img.xml` 등록 → `system-images;android-34;pleos_car_emulator;arm64-v8a`
  - Win/Linux 팀원은 `crp-sys-img.xml` 사용
- AVD `Pleos_Connect_v2.1.0`: RAM 4GB, cold boot 강제 (quick boot이면 VHAL 사용 불가)
- **JDK 17 필수**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Android Studio 내장 JBR은 Java 25라 Gradle 8.10 빌드 실패
- `~/.zshrc`에 설정돼 있지만 Bash 도구 환경엔 안 잡힐 수 있음 → `JAVA_HOME`·`ANDROID_HOME=~/Library/Android/sdk`를 명시(`mockup/local.properties` 없음)

### 자주 쓰는 명령
```sh
SDK=~/Library/Android/sdk
$SDK/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load   # 에뮬레이터 부팅
$SDK/platform-tools/adb forward tcp:8088 tcp:8088                      # Virtual Console (부팅마다 다시)
./gradlew installDebug                                                 # 각 프로젝트 루트에서 (JAVA_HOME은 JDK 17)
$SDK/platform-tools/adb logcat -s SMOKE                                # smoketest 결과
$SDK/platform-tools/adb shell pm grant --user 10 <pkg> android.car.permission.CAR_SPEED  # AAOS는 사용자 10
```
- Virtual Console HTTP API (`http://127.0.0.1:8088`): `POST /vehicle/gear?gear=Park|Reverse|Neutral|Drive`, `GET /vehicle/state`, `/vehicle/hvac`, `/vehicle/body`, `/vehicle/controls`, `/vehicle/catalog`. 시연·테스트 자동화에 활용
- `adb shell cmd car_service inject-vhal-event`는 user build라 불가 → Virtual Console 사용

### 저장소 구성
- `sample/pleos-sample-vehicle-app/`: 공식 샘플 clone (442개 public 메서드 탐색기). `gradle/libs.versions.toml`의 SDK 버전을 2.3.1→2.3.2로 로컬 수정. 외부 저장소이므로 커밋 대상 아님
- `smoketest/`: 조회·제어 API를 순서대로 호출하고 `SMOKE` 태그로 로그를 남기는 검증 앱. suite: 기본(제어), `--es suite logbook`, `--es suite speech`
- `mockup/`: 앱 "Route94"(옛 이름 도착노트, `ai.pleos.challenge.arrival`, Compose). **기록은 자동(무음), 정리는 부를 때**
  - 기어 P 이탈/복귀마다 구간(`Segment`: 시각·누적 거리·내비 주소)을 조용히 저장. P 복귀 시 하단 배너 "운행일지 정리할까요?"만 표시
  - 하단 말하기 바 → 명령 인식(`Commands`): "운행일지 정리해줘" → 미정리 구간을 한 운행으로 합침(0.3km 미만이면 "정리할 운행이 없어요") → 운행 요약 TTS + "운행을 기록합니다"(일반 업무로 바로 저장, 구분은 정리 화면 버튼 `changePurpose`로 변경) → "어디에 주차하셨나요?"("아니"면 운행만 저장) → 저장 확인 → 운행일지 탭 + 문자 QR. 운행 구분·주차 여부를 따로 묻던 단계(`ASK_PURPOSE`, `ASK_PARK`)는 2026-11-10에 없앰. "주차 어디 했지?" → 마지막 주차 위치 TTS 답변
  - 화면 구성(2026-11-10 디자인 정리): 색·글자 크기·공통 부품은 `Ui.kt` 한 곳(`Ui` 토큰, `Panel`, `PrimaryButton`/`QuietButton`, `Segmented`, `SharePanel`, `QrPlate`, `MicButton`). 에뮬레이터 화면이 160dpi·앱 영역 약 1680×1210dp라 본문 24sp 기준. 이모지·글리프 아이콘(🎤 ★ ⚙ ◀▶ ⌫)은 그린 마이크 아이콘과 Material 기본 아이콘으로 교체, 제목 밑 홍보 문구·카드별 한 줄 설명 제거. **세 탭 모두 같은 뼈대**: 왼쪽 = 차에 남은 기록, 오른쪽 = `휴대폰으로 열기`. **탭 이름(2026-11-10, PR #21, 브랜드 스토리 `docs/brand/`)**: `My Route`(옛 오늘, 메인) · `My Story`(옛 운행일지) · `My Discovery`(옛 발자취); 오늘 탭 줄 제목도 My Story / My Stop / My Discovery. 코드의 `Screen.HOME/RECORDS/JOURNEY`와 아래 설명의 "오늘/운행일지/발자취"는 화면 역할 이름으로 그대로 씀. 버튼 문구(`기록 보기`, `발자취 보기`)는 한글 유지(`SharePanel`, 폭 440dp 고정, QR은 대화상자 없이 그 자리에 표시). `오늘`: 운행일지·주차 위치·여행 세 줄 요약(`SummaryRow`) + 마지막 주차 위치 문자 QR. `운행일지`: **한 달 단위** 기록 표(제목 줄의 ◀ YYYY년 M월 ▶로 넘김, 이번 달은 기록이 없어도 표시) + `운행 문자`/`월간 운행기록부`(표와 같은 달). `발자취`(옛 `여행`, `JourneyScreen`): **오늘 발자취만**(`JourneyDays.today`) 들른 곳 목록·상세 | 경로 | `여행 기록`/`엑셀`/`이 장소 지도`(`JourneyShare.kt`, 예전 `JourneyExportDialog`·`StopMapDialog` 대화상자를 대체). 같은 탭 왼쪽 위의 `오늘 발자취 | 발자국` 전환(`Segmented`)으로 `PlacesView`(`PlacesScreen.kt`, 2026-11-10)를 보여 줌: 모든 날의 별표한 곳을 최신순으로 나열 | 상세(메모 고치기, 발자국 지우기). 별표 라벨은 `발자국 남기기`/`발자국` | `이 장소 지도` QR. 별도 탭이 아님(같은 성격의 기능이라 한 탭에 둠). 지난 날의 발자취는 `JourneyDays.keepStarred`가 경로와 별표 없는 방문을 지워 별표한 곳만 남김(로드·기어 이벤트·별 해제 때 적용) → 동의 문구에 반영, `Consent.VERSION` 8. 음성 "휴대폰으로 보여 줘"/"엑셀로 내보내 줘"는 여행 탭의 해당 항목을 고름. 말하기 바(`VoiceBar`)는 정리 흐름을 뺀 모든 탭 아래에 있고 탭에 맞는 예시 문장을 보여 줌(여행 탭의 자체 말하기 버튼은 제거). ⚙은 설정 화면(`SettingsScreen`, 2026-11-10 재구성): 왼쪽 `기본 정보`(휴대폰 번호 + `LogbookProfile` 6칸), 오른쪽 `지도 앱`(`StopMapApp` 하나만 저장, `MapApp`은 같은 이름으로 파생 → 기록 페이지의 첫 버튼과 발자취 장소 QR이 같은 지도) · 개인정보 · 앱 정보. 예전 "문자 지도 링크" 설정(`TripStore.mapApp`)은 기록 페이지가 지도 3개를 다 보여 주므로 제거. 발자취 탭의 장소 QR은 여전히 `mapPick`으로 그 QR에 한해 바꿀 수 있음(저장 안 함). ⚙을 다시 누르면 열기 전 화면으로 돌아감(`beforeSettings`). 설정 화면에서는 말하기 바를 숨김. 철회 문구가 바뀌어 `Consent.VERSION` 7(여행 보관 문구로 8, 운행기록부 기본 정보 항목 추가로 9). 설정 오른쪽 패널 `운행기록부 기본 정보`(`LogbookProfile`: company·bizNo·model·plate·dept·name, `TripStore.profile`, 두 칸씩 세 줄, 빈칸 허용, 입력은 draft에 두고 `저장` 버튼으로만 반영 → "저장했어요." 2.5초 표시·키보드 내림, 미저장이면 "저장을 눌러야 반영돼요.") → `LogbookLink.payload`의 `"p":{…}`로 월간 링크에 실려 `viewer/index.html`의 기본 정보 칸을 채움(링크 값이 있으면 휴대폰 localStorage를 덮어씀)
  - 주차만 기록(2026-11-09): "주차 위치 기록해줘"(`Commands.Intent.PARK_ONLY`, `parseIntent`에서 SAVE_PLACE·WHERE_PARKED·LOGBOOK보다 먼저 검사) 또는 오늘 탭 주차 위치 줄의 "기록하기" → 주차 위치 질문 → 저장 확인. 운행 구분 질문 없음, 미정리 구간은 그대로 둠. `ParkingNote`(시각·위치·주소·좌표)로 `TripStore`의 `parking` 키에 따로 저장(최대 20개) → 운행기록부 표·월간 비율에 섞이지 않음. "주차 어디 했지?"와 주차 카드는 `ParkingNote.latest(trips, notes)`로 둘 중 최신을 사용. 문자 QR은 오늘 탭 오른쪽 `휴대폰으로 열기`에 항상 표시
  - 백그라운드 기록(2026-11-09): 주행 중에는 내비가 같은 앱 영역을 차지해 앱이 가려짐. 가려진 앱은 얼어서(추정: cached app freezer) 기어·거리 콜백이 다시 열 때까지 안 옴(에뮬레이터에서 18초 지연 확인) → `RecordingService`(foreground service, `specialUse` 타입, "운행을 기록하고 있어요" 알림)로 프로세스를 유지. 기록 로직은 여전히 `MainActivity`가 가짐(액티비티가 파괴되면 기록 중단). 내비가 앞에 있을 때 P 복귀하면 `Notices.showArrival` 알림(정리하기·주차만 기록·말하기 버튼 → `onNewIntent`에서 해당 흐름 시작). **Pleos 에뮬레이터는 헤드업 팝업을 띄우지 않고 알림 패널(상단 바를 아래로 쓸기)에만 표시**. 서비스에서 `startActivity`로 스스로 앞에 나오는 것은 `BAL_BLOCK`으로 차단됨(확인). `POST_NOTIFICATIONS`는 런타임 요청
  - 도착 음성 질문(2026-11-09): 화면이 가려진 상태(`visible == false`)에서 P 복귀 + 미정리 거리가 1km 이상(`ASK_MIN_KM`)이고 지난 질문 뒤로 1km 이상 더 달렸을 때만(`askedAtKm`) TTS로 두 단계 질문. ① "도착하셨나요?"(`ASK_PARKED`, `Commands.parseParked`) → "아니/잠깐"이면 "알겠어요. 이어서 기록할게요."로 끝(구간은 이어져 나중에 한 운행으로 합쳐짐), "응/도착했어"면 ② "운행을 기록할까요?"(`ASK_ARRIVAL`) → "응"이면 일반 업무로, "출퇴근이야"·"개인 용도야"면 그 구분으로 저장하고 "N킬로미터 운행을 기록합니다. 어디에 주차하셨나요?"로 바로 이어짐. "아니"면 운행은 남겨 두고 주차 위치만 묻는 흐름(`startParkOnly`)으로 넘어감. 무응답이면 조용히 종료. 앱이 보이는 상태에서는 기존처럼 배너만(무음). 에뮬레이터에서 내비를 앞에 둔 채 `--es demo_script yes.pcm,yes.pcm,floor.pcm,pillar.pcm,yes.pcm`(기록 + 주차)와 `yes.pcm,no.pcm,park_tower.pcm,yes.pcm`(주차만) 확인. 1km 기준은 0.5km(질문 없음) → 2km → 0.5km(질문 없음) → 2km로 확인. **실제 마이크가 백그라운드에서 열리는지는 미확인**(데모는 `sendAudio`로 마이크를 우회). 동의 화면 음성 항목에 "주차했을 때 앱이 먼저 묻는 질문에 답하는 동안 마이크를 연다"고 명시
  - 주차 질문(2026-11-09): "몇 층이에요?" 대신 "어디에 주차하셨나요?" 하나로 물음(`Step.ASK_FLOOR/HEAR_FLOOR` 이름은 그대로). 대답 해석 순서: 주차타워·기계식(`ParkingText.towerLabel`, "3번 타워" → "주차타워 3번") → 층(+기둥) → 층 뒤에 기둥 코드가 아닌 설명이 오면 말한 그대로 붙임(`placeAfterFloor`, "지하 1층 B동 앞에" → "지하 1층 B동 앞", STT가 "비동"으로 쓰면 "B동"으로 되돌림) → 층만이면 기둥 질문("없어"면 층만 저장) → 지상·야외·길가(`isOutdoor`)면 "근처에 보이는 걸 한마디로"(`ASK_SPOT_NOTE`) → 그 밖의 말은 들은 그대로 → 모두 "…, 저장할까요?" 확인. 층·기둥이 아닌 위치는 `setFreeSpot`으로 직접 입력 칸(`manualSpot`)에 넣음. 화면 선택기(2026-11-10 재설계): 맨 위 `주차장 | 다른 곳` 전환(`SpotKind`). 주차장 = 주차장 이름(선택, `lotName`) → 층 → 기둥 순으로 `parkingText()`가 "본관 지하 5층 A4"처럼 합침. 다른 곳 = `주차타워`·`지상 주차`·`길가` 버튼 + "어디인지 한마디로" 입력칸(`manualSpot`, 항상 표시). 음성으로 층·기둥이 들리면 주차장, 그 밖의 말은 다른 곳으로 자동 전환. 예전 `직접 입력` 토글(`showManual`)은 제거. 입력칸에 포커스가 있으면 키 격자를 숨김. 시연 녹음 `park_tower.pcm`·`park_outdoor.pcm`·`park_note.pcm`·`no.pcm`은 macOS `say -v Yuna` + `afconvert`(16kHz mono PCM)로 생성
  - TTS 발음: `say()`가 `ParkingText.spoken()`을 거쳐 기둥 코드를 한글로 읽음("A4로" → "에이 4로", "AL02" → "에이엘 공이"). TTS가 "A4로"를 영어 단어처럼 "어포로"로 읽던 문제 대응. 화면 표시는 원문 그대로
  - 흐름 중 이탈(2026-11-10): 정리·주차 기록 화면에서 탭이나 ⚙을 누르면 `leaveFlow()`가 흐름을 끝냄(듣기 중단, `draft`·`parkOnly` 초기화, 말하기 바에 "주차 위치 기록을 취소했어요" 또는 "운행은 저장했어요…" 표시). 운행은 이미 저장돼 있으므로 주차 위치만 빠짐. 전에는 흐름이 보이지 않는 채 남아 말하기·기록하기 버튼이 잠겼음
  - 말하기 취소(2026-11-10): 말하기 바의 `MicButton`이 듣는 중(`HEAR_COMMAND`)엔 같은 자리에서 ■ `취소`로 바뀜(`listening` 파라미터, `StopIcon`; `cancelListening`: 토큰 증가·STT 중단·IDLE). 발자취 음성 메모도 듣는 중엔 `음성 메모` 자리에 `취소`(메모 대상 해제, "메모를 취소했어요."). 에뮬레이터 마이크는 무음이 아니라 **맥 마이크를 그대로 들음**(2026-11-10 확인, 옆 사람 말이 메모로 들어감)
  - 직접 입력(2026-11-10 수정): 에뮬레이터 키보드 창의 터치 영역이 보이는 자판보다 위로 약 80px 넓어, 자판 바로 위에 놓인 `저장` 줄이 눌리지 않았음(`dumpsys input`의 InputMethod touchableRegion으로 확인). `windowSoftInputMode=adjustResize` + 입력칸에 포커스가 있는 동안 키 격자(층·기둥 등)를 숨겨 입력칸·버튼이 자판 위에 남게 함. `AssistantPanel`의 "듣고 있어요" 줄은 항상 한 줄 차지(사라질 때 버튼이 튀어 탭이 빗나가던 문제)
  - 기둥 코드: 여러 글자·앞자리 0·하이픈 지원(`에이엘 공이`→AL02, `비 십오`→B15, `씨 다시 삼`→C-3). UI는 A~Z·0~9·`-`·⌫·전체 지우기를 눌러 이어 붙이는 방식 + "직접 입력"(자유 텍스트가 있으면 그대로 저장)
  - 파일: `CarSignals`(기어·누적 거리·내비 주소), `Logbook`(`Segment`, 구간 합치기, 저장), `Commands`(의도·예/아니오·운행 구분 파서), `Voice`(TTS·STT, 데모 스크립트), `ParkingText`(층/기둥 파서), `Trip`(모델·`smsBody`·`smsQrText`·저장), `Qr`, `MainActivity`(홈/정리/운행기록부)
  - 휴대폰 전달(2026-11-10 변경): 운행 한 건·마지막 주차 위치 QR은 `RecordLink`가 만든 `BASE_URL/record.html#<deflate+base64url JSON>`(`viewer/record.html`·`record.mjs`, 테스트 `record.test.mjs`). 페이지에 지도 앱 3개 버튼(설정의 지도 앱이 "기본"), `문자로 보내기`(`sms:<번호>?body=`, iOS는 `&body=`; 번호는 링크의 `ph`), `내용 복사`. 문자 본문은 `smsBody()`와 같음. `SmsShare` 컴포저블과 SMS QR은 화면에서 제거(`smsQrText`는 로그·테스트에만 남음). 이전 방식: QR = `SMSTO:<내 번호>:<본문>`. **아이폰 실기기에서 받는 사람·본문이 채워진 문자 작성 화면이 열리는 것 확인(2026-11-01)**. 번호 없는 `sms:` 링크는 아이폰에서 "연락처에 추가"만 떠서 실패. 내 번호는 운행기록부 화면에서 입력(SharedPreferences)
  - 주차 지도 링크(2026-11-03): `CarSignals`가 FusedLocation `registerFusedLocationCallback`으로 마지막 좌표(`lastFix`)를 유지 → P 복귀 시 `Segment.arriveLat/Lng` → `Trip.endLat/Lng` → `smsBody(map)` 끝에 구글(`google.com/maps/search/?api=1&query=`) 또는 카카오(`map.kakao.com/link/map/주차,lat,lng`) 링크(선택은 `TripStore.mapApp`). `pleos.car.permission.FUSED_LOCATION`은 dangerous라 동의 후 런타임 요청(시스템 대화상자 문구가 "알 수 없는 작업"으로 뜸), 허용 시 `startLocation()` 재호출. 에뮬레이터 GPS 기본값은 구글 본사 → `adb emu geo fix 127.1465 37.4502`. 링크가 붙으면 SMS가 LMS가 됨. 네이버 지도(2026-11-09 추가)는 `https://m.map.naver.com/map.naver?lat=…&lng=…&pinTitle=주차&pinType=site&dlevel=11`(모바일 웹에서 해당 좌표에 핀이 찍히는 것 확인, 공식 문서화된 형식은 아님). 문자 속 링크는 https여야 눌려서 여행 QR의 `nmap://` 스킴은 쓰지 않음. 선택 순서: 카카오맵·네이버 지도·구글 지도
  - 월간 운행기록부(2026-11-02): 운행기록부 화면 "월간 운행기록부" 탭 → 월별 합계·업무사용비율 + 링크 QR. `LogbookLink`가 `{"v":1,"m":"YYYY-MM","t":[[시작분,출발km×10,도착km×10,"C|B|P",도착지]]}`를 raw deflate + base64url로 `BASE_URL#…`에 담음. `viewer/index.html`(정적, `DecompressionStream('deflate-raw')`)이 휴대폰에서 국세청 양식 표·CSV·인쇄 제공, 상호·차량번호·성명 등은 휴대폰 localStorage에만. `BASE_URL` = `https://pleos-note.vercel.app/` (Vercel Hobby 프로젝트 `pleos-note`, 이 저장소 `viewer/`를 Root Directory로 연결 → main push마다 자동 배포). 공개 전 테스트는 `--es viewer_url http://<맥 IP>:8765/` + `python3 -m http.server 8765`(viewer/에서)
  - 발자취 QR(2026-11-10): 링크 한도 `JourneyExport.MAX_LINK_BYTES` 1800 → **800**(QR 버전 20 이하, 97모듈). 1800이면 버전 33+라 320dp에서 카메라가 못 읽음. 기록 QR 판은 380dp. 방문 하나의 메모만으로 넘치면 링크 속 메모만 줄임(`NOTE_CUTS`, CSV 파일은 전문). 좌표는 소수 5자리로 반올림
  - 카카오 장소 검색 키(2026-11-10): `~/.gradle/gradle.properties`의 `kakaoRestApiKey`(저장소 밖). **키를 넣거나 바꾼 뒤 첫 빌드는 `--rerun-tasks`(또는 `clean`) 필요** — `BuildConfig.KAKAO_REST_API_KEY`는 `static final`이라 Kotlin이 사용처(`JourneyScreen`)에 상수를 인라인하는데, 증분 컴파일이 BuildConfig 변경을 추적하지 못해 빈 문자열이 남아 "장소 검색이 아직 연결되지 않았어요"가 뜸. 확인: `strings app/build/outputs/apk/debug/app-debug.apk`의 dex에 키가 있는지
  - 테스트: `sh ./gradlew testDebugUnitTest` (ParkingText·Commands·Logbook·SMS 본문)
  - 개인정보 동의(`Consent.kt`): 첫 실행 시 수집 항목(음성·차량 정보·위치·휴대폰 번호)과 용도·보관(차량 안에만, 서버 전송 없음)·철회 방법을 보여 주고 "동의하고 시작" 버튼으로만 동의. 동의 전에는 차량·마이크·내비를 읽지 않음. ⚙ 설정의 "동의 철회 및 기록 삭제" → 수집 중단 + 기록·번호 전부 삭제(`TripStore.clearAll`). 문구 바뀌면 `Consent.VERSION` 올리기
  - 안정성 처리(2026-11-01): 주차 위치는 "…, 저장할까요?" 음성 확인 후 저장. TTS가 끝난 뒤(onDone+0.4초)에만 마이크를 열고, 앱 질문이 되들린 STT 결과(에코)는 한 번 무시하고 다시 들음. 주행 중(P 아님)에는 운행일지 정리·텍스트 입력 불가(진행 중이면 중단), "주차 어디"는 가능. 앱 재시작 시 열린 구간을 이어서 닫고, 누적 거리 없이(0) 기록된 출발값은 첫 odometer 값으로 채움 + merge에서 0·감소 구간 제외
  - 시연: `am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --ez demo_audio true` → STT 요청마다 `assets/`의 녹음을 순서대로 사용(cmd_logbook → floor → pillar → yes(저장 확인) → cmd_where → cmd_logbook). 데모 전에 동의 화면에서 "동의하고 시작"을 눌러야 함. 다른 순서는 `--es demo_script a.pcm,b.pcm`. Virtual Console 하트비트 유지 + odometer 증가 + gear 조작

## 팀 회의 결과 (2026-10-30)
회의에서 아래 두 아이디어로 좁힘. **둘을 하나의 앱으로 합치기로 결정** (둘 다 "도착(주차) 순간"이 트리거). 전기차 전용 공모전 아님(일반 차량, 사이트 문구에 "주차"가 이용 상황으로 명시)
1. **운행일지 음성 안내**: 도착하면 "OOkm에서 OOkm까지, 총 OOkm 주행"을 음성으로 알려줌 → 앱 화면/휴대폰으로 전달 → 국세청 운행기록부 양식으로 정리
2. **주차 위치 음성 메모**: 도착 후 "지하 5층 A4" 같이 말하면 저장 → 나중에 휴대폰으로 확인. 블랙박스 이미지 캡처 첨부 아이디어도 나옴
- 쓰는 SDK: 도착 감지(NaviHelper `onDestinationArrived` 또는 기어 P), Odometer, TTS, STT, FusedLocation → 공모전 지정 SDK 5종을 모두 활용 가능
- 미해결 리스크:
  - (해결) Odometer·TTS·STT 모두 에뮬레이터에서 동작
  - STT 숫자·영문 오인식 → 후처리 + 수정 UI로 대응 중
  - 휴대폰 전달은 차량 앱 단독으로 불가 → 자체 서버/푸시, 또는 화면 QR 등 방식 결정 필요. 기아·삼성 앱 연동은 제3자 앱이라 사실상 불가
  - 블랙박스(카메라) 접근 API는 공개 SDK에 없음
  - 사이트 예시 "음성 메모"와 겹칠 수 있음 → 주차 위치라는 구체적 Pain Point로 차별화 필요

## 아이디어 후보 (회의 전 초안)
- 제어 SDK가 에뮬레이터에서 동작하므로 후보 1·2·4의 최대 리스크는 해소됨
- 시트·도어 제어가 주행 중(D) 잠기는 점은 "정차 맥락" 설계 근거로 PT에 활용 가능
- 후보 4의 연속 주행 시간: `getTripCurrentDrivingTime` 미지원 → 거리 getter나 앱 자체 타이머(기어·속도 기반)로 대체 필요
- 후보 2의 시동 OFF 상태 앱 동작은 여전히 미확인
1. **충전 라운지 모드**: 충전 감지 → 시트 리클라인·무드등·공조로 휴식 공간화, SOC 기반 완료 시간 예측 → 완료 시 깨우기. EvBattery+Seat+Light+HVAC. *제어 SDK 필요*
2. **펫·키즈 대기 케어**: 정차 중 실내 온도 유지·문/창문 감시·안내 화면. HVAC+Door+Window+Seat Occupancy. *제어 SDK 필요, 시동 OFF 동작 불확실, 휴대폰 알림은 서버 필요*
3. **업무용 운행일지 자동화(B2B)**: Odometer 거리 + NaviHelper 목적지 + 목적 입력 → 국세청 운행기록부 양식 출력. **현재 공개 SDK만으로 가능**. 약점: 차량 제어가 없어 시연이 밋밋함
4. **연속 운전 → 쉼터 경유 → 파워냅**: 트립 주행 시간 기반 휴식 권유, NaviHelper로 쉼터 경유지 추가, 도착 후 시트·조명·공조 낮잠 모드. *제어 SDK 필요* (졸음 경고 API는 없음)
- Vehicle Control을 쓸 수 없으면: **조회 데이터 + NaviHelper 중심으로 차량특화성을 살리는 방향**으로 후보 재구성
