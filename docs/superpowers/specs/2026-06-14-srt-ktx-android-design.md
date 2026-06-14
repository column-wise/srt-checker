# SRT/KTX 취소표 알리미 Android 앱 설계

## 개요

SRT·KTX 취소표를 주기적으로 감시하고, 발견 시 자동 예약을 시도하는 안드로이드 앱.

- **언어**: Kotlin
- **최소 SDK**: API 26 (Android 8.0)
- **아키텍처**: MVVM + Repository 패턴

---

## 결정 사항 요약

| 항목 | 결정 |
|------|------|
| 네비게이션 | Bottom Navigation (홈·감시목록·설정) |
| 백그라운드 폴링 | Foreground Service (초 단위 폴링) |
| 취소표 감지 시 | 자동 예약 시도 + 푸시 알림 |
| 자격증명 저장 | EncryptedSharedPreferences (Android Keystore) |

---

## 화면 구조

```
MainActivity
├── HomeFragment          ← 감시 조건 입력 + 시작
│   ├── SRT 탭
│   └── KTX 탭
├── WatchListFragment     ← 진행 중·완료 감시 목록
└── SettingsFragment      ← 로그인 정보·폴링 간격
```

### HomeFragment
- SRT/KTX 탭 전환 (TabLayout + ViewPager2)
- 입력: 출발역·도착역 (드롭다운), 날짜, 출발 시간 범위, 좌석 유형 (일반/특실)
- "감시 시작" 버튼 → TicketWatcherService 실행

### WatchListFragment
- Room DB에서 WatchJob 목록 표시
- 상태: 감시중 / 예약성공 / 실패 / 취소
- 스와이프로 감시 취소

### SettingsFragment
- SRT 아이디·비밀번호 입력 및 저장
- KTX 아이디·비밀번호 입력 및 저장
- 폴링 간격 설정 (기본 15초, 범위 15~60초)

---

## 서비스 계층

### TicketWatcherService (Foreground Service)

```
시작 → 상단 알림 "감시 중: 수서→부산 12/25" 표시
루프:
  1. NetFunnelHelper.getToken() (SRT만, 캐시 48초)
  2. repository.searchTrains(조건)
  3. 취소표 있으면 → repository.reserve()
  4a. 성공 → 알림 발송, WatchJob 상태 업데이트, 서비스 종료
  4b. 실패 (이미 없음) → 계속 루프
  5. delay(폴링간격)
```

- 코루틴 기반 (`lifecycleScope` 대신 `ServiceScope`)
- 여러 WatchJob 동시 감시: 각 Job마다 코루틴 launch
- 서비스 종료 조건: 모든 WatchJob 완료 또는 사용자 취소

---

## 데이터 계층

### SrtRepository

참조: [lapis42/srtgo](https://github.com/lapis42/srtgo) `srt.py`

| 메서드 | 엔드포인트 |
|--------|-----------|
| `login()` | `POST /apb/selectListApb01080_n.do` |
| `searchTrains()` | `POST /ara/selectListAra10007_n.do` |
| `reserve()` | `POST /arc/selectListArc05013_n.do` |
| `getReservations()` | `POST /atc/selectListAtc14016_n.do` |
| `cancel()` | `POST /ard/selectListArd02045_n.do` |

- Base URL: `https://app.srail.or.kr:443`
- User-Agent: `SRT-APP-Android V.2.0.38` (공식 앱과 동일)
- 세션 쿠키: OkHttp `CookieJar`로 자동 관리

#### NetFunnelHelper

예약/조회 전 `nf.letskorail.com/ts.wseq`에서 토큰 발급 필요.
- TTL: 48초 캐시 (매 요청마다 새로 받지 않음)
- 대기열 발생 시 폴링으로 통과 대기

### KtxRepository

참조: [lapis42/srtgo](https://github.com/lapis42/srtgo) `ktx.py`

| 메서드 | 엔드포인트 |
|--------|-----------|
| `login()` | `POST .login.Login` |
| `searchTrains()` | `POST .seatMovie.ScheduleView` |
| `reserve()` | `POST .certification.TicketReservation` |
| `cancel()` | `POST .reservationCancel.ReservationCancelChk` |

- Base URL: `https://smart.letskorail.com:443`
- User-Agent: `Dalvik/2.1.0 (Linux; U; Android 14; SM-S912N)`
- 일부 필드 AES 암호화 필요 (`AesHelper` 별도 구현)

---

## 로컬 데이터베이스 (Room)

### WatchJob 엔티티

```kotlin
@Entity
data class WatchJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainType: TrainType,       // SRT | KTX
    val depStation: String,
    val arrStation: String,
    val date: String,               // YYYYMMDD
    val timeFrom: String,           // HHMM
    val timeTo: String,             // HHMM
    val seatType: SeatType,         // GENERAL | SPECIAL | ANY
    val status: WatchStatus,        // WATCHING | SUCCESS | FAILED | CANCELLED
    val reservationNumber: String?, // 예약 성공 시
    val createdAt: Long,
    val updatedAt: Long,
)
```

---

## 자격증명 저장

- `EncryptedSharedPreferences` (Jetpack Security)
- Android Keystore로 키 보호
- 저장 항목: SRT ID/PW, KTX ID/PW
- Foreground Service에서 직접 읽어 사용 (생체인증 없이 복호화 가능)

---

## 알림

- **ongoing 알림**: 감시 중 상태 (Foreground Service 필수 알림)
  - 내용: "감시 중: [출발]→[도착] [날짜]"
  - 탭하면 WatchListFragment로 이동
- **결과 알림**: 예약 성공/실패
  - 성공: "예약 완료! 수서→부산 12/25 08:00 [호차·좌석]"
  - 실패 (재고 없음): 알림 없음, 계속 감시
  - 실패 (로그인 만료 등): "오류 발생 — 앱을 확인해주세요"

---

## 기술 스택

| 분류 | 라이브러리 |
|------|-----------|
| UI | Fragment + ViewPager2 + Material3 |
| 네트워크 | OkHttp + Retrofit + Gson |
| 코루틴 | Kotlin Coroutines + Flow |
| DB | Room |
| 자격증명 | Jetpack Security (EncryptedSharedPreferences) |
| DI | Hilt |
| 뷰모델 | ViewModel + StateFlow |

---

## 주요 리스크

1. **IP 차단**: SRT/KTX가 비정상 트래픽 감지 시 블록. 폴링 간격 너무 짧으면 위험. 최소 15초 권장.
2. **NetFunnel 대기**: 혼잡 시간대 토큰 발급에 수십 초 소요 가능. 타임아웃 처리 필요.
3. **KTX AES 암호화**: 키·IV 값 역공학 필요. [srtgo](https://github.com/lapis42/srtgo) 코드 참고.
4. **세션 만료**: 장시간 감시 중 세션 쿠키 만료 시 자동 재로그인 처리 필요.
5. **Doze 모드**: Foreground Service는 Doze 면제지만 제조사별 배터리 최적화로 강제 종료될 수 있음.
