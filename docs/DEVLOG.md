# 이음선 개발기

## 2026-11-26 · 첫 구현

### 처음 풀려고 한 세 가지 문제

이동 중 휴대전화는 모바일 데이터가 켜져 있어도 불안정한 Wi‑Fi를 기본 경로로 잡을 수 있다. Wi‑Fi 신호가 떨어지는데도 연결 자체는 유지되는 경우가 있고, 결국 셀룰러로 넘어갈 때는 기존 세션이 끊기기도 한다. 세 경우를 한 번에 'Wi‑Fi 없음'으로 처리하면 원인을 알아도 다음 행동을 고르기 어렵다. 첫 화면에 **Android가 지금 사용하는 경로**, **후보 경로의 인터넷 상태**, **지금 사용자가 할 수 있는 행동**을 별도로 배치했다.

### 연결됨, 인터넷 확인, 전환은 각각 다르다

Android의 `NET_CAPABILITY_INTERNET`은 인터넷용 경로로 설정됐다는 의미이지 그 순간 실제 인터넷에 닿는다는 보장이 아니다. `VALIDATED`와 `CAPTIVE_PORTAL`을 구분하고, 무선 신호는 인터넷 확인 여부에 합쳐 숨기지 않는다. 개방형 AP와 로그인 포털도 동의어가 아니다. 보안 유형이 가려지면 개방형으로 추정하지 않는다. 이동 중 제각각 도착하는 콜백과 잠깐 흔들리는 RSSI 때문에, 한 순간의 표본으로 상태를 번복하지 않는 판단 로직을 둔다. 첫 기준은 −76 dBm 이하가 6초 이상 이어질 때 약화, −68 dBm 이상에서 회복이다. 값이 없거나 다른 AP로 바뀌면 이전 판단의 근거를 버린다. 이 수치는 기기별 실측을 거치기 전인 초기 기준이다. 사용자에게 보일 상태와 모바일 준비를 요청하는 동작도 분리했다.

모바일 준비는 사용자 요청이 있을 때 Android에 임시 셀룰러 경로를 요청하는 것이다. 시스템 데이터 설정·SIM·통신사 상황이 우선이며 요청 성공조차 단말의 기본 인터넷 전환을 뜻하지 않는다. 연결을 켜고 끄는 일을 앱이 한 것처럼 표현하지 않고 필요한 경우 Android 인터넷 설정으로 보낸다. 같은 Wi‑Fi에 반복해서 잡히면 해당 네트워크의 자동 연결을 끄거나 삭제하도록 기본 Wi‑Fi 설정을 연다. 상시 유지 대신 앱 화면이 표시되는 동안 최대 2분으로 요청을 제한해 데이터 사용량과 전력에 미치는 비용을 줄이는 방향으로 설계했다.

### 왜 전역 강제 전환이나 VPN을 넣지 않았나

일반 최신 Android 앱은 Wi‑Fi를 조용히 끌 수 없고, 다른 앱의 기존 소켓을 새 네트워크에 붙일 수도 없다. Android는 개별 네트워크 소켓 사용을 권장하고 기존 소켓이 끊긴 네트워크에 묶이면 동작하지 않는다고 명시한다. 외부 게임·통화·다운로드가 쓰는 전송 프로토콜과 서버가 재연결을 지원하는지도 이 앱이 바꿀 수 없다. 전역 VPN을 도입하면 사용자 동의, 가용한 서버, 라우팅·DNS·IPv6·장애 전환 검증과 개인정보 처리 설계가 별도로 필요하다. 이번 버전은 패킷을 읽거나 터널을 운영하지 않고, 원인과 전환 상태를 명확히 보여 주는 데 집중했다.

- Android: [네트워크 상태 읽기](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) · [ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager) · [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager) · [인터넷 설정 패널](https://developer.android.com/reference/android/provider/Settings.Panel)
- Cloudflare: [Pages Git 연동](https://developers.cloudflare.com/pages/configuration/git-integration/) · [빌드 구성](https://developers.cloudflare.com/pages/configuration/build-configuration/)

### 이름과 화면

**이음선**은 두 네트워크를 하나라고 보이게 하는 이름이 아니라, 끊어지기 전에 다음 경로를 확인한다는 의미다. 짙은 청록은 판독이 필요한 연결 화면의 바탕, 민트는 사용할 수 있는 경로, 미색은 설명과 대비를 위한 배경이다. 색으로만 상태를 표현하지 않고 실제 상태명과 다음 행동을 함께 둔다. [Lemonade](https://github.com/hslcrb/lemonade-skills)에서 정보별 위계, 명확한 빈 상태와 제한, 모바일에서의 콘텐츠 순서, 실제로 동작하는 컨트롤과 반응형 검증 방식을 적용했다. 소개 페이지는 앱을 원격으로 제어하는 대신 문제·작동 범위·실행 방법을 설명하는 Vite 정적 사이트다.

### 실기기에서 남은 확인

서로 다른 휴대전화와 통신사에서 Wi‑Fi 약화 감지 지연, 모바일 준비 요청의 응답, 중간에 열린 통화·스트리밍의 실제 복구 동작을 분리해 측정해야 한다. 이 저장소의 [실기기 검증표](DEVICE-CHECKS.md)는 재현 절차이며 통과 증거가 아니다. 요청이 충족되지 않는 기기는 원인 확인과 시스템 패널 이동까지만 지원한다.

### Vite 소개 페이지 검증

Node 환경에서 잠금 파일을 사용한 Vite 빌드를 통과했다. Chromium에서 1,440·1,024·768·390·320px 다섯 폭을 검사했다. 인증 필요·약한 신호·모바일 전환의 세 가상 상태를 마우스와 키보드로 바꾸고, 화면 읽기용 상태 문구, 링크, 포커스, 가로 넘침과 외부 요청 여부를 대조했다. JavaScript 없이도 기본 상태 설명이 읽힌다. 이 검증은 안드로이드 앱이나 휴대전화의 전환 주기를 검증한 것이 아니다.

### Android 빌드와 단위 검증

Kotlin 컴파일과 API 35 Android SDK의 AAPT2·D8을 이용한 패키징을 별도로 완료했다. APK 서명은 apksigner로 검증했으며 패키지 이름은 `life.rhee.ieumseon`, 최소 Android 10(API 29), 대상 API 35다. 약한 신호 지속·회복·새 연결, 로그인형 경로·인터넷 미확인, 모바일 대안, 경로 전환 관측을 포함한 판단 로직 JUnit 시험 8개를 통과했다.

표준 Gradle 검증의 첫 실행은 이 환경에서 Google의 Android Gradle Plugin 저장소를 해석하지 못해 중단됐다. SDK와 Kotlin으로 빌드한 것은 서명된 **설치 테스트용 디버그 APK**이며 스토어 배포물이나 실기기에서 검증된 결과가 아니다.

### Cloudflare Pages 첫 배포

소개 페이지를 `hslcrb/ieumseon`의 `main`에 반영한 뒤, Cloudflare Pages GitHub 연동으로 실제 [운영 주소](https://ieumseon.pages.dev/)에 배포했다. 설정은 `site/` 루트, `npm run build`, `dist/`, Node 22, 운영 브랜치 `main`이다. 첫 운영 배포는 성공했다. Android 패키지는 Vite 정적 산출물에 포함하지 않았다.
