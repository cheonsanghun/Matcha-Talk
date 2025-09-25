
/* =========================================================
 * 1) 회원/인증/잠금/소셜
 * ========================================================= */
CREATE TABLE users (
  user_pid           BIGINT AUTO_INCREMENT PRIMARY KEY,
  login_id           VARCHAR(30)  NOT NULL UNIQUE,
  password_hash      VARCHAR(255) NOT NULL,
  nick_name          VARCHAR(30)  NOT NULL,
  email              VARCHAR(100) NOT NULL,
  country_code       CHAR(2)      NOT NULL,
  gender             CHAR(1)      NOT NULL CHECK (gender IN ('M','F')),
  birth_date         DATE         NOT NULL CHECK (birth_date >= DATE '1900-01-01'),
  email_verified     TINYINT(1)   DEFAULT 0 CHECK (email_verified IN (0,1)),
  language_code      VARCHAR(8)   NULL,
  failed_login_count INT          DEFAULT 0 CHECK (failed_login_count >= 0),
  locked_until       DATETIME     NULL,
  enabled            TINYINT(1)   DEFAULT 1 CHECK (enabled IN (0,1)),
  rolename           VARCHAR(30)  DEFAULT 'ROLE_USER'
                      CHECK (rolename IN ('ROLE_USER','ROLE_ADMIN')),
  created_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_verifications (
  token_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  email       VARCHAR(100) NOT NULL,
  token       VARCHAR(100) NOT NULL,
  purpose     VARCHAR(20)  NOT NULL CHECK (purpose IN ('VERIFY_EMAIL','FIND_ID','RESET_PW')),
  expires_at  DATETIME     NOT NULL,
  used_at     DATETIME     NULL,
  created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_ev_token UNIQUE (token),
  INDEX idx_ev_user_purpose (email, purpose, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE oauth_accounts (
  oauth_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid     BIGINT NOT NULL,
  provider     VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE')),
  provider_uid VARCHAR(100) NOT NULL,
  linked_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_oa_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_oa_provider_uid UNIQUE (provider, provider_uid),
  INDEX idx_oa_user (user_pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 2) 방/멤버/그룹 전환·초대 (선행 생성: match_requests 등에서 참조)
 * ========================================================= */
CREATE TABLE rooms (
  room_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_type      VARCHAR(10) NOT NULL CHECK (room_type IN ('RANDOM','PRIVATE','GROUP')),
  capacity       TINYINT NOT NULL DEFAULT 2,
  created_from_room_id BIGINT NULL,
  promoted_at    TIMESTAMP NULL,
  promoted_reason VARCHAR(30) NULL,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  closed_at      TIMESTAMP NULL,
  CONSTRAINT fk_room_from_room FOREIGN KEY (created_from_room_id)
    REFERENCES rooms(room_id) ON DELETE SET NULL,
  INDEX idx_rooms_type (room_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  INDEX idx_rm_room_active (room_id, left_at),
  INDEX idx_rm_user_active (user_pid, left_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_creation_proposals (
  proposal_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  private_room_id BIGINT NOT NULL,
  invitee1_pid    BIGINT NOT NULL,
  invitee2_pid    BIGINT NOT NULL,
  user1_approve   TINYINT(1) DEFAULT 0 CHECK (user1_approve IN (0,1)),
  user2_approve   TINYINT(1) DEFAULT 0 CHECK (user2_approve IN (0,1)),
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_gcp_private FOREIGN KEY (private_room_id) REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv1    FOREIGN KEY (invitee1_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gcp_inv2    FOREIGN KEY (invitee2_pid)    REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_gcp_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_room_invites (
  invite_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id       BIGINT NOT NULL,
  inviter_pid   BIGINT NOT NULL,
  invitee_pid   BIGINT NOT NULL,
  status        VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_gri_room    FOREIGN KEY (room_id)     REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_gri_inviter FOREIGN KEY (inviter_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_gri_invitee FOREIGN KEY (invitee_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_gri_room_status (room_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_room_invite_approvals (
  invite_id     BIGINT NOT NULL,
  approver_pid  BIGINT NOT NULL,
  approved      TINYINT(1) DEFAULT 0 CHECK (approved IN (0,1)),
  approved_at   TIMESTAMP NULL,
  PRIMARY KEY (invite_id, approver_pid),
  CONSTRAINT fk_gria_invite   FOREIGN KEY (invite_id)   REFERENCES group_room_invites(invite_id) ON DELETE CASCADE,
  CONSTRAINT fk_gria_approver FOREIGN KEY (approver_pid) REFERENCES users(user_pid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 3) 팔로우/팔로우 캐시
 * ========================================================= */
CREATE TABLE follows (
  follow        BIGINT AUTO_INCREMENT PRIMARY KEY,
  follower_id   BIGINT NOT NULL,
  followee_id   BIGINT NOT NULL,
  status        VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_follows_pair UNIQUE (follower_id, followee_id),
  CONSTRAINT ck_follows_no_self CHECK (follower_id <> followee_id),
  INDEX idx_follows_follower (follower_id, status, created_at),
  INDEX idx_follows_followee (followee_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE follow_list (
  list_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  follow_id   BIGINT NOT NULL,
  owner_pid   BIGINT NOT NULL,
  target_pid  BIGINT NOT NULL,
  direction   VARCHAR(10) NOT NULL CHECK (direction IN ('FOLLOWING','FOLLOWER')),
  status      VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_fl_follow  FOREIGN KEY (follow_id)  REFERENCES follows(follow) ON DELETE CASCADE,
  CONSTRAINT fk_fl_owner   FOREIGN KEY (owner_pid)  REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_fl_target  FOREIGN KEY (target_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT uq_fl_owner_target_dir UNIQUE (owner_pid, target_pid, direction),
  INDEX idx_follow_list_owner (owner_pid, status, direction),
  INDEX idx_follow_list_target (target_pid),
  INDEX idx_fl_follow_id (follow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 4) 랜덤 매칭(대기큐/핸드셰이크)
 *  - rooms를 선행 생성했으므로 FK OK
 * ========================================================= */
CREATE TABLE match_requests (
  request_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid       BIGINT NOT NULL,
  choice_gender  CHAR(1) NOT NULL CHECK (choice_gender IN ('M','F','A')),
  min_age        INT NOT NULL,
  max_age        INT NOT NULL,
  region_code    VARCHAR(10) NOT NULL,
  interests_json JSON NOT NULL,
  room_id        BIGINT NULL,
  status         VARCHAR(10) NOT NULL DEFAULT 'WAITING'
                  CHECK (status IN ('WAITING','MATCHED','CONFIRMED','DECLINED','CANCELLED')),
  requested_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mr_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT ck_mr_age_range CHECK (max_age >= min_age),
  CONSTRAINT fk_mr_room FOREIGN KEY (room_id) REFERENCES rooms(room_id) ON DELETE SET NULL,
  INDEX idx_mr_match_scan (status, choice_gender, region_code, min_age, max_age, requested_at),
  INDEX idx_mr_user (user_pid),
  INDEX idx_mr_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 5) 메시지(텍스트/이미지/파일/시스템)
 * ========================================================= */
CREATE TABLE room_messages (
  message_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id       BIGINT NOT NULL,
  sender_pid    BIGINT NULL,
  content_type  VARCHAR(10) NOT NULL CHECK (content_type IN ('TEXT','IMAGE','FILE','SYSTEM')),
  text_content  TEXT,
  file_name     VARCHAR(300),
  file_path     VARCHAR(500),
  mime_type     VARCHAR(100),
  size_bytes    BIGINT,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_msg_room FOREIGN KEY (room_id)    REFERENCES rooms(room_id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_user FOREIGN KEY (sender_pid) REFERENCES users(user_pid) ON DELETE SET NULL,
  CONSTRAINT ck_msg_payload_min CHECK (
    (content_type = 'TEXT'  AND text_content IS NOT NULL)
    OR (content_type IN ('FILE','IMAGE') AND file_path IS NOT NULL)
    OR (content_type = 'SYSTEM')
  ),
  INDEX idx_msg_room_time (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 6) 프로필/공개 범위/사용 언어
 * ========================================================= */
CREATE TABLE profiles (
  user_pid       BIGINT PRIMARY KEY,
  avatar_url     VARCHAR(300) NULL,
  bio            VARCHAR(500) NULL,
  languages_json JSON NULL,
  visibility     VARCHAR(10) NOT NULL DEFAULT 'PUBLIC'
                  CHECK (visibility IN ('PUBLIC','FOLLOWERS','PRIVATE')),
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 7) 번역 단어 저장(단어장)
 * ========================================================= */
CREATE TABLE saved_words (
  word_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid        BIGINT NOT NULL,
  source_text     VARCHAR(500) NOT NULL,
  translated_text VARCHAR(500) NOT NULL,
  source_lang     CHAR(2) NOT NULL,
  target_lang     CHAR(2) NOT NULL,
  context         VARCHAR(500) NULL,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sw_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_sw_user_time (user_pid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/* =========================================================
 * 8) 문의/신고/제재(관리자)
 * ========================================================= */
CREATE TABLE user_inquiries (
  inquiry_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid     BIGINT NOT NULL,
  category     VARCHAR(30) NOT NULL,
  title        VARCHAR(200) NOT NULL,
  content      TEXT NOT NULL,
  status       VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ANSWERED','CLOSED')),
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  answered_at  TIMESTAMP NULL,
  CONSTRAINT fk_ui_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_ui_user_status (user_pid, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_reports (
  report_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  reporter_pid  BIGINT NOT NULL,
  reported_pid  BIGINT NOT NULL,
  reason        VARCHAR(100) NOT NULL,
  detail        TEXT NULL,
  status        VARCHAR(12) NOT NULL DEFAULT 'OPEN'
                 CHECK (status IN ('OPEN','REVIEWING','ACTIONED','DISMISSED')),
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ur_reporter FOREIGN KEY (reporter_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  CONSTRAINT fk_ur_reported FOREIGN KEY (reported_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_ur_status_time (status, created_at),
  INDEX idx_ur_participants (reporter_pid, reported_pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_penalties (
  penalty_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_pid    BIGINT NOT NULL,
  type        VARCHAR(20) NOT NULL CHECK (type IN ('WARN','SUSPEND','BAN')),
  reason      VARCHAR(200) NULL,
  starts_at   DATETIME NOT NULL,
  ends_at     DATETIME NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_up_user FOREIGN KEY (user_pid) REFERENCES users(user_pid) ON DELETE CASCADE,
  INDEX idx_up_user_window (user_pid, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


