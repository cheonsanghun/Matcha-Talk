/* =========================================
 * 1) 회원/인증/잠금/소셜
 * ========================================= */
CREATE TABLE users (
  user_pid           BIGINT AUTO_INCREMENT PRIMARY KEY,                -- 내부 PK
  login_id           VARCHAR(30)  NOT NULL UNIQUE,                      -- 로그인 아이디(중복확인)
  password_hash      VARCHAR(255) NOT NULL,                             -- 해시된 비밀번호(규칙은 앱에서 검증)
  nick_name          VARCHAR(30)  NOT NULL,                             -- 이름/표시명
  email              VARCHAR(100) NOT NULL,                             -- 이메일(본인인증/복구용)
  country_code       CHAR(2)      NOT NULL,                             -- 국적(ISO-3166-1 alpha-2, 예: KR/US)
  gender             CHAR(1)      NOT NULL                              -- 성별(M/F)
                      CHECK (gender IN ('M','F')),                      -- UI에서 블럭 클릭: 남=‘M’, 여=‘F’로 매핑 저장
  birth_date         DATE         NOT NULL                              -- 생년월일
                      /* 날짜 슬라이드바로 선택된 값을 Date로 변환 저장.
                         가입 연령 하한(예: 13세) 및 비현실 날짜는 앱단에서 우선 검증.
                         추가 안전장치로 체크 제약을 둠(서버 시간 기준). */
                      CHECK (birth_date >= DATE '1900-01-01'),
  email_verified     TINYINT(1)   DEFAULT 0 CHECK (email_verified IN (0,1)), -- 이메일 인증 여부
  failed_login_count INT          DEFAULT 0 CHECK (failed_login_count >= 0),  -- 연속 로그인 실패 횟수
  locked_until       DATETIME     NULL,                                  -- 5회 이상 실패 시 잠금 해제 예정 시각
  enabled            TINYINT(1)   DEFAULT 1 CHECK (enabled IN (0,1)),   -- 계정 사용 가능 여부(1:사용,0:정지)
  rolename           VARCHAR(30)  DEFAULT 'ROLE_USER'
                      CHECK (rolename IN ('ROLE_USER','ROLE_ADMIN')),    -- 권한
  created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,             -- 생성시각
  updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 수정시각
  CONSTRAINT uq_users_email UNIQUE (email)                               -- 이메일 유니크(계정복구)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- (자주 쓰는 조회를 위한 보조 인덱스가 필요하다면 상황에 따라 추가하세요)
-- 예: INDEX idx_users_login (login_id), INDEX idx_users_email (email)
/* 이메일 인증/아이디 찾기/비번 재설정 토큰 */
CREATE TABLE email_verifications (
  token_id    BIGINT AUTO_INCREMENT PRIMARY KEY,                         -- 토큰 PK
  user_pid    BIGINT NOT NULL,                                           -- 대상 사용자 FK
  token       VARCHAR(100) NOT NULL,                                     -- 인증 토큰(랜덤)
  purpose     VARCHAR(20)  NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','FIND_ID','RESET_PW')), -- 용도
  expires_at  DATETIME     NOT NULL,                                     -- 만료시각
  used_at     DATETIME     NULL,                                         -- 사용 시각
  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,                    -- 생성시각
  CONSTRAINT fk_ev_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_ev_token UNIQUE (token),
  INDEX idx_ev_user_purpose (user_pid, purpose, expires_at)              -- 사용자·용도별 유효 토큰 조회
);

/* Gmail OAuth 연동(사이트 내 계정과 연결) */
CREATE TABLE oauth_accounts (
  oauth_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                        -- 소셜 계정 PK
  user_pid     BIGINT NOT NULL,                                          -- 연결된 내부 사용자
  provider     VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE')),      -- 제공자(구글)
  provider_uid VARCHAR(100) NOT NULL,                                    -- 제공자측 고유 ID(sub)
  linked_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                      -- 연결 시각
  CONSTRAINT fk_oa_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_oa_provider_uid UNIQUE (provider, provider_uid),         -- 동일 소셜계정 중복 연결 방지
  INDEX idx_oa_user (user_pid)                                           -- 사용자 기준 소셜 연결 조회
);

/* =========================================
 * 2) 팔로우/팔로우 캐시
 * ========================================= */
CREATE TABLE follows (
  follow        BIGINT AUTO_INCREMENT PRIMARY KEY,                       -- 팔로우 요청 PK
  follower_id   BIGINT NOT NULL,                                         -- 요청자(나)
  followee_id   BIGINT NOT NULL,                                         -- 대상자(상대)
  status        VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')), -- 상태
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                     -- 생성시각
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 수정시각
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_follows_pair UNIQUE (follower_id, followee_id),          -- 동일쌍 중복 금지
  CONSTRAINT ck_follows_no_self CHECK (follower_id <> followee_id),      -- 자기자신 팔로우 금지

  -- 인덱스(목록/페이지네이션 최적화)
  INDEX idx_follows_follower (follower_id, status, created_at),          -- 내가 팔로우한 목록 최신순
  INDEX idx_follows_followee (followee_id, status, created_at)           -- 나를 팔로우하는 목록 최신순
);

CREATE TABLE follow_list (
  list_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                         -- 캐시 PK
  follow_id   BIGINT NOT NULL,                                           -- 원본 follows FK
  owner_pid   BIGINT NOT NULL,                                           -- 리스트 소유자(나)
  target_pid  BIGINT NOT NULL,                                           -- 표시 대상(상대)
  direction   VARCHAR(10) NOT NULL CHECK (direction IN ('FOLLOWING','FOLLOWER')), -- 방향
  status      VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')), -- 원본 상태
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                       -- 생성시각
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 수정시각
  CONSTRAINT fk_fl_follow  FOREIGN KEY (follow_id)  REFERENCES follows(follow) ON DELETE CASCADE,
  CONSTRAINT fk_fl_owner   FOREIGN KEY (owner_pid)  REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_fl_target  FOREIGN KEY (target_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_fl_owner_target_dir UNIQUE (owner_pid, target_pid, direction), -- 같은 대상·방향 1회만

  -- 인덱스(리스트 화면)
  INDEX idx_follow_list_owner (owner_pid, status, direction),            -- 내 리스트 필터링
  INDEX idx_follow_list_target (target_pid),                             -- 대상 기준 역탐색
  INDEX idx_fl_follow_id (follow_id)                                     -- 원본 조인/동기화
);

/* =========================================
 * 3) 랜덤 매칭(대기큐/핸드셰이크)
 * ========================================= */
CREATE TABLE match_requests (
  request_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                      -- 요청 PK
  user_pid       BIGINT NOT NULL,                                        -- 사용자
  choice_gender  CHAR(1) NOT NULL CHECK (choice_gender IN ('M','F','A')),-- 희망 성별(M/F/All)
  min_age        INT NOT NULL,                                           -- 최소 나이
  max_age        INT NOT NULL ,                -- 최대 나이
  region_code    VARCHAR(10) NOT NULL,                                   -- 희망 지역
  interests_json JSON NOT NULL,                                          -- 관심사 배열(JSON)
  status         VARCHAR(10) NOT NULL DEFAULT 'WAITING'                  -- 대기/매칭/취소
                  CHECK (status IN ('WAITING','MATCHED','CANCELLED')),
  requested_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                    -- 요청시각
  CONSTRAINT fk_mr_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT ck_mr_age_range CHECK (max_age >= min_age),
  
  -- 인덱스(매칭 스캔)
  INDEX idx_mr_match_scan (status, choice_gender, region_code, min_age, max_age, requested_at), -- 조건+FIFO
  INDEX idx_mr_user (user_pid)                                            -- 사용자별 최신 요청 조회
);

/* =========================================
 * 4) 방/멤버/그룹 전환·초대
 * ========================================= */
CREATE TABLE rooms (
  room_id        BIGINT AUTO_INCREMENT PRIMARY KEY,                      -- 방 PK
  room_type      VARCHAR(10) NOT NULL CHECK (room_type IN ('RANDOM','PRIVATE','GROUP')), -- 방 유형
  capacity       TINYINT NOT NULL DEFAULT 2,                              -- 정원(GROUP=4)
  created_from_room_id BIGINT NULL,                                       -- 1:1→그룹 전환 원본 방
  promoted_at    TIMESTAMP NULL,                                          -- 승격 시각
  promoted_reason VARCHAR(30) NULL,                                       -- 승격 사유(예: FOLLOW_BOTH)
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                     -- 생성시각
  closed_at      TIMESTAMP NULL,                                          -- 종료시각
  CONSTRAINT fk_room_from_room FOREIGN KEY (created_from_room_id) REFERENCES rooms(room_id) ON DELETE SET NULL,

  -- 인덱스(유형별/최근 방 탐색)
  INDEX idx_rooms_type (room_type, created_at)                            -- 유형+생성시각
);

CREATE TABLE room_members (
  room_id        BIGINT NOT NULL,                                         -- 방
  user_pid       BIGINT NOT NULL,                                         -- 사용자
  role           VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('HOST','MEMBER')), -- 역할
  invited_by_pid BIGINT NULL,                                             -- 초대한 사람(선택)
  joined_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                     -- 입장시각
  left_at        TIMESTAMP NULL,                                          -- 퇴장시각(NULL=활성)
  PRIMARY KEY (room_id, user_pid),
  CONSTRAINT fk_rm_room       FOREIGN KEY (room_id)       REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_rm_user       FOREIGN KEY (user_pid)      REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_rm_invited_by FOREIGN KEY (invited_by_pid) REFERENCES users(user_pid) ON DELETE SET NULL,

  -- 인덱스(활성 멤버/내 방)
  INDEX idx_rm_room_active (room_id, left_at),                             -- 방의 활성 멤버 조회
  INDEX idx_rm_user_active (user_pid, left_at)                             -- 내가 속한 활성 방 조회
);

CREATE TABLE group_creation_proposals (
  proposal_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                      -- 제안 PK
  private_room_id BIGINT NOT NULL,                                        -- 대상 1:1(=PRIVATE) 방
  invitee1_pid    BIGINT NOT NULL,                                        -- 사용자1의 초대상대
  invitee2_pid    BIGINT NOT NULL,                                        -- 사용자2의 초대상대
  user1_approve   TINYINT(1) DEFAULT 0 CHECK (user1_approve IN (0,1)),    -- 1의 동의
  user2_approve   TINYINT(1) DEFAULT 0 CHECK (user2_approve IN (0,1)),    -- 2의 동의
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'                  -- 승인/거절/만료
                   CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                     -- 생성시각
  CONSTRAINT fk_gcp_private FOREIGN KEY (private_room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv1    FOREIGN KEY (invitee1_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv2    FOREIGN KEY (invitee2_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,

  -- 인덱스(대기목록/만료스윕)
  INDEX idx_gcp_status (status, created_at)                                -- 상태+생성시각
);

CREATE TABLE group_room_invites (
  invite_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                         -- 초대 PK
  room_id       BIGINT NOT NULL,                                           -- 그룹 방
  inviter_pid   BIGINT NOT NULL,                                           -- 초대한 사람
  invitee_pid   BIGINT NOT NULL,                                           -- 초대받은 사람
  status        VARCHAR(10) NOT NULL DEFAULT 'PENDING'                     -- 승인/거절/만료
                 CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                       -- 생성시각
  CONSTRAINT fk_gri_room    FOREIGN KEY (room_id)     REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gri_inviter FOREIGN KEY (inviter_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gri_invitee FOREIGN KEY (invitee_pid) REFERENCES users(user_pid) ON DELETE CASCADE,

  -- 인덱스(방별 진행중 초대)
  INDEX idx_gri_room_status (room_id, status, created_at)                  -- 방+상태+시각
);

CREATE TABLE group_room_invite_approvals (
  invite_id     BIGINT NOT NULL,                                           -- 초대
  approver_pid  BIGINT NOT NULL,                                           -- 당시 활성 멤버(승인자)
  approved      TINYINT(1) DEFAULT 0 CHECK (approved IN (0,1)),            -- 동의 여부
  approved_at   TIMESTAMP NULL,                                            -- 동의 시각
  PRIMARY KEY (invite_id, approver_pid),
  CONSTRAINT fk_gria_invite   FOREIGN KEY (invite_id)   REFERENCES group_room_invites(invite_id) ON DELETE CASCADE,
  CONSTRAINT fk_gria_approver FOREIGN KEY (approver_pid) REFERENCES users(user_pid) ON DELETE CASCADE
);

/* =========================================
 * 5) 메시지(텍스트/이미지/파일/시스템)
 * ========================================= */
CREATE TABLE room_messages (
  message_id    BIGINT AUTO_INCREMENT PRIMARY KEY,                         -- 메시지 PK
  room_id       BIGINT NOT NULL,                                           -- 방 FK
  sender_pid    BIGINT NULL,                                               -- 보낸 사람(탈퇴 시 NULL)
  content_type  VARCHAR(10) NOT NULL CHECK (content_type IN ('TEXT','IMAGE','FILE','SYSTEM')), -- 타입
  text_content  TEXT,                                                      -- 텍스트 본문
  file_name     VARCHAR(300),                                              -- 파일명
  file_path     VARCHAR(500),                                              -- 파일 저장 경로/URL
  mime_type     VARCHAR(100),                                              -- MIME 타입
  size_bytes    BIGINT,                                                    -- 파일 크기
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                       -- 전송시각
  CONSTRAINT fk_msg_room FOREIGN KEY (room_id)    REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_user FOREIGN KEY (sender_pid) REFERENCES users(user_pid) ON DELETE SET NULL,
  CONSTRAINT ck_msg_payload_min CHECK (                                      -- 페이로드 최소 요건
    (content_type = 'TEXT'  AND text_content IS NOT NULL)
    OR (content_type IN ('FILE','IMAGE') AND file_path IS NOT NULL)
    OR (content_type = 'SYSTEM')
  ),

  -- 인덱스(타임라인 페이지네이션)
  INDEX idx_msg_room_time (room_id, created_at)                            -- 방 타임라인 최신순
);

/* =========================================
 * 6) 프로필/공개 범위/사용 언어
 * ========================================= */
CREATE TABLE profiles (
  user_pid      BIGINT PRIMARY KEY,                                        -- 사용자 PK=FK
  avatar_url    VARCHAR(300) NULL,                                         -- 프로필 이미지
  bio           VARCHAR(500) NULL,                                         -- 자기소개
  languages_json JSON NULL,                                                -- 사용언어 목록(JSON)
  visibility    VARCHAR(10) NOT NULL DEFAULT 'PUBLIC'                      -- 공개범위
                  CHECK (visibility IN ('PUBLIC','FOLLOWERS','PRIVATE')),
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- 수정시각
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE
);

/* =========================================
 * 7) 번역 단어 저장(단어장)
 * ========================================= */
CREATE TABLE saved_words (
  word_id         BIGINT AUTO_INCREMENT PRIMARY KEY,                       -- 단어 저장 PK
  user_pid        BIGINT NOT NULL,                                         -- 소유 사용자
  source_text     VARCHAR(500) NOT NULL,                                   -- 원문
  translated_text VARCHAR(500) NOT NULL,                                   -- 번역문
  source_lang     CHAR(2) NOT NULL,                                        -- 원문 언어 코드
  target_lang     CHAR(2) NOT NULL,                                        -- 번역 언어 코드
  context         VARCHAR(500) NULL,                                       -- 문맥/메모
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                      -- 저장시각
  CONSTRAINT fk_sw_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_sw_user_time (user_pid, created_at)                            -- 사용자별 최근 단어 조회
);

/* =========================================
 * 8) 문의/신고/제재(관리자)
 * ========================================= */
CREATE TABLE user_inquiries (
  inquiry_id   BIGINT AUTO_INCREMENT PRIMARY KEY,                          -- 문의 PK
  user_pid     BIGINT NOT NULL,                                            -- 작성자
  category     VARCHAR(30) NOT NULL,                                       -- 카테고리(예: ACCOUNT, BUG)
  title        VARCHAR(200) NOT NULL,                                      -- 제목
  content      TEXT NOT NULL,                                              -- 내용
  status       VARCHAR(10) NOT NULL DEFAULT 'OPEN'                         -- 처리 상태
                CHECK (status IN ('OPEN','ANSWERED','CLOSED')),
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                        -- 접수시각
  answered_at  TIMESTAMP NULL,                                             -- 답변시각
  CONSTRAINT fk_ui_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_ui_user_status (user_pid, status, created_at)                  -- 사용자/상태별 조회
);

CREATE TABLE user_reports (
  report_id     BIGINT AUTO_INCREMENT PRIMARY KEY,                          -- 신고 PK
  reporter_pid  BIGINT NOT NULL,                                            -- 신고자
  reported_pid  BIGINT NOT NULL,                                            -- 피신고자
  reason        VARCHAR(100) NOT NULL,                                      -- 사유(코드/간단문구)
  detail        TEXT NULL,                                                  -- 상세 내용
  status        VARCHAR(12) NOT NULL DEFAULT 'OPEN'                         -- 진행 상태
                 CHECK (status IN ('OPEN','REVIEWING','ACTIONED','DISMISSED')),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                        -- 신고시각
  CONSTRAINT fk_ur_reporter FOREIGN KEY (reporter_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_ur_reported FOREIGN KEY (reported_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_ur_status_time (status, created_at),                            -- 상태별 최신 신고
  INDEX idx_ur_participants (reporter_pid, reported_pid)                    -- 양자 관계 조회
);

CREATE TABLE user_penalties (
  penalty_id  BIGINT AUTO_INCREMENT PRIMARY KEY,                            -- 제재 PK
  user_pid    BIGINT NOT NULL,                                              -- 대상 사용자
  type        VARCHAR(20) NOT NULL CHECK (type IN ('WARN','SUSPEND','BAN')),-- 제재 종류
  reason      VARCHAR(200) NULL,                                            -- 사유
  starts_at   DATETIME NOT NULL,                                            -- 시작시각
  ends_at     DATETIME NULL,                                                -- 종료시각(NULL=무기한 BAN)
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                           -- 생성시각
  CONSTRAINT fk_up_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_up_user_window (user_pid, starts_at, ends_at)                   -- 기간 중복/효력 조회
);



