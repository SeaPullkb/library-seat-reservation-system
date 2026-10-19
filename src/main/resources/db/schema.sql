-- 可重复导入；不删除或覆盖已有数据。后续结构变更必须使用显式迁移脚本。
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    class_name VARCHAR(100) NULL,
    phone VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT ck_user_role CHECK (role IN ('STUDENT', 'LIBRARIAN', 'SUPER_ADMIN')),
    CONSTRAINT ck_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reading_room (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_name VARCHAR(100) NOT NULL,
    location VARCHAR(200) NOT NULL,
    description VARCHAR(255) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_room_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    seat_no VARCHAR(20) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_room_number UNIQUE (room_id, seat_no),
    CONSTRAINT fk_seat_room FOREIGN KEY (room_id) REFERENCES reading_room (id),
    CONSTRAINT ck_seat_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS reserve_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    reserve_date DATE NOT NULL,
    time_slot TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    checkin_deadline DATETIME NOT NULL,
    checkin_at DATETIME NULL,
    checkout_at DATETIME NULL,
    released_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    cancelled_by BIGINT NULL,
    cancel_reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_reserve_seat_slot_status (seat_id, reserve_date, time_slot, status),
    INDEX idx_reserve_user_slot_status (user_id, reserve_date, time_slot, status),
    INDEX idx_reserve_status_deadline (status, checkin_deadline),
    CONSTRAINT fk_reserve_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_reserve_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    CONSTRAINT fk_reserve_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES `user` (id),
    CONSTRAINT ck_reserve_slot CHECK (time_slot IN (1, 2, 3)),
    CONSTRAINT ck_reserve_status CHECK (status IN ('PENDING', 'USING', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
