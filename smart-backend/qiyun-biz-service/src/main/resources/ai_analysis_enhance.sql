-- ============================================
-- AI 分析功能增强脚本
-- 为 ai_ticket_analysis 表添加新字段
-- ============================================

-- 添加 ticket_id 字段（关联工单ID）
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'ai_ticket_analysis')
    AND (COLUMN_NAME = 'ticket_id')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `ai_ticket_analysis` ADD COLUMN `ticket_id` BIGINT NULL AFTER `id`')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 urgency 字段（紧急程度）
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'ai_ticket_analysis')
    AND (COLUMN_NAME = 'urgency')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `ai_ticket_analysis` ADD COLUMN `urgency` VARCHAR(20) NULL AFTER `priority`')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 suggestion 字段（AI维修建议）
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'ai_ticket_analysis')
    AND (COLUMN_NAME = 'suggestion')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `ai_ticket_analysis` ADD COLUMN `suggestion` TEXT NULL AFTER `urgency`')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 keywords 字段（关键词JSON数组）
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'ai_ticket_analysis')
    AND (COLUMN_NAME = 'keywords')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `ai_ticket_analysis` ADD COLUMN `keywords` TEXT NULL AFTER `suggestion`')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为 ticket_id 添加索引（如果不存在）
SET @indexExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'ai_ticket_analysis')
    AND (INDEX_NAME = 'idx_ai_analysis_ticket_id')
);

SET @sql = IF(@indexExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `ai_ticket_analysis` ADD INDEX `idx_ai_analysis_ticket_id` (`ticket_id`)')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 完成提示
SELECT 'AI 分析表字段更新完成' AS message;