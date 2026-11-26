# 이음선 · IEUMSEON

**연결 표시만 보지 말고, 인터넷이 통하는 길을 보세요.** 이음선은 약해지거나 인터넷을 통과시키지 못하는 Wi‑Fi를 감지하고, 모바일 데이터가 대안이 될 수 있는지 확인하는 Kotlin Android 앱입니다.

- 기본 연결, Wi‑Fi 신호·인터넷 확인·로그인 필요 여부, 관측된 모바일 데이터 상태를 한 화면에 보여줍니다.
- 약한 Wi‑Fi가 일정 시간 지속될 때만 경고합니다. 카페·역의 로그인 화면처럼 Wi‑Fi는 연결됐지만 인터넷을 쓸 수 없는 상태도 구분합니다. 개방형 여부를 Android가 앱에 알려 주지 않으면 ‘확인 불가’로 둡니다.
- 명시적으로 요청하면 앱이 열려 있는 동안 잠시 모바일 연결 준비를 시도합니다. 실제 전환은 Android가 결정하며 인터넷 설정 패널에서 사용자가 바로 개입할 수 있습니다.
- 루트, VPN, 트래픽 수집, 계정, 원격 측정 서버를 사용하지 않습니다. 다른 앱의 경로를 강제로 바꾸거나 이미 열린 통화·스트리밍·TCP 연결을 보존하지는 않습니다.

| 위치 | 내용 |
| --- | --- |
| `app/` | Kotlin Android 앱, 전환 판단 단위 테스트 |
| `site/` | Vite 정적 소개 사이트 |
| [개발기](docs/DEVLOG.md) | 설계 결정과 검증 범위 |
| [실기기 검증표](docs/DEVICE-CHECKS.md) | 끊김, 약한 신호, 로그인형 Wi‑Fi 재현 절차 |

## Android 빌드

Android Studio에서 이 저장소 루트를 열고 Android SDK를 설치한 뒤 `app` 구성을 실행합니다. 명령줄에서는 JDK 17 이상과 Android SDK가 필요합니다.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Windows에서는 `gradlew.bat testDebugUnitTest assembleDebug`를 사용합니다. 디버그 APK 경로: `app/build/outputs/apk/debug/app-debug.apk`.

## 소개 사이트

```sh
cd site
npm ci
npm run dev
npm run build
```

Cloudflare Pages는 이 저장소의 `main`을 사용합니다. 루트 디렉터리는 `site`, 빌드 명령은 `npm run build`, 출력은 `dist`입니다. GitHub Actions는 사용하지 않습니다.
