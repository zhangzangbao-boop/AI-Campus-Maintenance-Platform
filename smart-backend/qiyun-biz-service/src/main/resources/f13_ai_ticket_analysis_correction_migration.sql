ALTER TABLE `ai_ticket_analysis`
    ADD COLUMN IF NOT EXISTS `ticket_id` BIGINT,
    ADD COLUMN IF NOT EXISTS `urgency` VARCHAR(20),
    ADD COLUMN IF NOT EXISTS `suggestion` TEXT,
    ADD COLUMN IF NOT EXISTS `keywords` TEXT,
    ADD COLUMN IF NOT EXISTS `final_category_key` VARCHAR(50),
    ADD COLUMN IF NOT EXISTS `final_urgency` VARCHAR(20),
    ADD COLUMN IF NOT EXISTS `final_suggestion` TEXT,
    ADD COLUMN IF NOT EXISTS `correction_reason` TEXT,
    ADD COLUMN IF NOT EXISTS `corrected_by` VARCHAR(255),
    ADD COLUMN IF NOT EXISTS `corrected_at` DATETIME;

CREATE TABLE IF NOT EXISTS `ai_ticket_analysis_correction` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `analysis_id` BIGINT NOT NULL,
    `ticket_id` BIGINT NOT NULL,
    `previous_category_key` VARCHAR(50),
    `previous_urgency` VARCHAR(20),
    `previous_suggestion` TEXT,
    `new_category_key` VARCHAR(50),
    `new_urgency` VARCHAR(20),
    `new_suggestion` TEXT,
    `reason` TEXT NOT NULL,
    `corrected_by` VARCHAR(255) NOT NULL,
    `corrected_at` DATETIME NOT NULL,
    CONSTRAINT `fk_ai_correction_analysis`
        FOREIGN KEY (`analysis_id`) REFERENCES `ai_ticket_analysis` (`id`) ON DELETE CASCADE,
    INDEX `idx_ai_correction_analysis` (`analysis_id`),
    INDEX `idx_ai_correction_ticket` (`ticket_id`),
    INDEX `idx_ai_correction_corrected_at` (`corrected_at`)
);
