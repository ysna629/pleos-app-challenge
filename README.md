# 도착노트 (가칭)

현대자동차그룹 **차량용 앱 개발 공모전 — Code your App into Pleos App Market** 출품작입니다.
Pleos Connect(Android Automotive OS)에서 동작하는 차량용 앱으로, **운행일지**와 **주차 위치 기록**을 음성으로 처리합니다.

## 어떤 문제를 푸나요

- **업무용 차량 운행일지:** 국세청 업무용승용차 운행기록부는 주행 전·후 계기판 거리와 업무 구분을 매번 적어야 해서 번거롭습니다.
- **주차 위치:** 지하 주차장은 GPS가 잡히지 않아 기둥 번호를 사진으로 찍어 두곤 합니다.

도착노트는 차량이 직접 주행 기록을 남기고, 운전자는 말로 정리만 하면 됩니다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| 자동 운행 기록 | 기어가 P를 벗어나고 다시 P로 돌아올 때마다 시각, 계기판 누적 거리, 내비게이션 주소를 조용히 기록합니다. |
| 음성으로 운행일지 정리 | "운행일지 정리해 줘"라고 말하면 마지막 정리 이후의 구간을 하나의 운행으로 합쳐 안내하고, 업무·출퇴근 구분을 물어 저장합니다. 잠깐 들른 곳이나 주차장 안에서 자리를 옮긴 것은 따로 남지 않습니다. |
| 음성으로 주차 위치 기록 | "몇 층이에요?", "기둥 번호를 말씀해 주세요"로 나눠 묻고, `지하 5층 A4`, `AL02`, `B-15` 같은 형태로 정리합니다. 틀리면 버튼이나 직접 입력으로 고칠 수 있습니다. |
| 주차 위치 묻기 | "주차 어디 했지?"라고 물으면 마지막 주차 위치를 음성으로 알려 줍니다. |
| 휴대폰으로 받기 | 운행기록부 화면의 QR 코드를 휴대폰 카메라로 찍으면 기록이 채워진 문자 작성 화면이 열립니다. 보내면 내 문자함에 남습니다. 서버가 필요 없습니다. |
| 월간 운행기록부 | 운행기록부 화면의 월간 QR을 찍으면 휴대폰 브라우저에 국세청 업무용승용차 운행기록부 양식(총주행거리, 업무용 사용거리, 업무사용비율)이 열리고 엑셀(CSV)로 저장하거나 인쇄할 수 있습니다. 기록은 링크의 `#` 뒤에 압축해 담기 때문에 서버로 전송되지 않습니다. |
| 정차 중심 설계 | 운행일지 정리와 글자 입력은 정차(P) 중에만 할 수 있습니다. |
| 개인정보 동의 | 첫 실행 시 수집 항목과 용도를 안내하고 동의를 받습니다. 동의를 철회하면 저장된 기록을 모두 지웁니다. 모든 기록은 차량 안에만 저장됩니다. |

## 사용한 Pleos SDK

| SDK | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 기어 상태, 계기판 누적 거리 |
| NaviHelper | 3.0.5 | 현재 위치 주소 (출발지·도착지) |
| TextToSpeech | 3.3.1 | 운행 요약과 질문 음성 안내 (온디바이스) |
| SpeechToText | 3.3.1 | 음성 명령과 주차 위치 인식 (온디바이스) |

## 저장소 구성

```
mockup/      도착노트 앱 (Kotlin, Jetpack Compose)
viewer/      월간 운행기록부 웹 페이지 (정적 HTML, 휴대폰에서 열림)
smoketest/   Pleos SDK 동작 검증용 앱 (호출 결과를 logcat SMOKE 태그로 출력)
```

`mockup/app/src/main/java/ai/pleos/challenge/arrival/`

| 파일 | 역할 |
|---|---|
| `MainActivity.kt` | 화면(홈, 운행일지 정리, 운행기록부)과 음성 대화 흐름 |
| `CarSignals.kt` | 기어·누적 거리·내비 주소 수신 |
| `Logbook.kt` | 운행 구간 저장과 합치기 |
| `Voice.kt` | TTS·STT |
| `Commands.kt` | 음성 명령, 예/아니오, 운행 구분 해석 |
| `ParkingText.kt` | 층·기둥 번호 해석 (한글 발음 → 알파벳·숫자) |
| `Trip.kt`, `Qr.kt` | 운행 기록 모델, 문자 QR |
| `LogbookLink.kt` | 월간 운행기록부 링크 (월별 합계, 압축 데이터) |
| `Consent.kt` | 개인정보 동의 |

## 실행 방법

### 준비

- Android Studio, Android SDK Platform 34
- **JDK 17** (`JAVA_HOME`을 JDK 17로 지정)
- Pleos Connect 에뮬레이터: [설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 따라 AVD를 만들고 **Cold Boot**로 실행합니다. Quick Boot로 켜면 차량 데이터를 쓸 수 없습니다.

### 빌드와 설치

```sh
cd mockup
./gradlew installDebug
```

앱 이름은 에뮬레이터 앱 목록에서 **도착노트**로 보입니다. 첫 화면에서 개인정보 수집·이용에 동의하면 시작합니다.

### 주행 흉내 내기 (Virtual Console)

에뮬레이터의 Virtual Console로 기어와 계기판 거리를 바꿀 수 있습니다.

```sh
adb forward tcp:8088 tcp:8088
```

1. 브라우저에서 `http://127.0.0.1:8088`을 열어 둡니다. 페이지가 열려 있어야 조작이 반영됩니다.
2. 기어를 **D**로 바꾸고, 계기판 누적 거리(Odometer)를 지금보다 큰 값으로 올린 뒤, 기어를 **P**로 바꿉니다.
3. 앱 하단에 "운행일지 정리할까요?"가 뜨면 🎤 버튼을 누르고 "운행일지 정리해 줘"라고 말합니다.

### 음성 데모 모드

에뮬레이터 마이크를 쓰기 어려울 때는 미리 녹음한 음성을 순서대로 넣는 데모 모드로 실행합니다.

```sh
adb shell am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --ez demo_audio true
```

### 테스트

```sh
cd mockup
./gradlew testDebugUnitTest
```

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect v2.1.0 (Android 14, API 34, arm64) |
| 개발 PC | macOS (Apple Silicon), Android Studio, JDK 17 |
| 빌드 | Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk 35, minSdk 28 |
| 휴대폰 | iPhone 기본 카메라로 문자 QR 확인 |
