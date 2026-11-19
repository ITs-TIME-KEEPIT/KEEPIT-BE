-- 업로드 이미지 (옷 등록 전 단계)
--  - 업로드 시 미연결(linked_at NULL)로 저장하고, 옷 등록 시 linked_at을 채워 연결한다
--  - image_key는 스토리지 객체 키 (예: clothes/{UUID}.jpg)

CREATE TABLE images (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    uploader_id        BIGINT       NOT NULL,
    image_key          VARCHAR(255) NOT NULL,
    server_received_at DATETIME(6)  NOT NULL,
    device_taken_at    DATETIME(6)  NULL,
    linked_at          DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT pk_images PRIMARY KEY (id),
    CONSTRAINT uk_images_image_key UNIQUE (image_key),
    CONSTRAINT fk_images_uploader_id FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
