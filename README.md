# MatchTalk - 실시간 채팅 및 화상통화 애플리케이션

## 프로젝트 개요
MatchTalk은 Spring Boot와 WebSocket을 기반으로 한 실시간 채팅 및 WebRTC를 이용한 화상통화 서비스입니다.

## 주요 기능
- 🔐 JWT 기반 사용자 인증
- 💬 실시간 텍스트 채팅 (WebSocket + STOMP)
- 📹 WebRTC 기반 화상통화
- 👥 그룹 채팅방 생성 및 관리
- 🔒 Spring Security를 통한 보안 강화

## 기술 스택
- **Backend**: Spring Boot 3.5.4, Spring Security, Spring Data JPA
- **Database**: MySQL 8.0
- **Authentication**: JWT (JSON Web Token)
- **Real-time Communication**: WebSocket, STOMP, WebRTC
- **Frontend**: HTML5, JavaScript, CSS3
- **Build Tool**: Gradle

## 시작하기

### 필수 요구사항
- Java 17 이상
- MySQL 8.0 이상
- Gradle 7.0 이상

### 데이터베이스 설정
```sql
CREATE DATABASE matcha_talk_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 개발 환경용
CREATE DATABASE matcha_talk_dev_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 실행 방법
```bash
# 프로젝트 클론
git clone <repository-url>
cd Matcha-Talk

# 개발 환경 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 프로덕션 환경 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 빌드 및 배포
```bash
# JAR 파일 빌드
./gradlew bootJar

# 생성된 JAR 실행
java -jar build/libs/matcha-talk-1.0.0-SNAPSHOT.jar
```

## API 엔드포인트

### 인증 관련
- `POST /api/users/signup` - 회원가입
- `POST /api/users/login` - 로그인
- `GET /api/users/profile` - 사용자 프로필 조회

### 채팅 관련
- `POST /api/rooms` - 채팅방 생성
- WebSocket 연결: `/ws-connect`
- 메시지 전송: `/app/chat.sendMessage/{roomId}`
- WebRTC 시그널링: `/app/signal`

## 프로젝트 구조
```
src/main/java/net/datasa/project01/
├── Config/                 # 설정 클래스들
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   └── ...
├── controller/             # REST API 컨트롤러
├── domain/                 # 도메인 모델
│   ├── dto/               # Data Transfer Objects
│   └── entity/            # JPA 엔티티
├── repository/            # 데이터 접근 계층
├── service/               # 비즈니스 로직
└── util/                  # 유틸리티 클래스들

src/main/resources/
├── static/                # 정적 파일들 (HTML, CSS, JS)
├── application.properties # 기본 설정
├── application-dev.properties    # 개발 환경 설정
└── application-prod.properties   # 프로덕션 환경 설정
```

## 환경별 설정
- **개발 환경**: `application-dev.properties`
- **프로덕션 환경**: `application-prod.properties`

## 보안 설정
- JWT 토큰 기반 인증
- Spring Security를 통한 엔드포인트 보호
- WebSocket 연결 시 JWT 검증
- 비밀번호 BCrypt 암호화

## TODO 및 향후 개선사항

### 백엔드
- [ ] 이메일 인증 시스템 구축
- [ ] Redis 캐시 도입 (세션 관리)
- [ ] 파일 업로드 기능
- [ ] 푸시 알림 서비스
- [ ] API 문서화 (Swagger)
- [ ] 테스트 코드 작성 및 커버리지 향상
- [ ] Docker 컨테이너 지원
- [ ] 로그 수집 및 모니터링 시스템
- [ ] Rate Limiting 구현

### 프론트엔드
- [ ] React/Vue.js로 프론트엔드 리팩토링
- [ ] 반응형 디자인 개선
- [ ] PWA 지원
- [ ] 다국어 지원

### 인프라
- [ ] CI/CD 파이프라인 구축
- [ ] AWS/GCP 배포 설정
- [ ] 로드밸런싱 및 스케일링
- [ ] 데이터베이스 백업 및 복구 전략

## 기여하기
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 라이선스
이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참고하세요.

## 연락처
프로젝트에 대한 문의사항이 있으시면 이슈를 생성하거나 이메일로 연락주세요.