# 개발 메모

README에 두기엔 자세한 내용을 모았습니다. 앱 소개와 기본 실행 방법은 [README](../README.md)에 있습니다.

## 파일 구성

`mockup/app/src/main/java/ai/pleos/challenge/arrival/`

| 파일 | 역할 |
|---|---|
| `MainActivity.kt` | 화면(오늘 요약, 운행일지 정리, 주차 위치 기록, 운행일지 탭)과 음성 대화 흐름, 도착 질문 |
| `RecordingService.kt` | 다른 앱이 떠 있어도 기록이 이어지게 하는 포그라운드 서비스와 도착 알림 |
| `CarSignals.kt` | 기어·누적 거리·내비 주소·GPS 수신 |
| `Logbook.kt` | 운행 구간 저장과 합치기 |
| `Voice.kt` | TTS·STT, 데모 녹음 재생 |
| `Commands.kt` | 음성 명령, 예/아니오, "도착하셨나요?" 대답, 운행 구분 해석 |
| `ParkingText.kt` | 주차 위치 해석(층·기둥, 주차타워, 지상, 층 뒤 설명)과 음성 안내용 한글 읽기 |
| `Trip.kt`, `Qr.kt` | 운행 기록·주차 기록 모델, 문자 QR, 지도 링크(카카오맵·네이버 지도·구글 지도) |
| `LogbookLink.kt` | 월간 운행기록부 링크(월별 합계, 압축 데이터) |
| `Consent.kt` | 개인정보 동의 |
| `JourneyScreen.kt`, `Journey*.kt` | 여행 탭 화면, 경유지 모델·저장·내보내기 |
| `StopMapLink.kt` | 방문 장소의 지도 QR 링크 |
| `KakaoPlaces.kt` | 카카오 로컬 API로 주변 장소 후보 검색 |
| `DrivingScreen.kt` | 주행 전용 화면과 차량 주행 제한 신호 |

## Virtual Console을 터미널에서 다루기

콘솔 페이지를 열어 두는 대신 세션을 직접 유지할 수 있습니다. 세션이 끊기면 `{"error":"emulator_inactive"}`가 돌아옵니다.

```sh
adb forward tcp:8088 tcp:8088
# 1초마다 세션 유지
while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=dev" >/dev/null; sleep 1; done &
curl -X POST "http://127.0.0.1:8088/vehicle/gear?gear=Drive"
curl -X POST "http://127.0.0.1:8088/vehicle/odometer?traveled_distance_km=12700&usage_time_min=20"
curl -X POST "http://127.0.0.1:8088/vehicle/gear?gear=Park"
```

누적 거리는 줄어드는 값을 무시하므로 항상 지금보다 큰 값을 넣습니다.

## 내비가 떠 있을 때의 도착 질문 시험하기

1. 앱을 켜고 동의한 뒤 기어를 D로 바꿉니다.
2. 아래 줄의 내비 아이콘을 누르거나 `adb shell am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity`로 내비를 띄웁니다.
3. 거리를 1km 이상 올리고 P로 바꿉니다. 화면은 내비 그대로이고 "도착하셨나요?"가 들립니다. 위쪽 바를 아래로 쓸면 알림이 보입니다.

질문은 미정리 거리가 1km 이상이고 지난 질문 뒤로 1km 이상 더 달렸을 때만 나옵니다. 앱이 스스로 화면 앞으로 나오는 것은 시스템이 막습니다(`BAL_BLOCK`).

## 음성 데모 녹음

`mockup/app/src/main/assets/`의 녹음을 `--es demo_script`에 쉼표로 나열하면 STT 요청마다 순서대로 들어갑니다.

| 파일 | 내용 |
|---|---|
| `cmd_logbook.pcm` | "운행일지 정리해 줘" |
| `cmd_where.pcm` | "주차 어디 했지?" |
| `yes.pcm`, `no.pcm` | "응", "아니요" |
| `purpose.pcm` | "업무 운행이에요" |
| `floor.pcm`, `pillar.pcm`, `pillar_al02.pcm` | "지하 5층", "에이 사", "에이엘 공이" |
| `park_tower.pcm`, `park_outdoor.pcm`, `park_note.pcm` | "3번 주차타워에 넣었어", "지상 주차장이야", "정문 옆 편의점 앞" |
| `journey_*.pcm` | 여행 기록 명령과 메모 |

예시:

```sh
# 내비를 띄운 채 주차했을 때: 운행 기록 후 주차 위치
adb shell am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --es demo_script yes.pcm,yes.pcm,floor.pcm,pillar.pcm,yes.pcm
# 주차 위치만
adb shell am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --es demo_script yes.pcm,no.pcm,park_tower.pcm,yes.pcm
```

새 녹음은 macOS에서 `say -v Yuna -o x.aiff "문장"` → `afconvert x.aiff -f WAVE -d LEI16@16000 -c 1 x.wav` → WAV 헤더를 뗀 16kHz mono PCM으로 만듭니다. 합성 음성은 STT가 잘못 듣는 경우가 있어("비동 앞에" → "비트코인 카페") 실제 목소리 녹음이 더 믿을 만합니다.

실제 Mac 마이크로 시험하려면 `adb emu avd hostmicon`으로 입력을 켠 뒤 `demo_script` 없이 실행합니다. 첫 단어 누락과 부분 인식이 남아 있습니다.

## 웹 페이지(viewer/) 로컬 테스트와 배포

`viewer/`는 https://pleos-note.vercel.app 에 배포됩니다(Vercel Hobby, 프로젝트 `pleos-note`, Root Directory `viewer`). `main`에 푸시되면 자동 배포되지만, 저장소가 비공개라 **프로젝트 소유자(`ysna629`)가 작성한 커밋**만 배포됩니다. 다른 팀원의 작업은 PR로 올리고 소유자가 **Create a merge commit**으로 머지합니다.

배포 전 확인:

```sh
python3 -m http.server 8766 --bind 0.0.0.0 --directory viewer
adb shell am start --user 10 -n ai.pleos.challenge.arrival/.MainActivity --es viewer_url http://<개발-PC-LAN-IP>:8766/
```

휴대폰을 같은 네트워크에 두고 앱의 QR을 찍습니다. 기록은 링크의 `#` 뒤에 압축되어 있어 서버로 전송되지 않습니다. 웹 테스트는 `node --test viewer/journey-export.test.mjs`.

## 여행 기록 CSV

- 차량의 문서 저장창으로 저장하거나, 휴대폰 QR 페이지에서 CSV를 받습니다. 긴 여행은 QR 여러 개로 나뉘며 각 파일에 파트 번호가 붙습니다.
- 여행명, 시작·종료, 시간대, 수집 GPS 거리, 방문 순서, 장소명, 주소, 도착·출발, 체류분, 기록 상태, 즐겨찾기, 메모, 주차 위도·경도가 들어갑니다. 이동 경로 전체의 원시 좌표는 넣지 않습니다.
- UTF-8 BOM을 포함해 Excel에서 바로 열립니다. 쉼표·따옴표·줄바꿈을 보존하고 수식 실행을 막습니다.
- Pleos Connect v2.1.0 에뮬레이터의 문서 저장창은 placeholder라 직접 저장이 되지 않으며, 그 경우 휴대폰 QR을 안내합니다.
- 휴대폰으로 가져간 사본은 차량의 동의 철회로 지워지지 않습니다.

## 카카오 장소 검색 키

실제 검색은 로컬 Gradle 속성 `kakaoRestApiKey`(또는 환경변수 `ORG_GRADLE_PROJECT_kakaoRestApiKey`)를 설정하고 다시 빌드하면 연결됩니다. 키는 저장소에 넣지 않습니다. 키가 없어도 나머지 기능은 모두 동작합니다.
