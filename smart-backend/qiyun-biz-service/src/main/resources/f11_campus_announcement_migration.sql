CREATE TABLE IF NOT EXISTS `campus_announcement` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `type` VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    `priority` VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    `publish_time` DATETIME,
    `expire_time` DATETIME,
    `pinned` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    INDEX `idx_announcement_status_time` (`status`, `publish_time`, `expire_time`),
    INDEX `idx_announcement_pinned` (`pinned`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;