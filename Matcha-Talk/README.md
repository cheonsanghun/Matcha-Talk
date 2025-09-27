# Matcha Talk

## 개요
- Spring Session + Redis 기반 세션 쿠키 인증으로 JWT 없이 로그인 상태를 관리합니다.
- STOMP/SockJS 웹소켓을 통해 세션 인증을 재사용하고, 방 ACL · 제재 · 페이로드 사이즈 제한을 서버 단에서 검증합니다.
- WebRTC용 ICE 설정 API, 번역/단어장 REST API, 채팅 메시지 멱등 처리를 추가하여 랜덤 매칭과 실시간 커뮤니케이션 안정성을 높였습니다.
- 프런트엔드는 쿠키 기반 Axios 설정, CSRF 토큰 갱신, STOMP 재연결, Perfect Negotiation 전략, 번역 UX 개선을 반영했습니다.

## 빠른 시작
1. Java 17+, Node.js 18+, Redis가 설치되어 있어야 합니다.
2. 백엔드 의존성 설치 및 실행
   ```bash
   cd backend
   ./gradlew clean build
   ./gradlew bootRun
   ```
3. 프런트엔드 의존성 설치 및 실행
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. 브라우저에서 로그인 후 `/api/webrtc/config`, `/api/translate` 등 주요 API가 쿠키 기반으로 정상 응답하는지 확인합니다.

## 필수 환경 변수
### 공통
```bash
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export SPRING_PROFILES_ACTIVE="local"
```

### WebRTC (Metered TURN)
```bash
export METERED_TURN_URLS="stun:stun.metered.ca:80,turn:a.relay.metered.ca:80,turn:a.relay.metered.ca:443?transport=tcp,turns:a.relay.metered.ca:443"
export METERED_TURN_USERNAME="your-username"
export METERED_TURN_CREDENTIAL="your-password"
```
- 빈 문자열을 넣으면 백엔드가 Google STUN 기본값을 반환합니다.
- 실제 서비스 환경에서는 TURN 서버 자격 증명이 필수입니다.

### 번역 API
```bash
export TRANSLATE_API_BASE_URL="https://your-translate-endpoint"
export TRANSLATE_API_KEY="your-api-key"
export TRANSLATE_API_PROVIDER="generic"  # generic | deepl | google 등 중 택 1
```
- 키/비밀번호는 절대로 로그에 남기지 마세요.
- 프로바이더 문자열은 백엔드 `TranslateService`에서 응답 파싱 로직을 선택하는 데 사용됩니다.

### 이메일 발송
```bash
export SPRING_MAIL_HOST="smtp.gmail.com"
export SPRING_MAIL_PORT="587"
export SPRING_MAIL_USERNAME="your-account@example.com"
export SPRING_MAIL_PASSWORD="app-password-or-token"
```
- Gmail 등 외부 SMTP 사용 시 2단계 인증 + 앱 비밀번호 사용을 권장합니다.
- 개발 환경에서는 MailHog, Mailpit 등의 로컬 SMTP를 지정해도 됩니다.

### Papago 번역
```bash
export PAPAGO_CLIENT_ID="your-naver-client-id"
export PAPAGO_CLIENT_SECRET="your-naver-client-secret"
export PAPAGO_API_URL="https://papago.apigw.ntruss.com/nmt/v1/translation"
```
- Papago를 사용하지 않는다면 `PAPAGO_CLIENT_ID`와 `PAPAGO_CLIENT_SECRET`를 비워두고 `TRANSLATE_API_PROVIDER`를 `generic` 등 다른 제공자로 설정하세요.

### 파일 업로드 사전 서명
```bash
export FILE_PRESIGN_BASE_URL="https://uploader.example.com"
```
- 브라우저는 `/api/rooms/{id}/files/presign` 응답으로 받은 URL에 직접 업로드합니다.

### 세션 쿠키 플래그
```bash
export SERVER_SESSION_COOKIE_SECURE="true"     # HTTPS 배포 환경에서만 true
export SERVER_SESSION_COOKIE_SAMESITE="None"   # 크로스 도메인에서 쿠키 전송이 필요할 때 조정
```
- 로컬 HTTP 개발 환경에서는 `SERVER_SESSION_COOKIE_SECURE`를 지정하지 않으면 기본값(`false`)이 적용되어 세션 쿠키가 전송됩니다.
- 서로 다른 도메인에서 쿠키를 공유해야 한다면 `SameSite=None`과 함께 HTTPS(secure=true)를 반드시 사용하세요.

환경 변수는 OS나 배포 플랫폼의 시크릿 매니저를 활용해 관리하고, `.env` 파일은 버전 관리 대상에서 제외하세요.

## 프록시 설정 예시
### Nginx
```nginx
server {
    listen 443 ssl;
    server_name chat.example.com;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```
- `/ws/` 경로는 SockJS가 내부적으로 다양한 하위 경로를 사용하므로 슬래시(`/`)로 끝나도록 설정합니다.

### Vite 개발 서버
`frontend/vite.config.js`
```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
```

## 참고 문서
- [`docs/turn-and-translate.md`](docs/turn-and-translate.md): Metered TURN, 번역 API 환경 변수 및 운영 주의 사항
- 백엔드 설정: `backend/src/main/resources/application.yml`
- 프런트엔드 STOMP 클라이언트: `frontend/src/services/ws.js`

필요한 추가 설정이나 운영 노하우가 생기면 문서를 보강해주세요.
