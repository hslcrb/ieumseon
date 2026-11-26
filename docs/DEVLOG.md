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

## v0.2.0-alpha.1 · 1–2칸 선제 전환

첫 버전은 RSSI의 고정 임계치로 경고를 띄웠다. 이번 버전은 이동 중 사용자가 실제로 보는 기준인 **5칸 중 1–2칸**을 앱 안에도 표시한다. Android가 보고하는 신호 수준이 기기마다 같은 칸 수가 아닐 수 있어 앱 게이지에서 다섯 칸으로 정규화한다. Android 10은 플랫폼의 5단계 변환을 사용한다. 강도 값 자체가 없으면 위험이라고 추정하지 않는다. 자동 대기 옵션이 켜져 있어도 약함이 6초 연속 지속될 때만 반응하며 새로운 연결이나 회복에서 다시 센다. 이 설계는 걸어가며 겪는 순간적인 흔들림을 따라 과도하게 요청하는 일을 줄이기 위한 것이다.

### 전환을 시작하는 조건과 전환이 끝났다는 증거

1. 앱이 실제 기본 네트워크를 Wi‑Fi로 보고할 때 취약 상태를 평가한다.
2. 일반 Android에서는 사용자가 선택한 자동 대기 옵션에 따라 셀룰러 네트워크를 잠시 요청한다. 요청 수락 또는 신호만으로 '전환 완료'라고 표시하지 않는다. 기본 경로를 바꾸려면 앱의 인터넷 설정 버튼을 사용한다.
3. 별도로 켠 관리형 모드에서는 기기 소유자 여부를 먼저 확인한다. 인터넷이 검증되고 차단 여부도 확인된 셀룰러 경로가 안정적으로 보이면 Wi‑Fi 끄기를 요청할 수 있다. 호출 성공과 실제 기본 경로 전환은 다른 사건이며, Android 기본 네트워크 피드백으로 나눠 기록한다.
4. Wi‑Fi가 꺼진 뒤 앱 모니터링을 그만두면 앱에 의존해서 연결이 복원되지 않도록 되돌리기 경로와 Android 인터넷 설정을 제공한다. 알 수 없는 상태, 거부, 소유권 상실, 모바일 데이터 없음에서는 자동으로 더 강한 권한을 얻으려고 하지 않는다.

### 권한을 늘린 이유와 넣지 않은 것

일반 설치에서 앱에 `CHANGE_WIFI_STATE`가 선언되어 있어도 최신 Android는 임의 Wi‑Fi 토글을 허용하지 않는다. 이 권한은 별도 관리 환경의 공식 예외에서만 가치가 있다. 위치, VPN, 접근성 서비스, 루트 및 레거시 SDK를 통한 우회는 도입하지 않았다. Wi‑Fi를 끌 수 있는 환경에서도 그것만으로 다른 앱의 기존 소켓이나 통화가 새 경로로 이동하지 않으므로, '끊김 없는 자동 핸드오버'라고 이름 붙이지 않았다.

참조: [Android 10 Wi‑Fi 변경 제한](https://developer.android.com/about/versions/10/privacy/changes#configure-wifi) · [WifiManager 공식 API](https://developer.android.com/reference/android/net/wifi/WifiManager) · [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager) · [ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager).

### 권한 대안 비교

| 후보 | 검토 결과 |
| --- | --- |
| 일반 기기 관리자(Device Admin) 활성화 | 비밀번호·관리 정책을 위한 레거시 역할이다. 일반 관리자가 되더라도 Wi‑Fi 토글 예외가 생기지 않으므로 사용자에게 활성화를 요구하지 않는다. |
| 기기 소유자(Device Owner) | 완전 관리 단말에서 Android Wi‑Fi 토글 예외를 쓸 수 있다. 앱은 소유자 여부를 확인할 뿐 소유자 지정이나 회사 정책 변경은 하지 않는다. 수동 동의가 2분 지나면 만료된다. |
| 작업 프로필의 Profile Owner | Android API에 관련 예외가 있으나 이 버전은 프로필 소유자만 있는 개인 단말에 안전하다고 가정하지 않는다. 기기 소유자만 허용한다. |
| 통신사·시스템 권한 | 일반 앱 설치가 얻을 수 있는 권한이 아니다. 모바일 데이터가 꺼져 있을 때 앱이 몰래 켜지 않는다. |
| 다른 앱 트래픽을 다루는 VPN | 동의 후 라우팅할 수 있지만 Wi‑Fi 무음 토글 권한이나 기존 연결 보존을 자동으로 얻지는 못한다. 추가 인프라와 데이터 처리 범위를 요구하므로 제외했다. |
| 접근성 자동 클릭·루트·낮은 target SDK | 시스템 권한을 대신하는 안정적인 방법이 아니고 UI 상태나 보안 모델을 깨뜨릴 수 있다. 구현하지 않았다. |

관리 게이트는 Wi‑Fi 로그인 포털 및 VPN을 피하고, 별도로 요청한 셀룰러의 검증 및 차단 여부가 2초 동안 안정됐을 때만 최초 한 번 Wi‑Fi 끄기를 요청한다. 기기 기본 경로를 8초 이내 확인하지 못하면 화면이 보이는 동안 복구를 한 번 시도한다. 이전 Wi‑Fi 상태로 되돌려야 하는 표식은 로컬에 보존해 앱을 다시 열었을 때 복구 버튼을 제공한다. 이 표식은 SSID·위치·패킷을 담지 않는다. 관리자가 Wi‑Fi를 다시 켜거나 앱의 만료 시간이 지나도 자동으로 재전환하지 않는다.

### 알파 빌드와 검증

공식 Android SDK 35의 AAPT2·D8 및 Kotlin을 사용해 APK를 만들고 서명을 검증했다. Android 10 이상을 대상으로 하며, 패키지 이름은 `life.rhee.ieumseon`, 버전 코드는 2다. v0.1의 테스트 APK와 같은 디버그 인증서로 서명해 앱 업그레이드가 가능하도록 했다. 정책 단위 테스트 12개가 통과했다. 기기 소유자 지정, 샘플 검증, 운영 통신망의 지연과 전환은 서로 다른 검증이므로 결과를 섞지 않는다.

추가 참조: [Device Administration](https://developer.android.com/work/device-admin) · [VPN 서비스](https://developer.android.com/develop/connectivity/vpn).

## v0.3.0-alpha.1 · 화면 밖 관리형 전환과 공공 Wi‑Fi 프로필

관리형 자동 모드를 화면의 수명과 분리했다. 이미 Device Owner인 앱에서 사용자가 감시를 한 번 시작하면 포그라운드 서비스가 약한 신호를 계속 감시한다. API 34부터 기기 소유자 역할에 맞는 `systemExempted` 서비스 유형을 사용한다. 중지 알림을 보일 수 없으면 시작하지 않으며, 구독·모니터를 중지하고 Wi‑Fi 복구를 요청할 수 있다. 일반 설치에 이 전환 능력을 제공하는 공개 Android 권한은 확인되지 않았다. 재부팅·강제 종료 후 자동 시작하지 않으며, 한 감시 세션에서 한 번만 전환을 시도해 같은 AP 주변에서 반복으로 끄고 켜는 일을 피한다. 화면이 꺼진 상태에서는 절전 정책 때문에 처리 지연이 달라질 수 있다. [Android 서비스 유형](https://developer.android.com/reference/android/content/pm/ServiceInfo#FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)

수원 공공장소·전국 버스 자료에서는 서로 다른 보안 SSID가 확인됐다. 전국 공공와이파이 문서에는 시스템 인증서 경로에서 `wifisecure.kr` 안내가 있지만 수원 버스 전체의 인증 서버·루트 CA로 확인할 수 없어서 앱에 고정하지 않았다. 공식 안내판의 정확한 이름과 운영사 CA·도메인이 사용자에게 제공된 경우에만, 공개 계정 `wifi / wifi`를 미리 채운 Android 네트워크 추가 화면을 연다. Android 11 이상의 [기업형 Wi‑Fi 보안 구성](https://developer.android.com/guide/topics/connectivity/wifi-enterprise)에 맞춰 검증을 비활성화하지 않는다. SSID가 같아도 실제 탑승 차량을 구분할 수 없으므로 주변 AP를 자동 스캔하거나 `Secure`라는 이름만 보고 접속하지 않는다.

SDK 35로 APK 빌드와 서명을 확인했고, JUnit 13개 및 Vite 빌드가 통과했다. 실제 차량의 인증·관리 기기의 무선 전환은 시험하지 않았다.
