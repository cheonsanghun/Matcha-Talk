# Matcha Talk Development Notes

## Local HTTPS Setup with mkcert

The frontend uses WebRTC and secure cookies, so running the dev stack over HTTPS avoids most browser permission prompts.

1. [Install mkcert](https://github.com/FiloSottile/mkcert) for your platform.
2. Trust the local certificate authority:
   ```bash
   mkcert -install
   ```
3. Create a certificate for your dev hostname (adjust as needed):
   ```bash
   mkcert localhost 127.0.0.1 ::1
   ```
   This produces `localhost+2.pem` and `localhost+2-key.pem`. Move them to a safe place inside the repo (e.g. `certs/`).
4. Reference the certificates from Vite by updating `frontend/vite.config.ts` (or `.js`):
   ```ts
   import fs from 'node:fs'
   import { defineConfig } from 'vite'

   export default defineConfig({
     server: {
       https: {
         cert: fs.readFileSync('certs/localhost+2.pem'),
         key: fs.readFileSync('certs/localhost+2-key.pem'),
       },
       host: '0.0.0.0',
       port: 5173,
     },
   })
   ```
5. Restart the Vite dev server. Visit `https://localhost:5173/` and accept the certificate once in each browser profile.

## Backend WebSocket Origins

The WebSocket endpoint `/ws-stomp` reads allowed origins from `app.ws.allowed-origins`. Define it via environment variable for development:

```properties
# backend/src/main/resources/application.properties
app.ws.allowed-origins=${APP_WS_ALLOWED_ORIGINS:}
```

Set it before starting Spring Boot:

```bash
export APP_WS_ALLOWED_ORIGINS="https://localhost:5173,https://192.168.0.100:5173"
./gradlew bootRun
```

If the variable is empty the server permits the standard localhost patterns.

## HTTPS/Vite/Spring Checklist

- [ ] mkcert certificates installed and referenced by Vite.
- [ ] Vite dev server runs with `https://` URL that matches `APP_WS_ALLOWED_ORIGINS`.
- [ ] `.env` (frontend) points `VITE_API_BASE_URL` to the backend origin (`https://localhost:8080/api` for example).
- [ ] Backend runs with TLS termination handled externally (nginx, dev proxy) or stays on HTTP if you forward via HTTPS proxy.
- [ ] STOMP clients reconnect automatically when tokens refresh; monitor the browser console for `[stomp]` logs in dev mode.

## TURN/STUN Notes

The TURN client logs the chosen ICE servers in development with credentials redacted. Configure `VITE_TURN_URLS`, `VITE_TURN_CREDENTIAL`, or a credentials endpoint (`VITE_TURN_CREDENTIALS_URL`) if you have a TURN service. When the API call fails the app falls back to `stun:stun.l.google.com:19302`.

## Error Handling Visibility

The backend now emits structured errors on `/user/queue/errors` when signaling fails. The frontend surfaces these as status messages and console warnings so you can diagnose invalid routing or authentication problems quickly.
