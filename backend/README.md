# Backend Authentication Guide

## 왜 JWT(Json Web Token)가 필요한가?
- **무상태(Stateless) 아키텍처**: 매치 요청 API(`/api/match/**`)는 스케일링과 세션 공유 문제를 피하기 위해 HTTP 요청마다 사용자를 식별할 수 있어야 합니다. 서버가 세션 상태를 보관하지 않으므로, 클라이언트가 스스로 신원을 증명하는 토큰이 필요합니다.
- **웹/모바일 클라이언트 공통 인증**: JWT는 HTTP 헤더로 전송되므로, 브라우저뿐 아니라 모바일 앱·외부 서비스에서도 동일한 인증 방식을 사용할 수 있습니다.
- **보안 및 무결성**: 발급 시 서버 비밀키로 서명되기 때문에, 중간에서 토큰이 변조되면 검증에 실패하여 차단됩니다. 또한 만료 시각(exp)으로 재인증 정책을 강제할 수 있습니다.

요약하면, JWT는 서버가 상태를 저장하지 않고도 "누가 요청을 보냈는지"를 검증하게 해 주는 핵심 수단입니다.

## Authorization 헤더 전송 방법
모든 인증이 필요한 요청에는 다음 헤더를 포함해야 합니다.

```
Authorization: Bearer <JWT>
```

예시 (cURL):

```bash
curl -X POST https://your-domain/api/match/requests \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{"gender":"FEMALE", "minAge":25, "maxAge":35}'
```

토큰은 `/api/auth/login`에서 로그인 성공 시 `token` 필드로 내려옵니다.

## 인증이 필요 없는 엔드포인트 허용하기
`SecurityConfig`에서는 회원가입/로그인 등 공개 API를 `permitAll()`로 설정하고, 매칭 관련 API만 `authenticated()`로 지정했습니다. 또한 `JwtAuthenticationFilter`가 해당 보호 경로(`/api/match/**`)에만 동작하도록 조정하여, 공개 엔드포인트에서는 토큰이 없어도 401이 발생하지 않습니다.

필요 시 `protectedEndpoints()` 또는 `authorizeHttpRequests` 설정에 경로를 추가/수정하여 인증 범위를 확장할 수 있습니다.

## 인증 실패 응답 형식
JWT가 없거나 유효하지 않은 상태로 보호된 API를 호출하면 HTTP 401 응답과 함께 다음과 같은 JSON 본문이 반환됩니다.

```
{
  "message": "Authorization 헤더에 JWT 토큰이 없습니다."
}
```

검증 단계에서 발견된 실제 사유(토큰 누락, 만료, 사용자 미존재 등)가 `message` 필드에 담기므로, 클라이언트는 이를 참고해 재로그인이나 토큰 재발급 등의 후속 조치를 수행하면 됩니다.
