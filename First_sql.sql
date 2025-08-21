/* =========================
 * 1) 사용자(개인 식별 번호 기반)
 * ========================= */
CREATE TABLE users (
  user_pid       BIGINT AUTO_INCREMENT PRIMARY KEY,                 -- 개인 식별 번호(PK)
  login_id       VARCHAR(30) COLLATE utf8mb4_bin NOT NULL UNIQUE,   -- 로그인 ID(대소문자 구분 유니크)
  password_hash  VARCHAR(255) NOT NULL,                             -- 암호화 비밀번호(여유 길이)
  nick_name      VARCHAR(30) NOT NULL,                              -- 표시 이름
  email          VARCHAR(100),                                      -- 이메일(선택)
  country_code   CHAR(2) NOT NULL,                                  -- 국가 코드(사용자 선택)
  gender         CHAR(1) NOT NULL CHECK (gender IN ('M','F')),
  birth_date     DATE NOT NULL,                                     -- 나이 계산 근거(매칭용)
  enabled        TINYINT(1) DEFAULT 1 CHECK (enabled IN (0,1)),     -- 1:사용,0:정지
  rolename       VARCHAR(30) DEFAULT 'ROLE_USER'
                  CHECK (rolename IN ('ROLE_USER','ROLE_ADMIN')),
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_users_country_upper CHECK (country_code = UPPER(country_code))
);


/* =========================
 * 2) 팔로우(상호승인형)
 * ========================= */
CREATE TABLE follows (
  follow         BIGINT AUTO_INCREMENT PRIMARY KEY, 
  follower_id    BIGINT NOT NULL,               -- 팔로우 요청자
  followee_id    BIGINT NOT NULL,               -- 대상자
  status         VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(user_pid) ON DELETE CASCADE,

  -- 동일 쌍 중복 금지 + 자기 팔로우 금지
  CONSTRAINT uq_follows_pair UNIQUE (follower_id, followee_id),
  CONSTRAINT ck_follows_no_self CHECK (follower_id <> followee_id),

  -- 조회 인덱스
  INDEX idx_follows_follower (follower_id, status, created_at),
  INDEX idx_follows_followee (followee_id, status, created_at)
);

-- 팔로우 리스트(화면 출력/캐시용)
CREATE TABLE follow_list (
  list_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  follow_id    BIGINT NOT NULL,              -- 원본 follows.pk 참조
  owner_pid    BIGINT NOT NULL,              -- "내 리스트"의 주인
  target_pid   BIGINT NOT NULL,              -- 리스트에 표시될 상대
  direction    VARCHAR(10) NOT NULL CHECK (direction IN ('FOLLOWING','FOLLOWER')),
  status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  -- 동일 대상이라도 방향이 다르면(팔로워/팔로잉) 별도 행 허용
  UNIQUE KEY uk_follow_list_owner_target_dir (owner_pid, target_pid, direction),

  -- 조회 최적화
  KEY idx_follow_list_owner (owner_pid, status, direction),
  KEY idx_follow_list_target (target_pid),
  KEY idx_fl_follow_id (follow_id),

  CONSTRAINT fk_fl_follow  FOREIGN KEY (follow_id)  REFERENCES follows(follow) ON DELETE CASCADE,
  CONSTRAINT fk_fl_owner   FOREIGN KEY (owner_pid)  REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_fl_target  FOREIGN KEY (target_pid) REFERENCES users(user_pid) ON DELETE CASCADE
);


/* =========================
 * 3) 랜덤매칭 ‘선택 조건’ 단일 테이블
 * ========================= */
CREATE TABLE match_requests (
  request_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid       BIGINT NOT NULL,
  choice_gender  CHAR(1) NOT NULL CHECK (choice_gender IN ('M','F','A')),
  min_age        INT NULL,
  max_age        INT NULL CHECK (max_age >= min_age),
  region_code    VARCHAR(10) NOT NULL,
  interests_json JSON NOT NULL,
  status         VARCHAR(10) NOT NULL DEFAULT 'WAITING'
                 CHECK (status IN ('WAITING','MATCHED','CANCELLED')),
  requested_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_mr_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,

  -- 매칭 큐 탐색 인덱스
  INDEX idx_mr_match_scan (status, choice_gender, region_code, min_age, max_age, requested_at)
);


/* =========================
 * 4) 수락 핸드셰이크(5초 내 양측 수락)
 * ========================= */
CREATE TABLE match_candidate (
  candidate_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  user1_pid      BIGINT NOT NULL,
  user2_pid      BIGINT NOT NULL,
  user1_accept   TINYINT(1) DEFAULT 0 CHECK (user1_accept IN (0,1)),
  user2_accept   TINYINT(1) DEFAULT 0 CHECK (user2_accept IN (0,1)),
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),

  -- 자기 매칭 금지
  CONSTRAINT ck_mc_no_self CHECK (user1_pid <> user2_pid),

  CONSTRAINT fk_mc_u1 FOREIGN KEY (user1_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_mc_u2 FOREIGN KEY (user2_pid) REFERENCES users(user_pid) ON DELETE CASCADE,

  -- 동일 페어 중복 제어(순서 무시)
  u_small BIGINT AS (LEAST(user1_pid, user2_pid)) STORED,
  u_large BIGINT AS (GREATEST(user1_pid, user2_pid)) STORED,
  UNIQUE KEY uq_mc_pair_status (u_small, u_large, status),
  INDEX idx_mc_created (created_at)
);


/* =========================
 * 5) 방/멤버(단일 모델)
 * ========================= */
CREATE TABLE rooms (
  room_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_type      VARCHAR(10) NOT NULL CHECK (room_type IN ('RANDOM','PRIVATE','GROUP')),
  capacity       TINYINT NOT NULL DEFAULT 2,  -- GROUP 생성 시 4로 지정
  created_from_room_id BIGINT NULL,           -- 1:1→그룹 시 원본 방 참조(선택)
  promoted_at    TIMESTAMP NULL,              -- RANDOM→PRIVATE 승격 시각(선택)
  promoted_reason VARCHAR(30) NULL,           -- 'FOLLOW_BOTH' 등(선택)
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  closed_at      TIMESTAMP NULL,

  CONSTRAINT fk_room_from_room FOREIGN KEY (created_from_room_id)
            REFERENCES rooms(room_id) ON DELETE SET NULL,

  INDEX idx_rooms_type (room_type, created_at)
);

CREATE TABLE room_members (
  room_id        BIGINT NOT NULL,
  user_pid       BIGINT NOT NULL,
  role           VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('HOST','MEMBER')),
  invited_by_pid BIGINT NULL,
  joined_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  left_at        TIMESTAMP NULL,

  PRIMARY KEY (room_id, user_pid),

  CONSTRAINT fk_rm_room       FOREIGN KEY (room_id)       REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_rm_user       FOREIGN KEY (user_pid)      REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_rm_invited_by FOREIGN KEY (invited_by_pid) REFERENCES users(user_pid) ON DELETE SET NULL,

  -- 활성 멤버·내 방 조회 최적화
  INDEX idx_rm_room_active (room_id, left_at),
  INDEX idx_rm_user_active (user_pid, left_at)
);


/* =========================
 * 6) 1:1→그룹 전환 제안(양 당사자 동시 승인)
 * ========================= */
CREATE TABLE group_creation_proposals (
  proposal_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  private_room_id BIGINT NOT NULL,         -- 반드시 room_type='PRIVATE'인 방
  invitee1_pid    BIGINT NOT NULL,         -- user1이 초대한 팔로우
  invitee2_pid    BIGINT NOT NULL,         -- user2가 초대한 팔로우
  user1_approve   TINYINT(1) DEFAULT 0 CHECK (user1_approve IN (0,1)),
  user2_approve   TINYINT(1) DEFAULT 0 CHECK (user2_approve IN (0,1)),
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_gcp_private FOREIGN KEY (private_room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv1    FOREIGN KEY (invitee1_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv2    FOREIGN KEY (invitee2_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,

  INDEX idx_gcp_status (status, created_at)
);


/* =========================
 * 7) 그룹 초대(모든 활성 멤버 동의 필요)
 * ========================= */
CREATE TABLE group_room_invites (
  invite_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id        BIGINT NOT NULL,       -- 반드시 room_type='GROUP'
  inviter_pid    BIGINT NOT NULL,
  invitee_pid    BIGINT NOT NULL,
  status         VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_gri_room    FOREIGN KEY (room_id)     REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gri_inviter FOREIGN KEY (inviter_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gri_invitee FOREIGN KEY (invitee_pid) REFERENCES users(user_pid) ON DELETE CASCADE,

  INDEX idx_gri_room_status (room_id, status, created_at)
);

CREATE TABLE group_room_invite_approvals (
  invite_id      BIGINT NOT NULL,
  approver_pid   BIGINT NOT NULL,      -- 당시 활성 멤버(left_at is null)
  approved       TINYINT(1) DEFAULT 0 CHECK (approved IN (0,1)),
  approved_at    TIMESTAMP NULL,

  PRIMARY KEY (invite_id, approver_pid),

  CONSTRAINT fk_gria_invite   FOREIGN KEY (invite_id)   REFERENCES group_room_invites(invite_id) ON DELETE CASCADE,
  CONSTRAINT fk_gria_approver FOREIGN KEY (approver_pid) REFERENCES users(user_pid) ON DELETE CASCADE
);


/* =========================
 * 8) 메시지(텍스트/이미지/파일 공통)
 * ========================= */
CREATE TABLE room_messages (
  message_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id        BIGINT NOT NULL,
  sender_pid     BIGINT NULL,  -- FK 정책과 일치(사용자 삭제 시 NULL로 유지)
  content_type   VARCHAR(10) NOT NULL CHECK (content_type IN ('TEXT','IMAGE','FILE','SYSTEM')),
  text_content   TEXT,
  file_name      VARCHAR(300),
  file_path      VARCHAR(500),
  mime_type      VARCHAR(100),
  size_bytes     BIGINT,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_msg_room FOREIGN KEY (room_id)    REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_user FOREIGN KEY (sender_pid) REFERENCES users(user_pid) ON DELETE SET NULL,

  -- 간단한 내용 존재성 체크
  CONSTRAINT ck_msg_payload_min CHECK (
    (content_type = 'TEXT'  AND text_content IS NOT NULL)
    OR (content_type IN ('FILE','IMAGE') AND file_path IS NOT NULL)
    OR (content_type = 'SYSTEM')
  ),

  — 타임라인 조회 인덱스
  INDEX idx_msg_room_time (room_id, created_at)
);
