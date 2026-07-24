SET @db_name = CONVERT(DATABASE() USING utf8mb3) COLLATE utf8_tolower_ci;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'sentiment'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `sentiment` VARCHAR(20) NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'sentiment_score'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `sentiment_score` DOUBLE NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'sentiment_keywords'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `sentiment_keywords` TEXT NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'sentiment_summary'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `sentiment_summary` TEXT NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'sentiment_analyzed_at'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `sentiment_analyzed_at` DATETIME NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'follow_up_status'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `follow_up_status` VARCHAR(20) NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'follow_up_note'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `follow_up_note` TEXT NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'follow_up_operator_id'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `follow_up_operator_id` VARCHAR(255) NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND COLUMN_NAME = 'follow_up_updated_at'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD COLUMN `follow_up_updated_at` DATETIME NULL'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'repair_feedback' AND INDEX_NAME = 'idx_repair_feedback_follow_up_status'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD INDEX `idx_repair_feedback_follow_up_status` (`follow_up_status`)'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT IF(
    EXISTS (
        SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @db_name AND CONSTRAINT_NAME = 'fk_repair_feedback_follow_up_operator'
    ),
    'SELECT 1',
    'ALTER TABLE `repair_feedback` ADD CONSTRAINT `fk_repair_feedback_follow_up_operator` FOREIGN KEY (`follow_up_operator_id`) REFERENCES `sys_user` (`user_number`)'
) INTO @sql;
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `repair_feedback`
SET `follow_up_status` = 'PENDING',
    `follow_up_updated_at` = COALESCE(`sentiment_analyzed_at`, `created_at`)
WHERE `follow_up_status` IS NULL
  AND (`rating` <= 2 OR UPPER(COALESCE(`sentiment`, '')) = 'NEGATIVE' OR COALESCE(`resolved`, 0) = 0);
