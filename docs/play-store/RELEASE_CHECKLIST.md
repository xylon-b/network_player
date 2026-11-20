# 플레이스토어 출시 체크리스트

## A. 코드에서 이미 한 것 (이 PR)

| 항목 | 내용 |
|---|---|
| 타깃 SDK | `targetSdk`/`compileSdk` 35 → **36** (2026년 8월 31일부터 신규 앱·업데이트 필수). AGP 8.7.3 → 8.10.1 |
| 16KB 페이지 | libVLC 3.5.1의 네이티브 라이브러리가 4KB 정렬이라 **업로드가 거절됨** (2025년 11월부터 필수). 3.6.5로 올림 (API 변경 없음, 메서드만 추가). CI에서 매번 정렬을 검사 |
| 릴리스 서명 | release 빌드는 더 이상 디버그 키를 쓰지 않음. `keystore.properties`(로컬) 또는 환경 변수(CI)로 업로드 키를 읽음. 비밀번호는 저장소에 안 들어감 |
| AAB 빌드 | GitHub Secrets에 업로드 키가 있으면 CI가 `network-player-release-aab` 아티팩트를 만듦 |
| 버전 | `versionName` 1.0.0, `versionCode` 1 |
| 앱 아이콘 | 시스템 기본 재생 아이콘 → 전용 적응형 아이콘(테마 아이콘 지원). 스토어용 512px: `docs/play-store/icon-512.png` |
| 비밀번호 백업 | 공유폴더 비밀번호(`servers.xml`)와 썸네일을 구글 백업·기기 이전에서 제외 |
| 평문 HTTP | `usesCleartextTraffic` 전체 허용 → 127.0.0.1(앱 내부 스트리밍 서버)만 허용 |
| 권한 | INTERNET, ACCESS_NETWORK_STATE 두 개뿐. 민감 권한 없음 (권한 선언 양식 불필요) |
| 오픈소스 고지 | 첫 화면 ⓘ > 앱 정보: 라이브러리별 라이선스 전문 + libVLC 소스 링크 (LGPL 요구사항) |
| 개인정보처리방침 | 앱 안에 한국어 본문 탑재 + `docs/play-store/privacy-policy.md` (한/영) |

### 라이선스 정리
| 라이브러리 | 라이선스 | 해야 할 것 |
|---|---|---|
| libVLC 3.6.5 | LGPL 2.1+ | 고지 + 라이선스 전문 + 소스 위치 안내 (완료). 수정 없이 동적 라이브러리(.so)로 사용하므로 앱 코드 공개 의무 없음 |
| smbj | Apache 2.0 | 고지 + 전문 (완료) |
| smbj-rpc (Rapid7) | BSD 3-Clause | 고지 + 전문 (완료) |
| Bouncy Castle | MIT | 고지 + 전문 (완료) |
| AndroidX, Compose, Kotlin | Apache 2.0 | 고지 + 전문 (완료) |

주의: 앱 이름·아이콘·설명에 "VLC", "nPlayer" 같은 남의 상표를 쓰지 않기. (설명의 라이선스 고지는 괜찮음)

### 이번에 안 한 것 (선택)
- **R8 코드 축소**: 켜면 용량이 줄지만 smbj/BouncyCastle/libVLC가 리플렉션을 써서 규칙 없이 켜면 실행 중 죽을 수 있음. 출시 후 따로 테스트하며 켜는 걸 추천
- **비밀번호 암호화**: 지금은 앱 전용 저장공간에 평문 저장(다른 앱은 못 읽음, 백업 제외). Android Keystore로 암호화하면 더 안전
- 네이티브 디버그 심볼 업로드: 콘솔에 경고만 뜨고 출시는 가능

## B. TOY님이 직접 해야 하는 것 (순서대로)

### 1) 개발자 계정 (하루~며칠)
1. https://play.google.com/console/signup 에서 **개인** 계정 생성, 등록비 **25달러** (1회)
2. 신분증 인증 + 연락처(전화·이메일) 인증
3. Play Console 앱으로 **실제 안드로이드 폰 인증**
4. ⚠️ **2023년 11월 이후 만든 개인 계정은 바로 정식 출시가 안 됩니다.** 비공개 테스트에 **테스터 12명 이상이 14일 연속** 참여해야 프로덕션 신청 버튼이 열려요. 지인·구독자 단톡방 등으로 미리 모아두세요.

### 2) 업로드 키 만들기 (5분, PC에서 한 번만)
```bash
keytool -genkeypair -v -keystore upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```
- `upload-key.jks` 파일과 비밀번호는 **절대 저장소에 올리지 말고**, 클라우드 드라이브 등 두 군데 이상 백업
- 앱 서명 키는 구글이 보관(Play 앱 서명, 기본값). 업로드 키를 잃어버려도 콘솔에서 재설정 신청 가능

### 3) AAB 만들기 (둘 중 하나)
**방법 1: GitHub Actions (추천, PC에 개발 도구 필요 없음)**
저장소 Settings > Secrets and variables > Actions > New repository secret 에 4개 등록:

| 이름 | 값 |
|---|---|
| `NP_KEYSTORE_BASE64` | `base64 -w0 upload-key.jks` 결과 (윈도우: `certutil -encode upload-key.jks out.txt` 후 머리·꼬리 줄 빼고 붙여넣기) |
| `NP_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `NP_KEY_ALIAS` | `upload` |
| `NP_KEY_PASSWORD` | 키 비밀번호 |

그다음 Actions > Build APK > Run workflow → 아티팩트 `network-player-release-aab` 안의 `app-release.aab`

**방법 2: 로컬**
프로젝트 루트에 `keystore.properties` (git에 안 올라감):
```
storeFile=upload-key.jks
storePassword=****
keyAlias=upload
keyPassword=****
```
`./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

### 4) 앱 만들기 + 콘솔 양식
Play Console > 앱 만들기 (이름, 기본 언어 한국어, 앱, 무료)

**패키지 이름 `com.toy.networkplayer`는 한 번 올리면 영원히 못 바꿉니다.** 바꾸고 싶으면 첫 업로드 전에 말씀해 주세요.

대시보드 "앱 설정" 항목 답안:

| 양식 | 답 |
|---|---|
| 개인정보처리방침 | 공개 URL (privacy-policy.md를 올린 주소) |
| 앱 액세스 권한 | "일부 기능 제한": 사용자의 SMB 공유폴더가 있어야 동작한다고 적고, 짧은 시연 영상 링크(유튜브 일부공개)를 첨부하면 심사 거절 확률이 줄어요 |
| 광고 | 없음 |
| 콘텐츠 등급 | 설문: 카테고리 "기타(유틸리티/생산성…)", 폭력·성·도박 전부 "아니요", 사용자 간 소통·콘텐츠 공유 "아니요" → 전체이용가 예상 |
| 타겟층 | 13세 이상 연령대만 선택 (어린이 포함 시 가족 정책 심사가 붙음) |
| 뉴스 앱 | 아니요 |
| 데이터 보안 | "데이터를 수집하거나 공유하나요?" → **아니요** (모든 데이터가 기기 안에만 있고 개발자에게 전송되지 않음). 전송 중 암호화 질문은 해당 없음 |
| 정부 앱 / 금융 / 건강 | 아니요 |
| 광고 ID | 사용 안 함 |

### 5) 출시 순서
1. 비공개 테스트 트랙 만들기 → AAB 업로드 → 테스터 12명 이메일(또는 구글 그룹) 등록 → 검토 제출
2. 테스터가 옵트인 링크로 설치, **14일 유지**
3. 프로덕션 액세스 신청 (테스트 소감 질문 몇 개)
4. 승인 후 프로덕션 출시 (첫 심사 보통 수일)

### 6) 업데이트할 때마다
`app/build.gradle.kts`의 `versionCode`를 1 올리고 (`versionName`도 1.0.1 등) → AAB 다시 빌드 → 업로드

## C. 출시 전에 폰에서 한 번 확인해 주세요
- [ ] 새 아이콘이 잘 보이는지 (테마 아이콘 켰을 때도)
- [ ] libVLC 3.6.5에서 재생, 자막, 디코더 전환, 연속 재생이 그대로인지
- [ ] 첫 화면 ⓘ > 앱 정보 > 라이선스·개인정보처리방침이 열리는지
- [ ] Android 16 폰이라면 뒤로가기 제스처(예측 뒤로가기) 동작 확인

⚠️ release 빌드는 업로드 키로 서명되므로 지금 쓰는 디버그 APK 위에 덮어 설치되지 않습니다. 플레이스토어 버전으로 갈아탈 때는 기존 앱을 지우고 설치해야 해요(서버 목록 다시 입력).
