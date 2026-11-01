# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Pleos 차량용 앱 개발 공모전 프로젝트

현대자동차그룹 "차량용 앱 개발 공모전 — Code your App into Pleos App Market" 출품 프로젝트.
이 파일은 이전 세션(2026-10-23)에서 조사한 내용을 이어가기 위한 핸드오프 문서다.

> **현재 상태 (2026-11-01)**: 아이디어 확정(운행일지 + 주차 위치 음성 메모, 아래 "팀 회의 결과"). 기술 검증 완료, `mockup/`에 동작하는 목업 앱("도착노트") 있음. 첫 커밋 완료(`sample/`·빌드 산출물은 `.gitignore`로 제외). 다음: 실사용 테스트 → 제출 준비(앱 이름·팀명, 기획서, 시연 영상, README·릴리스 APK).

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
- `~/.zshrc`에 ANDROID_HOME/PATH 미설정 (사용자가 직접 추가 예정) → 명령은 전체 경로로 실행

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
- `mockup/`: 목업 앱 "도착노트" (`ai.pleos.challenge.arrival`, Compose). **기록은 자동(무음), 정리는 부를 때**
  - 기어 P 이탈/복귀마다 구간(`Segment`: 시각·누적 거리·내비 주소)을 조용히 저장. P 복귀 시 하단 배너 "운행일지 정리할까요?"만 표시
  - 홈의 🎤 말하기 → 명령 인식(`Commands`): "운행일지 정리해줘" → 미정리 구간을 한 운행으로 합침(0.3km 미만이면 "정리할 운행이 없어요") → 운행 요약 TTS + "업무/출퇴근?" → 저장 → "주차 위치도 기록할까요?" → 층 → 기둥 → 버튼 보정/직접 입력 → 운행기록부 + 문자 QR. "주차 어디 했지?" → 마지막 주차 위치 TTS 답변
  - 기둥 코드: 여러 글자·앞자리 0·하이픈 지원(`에이엘 공이`→AL02, `비 십오`→B15, `씨 다시 삼`→C-3). UI는 A~Z·0~9·`-`·⌫·전체 지우기를 눌러 이어 붙이는 방식 + "직접 입력"(자유 텍스트가 있으면 그대로 저장)
  - 파일: `CarSignals`(기어·누적 거리·내비 주소), `Logbook`(`Segment`, 구간 합치기, 저장), `Commands`(의도·예/아니오·운행 구분 파서), `Voice`(TTS·STT, 데모 스크립트), `ParkingText`(층/기둥 파서), `Trip`(모델·`smsBody`·`smsQrText`·저장), `Qr`, `MainActivity`(홈/정리/운행기록부)
  - 휴대폰 전달: QR = `SMSTO:<내 번호>:<본문>`. **아이폰 실기기에서 받는 사람·본문이 채워진 문자 작성 화면이 열리는 것 확인(2026-11-01)**. 번호 없는 `sms:` 링크는 아이폰에서 "연락처에 추가"만 떠서 실패. 내 번호는 운행기록부 화면에서 입력(SharedPreferences)
  - 테스트: `sh ./gradlew testDebugUnitTest` (ParkingText·Commands·Logbook·SMS 본문)
  - 개인정보 동의(`Consent.kt`): 첫 실행 시 수집 항목(음성·차량 정보·위치·휴대폰 번호)과 용도·보관(차량 안에만, 서버 전송 없음)·철회 방법을 보여 주고 "동의하고 시작" 버튼으로만 동의. 동의 전에는 차량·마이크·내비를 읽지 않음. 상단 "동의 철회" → 수집 중단 + 기록·번호 전부 삭제(`TripStore.clearAll`). 문구 바뀌면 `Consent.VERSION` 올리기
  - 안정성 처리(2026-11-01): 주차 위치는 "…, 저장할까요?" 음성 확인 후 저장. TTS가 끝난 뒤(onDone+0.4초)에만 마이크를 열고, 앱 질문이 되들린 STT 결과(에코)는 한 번 무시하고 다시 들음. 주행 중(P 아님)에는 운행일지 정리·텍스트 입력 불가(진행 중이면 중단), "주차 어디"는 가능. 앱 재시작 시 열린 구간을 이어서 닫고, 누적 거리 없이(0) 기록된 출발값은 첫 odometer 값으로 채움 + merge에서 0·감소 구간 제외
  - 시연: `am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --ez demo_audio true` → STT 요청마다 `assets/`의 녹음을 순서대로 사용(cmd_logbook → purpose → yes → floor → pillar → yes(저장 확인) → cmd_where → cmd_logbook). 데모 전에 동의 화면에서 "동의하고 시작"을 눌러야 함. 다른 순서는 `--es demo_script a.pcm,b.pcm`. Virtual Console 하트비트 유지 + odometer 증가 + gear 조작

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
