CREATE TABLE IF NOT EXISTS `fault_trend_alert` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `location` VARCHAR(200) NOT NULL,
    `category_key` VARCHAR(80) NOT NULL,
    `period_days` INT NOT NULL,
    `ticket_count` BIGINT NOT NULL,
    `previous_count` BIGINT NOT NULL,
    `growth_rate` DOUBLE NOT NULL,
    `risk_level` VARCHAR(20) NOT NULL,
    `ai_reason` TEXT,
    `suggestion` TEXT,
    `last_detected_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    UNIQUE KEY `uk_fault_trend_alert_scope` (`location`, `category_key`, `period_days`),
    INDEX `idx_fault_trend_risk` (`risk_level`),
    INDEX `idx_fault_trend_detected` (`last_detected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
