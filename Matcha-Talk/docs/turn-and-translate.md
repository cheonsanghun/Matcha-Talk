# TURN · 번역 API 연동 가이드

본 문서는 Metered TURN 서버와 외부 번역 API를 Matcha Talk 서비스에 연동할 때 필요한 환경 변수, 주의 사항, 점검 방법을 정리한 자료입니다. 모든 값은 반드시 환경 변수로 주입하고, 로그·Git 저장소에 민감 정보를 남기지 마세요.

## 1. Metered TURN 서버 연동

1. [Metered 대시보드](https://dashboard.metered.ca/turnserver/)에서 TURN 프로젝트를 생성하고 `API Key` 메뉴에서 ICE 서버 정보를 확인합니다.
2. 발급받은 URL/아이디/비밀번호를 아래 환경 변수에 설정합니다.
3. 백엔드 애플리케이션이 실행되면 `/api/webrtc/config` 요청 시 설정된 ICE 서버 목록이 내려옵니다. 값이 비어 있으면 자동으로 Google STUN 기본 세트를 반환합니다.

### 필수 환경 변수

```bash
export METERED_TURN_URLS="stun:stun.metered.ca:80,turn:a.relay.metered.ca:80,turn:a.relay.metered.ca:443?transport=tcp,turns:a.relay.metered.ca:443"
export METERED_TURN_USERNAME="your-username"
export METERED_TURN_CREDENTIAL="your-password"
```

- `METERED_TURN_URLS`: 콤마로 구분된 STUN/TURN URL 목록입니다. WebRTC 클라이언트는 순서대로 시도합니다.
- `METERED_TURN_USERNAME`, `METERED_TURN_CREDENTIAL`: TURN 인증에 사용되는 계정입니다.
- 로컬 개발 시 TURN 서버가 없으면 변수를 비워 두고 STUN만으로 동작을 확인할 수 있지만, 실제 서비스 환경에서는 TURN 서버를 반드시 준비하세요.

### 점검 체크리스트

- 백엔드 로그에 TURN 자격 증명이 노출되지 않는지 확인합니다.
- 프런트엔드에서 `getIceServers()` 호출 이후 콘솔에 ICE 서버 목록이 정상 출력되는지 확인합니다.
- 사설 네트워크 환경에서는 TCP TURN 포트(예: 443/tcp)도 열려 있는지 확인합니다.

## 2. 번역 API 연동

1. 선택한 번역 서비스(예: 자체 구축, Google, DeepL 등)의 엔드포인트와 API 키를 준비합니다.
2. 환경 변수로 아래 값을 주입합니다. 프로바이더 문자열은 백엔드 서비스 로직에서 라우팅을 위해 사용됩니다.

```bash
export TRANSLATE_API_BASE_URL="https://your-translate-endpoint"
export TRANSLATE_API_KEY="your-api-key"
export TRANSLATE_API_PROVIDER="generic"  # generic | deepl | google 등
```

### 구현 메모

- `translate.api-key`는 네트워크 로그, 서버 로그에 남기지 말고, 예외 메시지에도 포함되지 않도록 필터링되어 있습니다.
- 번역 API가 장애일 때 프런트엔드에는 친절한 오류 메시지가 노출되며, 단어 저장은 시도하지 않습니다.
- 저장된 단어장은 `/api/words` API로 관리하고, 저장 성공 시 클라이언트는 캐시를 새로고침합니다.

### 점검 체크리스트

- 번역 API 호출 실패 시에도 500 대신 처리 가능한 오류(JSON 바디 포함)가 내려오는지 확인합니다.
- 저장 옵션(`save=true`)을 포함한 요청에서 DB에 `saved_words`가 정상 적재되는지 확인합니다.
- 번역 결과가 비어 있을 경우 프런트엔드가 fallback 문구를 보여 주는지 확인합니다.

## 3. 보안 및 운영 주의 사항

- 모든 환경 변수는 운영 인프라의 시크릿 매니저(예: AWS SSM, Kubernetes Secret 등)에 저장하고, `.env` 파일을 버전 관리하지 않습니다.
- 로컬 테스트를 위해 `.env.local`을 사용하더라도 Git에 커밋하지 않도록 `.gitignore`를 점검하세요.
- TURN/번역 API 장애 시를 대비한 모니터링(응답 지연, HTTP 오류 비율)을 구성하고 Slack/메일 알림을 설정하는 것이 좋습니다.

## 4. 관련 문서

- `README.md` – 전체 개발 환경 구성, 프록시 설정 예시, 핵심 환경 변수 정리
- `frontend/src/services/webrtc.js` – ICE 서버 가져오기 로직
- `backend/src/main/resources/application.yml` – Spring Session · Redis · WebRTC · 번역 설정 키

필요 시 이 문서를 업데이트하여 새로운 인프라 구성이나 정책 변경 사항을 공유하세요.
