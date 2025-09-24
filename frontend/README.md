# Matcha Talk (Vue + Vuetify)

Vue 3 + Vite + Vuetify 3 프런트엔드입니다. 제공된 SQL 스키마를 기준으로 회원/매칭/채팅/프로필 화면을 설계했으며, Spring Boot + MySQL 백엔드와 연동하면 실제 동작합니다.

## 실행
1. 패키지 설치
   ```bash
   npm install
   ```
2. 환경 변수 파일 생성 후 수정
   ```bash
   cp .env.example .env
   # 필요한 값으로 수정
   ```
3. 개발 서버 실행
   ```bash
   npm run dev
   ```
   기본 포트는 `5173`이며 `VITE_DEV_ALLOWED_HOST`와 HTTPS 설정을 통해 동일 네트워크의 다른 기기에서도 접속할 수 있습니다.

## HTTPS 개발 서버 설정
로컬/LAN 환경에서 WebRTC를 사용하려면 브라우저 보안 정책상 **HTTPS**가 필수입니다. 기본적으로 본 레포지터리에는 `frontend/dev-key.pem`, `frontend/dev-cert.pem`이 포함되어 있으며, 별도 설정이 없으면 Vite 개발 서버가 해당 파일을 이용해 HTTPS로 기동됩니다. 처음 접속하는 기기에서는 이 인증서를 신뢰하도록 승인해야 합니다. 필요에 따라 아래 절차에 따라 자체 인증서를 발급하고 교체할 수 있습니다.

### 1. mkcert로 로컬 인증서 발급 (권장)
1. [mkcert](https://github.com/FiloSottile/mkcert)를 설치합니다.
2. 신뢰할 수 있는 로컬 루트 인증서를 등록합니다.
   ```bash
   mkcert -install
   ```
3. 개발에서 사용할 호스트를 지정하여 인증서를 생성합니다. (예: `localhost`, `127.0.0.1`, 사설 IP)
   ```bash
   mkcert localhost 127.0.0.1 ::1 192.168.0.165
   # 출력된 두 파일(.pem)을 프로젝트 내부 원하는 위치에 저장합니다.
   ```
   위 예시는 개발 PC의 현재 사설 IP가 `192.168.0.165`인 상황을 기준으로 합니다. 네트워크 환경이 바뀌어 IP가 달라지면 새 IP를 포함해 같은 명령을 다시 실행해야 합니다.

### 2. OpenSSL로 자체 서명 인증서 만들기 (대안)
mkcert를 사용할 수 없는 환경이라면 OpenSSL로 다음과 같이 생성할 수 있습니다.
```bash
openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout dev-key.pem \
  -out dev-cert.pem \
  -subj "/CN=matcha-talk.local"
```
생성된 파일을 프로젝트 내 `certs/` 등 적절한 위치에 보관합니다. 이 경우 브라우저에 “신뢰할 수 없는 인증서” 경고가 표시될 수 있으므로, 필요하다면 수동으로 신뢰하도록 설정합니다.

### 3. 환경 변수 등록
`.env` 파일에 다음 값을 지정합니다.
```bash
VITE_DEV_HTTPS_KEY=./certs/dev-key.pem
VITE_DEV_HTTPS_CERT=./certs/dev-cert.pem
# 동일 네트워크 다른 기기 접근 허용 (선택)
VITE_DEV_ALLOWED_HOST=192.168.0.165
```
위 환경 변수 역시 사설망 접속을 허용할 IP(`192.168.0.165`)에 맞춰 작성한 예시입니다. 개발 PC의 IP가 변경되면 해당 값을 함께 업데이트해야 합니다.
`npm run dev`를 다시 실행하면 `https://<호스트>:5173` 주소로 접속할 수 있으며, `navigator.mediaDevices.getUserMedia`가 정상 동작합니다.

### 4. 브라우저 신뢰 저장소 업데이트
사설 인증서를 사용한다면 각 기기 브라우저/OS 신뢰 저장소에 루트 인증서를 등록해야 합니다. 등록이 완료되어야 카메라·마이크 권한 요청이 정상적으로 표시됩니다.

## TURN 서버 설정
기본 구성은 구글 STUN 서버만 포함하고 있어, 대칭 NAT나 이동통신망 등의 환경에서는 WebRTC 연결이 실패할 수 있습니다. `MatchSession.vue`는 환경 변수 또는 외부 API에서 TURN 정보를 불러와 `RTCPeerConnection`에 주입하도록 수정되어 있으므로, 아래 방법 중 하나를 통해 ICE 서버 구성을 준비하세요.

### 1. Metered Open Relay (동적 자격 증명)
1. [Metered](https://www.metered.ca) 콘솔에서 프로젝트를 생성하고 Open Relay를 활성화합니다. 무료 티어는 월 500MB까지 TURN 트래픽을 제공합니다.
2. 콘솔의 TURN Credentials API 엔드포인트(예: `https://matchatalk.metered.live/api/v1/turn/credentials`)와 API Key를 확인합니다.
3. 프런트엔드 `.env`에 다음 값을 추가합니다.
   ```bash
   VITE_TURN_CREDENTIALS_URL=https://matchatalk.metered.live/api/v1/turn/credentials
   VITE_TURN_API_KEY=<Metered_API_Key>
   ```
4. 애플리케이션이 로드되면 `getIceServers()`가 위 엔드포인트를 호출해 임시 TURN 자격 증명을 받아 `RTCPeerConnection`에 적용합니다.

### 2. 정적 TURN 자격 증명 사용
Metered가 발급한 고정 Username/Password를 직접 사용하거나, 다른 TURN 서버(coturn 등)를 운영 중이라면 다음 변수를 설정합니다.
```bash
VITE_TURN_URLS=turn:seoul.relay.metered.ca:80,turn:seoul.relay.metered.ca:443?transport=tcp,turns:seoul.relay.metered.ca:443
VITE_TURN_USERNAME=<TURN_Username>
VITE_TURN_CREDENTIAL=<TURN_Password>
```
여러 URL은 쉼표로 구분하며, UDP/TCP/TLS를 모두 포함해 두면 다양한 네트워크 환경에서 성공 확률이 높아집니다.

> ⚠️ TURN API 키나 자격 증명은 민감 정보이므로 `.env.local`, `.env.production` 등 git에 커밋되지 않는 파일에서 관리하세요.

## 운영 환경 배포 체크리스트
- Nginx, Apache, CloudFront 등 프록시/로드밸런서에 TLS 인증서를 설치하고 443 포트를 개방합니다.
- `/ws-stomp` 경로(WebSocket 업그레이드 요청)를 포함해 모든 트래픽이 HTTPS로 서비스되도록 설정합니다.
  ```nginx
  server {
    listen 443 ssl;
    server_name example.com;

    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    location / {
      proxy_pass http://backend:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws-stomp {
      proxy_pass http://backend:8080/ws-stomp;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_set_header Host $host;
    }
  }
  ```
- 방화벽/보안 장비에서 WebSocket과 HTTPS 포트를 허용했는지 확인합니다.
- CDN이나 리버스 프록시를 사용할 경우에도 동일한 인증서를 적용하고, HTTP 접근은 HTTPS로 리다이렉션합니다.

## 기타
- 외부 플레이스홀더 이미지를 프로젝트 자산(`src/assets/default-avatar.svg`)으로 교체하여 오프라인/사설망 환경에서도 기본 프로필 이미지가 정상 표시됩니다.
- 개발 중 HTTPS 설정이나 권한 관련 오류가 발생하면 브라우저 콘솔과 `MatchingResult.vue`의 안내 메시지를 확인하세요.
