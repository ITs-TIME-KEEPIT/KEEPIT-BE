-- KEEPIT 초기 스키마 (ERD 기준)
--  - enum 컬럼은 VARCHAR(30)로 저장하고 JPA에서 @Enumerated(EnumType.STRING)으로 매핑
--  - 모든 테이블에 created_at / updated_at 포함 (BaseTimeEntity)
--  - payments, deposits는 PG 결제(P1) 진행 시 별도 마이그레이션으로 추가

-- =========================================================
-- user
-- =========================================================
CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    kakao_id          VARCHAR(64)  NOT NULL,
    nickname          VARCHAR(20)  NOT NULL,
    profile_image_key VARCHAR(255) NULL,
    phone             VARCHAR(20)  NULL,
    role              VARCHAR(30)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    suspend_reason    VARCHAR(255) NULL,
    suspended_at      DATETIME(6)  NULL,
    suspended_until   DATETIME(6)  NULL,
    delete_reason     VARCHAR(255) NULL,
    deleted_at        DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_kakao_id UNIQUE (kakao_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 사용자당 1개만 보관 (재로그인 시 교체), 토큰 원문 대신 SHA-256 해시(hex 64자) 저장
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_user_id UNIQUE (user_id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- region / holiday
-- =========================================================
CREATE TABLE regions (
    id         INT         NOT NULL AUTO_INCREMENT,
    sido       VARCHAR(10) NOT NULL,
    sigungu    VARCHAR(20) NOT NULL,
    is_open    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_regions PRIMARY KEY (id),
    CONSTRAINT uk_regions_sido_sigungu UNIQUE (sido, sigungu)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE holidays (
    id                   INT         NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(50) NOT NULL,
    start_date           DATE        NOT NULL,
    end_date             DATE        NOT NULL,
    delivery_cutoff_date DATE        NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    CONSTRAINT pk_holidays PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- clothing
-- =========================================================
CREATE TABLE clothes (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id           BIGINT       NOT NULL,
    region_id          INT          NOT NULL,
    title              VARCHAR(30)  NOT NULL,
    description        VARCHAR(500) NULL,
    category           VARCHAR(30)  NOT NULL,
    size               VARCHAR(30)  NOT NULL,
    total_length       DECIMAL(5, 1) NULL,
    shoulder_width     DECIMAL(5, 1) NULL,
    chest_width        DECIMAL(5, 1) NULL,
    sleeve_length      DECIMAL(5, 1) NULL,
    waist_width        DECIMAL(5, 1) NULL,
    hip_width          DECIMAL(5, 1) NULL,
    thigh_width        DECIMAL(5, 1) NULL,
    hem_width          DECIMAL(5, 1) NULL,
    has_defect         BOOLEAN      NOT NULL DEFAULT FALSE,
    defect_description TEXT         NULL,
    rental_fee         INT          NOT NULL,
    delivery_available BOOLEAN      NOT NULL DEFAULT FALSE,
    delivery_fee       INT          NULL,
    laundry_by         VARCHAR(30)  NOT NULL,
    laundry_method     VARCHAR(30)  NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    deleted_at         DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_clothes PRIMARY KEY (id),
    CONSTRAINT fk_clothes_owner_id FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_clothes_region_id FOREIGN KEY (region_id) REFERENCES regions (id),
    INDEX idx_clothes_region_status_category (region_id, status, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE clothing_colors (
    clothing_id BIGINT      NOT NULL,
    color       VARCHAR(30) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_clothing_colors PRIMARY KEY (clothing_id, color),
    CONSTRAINT fk_clothing_colors_clothing_id FOREIGN KEY (clothing_id) REFERENCES clothes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE clothing_unavailable_periods (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    clothing_id BIGINT      NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_clothing_unavailable_periods PRIMARY KEY (id),
    CONSTRAINT fk_clothing_unavailable_periods_clothing_id FOREIGN KEY (clothing_id) REFERENCES clothes (id),
    INDEX idx_clothing_unavailable_periods_clothing_start (clothing_id, start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE clothing_photos (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    clothing_id        BIGINT       NOT NULL,
    photo_part         VARCHAR(30)  NOT NULL,
    image_key          VARCHAR(255) NOT NULL,
    sort_order         INT          NOT NULL,
    server_received_at DATETIME(6)  NOT NULL,
    device_taken_at    DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_clothing_photos PRIMARY KEY (id),
    CONSTRAINT fk_clothing_photos_clothing_id FOREIGN KEY (clothing_id) REFERENCES clothes (id),
    INDEX idx_clothing_photos_clothing_sort (clothing_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- rental
-- =========================================================
CREATE TABLE rentals (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    clothing_id            BIGINT       NOT NULL,
    owner_id               BIGINT       NOT NULL,
    borrower_id            BIGINT       NOT NULL,
    start_date             DATE         NOT NULL,
    end_date               DATE         NOT NULL,
    trade_method           VARCHAR(30)  NOT NULL,
    applied_rental_fee     INT          NOT NULL,
    applied_delivery_fee   INT          NULL,
    applied_laundry_by     VARCHAR(30)  NOT NULL,
    applied_laundry_method VARCHAR(30)  NOT NULL,
    status                 VARCHAR(30)  NOT NULL,
    reject_reason          VARCHAR(30)  NULL,
    reject_reason_detail   VARCHAR(255) NULL,
    cancel_reason          VARCHAR(255) NULL,
    canceled_by            VARCHAR(30)  NULL,
    rules_agreed_at        DATETIME(6)  NOT NULL,
    requested_at           DATETIME(6)  NOT NULL,
    expires_at             DATETIME(6)  NOT NULL,
    accepted_at            DATETIME(6)  NULL,
    handed_over_at         DATETIME(6)  NULL,
    received_at            DATETIME(6)  NULL,
    returned_at            DATETIME(6)  NULL,
    completed_at           DATETIME(6)  NULL,
    phone_hidden_at        DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    CONSTRAINT pk_rentals PRIMARY KEY (id),
    CONSTRAINT fk_rentals_clothing_id FOREIGN KEY (clothing_id) REFERENCES clothes (id),
    CONSTRAINT fk_rentals_owner_id FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_rentals_borrower_id FOREIGN KEY (borrower_id) REFERENCES users (id),
    INDEX idx_rentals_clothing_status (clothing_id, status),
    INDEX idx_rentals_owner_status (owner_id, status),
    INDEX idx_rentals_borrower_status (borrower_id, status),
    INDEX idx_rentals_status_expires (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rental_photos (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id          BIGINT       NOT NULL,
    uploader_id        BIGINT       NOT NULL,
    stage              VARCHAR(30)  NOT NULL,
    photo_part         VARCHAR(30)  NOT NULL,
    image_key          VARCHAR(255) NOT NULL,
    sort_order         INT          NOT NULL,
    server_received_at DATETIME(6)  NOT NULL,
    device_taken_at    DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_rental_photos PRIMARY KEY (id),
    CONSTRAINT fk_rental_photos_rental_id FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_photos_uploader_id FOREIGN KEY (uploader_id) REFERENCES users (id),
    INDEX idx_rental_photos_rental_stage (rental_id, stage)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- from_status: 최초 생성 로그는 NULL / actor_id: actor_type = system 이면 NULL
CREATE TABLE rental_status_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id   BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL,
    to_status   VARCHAR(30)  NOT NULL,
    actor_type  VARCHAR(30)  NOT NULL,
    actor_id    BIGINT       NULL,
    reason      VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_rental_status_logs PRIMARY KEY (id),
    CONSTRAINT fk_rental_status_logs_rental_id FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_status_logs_actor_id FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- notification
-- =========================================================
-- rental_id: 대여와 무관한 알림(admin_status_changed 등)은 NULL
CREATE TABLE notifications (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    rental_id  BIGINT      NULL,
    type       VARCHAR(30) NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_rental_id FOREIGN KEY (rental_id) REFERENCES rentals (id),
    INDEX idx_notifications_user_read_created (user_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- report
-- =========================================================
CREATE TABLE reports (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    rental_id   BIGINT      NOT NULL,
    reporter_id BIGINT      NOT NULL,
    type        VARCHAR(30) NOT NULL,
    content     TEXT        NOT NULL,
    status      VARCHAR(30) NOT NULL,
    admin_note  TEXT        NULL,
    resolved_at DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_rental_id FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_reports_reporter_id FOREIGN KEY (reporter_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE damage_claims (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    report_id         BIGINT       NOT NULL,
    receipt_image_key VARCHAR(255) NULL,
    claim_amount      INT          NOT NULL,
    result            VARCHAR(255) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT pk_damage_claims PRIMARY KEY (id),
    CONSTRAINT fk_damage_claims_report_id FOREIGN KEY (report_id) REFERENCES reports (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- review
-- =========================================================
CREATE TABLE reviews (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id    BIGINT       NOT NULL,
    writer_id    BIGINT       NOT NULL,
    target_id    BIGINT       NOT NULL,
    satisfaction VARCHAR(30)  NOT NULL,
    comment      VARCHAR(255) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT uk_reviews_rental_writer UNIQUE (rental_id, writer_id),
    CONSTRAINT fk_reviews_rental_id FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_reviews_writer_id FOREIGN KEY (writer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_target_id FOREIGN KEY (target_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE review_keywords (
    review_id  BIGINT      NOT NULL,
    keyword    VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_review_keywords PRIMARY KEY (review_id, keyword),
    CONSTRAINT fk_review_keywords_review_id FOREIGN KEY (review_id) REFERENCES reviews (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
