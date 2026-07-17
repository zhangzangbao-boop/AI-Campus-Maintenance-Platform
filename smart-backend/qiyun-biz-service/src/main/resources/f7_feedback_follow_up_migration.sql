ALTER TABLE `repair_feedback`
    ADD COLUMN `follow_up_status` VARCHAR(20) NULL,
    ADD COLUMN `follow_up_note` TEXT NULL,
    ADD COLUMN `follow_up_operator_id` VARCHAR(255) NULL,
    ADD COLUMN `follow_up_updated_at` DATETIME NULL,
    ADD INDEX `idx_repair_feedback_follow_up_status` (`follow_up_status`),
    ADD CONSTRAINT `fk_repair_feedback_follow_up_operator`
        FOREIGN KEY (`follow_up_operator_id`) REFERENCES `sys_user` (`user_number`);

UPDATE `repair_feedback`
SET `follow_up_status` = 'PENDING',
    `follow_up_updated_at` = COALESCE(`sentiment_analyzed_at`, `created_at`)
WHERE `follow_up_status` IS NULL
  AND (`rating` <= 2 OR UPPER(COALESCE(`sentiment`, '')) = 'NEGATIVE');
