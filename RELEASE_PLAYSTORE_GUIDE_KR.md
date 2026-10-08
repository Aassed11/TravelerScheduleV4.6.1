# TRAVELER Schedule V4.6.2 — 정식 서명 Release / Google Play 준비

## 이번 버전에서 바뀐 것

- `compileSdk = 36`
- `targetSdk = 36`
- `versionCode = 35`
- `versionName = 4.6.2`
- Android Gradle Plugin `8.10.1`
- Gradle `8.11.1`
- Kotlin / Compose plugin `2.2.20`
- 정식 Release signingConfig 지원
- GitHub Actions에서 서명된 Release APK + AAB 생성
- Release 키/비밀번호는 GitHub Secrets에서만 읽음
- `keystore.properties` / `*.jks` Git 제외
- Play Store용 AAB 생성 워크플로 추가

## 왜 targetSdk 36으로 변경했나

2026-08-31 이후 Google Play의 새 앱/앱 업데이트는 Android 16(API 36) 이상을 타겟팅해야 합니다.
따라서 현재 시점의 신규 Play 등록을 준비하기 위해 API 36으로 맞췄습니다.

## 1. 업로드 키 생성

`CREATE_RELEASE_KEY_WINDOWS.txt`를 따라 `traveler-upload.jks`를 생성합니다.

이 키는 앞으로 TRAVELER Schedule의 업로드 키입니다.
분실하지 말고 최소 2곳 이상에 안전하게 백업하세요.

## 2. GitHub Secrets 등록

GitHub 저장소:
Settings → Secrets and variables → Actions → New repository secret

다음 4개를 등록합니다.

- `TRAVELER_KEYSTORE_BASE64`
- `TRAVELER_KEYSTORE_PASSWORD`
- `TRAVELER_KEY_ALIAS`
- `TRAVELER_KEY_PASSWORD`

키스토어 파일 자체를 GitHub 저장소에 커밋하지 않습니다.

## 3. Release 빌드

GitHub:
Actions → `Build Signed Release APK and AAB` → `Run workflow`

완료 후 Artifacts:
- `TravelerSchedule-V4.6.2-release-apk`
- `TravelerSchedule-V4.6.2-play-aab`

APK:
- 직접 설치 / 사내 배포 테스트용

AAB:
- Google Play Console 업로드용

## 4. Firebase Google 로그인에서 반드시 해야 할 것

Debug 서명과 Release 서명은 SHA 인증서 지문이 다릅니다.

업로드 키 생성 후:
Firebase Console → 프로젝트 설정 → 내 Android 앱 → SHA 인증서 지문 추가

`keytool -list -v -keystore traveler-upload.jks -alias traveler-upload`
에서 보이는:
- SHA-1
- SHA-256
을 등록합니다.

그 다음 최신 `google-services.json`을 다시 다운로드해
`app/google-services.json`에 덮어쓰는 것을 권장합니다.

## 5. Google Play App Signing 적용 후 한 번 더 확인

Google Play에 AAB를 처음 등록하면 Play App Signing을 사용하는 것이 일반적입니다.

Play Console:
설정/앱 무결성(App integrity) → 앱 서명 키 인증서

여기에 표시되는:
- SHA-1
- SHA-256

도 Firebase Android 앱에 추가하세요.

이 단계가 중요합니다.
Play에서 사용자에게 전달되는 APK는 업로드 키가 아니라 Play의 앱 서명 키로 서명될 수 있기 때문입니다.

Play App Signing 인증서를 Firebase에 등록하지 않으면
스토어에서 설치한 버전의 Google 로그인이 실패할 수 있습니다.

## 6. 첫 출시 권장 순서

1. Signed Release APK를 본인 휴대폰에 직접 설치
2. 이메일 로그인 테스트
3. Google 로그인 테스트
4. 일정 저장/동기화 테스트
5. 지문/PIN 잠금 테스트
6. 휴지통 복원 테스트
7. 알림 테스트
8. 위젯 테스트
9. Play Console 앱 생성
10. AAB를 내부 테스트 트랙에 먼저 업로드
11. Play 설치 버전에서 Google 로그인 재검증
12. 이상 없으면 비공개 테스트 → 정식 출시

## 보안 주의

절대 저장소에 올리면 안 되는 것:
- `traveler-upload.jks`
- 키스토어 비밀번호
- 키 비밀번호
- `keystore.properties`

`.gitignore`에 차단 규칙을 추가해두었습니다.
