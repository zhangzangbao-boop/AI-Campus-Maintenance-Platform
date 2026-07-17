-- 删除已存在的表，按依赖顺序
-- 先禁用外键检查以避免删除顺序问题
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `repair_feedback`;
DROP TABLE IF EXISTS `repair_order_image`;
DROP TABLE IF EXISTS `repair_order_status_log`;
DROP TABLE IF EXISTS `repair_order_comment`;
DROP TABLE IF EXISTS `repair_process_record`;
DROP TABLE IF EXISTS `sys_notification`;
DROP TABLE IF EXISTS `ai_ticket_analysis`;
DROP TABLE IF EXISTS `repair_knowledge_base`;
DROP TABLE IF EXISTS `sys_system_config`;
DROP TABLE IF EXISTS `sys_audit_log`;
DROP TABLE IF EXISTS `repair_order`;
DROP TABLE IF EXISTS `repair_category`;
DROP TABLE IF EXISTS `sys_user`;

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `sys_user` (
    `user_number` VARCHAR(255) PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `enabled` BOOLEAN DEFAULT TRUE,
    `phone` VARCHAR(20),
    `created_at` DATETIME NOT NULL
);

CREATE TABLE `repair_category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `category_key` VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE `repair_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_number` VARCHAR(255) NOT NULL,
    `category_key` VARCHAR(50),
    `repairman_id` VARCHAR(255),
    `status` VARCHAR(30) NOT NULL,
    `location` VARCHAR(100) NOT NULL,
    `description` TEXT,
    `rejection_reason` TEXT,
    `priority` VARCHAR(10) DEFAULT 'medium',
    `repair_notes` TEXT,
    `process_notes` TEXT,
    `estimated_completion_time` DATETIME,
    `created_at` DATETIME NOT NULL,
    `sentiment` VARCHAR(20),
    `sentiment_score` DOUBLE,
    `sentiment_keywords` TEXT,
    `sentiment_summary` TEXT,
    `sentiment_analyzed_at` DATETIME,
    `assigned_at` DATETIME,
    `completed_at` DATETIME,
    `student_confirmed_at` DATETIME,
    `student_rejection_reason` TEXT,
    `closed_at` DATETIME,
    `is_deleted` BOOLEAN DEFAULT FALSE,
    `deleted_at` DATETIME,
    `version` BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (`student_number`) REFERENCES `sys_user`(`user_number`),
    FOREIGN KEY (`category_key`) REFERENCES `repair_category`(`category_key`),
    FOREIGN KEY (`repairman_id`) REFERENCES `sys_user`(`user_number`)
);

CREATE TABLE `repair_order_image` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`)
);

CREATE TABLE `repair_order_status_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `operate_user_id` VARCHAR(255),
    `before_status` VARCHAR(30),
    `after_status` VARCHAR(30) NOT NULL,
    `operate_time` DATETIME NOT NULL,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`),
    FOREIGN KEY (`operate_user_id`) REFERENCES `sys_user`(`user_number`)
);

CREATE TABLE `repair_feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL UNIQUE,
    `student_number` VARCHAR(255) NOT NULL,
    `repairman_id` VARCHAR(255) NOT NULL,
    `rating` INT NOT NULL CHECK (`rating` >= 1 AND `rating` <= 5),
    `comment` TEXT,
    `speed_rating` INT CHECK (`speed_rating` IS NULL OR (`speed_rating` >= 1 AND `speed_rating` <= 5)),
    `quality_rating` INT CHECK (`quality_rating` IS NULL OR (`quality_rating` >= 1 AND `quality_rating` <= 5)),
    `attitude_rating` INT CHECK (`attitude_rating` IS NULL OR (`attitude_rating` >= 1 AND `attitude_rating` <= 5)),
    `resolved` BOOLEAN DEFAULT TRUE,
    `anonymous` BOOLEAN DEFAULT FALSE,
    `created_at` DATETIME NOT NULL,
    `sentiment` VARCHAR(20),
    `sentiment_score` DOUBLE,
    `sentiment_keywords` TEXT,
    `sentiment_summary` TEXT,
    `sentiment_analyzed_at` DATETIME,
    `follow_up_status` VARCHAR(20),
    `follow_up_note` TEXT,
    `follow_up_operator_id` VARCHAR(255),
    `follow_up_updated_at` DATETIME,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`),
    FOREIGN KEY (`student_number`) REFERENCES `sys_user`(`user_number`),
    FOREIGN KEY (`repairman_id`) REFERENCES `sys_user`(`user_number`),
    FOREIGN KEY (`follow_up_operator_id`) REFERENCES `sys_user`(`user_number`)
);

CREATE TABLE `repair_order_comment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `user_number` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `image_url` VARCHAR(500),
    `comment_type` VARCHAR(30) NOT NULL,
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_number`) REFERENCES `sys_user`(`user_number`),
    INDEX `idx_repair_order_comment_order` (`repair_order_id`),
    INDEX `idx_repair_order_comment_user` (`user_number`),
    INDEX `idx_repair_order_comment_type` (`comment_type`),
    INDEX `idx_repair_order_comment_created_at` (`created_at`)
);

CREATE TABLE `repair_process_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `staff_id` VARCHAR(255) NOT NULL,
    `action_type` VARCHAR(30) NOT NULL,
    `content` TEXT NOT NULL,
    `image_url` VARCHAR(500),
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`staff_id`) REFERENCES `sys_user`(`user_number`),
    INDEX `idx_repair_process_order` (`repair_order_id`),
    INDEX `idx_repair_process_staff` (`staff_id`),
    INDEX `idx_repair_process_action` (`action_type`),
    INDEX `idx_repair_process_created_at` (`created_at`)
);

CREATE TABLE `sys_notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `receiver_id` VARCHAR(255) NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `content` TEXT NOT NULL,
    `related_order_id` BIGINT,
    `read_flag` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`receiver_id`) REFERENCES `sys_user`(`user_number`) ON DELETE CASCADE,
    FOREIGN KEY (`related_order_id`) REFERENCES `repair_order`(`id`) ON DELETE CASCADE,
    INDEX `idx_notification_receiver` (`receiver_id`),
    INDEX `idx_notification_read` (`read_flag`),
    INDEX `idx_notification_created_at` (`created_at`)
);

CREATE TABLE `sys_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `actor_id` VARCHAR(255),
    `action` VARCHAR(80) NOT NULL,
    `module` VARCHAR(80) NOT NULL,
    `target_type` VARCHAR(80),
    `target_id` VARCHAR(120),
    `detail` TEXT,
    `request_method` VARCHAR(20),
    `request_path` VARCHAR(500),
    `success` BOOLEAN NOT NULL DEFAULT TRUE,
    `ip_address` VARCHAR(80),
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`actor_id`) REFERENCES `sys_user`(`user_number`) ON DELETE SET NULL,
    INDEX `idx_audit_actor` (`actor_id`),
    INDEX `idx_audit_module` (`module`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_created_at` (`created_at`)
);

CREATE TABLE `sys_system_config` (
    `config_key` VARCHAR(100) PRIMARY KEY,
    `config_value` TEXT,
    `description` VARCHAR(500),
    `updated_by` VARCHAR(255),
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    FOREIGN KEY (`updated_by`) REFERENCES `sys_user`(`user_number`) ON DELETE SET NULL
);

CREATE TABLE `repair_knowledge_base` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `category_key` VARCHAR(50),
    `title` VARCHAR(200) NOT NULL,
    `symptom_keywords` TEXT,
    `solution_steps` TEXT,
    `safety_notes` TEXT,
    `estimated_minutes` INT,
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    FOREIGN KEY (`category_key`) REFERENCES `repair_category`(`category_key`) ON DELETE SET NULL,
    INDEX `idx_knowledge_category` (`category_key`),
    INDEX `idx_knowledge_enabled` (`enabled`)
);

CREATE TABLE `ai_ticket_analysis` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `source_text` TEXT,
    `title` VARCHAR(200),
    `category_key` VARCHAR(50),
    `location_text` VARCHAR(200),
    `priority` VARCHAR(20),
    `summary` TEXT,
    `safety_tips` TEXT,
    `provider` VARCHAR(80),
    `model` VARCHAR(120),
    `raw_response` TEXT,
    `created_at` DATETIME NOT NULL,
    INDEX `idx_ai_analysis_category` (`category_key`),
    INDEX `idx_ai_analysis_created_at` (`created_at`)
);

-- 插入初始数据（如果已存在则忽略，避免重复插入错误）
INSERT IGNORE INTO `repair_category` (`category_key`) VALUES 
('水电维修'),
('网络故障'),
('家具维修'),
('电器故障'),
('公共设施');

-- 插入初始用户数据（如果已存在则忽略，避免重复插入错误）
INSERT IGNORE INTO `sys_user` (`user_number`, `name`, `password`, `role`, `enabled`, `phone`, `created_at`) VALUES
('admin', '管理员', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'ADMIN', TRUE, '13800000000', NOW()),
('worker001', '维修员张三', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000001', NOW()),
('worker002', '维修员李四', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000002', NOW()),
('20260001', '学生王五', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000003', NOW()),
('20260002', '学生赵六', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000004', NOW());

-- 如果需要重置所有测试用户的密码为123456，可以取消下面语句的注释
-- UPDATE `sys_user` SET `password` = '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2';

INSERT IGNORE INTO `sys_system_config`
    (`config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`)
VALUES
    ('ai.enabled', 'false', '是否启用外部大模型。未启用时系统使用本地规则引擎。', 'admin', NOW(), NOW()),
    ('ai.provider', 'deepseek', '大模型供应商配置。', 'admin', NOW(), NOW()),
    ('ai.model', 'deepseek-v4-flash', '默认大模型名称。', 'admin', NOW(), NOW()),
    ('ai.base-url', 'https://api.deepseek.com', 'DeepSeek OpenAI-Compatible API Base URL。', 'admin', NOW(), NOW()),
    ('ai.api-key', '', 'DeepSeek API Key。建议只在本地后台配置。', 'admin', NOW(), NOW()),
    ('ai.timeout-seconds', '20', '大模型接口超时时间，单位秒。', 'admin', NOW(), NOW()),
    ('ai.thinking.enabled', 'false', '是否启用模型思考模式。', 'admin', NOW(), NOW()),
    ('upload.max-image-count', '5', '单个工单最多上传图片数量。', 'admin', NOW(), NOW()),
    ('upload.max-image-size-mb', '5', '单张图片大小上限，单位 MB。', 'admin', NOW(), NOW()),
    ('backup.auto-enabled', 'false', '是否启用定时数据库备份。', 'admin', NOW(), NOW()),
    ('backup.cron', '0 30 2 * * ?', '定时备份 Cron 表达式，默认每天凌晨 2:30。', 'admin', NOW(), NOW()),
    ('backup.retention-days', '30', '备份文件保留天数。', 'admin', NOW(), NOW()),
    ('sla.high.responseHours', '2', '高优先级受理时限。', 'admin', NOW(), NOW()),
    ('sla.high.completionHours', '24', '高优先级完成时限。', 'admin', NOW(), NOW()),
    ('sla.medium.responseHours', '8', '中优先级受理时限。', 'admin', NOW(), NOW()),
    ('sla.medium.completionHours', '72', '中优先级完成时限。', 'admin', NOW(), NOW()),
    ('sla.low.responseHours', '24', '低优先级受理时限。', 'admin', NOW(), NOW()),
    ('sla.low.completionHours', '168', '低优先级完成时限。', 'admin', NOW(), NOW());

INSERT IGNORE INTO `repair_knowledge_base`
    (`category_key`, `title`, `symptom_keywords`, `solution_steps`, `safety_notes`, `estimated_minutes`, `enabled`, `created_at`, `updated_at`)
VALUES
    ('水电维修', '宿舍水管漏水应急处理', '漏水,滴水,水管,水龙头,积水', '关闭阀门，检查接口和阀芯，更换损坏配件，完成通水测试。', '地面有积水时先做防滑提示，疑似水电混合时必须断电后处理。', 45, TRUE, NOW(), NOW()),
    ('网络故障', '宿舍网口接触不良处理', '网络,网口,断线,无法连接,水晶头', '检测端口，重新压接水晶头或更换网线，重启 AP/端口并现场测试。', '不要随意拔插弱电间核心设备，必要时联系网络中心确认。', 40, TRUE, NOW(), NOW()),
    ('电器故障', '照明灯频闪与镇流器更换', '照明,灯,频闪,镇流器,灯管', '断电验电，检查灯管和镇流器，更换匹配配件并通电测试。', '必须断电操作，登高作业需要扶梯稳定。', 60, TRUE, NOW(), NOW());

-- ==========================================================
-- 视图定义：用于简化统计与报表查询
-- ==========================================================

-- 1. 工单综合视图：包含工单 + 学生 + 维修工 + 类别 + 评价
CREATE OR REPLACE VIEW `vw_ticket_summary` AS
SELECT
    o.id                       AS ticket_id,
    o.`status`                 AS `status`,
    o.priority                 AS priority,
    o.location                 AS location_text,
    o.description              AS description,
    o.estimated_completion_time,
    o.created_at,
    o.assigned_at,
    o.completed_at,
    o.closed_at,

    -- 学生信息
    o.student_number           AS student_id,
    stu.name                   AS student_name,
    stu.phone                  AS student_phone,

    -- 维修工信息
    o.repairman_id             AS repairman_id,
    w.name                     AS repairman_name,
    w.phone                    AS repairman_phone,

    -- 类别信息
    o.category_key             AS category_key,
    c.category_key             AS category_name,

    -- 评价信息（可能为空）
    f.rating                   AS rating_score,
    f.comment                  AS rating_comment,
    f.created_at               AS rating_time
FROM repair_order o
LEFT JOIN sys_user stu
       ON o.student_number = stu.user_number
LEFT JOIN sys_user w
       ON o.repairman_id = w.user_number
LEFT JOIN repair_category c
       ON o.category_key = c.category_key
LEFT JOIN repair_feedback f
       ON f.repair_order_id = o.id;

-- 统计相关：使用物化汇总表 + 触发器维护（替代视图实时聚合）

-- 1. 维修人员统计汇总表
CREATE TABLE IF NOT EXISTS repairman_stats (
    repairman_id       VARCHAR(50) PRIMARY KEY,
    total_tickets      INT NOT NULL DEFAULT 0,
    completed_tickets  INT NOT NULL DEFAULT 0,
    rated_tickets      INT NOT NULL DEFAULT 0,
    avg_rating         DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    last_completed_at  DATETIME NULL
);

-- 2. 分类统计汇总表
CREATE TABLE IF NOT EXISTS category_stats (
    category_key       VARCHAR(50) PRIMARY KEY,
    total_tickets      INT NOT NULL DEFAULT 0,
    completed_tickets  INT NOT NULL DEFAULT 0,
    rated_tickets      INT NOT NULL DEFAULT 0,
    avg_rating         DECIMAL(4,2) NOT NULL DEFAULT 0.00
);

DELIMITER $$

-- 新工单插入后，更新分类统计 & （如果有）维修人员统计
CREATE TRIGGER trg_repair_order_after_insert
AFTER INSERT ON repair_order
FOR EACH ROW
BEGIN
    -- 分类总工单 +1
    IF NEW.category_key IS NOT NULL THEN
        INSERT INTO category_stats (category_key, total_tickets, completed_tickets, rated_tickets, avg_rating)
        VALUES (NEW.category_key, 1, 0, 0, 0.00)
        ON DUPLICATE KEY UPDATE total_tickets = total_tickets + 1;
    END IF;

    -- 如果创建时已经指定维修工，则该维修工总工单 +1
    IF NEW.repairman_id IS NOT NULL THEN
        INSERT INTO repairman_stats (repairman_id, total_tickets, completed_tickets, rated_tickets, avg_rating, last_completed_at)
        VALUES (NEW.repairman_id, 1, 0, 0, 0.00, NULL)
        ON DUPLICATE KEY UPDATE total_tickets = total_tickets + 1;
    END IF;
END$$

-- 工单分配/修改维修工后，调整维修人员总工单
CREATE TRIGGER trg_repair_order_after_update_repairman
AFTER UPDATE ON repair_order
FOR EACH ROW
BEGIN
    IF NEW.repairman_id IS NOT NULL AND NEW.repairman_id <> OLD.repairman_id THEN
        -- 新维修工总工单 +1
        INSERT INTO repairman_stats (repairman_id, total_tickets, completed_tickets, rated_tickets, avg_rating, last_completed_at)
        VALUES (NEW.repairman_id, 1, 0, 0, 0.00, NULL)
        ON DUPLICATE KEY UPDATE total_tickets = total_tickets + 1;

        -- 旧维修工总工单 -1（防止出现负数）
        IF OLD.repairman_id IS NOT NULL THEN
            UPDATE repairman_stats
            SET total_tickets = GREATEST(total_tickets - 1, 0)
            WHERE repairman_id = OLD.repairman_id;
        END IF;
    END IF;
END$$

-- 工单状态从"未完成"进入"已完成相关状态"时，更新完成统计
CREATE TRIGGER trg_repair_order_after_update_status
AFTER UPDATE ON repair_order
FOR EACH ROW
BEGIN
    DECLARE old_done TINYINT DEFAULT 0;
    DECLARE new_done TINYINT DEFAULT 0;

    IF OLD.status IN ('RESOLVED','WAITING_FEEDBACK','FEEDBACKED','CLOSED') THEN
        SET old_done = 1;
    END IF;

    IF NEW.status IN ('RESOLVED','WAITING_FEEDBACK','FEEDBACKED','CLOSED') THEN
        SET new_done = 1;
    END IF;

    IF old_done = 0 AND new_done = 1 THEN
        -- 分类完成工单 +1
        IF NEW.category_key IS NOT NULL THEN
            INSERT INTO category_stats (category_key, total_tickets, completed_tickets, rated_tickets, avg_rating)
            VALUES (NEW.category_key, 0, 1, 0, 0.00)
            ON DUPLICATE KEY UPDATE completed_tickets = completed_tickets + 1;
        END IF;

        -- 维修工完成工单 +1，并更新最近完成时间
        IF NEW.repairman_id IS NOT NULL THEN
            INSERT INTO repairman_stats (repairman_id, total_tickets, completed_tickets, rated_tickets, avg_rating, last_completed_at)
            VALUES (NEW.repairman_id, 0, 1, 0, 0.00, NEW.completed_at)
            ON DUPLICATE KEY UPDATE
                completed_tickets = completed_tickets + 1,
                last_completed_at = GREATEST(IFNULL(last_completed_at, NEW.completed_at), NEW.completed_at);
        END IF;
    END IF;
END$$

-- 新增评价后，更新维修人员和分类的评价数量与平均分
CREATE TRIGGER trg_repair_feedback_after_insert
AFTER INSERT ON repair_feedback
FOR EACH ROW
BEGIN
    DECLARE v_repairman_id VARCHAR(50);
    DECLARE v_category_key VARCHAR(50);
    DECLARE v_old_avg DECIMAL(4,2);
    DECLARE v_old_count INT;

    SELECT o.repairman_id, o.category_key
    INTO v_repairman_id, v_category_key
    FROM repair_order o
    WHERE o.id = NEW.repair_order_id
    LIMIT 1;

    -- 更新维修工评价统计
    IF v_repairman_id IS NOT NULL THEN
        SELECT avg_rating, rated_tickets
        INTO v_old_avg, v_old_count
        FROM repairman_stats
        WHERE repairman_id = v_repairman_id
        FOR UPDATE;

        SET v_old_avg = IFNULL(v_old_avg, 0);
        SET v_old_count = IFNULL(v_old_count, 0);

        UPDATE repairman_stats
        SET
            rated_tickets = v_old_count + 1,
            avg_rating = (v_old_avg * v_old_count + NEW.rating) / (v_old_count + 1)
        WHERE repairman_id = v_repairman_id;
    END IF;

    -- 更新分类评价统计
    IF v_category_key IS NOT NULL THEN
        SELECT avg_rating, rated_tickets
        INTO v_old_avg, v_old_count
        FROM category_stats
        WHERE category_key = v_category_key
        FOR UPDATE;

        SET v_old_avg = IFNULL(v_old_avg, 0);
        SET v_old_count = IFNULL(v_old_count, 0);

        UPDATE category_stats
        SET
            rated_tickets = v_old_count + 1,
            avg_rating = (v_old_avg * v_old_count + NEW.rating) / (v_old_count + 1)
        WHERE category_key = v_category_key;
    END IF;
END$$

DELIMITER ;

-- ============================================
-- 并发控制：为现有数据库添加 version 字段（乐观锁）
-- ============================================

-- 检查 version 字段是否存在，不存在则添加
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'repair_order')
    AND (COLUMN_NAME = 'version')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `repair_order` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 软删除功能迁移脚本（为现有数据库添加 is_deleted 和 deleted_at 字段）
-- ============================================

-- 检查 is_deleted 字段是否存在，不存在则添加
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'repair_order')
    AND (COLUMN_NAME = 'is_deleted')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `repair_order` ADD COLUMN `is_deleted` BOOLEAN DEFAULT FALSE')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查 deleted_at 字段是否存在，不存在则添加
SET @columnExists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE
    (TABLE_SCHEMA = DATABASE())
    AND (TABLE_NAME = 'repair_order')
    AND (COLUMN_NAME = 'deleted_at')
);

SET @sql = IF(@columnExists > 0,
  'SELECT 1 AS message',
  CONCAT('ALTER TABLE `repair_order` ADD COLUMN `deleted_at` DATETIME')
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
