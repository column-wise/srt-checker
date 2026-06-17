# SRT 취소표 감시기

![GitHub Downloads](https://img.shields.io/github/downloads/column-wise/srt-checker/total?style=flat-square&label=Downloads)

SRT 취소표를 자동으로 감시하여 빈 좌석이 생기면 즉시 예약을 시도하는 Android 앱.

## 기능

- **자동 감시**: 백그라운드 포그라운드 서비스로 지속 실행, 앱 종료 후에도 동작
- **즉시 예약**: 취소표 감지 즉시 예약 시도, 성공 시 푸시 알림 발송
- **조건 설정**: 출발역/도착역, 날짜, 시간대(시작~종료), 좌석 유형(일반실/특실/상관없음)
- **다중 감시**: 여러 조건 동시 감시
- **자주 쓰는 경로**: 최근 사용 경로 칩으로 빠른 재입력
- **감시 목록**: 진행 중/완료/취소 내역 확인 및 개별 중지
- **로그 패널**: 설정 화면에서 실시간 감시 로그 확인

## 요구사항

- Android 8.0 (API 26) 이상
- SRT 회원 계정 (아이디: 이메일 / 휴대폰 / 멤버십번호)

## 사용법

1. **설정** 탭에서 SRT 아이디/비밀번호 입력
2. **홈** 탭에서 출발역, 도착역, 날짜, 시간대, 좌석 유형 선택
3. **감시 시작** 버튼 탭
4. 취소표 발생 시 알림 수신 → 알림 탭하면 SRT 결제 페이지로 이동

> 예약 성공 후 결제는 직접 SRT 앱/웹에서 완료해야 합니다.

## 기술 스택

| 분류 | 라이브러리 |
|------|-----------|
| DI | Hilt |
| DB | Room |
| 네트워크 | Retrofit + OkHttp |
| 비동기 | Kotlin Coroutines |
| UI | Material3, ViewBinding, Navigation, ViewPager2 |
| 보안 | EncryptedSharedPreferences |

## 빌드

```bash
./gradlew assembleDebug
```

Android Studio Meerkat 이상 권장.

## 주의사항

- 이 앱은 SRT 공식 앱/API를 비공식으로 호출합니다. SRT 서버 정책 변경 시 동작하지 않을 수 있습니다.
- 예약 성공 후 미결제 시 패널티가 부과될 수 있으니 알림 수신 즉시 결제를 완료하세요.
- 폴링 간격을 지나치게 짧게 설정하면 계정이 차단될 수 있습니다.
