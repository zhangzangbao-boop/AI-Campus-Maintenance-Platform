-- Campus Maintenance System full database initialization script.
-- Usage in MySQL CLI:
--   SOURCE E:/Software_System_Design_and_Development_Practice/Campus-Maintenance-System/backend/src/main/resources/full_init_test_data.sql;
--
-- This script recreates the repair_system project tables and inserts rich demo data.
-- It is intended for local development/demo reset. Existing data in these tables
-- will be deleted.
--
-- Login accounts: all demo users use password 123456
--   ADMIN:   admin, admin02
--   STAFF:   worker001, worker002, worker003, worker004
--   STUDENT: 20260001, 20260002, 20260003, 20260004, 20260005, 20260006, 20260007, 20260008

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE DATABASE IF NOT EXISTS `repair_system`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `repair_system`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TRIGGER IF EXISTS `trg_repair_order_after_insert`;
DROP TRIGGER IF EXISTS `trg_repair_order_after_update_repairman`;
DROP TRIGGER IF EXISTS `trg_repair_order_after_update_status`;
DROP TRIGGER IF EXISTS `trg_repair_feedback_after_insert`;

DROP VIEW IF EXISTS `vw_ticket_summary`;

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
DROP TABLE IF EXISTS `repairman_stats`;
DROP TABLE IF EXISTS `category_stats`;
DROP TABLE IF EXISTS `sys_user`;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `sys_user` (
    `user_number` VARCHAR(255) PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `phone` VARCHAR(20),
    `avatar_url` VARCHAR(500),
    `created_at` DATETIME NOT NULL,
    `sentiment` VARCHAR(20),
    `sentiment_score` DOUBLE,
    `sentiment_keywords` TEXT,
    `sentiment_summary` TEXT,
    `sentiment_analyzed_at` DATETIME,
    INDEX `idx_sys_user_role` (`role`),
    INDEX `idx_sys_user_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repair_category` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `category_key` VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    `assigned_at` DATETIME,
    `completed_at` DATETIME,
    `closed_at` DATETIME,
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE,
    `deleted_at` DATETIME,
    `version` BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT `fk_repair_order_student`
        FOREIGN KEY (`student_number`) REFERENCES `sys_user` (`user_number`),
    CONSTRAINT `fk_repair_order_category`
        FOREIGN KEY (`category_key`) REFERENCES `repair_category` (`category_key`),
    CONSTRAINT `fk_repair_order_repairman`
        FOREIGN KEY (`repairman_id`) REFERENCES `sys_user` (`user_number`),
    INDEX `idx_repair_order_student` (`student_number`),
    INDEX `idx_repair_order_staff` (`repairman_id`),
    INDEX `idx_repair_order_status` (`status`),
    INDEX `idx_repair_order_category` (`category_key`),
    INDEX `idx_repair_order_created_at` (`created_at`),
    INDEX `idx_repair_order_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repair_order_image` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_repair_order_image_order`
        FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    INDEX `idx_repair_order_image_order` (`repair_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repair_order_status_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `operate_user_id` VARCHAR(255),
    `before_status` VARCHAR(30),
    `after_status` VARCHAR(30) NOT NULL,
    `operate_time` DATETIME NOT NULL,
    CONSTRAINT `fk_status_log_order`
        FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_status_log_operator`
        FOREIGN KEY (`operate_user_id`) REFERENCES `sys_user` (`user_number`),
    INDEX `idx_status_log_order` (`repair_order_id`),
    INDEX `idx_status_log_operator` (`operate_user_id`),
    INDEX `idx_status_log_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    CONSTRAINT `fk_repair_feedback_order`
        FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_repair_feedback_student`
        FOREIGN KEY (`student_number`) REFERENCES `sys_user` (`user_number`),
    CONSTRAINT `fk_repair_feedback_repairman`
        FOREIGN KEY (`repairman_id`) REFERENCES `sys_user` (`user_number`),
    CONSTRAINT `fk_repair_feedback_follow_up_operator`
        FOREIGN KEY (`follow_up_operator_id`) REFERENCES `sys_user` (`user_number`),
    INDEX `idx_repair_feedback_student` (`student_number`),
    INDEX `idx_repair_feedback_staff` (`repairman_id`),
    INDEX `idx_repair_feedback_created_at` (`created_at`),
    INDEX `idx_repair_feedback_follow_up_status` (`follow_up_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repair_order_comment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `user_number` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `image_url` VARCHAR(500),
    `comment_type` VARCHAR(30) NOT NULL,
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_repair_order_comment_order`
        FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_repair_order_comment_user`
        FOREIGN KEY (`user_number`) REFERENCES `sys_user` (`user_number`),
    INDEX `idx_repair_order_comment_order` (`repair_order_id`),
    INDEX `idx_repair_order_comment_user` (`user_number`),
    INDEX `idx_repair_order_comment_type` (`comment_type`),
    INDEX `idx_repair_order_comment_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repair_process_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL,
    `staff_id` VARCHAR(255) NOT NULL,
    `action_type` VARCHAR(30) NOT NULL,
    `content` TEXT NOT NULL,
    `image_url` VARCHAR(500),
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_repair_process_order`
        FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_repair_process_staff`
        FOREIGN KEY (`staff_id`) REFERENCES `sys_user` (`user_number`),
    INDEX `idx_repair_process_order` (`repair_order_id`),
    INDEX `idx_repair_process_staff` (`staff_id`),
    INDEX `idx_repair_process_action` (`action_type`),
    INDEX `idx_repair_process_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `receiver_id` VARCHAR(255) NOT NULL,
    `title` VARCHAR(100) NOT NULL,
    `content` TEXT NOT NULL,
    `related_order_id` BIGINT,
    `read_flag` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_sys_notification_receiver`
        FOREIGN KEY (`receiver_id`) REFERENCES `sys_user` (`user_number`) ON DELETE CASCADE,
    CONSTRAINT `fk_sys_notification_order`
        FOREIGN KEY (`related_order_id`) REFERENCES `repair_order` (`id`) ON DELETE CASCADE,
    INDEX `idx_notification_receiver` (`receiver_id`),
    INDEX `idx_notification_read` (`read_flag`),
    INDEX `idx_notification_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    CONSTRAINT `fk_audit_log_actor`
        FOREIGN KEY (`actor_id`) REFERENCES `sys_user` (`user_number`) ON DELETE SET NULL,
    INDEX `idx_audit_actor` (`actor_id`),
    INDEX `idx_audit_module` (`module`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_system_config` (
    `config_key` VARCHAR(100) PRIMARY KEY,
    `config_value` TEXT,
    `description` VARCHAR(500),
    `updated_by` VARCHAR(255),
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    CONSTRAINT `fk_system_config_updated_by`
        FOREIGN KEY (`updated_by`) REFERENCES `sys_user` (`user_number`) ON DELETE SET NULL,
    INDEX `idx_system_config_updated_by` (`updated_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    CONSTRAINT `fk_knowledge_category`
        FOREIGN KEY (`category_key`) REFERENCES `repair_category` (`category_key`) ON DELETE SET NULL,
    INDEX `idx_knowledge_category` (`category_key`),
    INDEX `idx_knowledge_enabled` (`enabled`),
    FULLTEXT INDEX `ft_knowledge_search` (`title`, `symptom_keywords`, `solution_steps`, `safety_notes`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `repairman_stats` (
    `repairman_id` VARCHAR(255) PRIMARY KEY,
    `total_tickets` INT NOT NULL DEFAULT 0,
    `completed_tickets` INT NOT NULL DEFAULT 0,
    `rated_tickets` INT NOT NULL DEFAULT 0,
    `avg_rating` DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    `last_completed_at` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `category_stats` (
    `category_key` VARCHAR(50) PRIMARY KEY,
    `total_tickets` INT NOT NULL DEFAULT 0,
    `completed_tickets` INT NOT NULL DEFAULT 0,
    `rated_tickets` INT NOT NULL DEFAULT 0,
    `avg_rating` DECIMAL(4,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Password hash below is BCrypt for plain text password: 123456
INSERT INTO `sys_user`
    (`user_number`, `name`, `password`, `role`, `enabled`, `phone`, `avatar_url`, `created_at`)
VALUES
    ('admin', '管理员', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'ADMIN', TRUE, '13800000000', NULL, NOW() - INTERVAL 180 DAY),
    ('admin02', '值班管理员', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'ADMIN', TRUE, '13800000009', NULL, NOW() - INTERVAL 120 DAY),

    ('worker001', '维修员张三', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000001', NULL, NOW() - INTERVAL 170 DAY),
    ('worker002', '维修员李四', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000002', NULL, NOW() - INTERVAL 165 DAY),
    ('worker003', '维修员王强', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000005', NULL, NOW() - INTERVAL 110 DAY),
    ('worker004', '维修员陈敏', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STAFF', TRUE, '13800000006', NULL, NOW() - INTERVAL 90 DAY),

    ('20260001', '学生王五', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000003', NULL, NOW() - INTERVAL 160 DAY),
    ('20260002', '学生赵六', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000004', NULL, NOW() - INTERVAL 155 DAY),
    ('20260003', '学生孙七', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000007', NULL, NOW() - INTERVAL 130 DAY),
    ('20260004', '学生周八', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000008', NULL, NOW() - INTERVAL 125 DAY),
    ('20260005', '学生吴九', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000010', NULL, NOW() - INTERVAL 100 DAY),
    ('20260006', '学生郑十', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000011', NULL, NOW() - INTERVAL 95 DAY),
    ('20260007', '学生钱一', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000012', NULL, NOW() - INTERVAL 70 DAY),
    ('20260008', '学生李二', '$2a$10$BnD773cRGP0RgVgCWfk2aOnE26elWpSZBjNLz/GNV7hghhopQ7xu2', 'STUDENT', TRUE, '13800000013', NULL, NOW() - INTERVAL 45 DAY);

INSERT INTO `repair_category` (`id`, `category_key`)
VALUES
    (1, '水电维修'),
    (2, '网络故障'),
    (3, '家具维修'),
    (4, '电器故障'),
    (5, '公共设施'),
    (6, '门窗维修'),
    (7, '卫生清洁'),
    (8, '消防安全'),
    (9, '空调维修'),
    (10, '其他');

INSERT INTO `repair_order`
    (`id`, `student_number`, `category_key`, `repairman_id`, `status`, `location`, `description`,
     `rejection_reason`, `priority`, `repair_notes`, `process_notes`, `estimated_completion_time`,
     `created_at`, `assigned_at`, `completed_at`, `closed_at`, `is_deleted`, `deleted_at`, `version`)
VALUES
    (1, '20260001', '水电维修', NULL, 'WAITING_ACCEPT', '一号宿舍楼 3-305', '洗手台下方水管持续滴水，地面已经积水。', NULL, 'high', NULL, '学生刚提交，等待管理员分配。', NULL, NOW() - INTERVAL 2 HOUR, NULL, NULL, NULL, FALSE, NULL, 0),
    (2, '20260002', '网络故障', NULL, 'WAITING_ACCEPT', '二号教学楼 B203', '教室有线网络无法连接，投影电脑无法访问校园网。', NULL, 'medium', NULL, '等待网络维修人员确认。', NULL, NOW() - INTERVAL 5 HOUR, NULL, NULL, NULL, FALSE, NULL, 0),
    (3, '20260003', '家具维修', NULL, 'WAITING_ACCEPT', '图书馆三楼自习区', '自习桌桌腿松动，使用时晃动明显。', NULL, 'low', NULL, NULL, NULL, NOW() - INTERVAL 1 DAY, NULL, NULL, NULL, FALSE, NULL, 0),
    (4, '20260004', '电器故障', 'worker001', 'IN_PROGRESS', '三号宿舍楼 6-612', '寝室照明灯频闪，疑似镇流器故障。', NULL, 'medium', '已领取备件，预计今日完成更换。', '管理员已分配给维修员张三。', NOW() + INTERVAL 6 HOUR, NOW() - INTERVAL 22 HOUR, NOW() - INTERVAL 20 HOUR, NULL, NULL, FALSE, NULL, 0),
    (5, '20260005', '公共设施', 'worker002', 'IN_PROGRESS', '食堂一楼东门', '入口玻璃门闭门器损坏，关门速度过快。', NULL, 'high', '正在检查闭门器型号。', '现场已设置提示牌。', NOW() + INTERVAL 3 HOUR, NOW() - INTERVAL 18 HOUR, NOW() - INTERVAL 16 HOUR, NULL, NULL, FALSE, NULL, 0),
    (6, '20260006', '空调维修', 'worker003', 'IN_PROGRESS', '实验楼 A508', '空调启动后有异响，制冷效果差。', NULL, 'medium', '已清理滤网，等待进一步检测压缩机。', NULL, NOW() + INTERVAL 1 DAY, NOW() - INTERVAL 14 HOUR, NOW() - INTERVAL 13 HOUR, NULL, NULL, FALSE, NULL, 0),
    (7, '20260001', '门窗维修', 'worker001', 'RESOLVED', '一号宿舍楼 3-305', '阳台门锁无法正常闭合。', NULL, 'medium', '已更换锁芯并调试门框位置。', '维修完成，等待学生确认。', NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 1 DAY - INTERVAL 18 HOUR, NULL, FALSE, NULL, 0),
    (8, '20260002', '水电维修', 'worker002', 'RESOLVED', '二号宿舍楼 1-108', '卫生间水龙头无法完全关闭。', NULL, 'high', '已更换阀芯。', '维修工已标记完成，等待学生评价入口。', NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 2 DAY - INTERVAL 10 HOUR, NULL, FALSE, NULL, 0),
    (9, '20260003', '网络故障', 'worker003', 'WAITING_FEEDBACK', '四号宿舍楼 2-204', '寝室无线网络信号弱，经常断线。', NULL, 'medium', '已更换 AP 网线并重启设备。', '处理完成，等待学生评价。', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY - INTERVAL 22 HOUR, NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR, NULL, FALSE, NULL, 0),
    (10, '20260004', '卫生清洁', 'worker004', 'WAITING_FEEDBACK', '五号宿舍楼一楼公共洗衣房', '地漏返味明显，排水速度慢。', NULL, 'low', '已疏通地漏并消毒。', '已通知宿管复查。', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 4 DAY - INTERVAL 12 HOUR, NULL, FALSE, NULL, 0),
    (11, '20260001', '电器故障', 'worker001', 'FEEDBACKED', '综合楼 C301', '多媒体讲台插座无电。', NULL, 'high', '重新接线并更换老化插座。', '课堂前已完成抢修。', NOW() - INTERVAL 13 DAY, NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 15 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 14 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 13 DAY - INTERVAL 22 HOUR, FALSE, NULL, 0),
    (12, '20260002', '家具维修', 'worker001', 'FEEDBACKED', '图书馆二楼阅览室', '椅子靠背断裂，存在安全风险。', NULL, 'medium', '已更换新椅并回收损坏椅子。', NULL, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 14 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 13 DAY - INTERVAL 12 HOUR, NOW() - INTERVAL 12 DAY - INTERVAL 20 HOUR, FALSE, NULL, 0),
    (13, '20260003', '公共设施', 'worker002', 'FEEDBACKED', '篮球场西侧饮水机', '饮水机出水按钮卡住，无法正常使用。', NULL, 'low', '已更换按钮组件。', '巡检时同步检查其他饮水点。', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 13 DAY, NOW() - INTERVAL 12 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 11 DAY - INTERVAL 16 HOUR, NOW() - INTERVAL 10 DAY - INTERVAL 20 HOUR, FALSE, NULL, 0),
    (14, '20260004', '空调维修', 'worker003', 'FEEDBACKED', '教学楼 D502', '空调只送风不制冷。', NULL, 'high', '补充制冷剂并清洗室外机。', NULL, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 11 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 10 DAY - INTERVAL 12 HOUR, NOW() - INTERVAL 9 DAY - INTERVAL 20 HOUR, FALSE, NULL, 0),
    (15, '20260005', '消防安全', 'worker004', 'FEEDBACKED', '实验楼 B 区三楼走廊', '应急照明灯不亮。', NULL, 'high', '已更换电池并测试断电照明。', '按消防巡检要求记录。', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY - INTERVAL 22 HOUR, NOW() - INTERVAL 8 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 7 DAY - INTERVAL 18 HOUR, FALSE, NULL, 0),
    (16, '20260006', '水电维修', 'worker002', 'CLOSED', '六号宿舍楼 5-501', '走廊公共照明不亮。', NULL, 'medium', '已更换灯管。', '管理员验收后关闭。', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 21 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 20 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 18 DAY, FALSE, NULL, 0),
    (17, '20260007', '网络故障', 'worker003', 'CLOSED', '信息楼 2-210', '机房交换机端口异常。', NULL, 'high', '已更换故障端口模块。', '学生已评价，管理员归档关闭。', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 29 DAY, NOW() - INTERVAL 28 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 27 DAY - INTERVAL 12 HOUR, NOW() - INTERVAL 25 DAY, FALSE, NULL, 0),
    (18, '20260008', '其他', NULL, 'REJECTED', '校医院门口', '希望增设共享充电宝柜。', '该事项不属于维修报修范围，请通过后勤建议渠道提交。', 'low', NULL, '管理员审核后驳回。', NULL, NOW() - INTERVAL 2 DAY - INTERVAL 4 HOUR, NULL, NULL, NOW() - INTERVAL 2 DAY, FALSE, NULL, 0),
    (19, '20260002', '家具维修', NULL, 'REJECTED', '二号宿舍楼 1-108', '想更换个人书桌。', '现场核查后未发现损坏，暂不受理更换。', 'low', NULL, '重复报修，管理员驳回。', NULL, NOW() - INTERVAL 8 DAY, NULL, NULL, NOW() - INTERVAL 7 DAY - INTERVAL 20 HOUR, FALSE, NULL, 0),
    (20, '20260005', '公共设施', NULL, 'WAITING_ACCEPT', '操场看台西侧', '座椅表面开裂。', NULL, 'medium', NULL, '学生撤销的历史报修。', NULL, NOW() - INTERVAL 30 DAY, NULL, NULL, NULL, TRUE, NOW() - INTERVAL 29 DAY, 0),
    (21, '20260007', '水电维修', 'worker004', 'IN_PROGRESS', '七号宿舍楼 4-418', '宿舍总闸偶发跳闸。', NULL, 'high', '已排查大功率电器，准备检测线路绝缘。', '高优先级处理中。', NOW() + INTERVAL 8 HOUR, NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 9 HOUR, NULL, NULL, FALSE, NULL, 0),
    (22, '20260008', '卫生清洁', 'worker004', 'FEEDBACKED', '体育馆一楼淋浴区', '排水沟堵塞，有积水。', NULL, 'medium', '已疏通并完成清洁消毒。', NULL, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 4 DAY - INTERVAL 12 HOUR, NOW() - INTERVAL 4 DAY, FALSE, NULL, 0),
    (23, '20260001', '网络故障', 'worker002', 'WAITING_FEEDBACK', '一号宿舍楼 3-305', '宿舍网口接触不良。', NULL, 'medium', '已重新压接水晶头并测试通过。', '等待学生确认。', NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 8 HOUR, NULL, FALSE, NULL, 0),
    (24, '20260004', '公共设施', 'worker001', 'RESOLVED', '南门自行车棚', '照明灯白天常亮，疑似时控开关异常。', NULL, 'low', '已调整时控开关。', NULL, NOW() - INTERVAL 15 HOUR, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY - INTERVAL 16 HOUR, NOW() - INTERVAL 15 HOUR, NULL, FALSE, NULL, 0),
    (25, '20260006', '门窗维修', 'worker003', 'CLOSED', '实验楼 A508', '窗户把手脱落。', NULL, 'medium', '已更换把手。', '长期未评价，管理员关闭。', NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 18 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 16 DAY, FALSE, NULL, 0),
    (26, '20260003', '电器故障', 'worker002', 'FEEDBACKED', '四号宿舍楼 2-204', '插排烧焦，有异味。', NULL, 'high', '拆除故障插排，检查线路正常。', '已提醒学生注意用电安全。', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR, NOW() - INTERVAL 3 DAY, FALSE, NULL, 0),
    (27, '20260007', '其他', NULL, 'REJECTED', '七号宿舍楼 4-418', '申请增加宿舍储物柜。', '该需求属于资产配置申请，不属于维修工单。', 'low', NULL, NULL, NULL, NOW() - INTERVAL 11 DAY, NULL, NULL, NOW() - INTERVAL 10 DAY - INTERVAL 20 HOUR, FALSE, NULL, 0),
    (28, '20260008', '消防安全', 'worker004', 'IN_PROGRESS', '体育馆二楼器材室', '灭火器压力指针接近红区。', NULL, 'high', '已登记更换，等待消防维保单位送达。', NULL, NOW() + INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR, NOW() - INTERVAL 1 DAY - INTERVAL 3 HOUR, NULL, NULL, FALSE, NULL, 0),
    (29, '20260005', '空调维修', NULL, 'WAITING_ACCEPT', '五号宿舍楼 2-219', '空调遥控器无法控制温度。', NULL, 'low', NULL, '等待分配。', NULL, NOW() - INTERVAL 30 MINUTE, NULL, NULL, NULL, FALSE, NULL, 0),
    (30, '20260006', '公共设施', 'worker001', 'FEEDBACKED', '六号宿舍楼门厅', '公告栏玻璃破裂。', NULL, 'medium', '已更换公告栏玻璃并清理碎片。', NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY - INTERVAL 20 HOUR, NOW() - INTERVAL 2 DAY - INTERVAL 12 HOUR, NOW() - INTERVAL 1 DAY, FALSE, NULL, 0);

INSERT INTO `repair_order_image` (`repair_order_id`, `image_url`, `created_at`)
VALUES
    (1, '/uploads/demo/order-1-leak-1.jpg', NOW() - INTERVAL 2 HOUR),
    (1, '/uploads/demo/order-1-leak-2.jpg', NOW() - INTERVAL 2 HOUR),
    (4, '/uploads/demo/order-4-light.jpg', NOW() - INTERVAL 21 HOUR),
    (5, '/uploads/demo/order-5-door.jpg', NOW() - INTERVAL 17 HOUR),
    (9, '/uploads/demo/order-9-network.jpg', NOW() - INTERVAL 5 DAY),
    (14, '/uploads/demo/order-14-ac.jpg', NOW() - INTERVAL 12 DAY),
    (15, '/uploads/demo/order-15-emergency-light.jpg', NOW() - INTERVAL 10 DAY),
    (21, '/uploads/demo/order-21-switch.jpg', NOW() - INTERVAL 10 HOUR),
    (26, '/uploads/demo/order-26-socket.jpg', NOW() - INTERVAL 5 DAY),
    (30, '/uploads/demo/order-30-board.jpg', NOW() - INTERVAL 4 DAY);

INSERT INTO `repair_feedback`
    (`repair_order_id`, `student_number`, `repairman_id`, `rating`, `comment`, `speed_rating`, `quality_rating`, `attitude_rating`, `resolved`, `anonymous`, `created_at`)
VALUES
    (11, '20260001', 'worker001', 5, '处理很快，课前就恢复供电了。', 5, 5, 5, TRUE, FALSE, NOW() - INTERVAL 13 DAY - INTERVAL 22 HOUR),
    (12, '20260002', 'worker001', 4, '椅子已经更换，响应速度不错。', 4, 4, 5, TRUE, FALSE, NOW() - INTERVAL 12 DAY - INTERVAL 20 HOUR),
    (13, '20260003', 'worker002', 5, '饮水机恢复正常，维修说明清楚。', 5, 5, 4, TRUE, FALSE, NOW() - INTERVAL 10 DAY - INTERVAL 20 HOUR),
    (14, '20260004', 'worker003', 4, '空调制冷恢复，噪音也小了。', 4, 5, 4, TRUE, TRUE, NOW() - INTERVAL 9 DAY - INTERVAL 20 HOUR),
    (15, '20260005', 'worker004', 5, '消防设备处理及时，态度很好。', 5, 5, 5, TRUE, FALSE, NOW() - INTERVAL 7 DAY - INTERVAL 18 HOUR),
    (17, '20260007', 'worker003', 3, '问题解决了，但等待时间有点长。', 2, 4, 3, TRUE, FALSE, NOW() - INTERVAL 26 DAY),
    (22, '20260008', 'worker004', 5, '积水处理干净，后续没有返味。', 5, 5, 5, TRUE, FALSE, NOW() - INTERVAL 4 DAY),
    (26, '20260003', 'worker002', 4, '维修后安全隐患消除了。', 4, 4, 4, TRUE, FALSE, NOW() - INTERVAL 3 DAY),
    (30, '20260006', 'worker001', 5, '更换很及时，现场也清理干净。', 5, 5, 5, TRUE, FALSE, NOW() - INTERVAL 1 DAY);

INSERT INTO `repair_order_status_log`
    (`repair_order_id`, `operate_user_id`, `before_status`, `after_status`, `operate_time`)
VALUES
    (1, '20260001', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 2 HOUR),
    (2, '20260002', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 5 HOUR),
    (3, '20260003', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 1 DAY),

    (4, '20260004', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 22 HOUR),
    (4, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 20 HOUR),
    (5, '20260005', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 18 HOUR),
    (5, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 16 HOUR),
    (6, '20260006', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 14 HOUR),
    (6, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 13 HOUR),

    (7, '20260001', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 3 DAY),
    (7, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 2 DAY - INTERVAL 20 HOUR),
    (7, 'worker001', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 1 DAY - INTERVAL 18 HOUR),
    (8, '20260002', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 4 DAY),
    (8, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 3 DAY - INTERVAL 20 HOUR),
    (8, 'worker002', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 2 DAY - INTERVAL 10 HOUR),

    (9, '20260003', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 5 DAY),
    (9, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 4 DAY - INTERVAL 22 HOUR),
    (9, 'worker003', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR),
    (9, 'worker003', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 3 DAY - INTERVAL 17 HOUR),
    (10, '20260004', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 6 DAY),
    (10, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 5 DAY - INTERVAL 18 HOUR),
    (10, 'worker004', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 4 DAY - INTERVAL 12 HOUR),
    (10, 'worker004', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 4 DAY - INTERVAL 11 HOUR),

    (11, '20260001', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 16 DAY),
    (11, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 15 DAY - INTERVAL 20 HOUR),
    (11, 'worker001', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 14 DAY - INTERVAL 18 HOUR),
    (11, 'worker001', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 14 DAY - INTERVAL 17 HOUR),
    (11, '20260001', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 13 DAY - INTERVAL 22 HOUR),
    (12, '20260002', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 15 DAY),
    (12, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 14 DAY - INTERVAL 18 HOUR),
    (12, 'worker001', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 13 DAY - INTERVAL 12 HOUR),
    (12, 'worker001', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 13 DAY - INTERVAL 11 HOUR),
    (12, '20260002', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 12 DAY - INTERVAL 20 HOUR),
    (13, '20260003', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 13 DAY),
    (13, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 12 DAY - INTERVAL 18 HOUR),
    (13, 'worker002', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 11 DAY - INTERVAL 16 HOUR),
    (13, 'worker002', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 11 DAY - INTERVAL 15 HOUR),
    (13, '20260003', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 10 DAY - INTERVAL 20 HOUR),
    (14, '20260004', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 12 DAY),
    (14, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 11 DAY - INTERVAL 20 HOUR),
    (14, 'worker003', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 10 DAY - INTERVAL 12 HOUR),
    (14, 'worker003', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 10 DAY - INTERVAL 11 HOUR),
    (14, '20260004', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 9 DAY - INTERVAL 20 HOUR),
    (15, '20260005', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 10 DAY),
    (15, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 9 DAY - INTERVAL 22 HOUR),
    (15, 'worker004', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 8 DAY - INTERVAL 18 HOUR),
    (15, 'worker004', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 8 DAY - INTERVAL 17 HOUR),
    (15, '20260005', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 7 DAY - INTERVAL 18 HOUR),

    (16, '20260006', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 22 DAY),
    (16, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 21 DAY - INTERVAL 20 HOUR),
    (16, 'worker002', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 20 DAY - INTERVAL 20 HOUR),
    (16, 'admin', 'RESOLVED', 'CLOSED', NOW() - INTERVAL 18 DAY),
    (17, '20260007', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 29 DAY),
    (17, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 28 DAY - INTERVAL 18 HOUR),
    (17, 'worker003', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 27 DAY - INTERVAL 12 HOUR),
    (17, 'worker003', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 27 DAY - INTERVAL 11 HOUR),
    (17, '20260007', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 26 DAY),
    (17, 'admin', 'FEEDBACKED', 'CLOSED', NOW() - INTERVAL 25 DAY),

    (18, '20260008', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 2 DAY - INTERVAL 4 HOUR),
    (18, 'admin', 'WAITING_ACCEPT', 'REJECTED', NOW() - INTERVAL 2 DAY),
    (19, '20260002', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 8 DAY),
    (19, 'admin02', 'WAITING_ACCEPT', 'REJECTED', NOW() - INTERVAL 7 DAY - INTERVAL 20 HOUR),
    (20, '20260005', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 30 DAY),
    (21, '20260007', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 10 HOUR),
    (21, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 9 HOUR),
    (22, '20260008', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 6 DAY),
    (22, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 5 DAY - INTERVAL 20 HOUR),
    (22, 'worker004', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 4 DAY - INTERVAL 12 HOUR),
    (22, 'worker004', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 4 DAY - INTERVAL 11 HOUR),
    (22, '20260008', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 4 DAY),
    (23, '20260001', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 2 DAY),
    (23, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 1 DAY - INTERVAL 20 HOUR),
    (23, 'worker002', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 8 HOUR),
    (23, 'worker002', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 7 HOUR),
    (24, '20260004', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 3 DAY),
    (24, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 2 DAY - INTERVAL 16 HOUR),
    (24, 'worker001', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 15 HOUR),
    (25, '20260006', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 19 DAY),
    (25, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 18 DAY - INTERVAL 20 HOUR),
    (25, 'worker003', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 17 DAY),
    (25, 'admin', 'RESOLVED', 'CLOSED', NOW() - INTERVAL 16 DAY),
    (26, '20260003', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 5 DAY),
    (26, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 4 DAY - INTERVAL 18 HOUR),
    (26, 'worker002', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR),
    (26, 'worker002', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 3 DAY - INTERVAL 17 HOUR),
    (26, '20260003', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 3 DAY),
    (27, '20260007', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 11 DAY),
    (27, 'admin', 'WAITING_ACCEPT', 'REJECTED', NOW() - INTERVAL 10 DAY - INTERVAL 20 HOUR),
    (28, '20260008', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR),
    (28, 'admin02', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 1 DAY - INTERVAL 3 HOUR),
    (29, '20260005', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 30 MINUTE),
    (30, '20260006', NULL, 'WAITING_ACCEPT', NOW() - INTERVAL 4 DAY),
    (30, 'admin', 'WAITING_ACCEPT', 'IN_PROGRESS', NOW() - INTERVAL 3 DAY - INTERVAL 20 HOUR),
    (30, 'worker001', 'IN_PROGRESS', 'RESOLVED', NOW() - INTERVAL 2 DAY - INTERVAL 12 HOUR),
    (30, 'worker001', 'RESOLVED', 'WAITING_FEEDBACK', NOW() - INTERVAL 2 DAY - INTERVAL 11 HOUR),
    (30, '20260006', 'WAITING_FEEDBACK', 'FEEDBACKED', NOW() - INTERVAL 1 DAY);

INSERT INTO `repair_order_comment`
    (`repair_order_id`, `user_number`, `content`, `image_url`, `comment_type`, `created_at`)
VALUES
    (1, '20260001', '补充说明：洗手台下方水管仍在持续漏水，地面已经有明显积水。', NULL, 'SUPPLEMENT', NOW() - INTERVAL 1 HOUR - INTERVAL 45 MINUTE),
    (1, 'admin', '处理回复：管理员已看到该工单，会尽快安排对应维修人员处理。', NULL, 'REPLY', NOW() - INTERVAL 1 HOUR - INTERVAL 30 MINUTE),
    (4, 'worker001', '处理回复：镇流器备件已经准备好，等宿舍方便进入后会立即更换。', NULL, 'REPLY', NOW() - INTERVAL 18 HOUR),
    (4, '20260004', '补充说明：晚自习时灯仍然频繁闪烁，对学习影响比较明显。', NULL, 'SUPPLEMENT', NOW() - INTERVAL 10 HOUR),
    (5, '20260005', '催单提醒：食堂入口门关闭速度过快，就餐高峰容易夹到同学，请尽快处理。', NULL, 'URGE', NOW() - INTERVAL 8 HOUR),
    (5, 'worker002', '处理回复：闭门器型号已确认，正在等待匹配配件，现场已经放置安全提示牌。', NULL, 'REPLY', NOW() - INTERVAL 7 HOUR - INTERVAL 30 MINUTE),
    (9, 'worker003', '处理回复：已更换 AP 网线并重启设备，请同学再测试无线网络信号。', NULL, 'REPLY', NOW() - INTERVAL 3 DAY - INTERVAL 16 HOUR),
    (9, '20260003', '补充说明：目前信号基本稳定，我会再观察一个晚自习后提交评价。', NULL, 'SUPPLEMENT', NOW() - INTERVAL 2 DAY - INTERVAL 10 HOUR),
    (21, '20260007', '催单提醒：宿舍总闸仍会偶发跳闸，影响正常用电，请尽快排查。', NULL, 'URGE', NOW() - INTERVAL 6 HOUR),
    (21, 'worker004', '处理回复：该工单已按高优先级处理，请暂时避免使用大功率电器。', NULL, 'REPLY', NOW() - INTERVAL 5 HOUR - INTERVAL 30 MINUTE),
    (23, 'worker002', '处理回复：网口连通性测试已通过，请同学方便时确认并评价。', NULL, 'REPLY', NOW() - INTERVAL 6 HOUR),
    (23, '20260001', '补充说明：笔记本已经可以正常联网，但台式机还需要再测试一次。', NULL, 'SUPPLEMENT', NOW() - INTERVAL 5 HOUR),
    (28, 'admin02', '处理回复：消防器材更换已联系外部维保单位安排处理。', NULL, 'REPLY', NOW() - INTERVAL 1 DAY),
    (29, '20260005', '补充说明：空调遥控器屏幕不亮，机身按键也无法调节温度。', NULL, 'SUPPLEMENT', NOW() - INTERVAL 20 MINUTE);

INSERT INTO `repair_process_record`
    (`repair_order_id`, `staff_id`, `action_type`, `content`, `image_url`, `created_at`)
VALUES
    (4, 'worker001', 'ARRIVED', '已到达三号宿舍楼 6-612，确认照明灯频闪原因是镇流器老化。', '/uploads/demo/process-4-before.jpg', NOW() - INTERVAL 19 HOUR),
    (4, 'worker001', 'MATERIAL_USED', '已准备匹配镇流器 1 个和绝缘胶带，用于现场更换。', NULL, NOW() - INTERVAL 18 HOUR - INTERVAL 30 MINUTE),
    (5, 'worker002', 'ARRIVED', '已检查食堂一楼东门，确认闭门器缓冲部件损坏。', '/uploads/demo/process-5-door.jpg', NOW() - INTERVAL 15 HOUR),
    (5, 'worker002', 'MATERIAL_USED', '记录所需耗材：液压闭门器，型号 D-120。', NULL, NOW() - INTERVAL 14 HOUR),
    (7, 'worker001', 'ARRIVED', '已到达一号宿舍楼 3-305，检查阳台门锁变形情况。', NULL, NOW() - INTERVAL 2 DAY - INTERVAL 18 HOUR),
    (7, 'worker001', 'FINISHED', '已更换锁芯并调整门框位置，阳台门可以正常闭合。', '/uploads/demo/process-7-after.jpg', NOW() - INTERVAL 1 DAY - INTERVAL 18 HOUR),
    (9, 'worker003', 'ARRIVED', '已检查无线 AP，发现网线接触不稳定。', NULL, NOW() - INTERVAL 4 DAY - INTERVAL 20 HOUR),
    (9, 'worker003', 'FINISHED', '已更换 AP 网线并重启设备，现场信号测试通过。', '/uploads/demo/process-9-after.jpg', NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR),
    (21, 'worker004', 'ARRIVED', '已到达七号宿舍楼 4-418，确认总闸存在间歇性跳闸。', NULL, NOW() - INTERVAL 8 HOUR),
    (21, 'worker004', 'TRANSFER_REQUEST', '疑似大负载线路故障，建议安排电气安全专员协同处理。', NULL, NOW() - INTERVAL 6 HOUR),
    (23, 'worker002', 'ARRIVED', '已检查宿舍网口，发现水晶头接触松动。', NULL, NOW() - INTERVAL 1 DAY - INTERVAL 18 HOUR),
    (23, 'worker002', 'FINISHED', '已重新压接水晶头并完成连通性测试，网络访问恢复正常。', '/uploads/demo/process-23-after.jpg', NOW() - INTERVAL 8 HOUR),
    (28, 'worker004', 'ARRIVED', '已检查灭火器压力表，确认指针接近红色区域。', '/uploads/demo/process-28-before.jpg', NOW() - INTERVAL 1 DAY - INTERVAL 2 HOUR),
    (28, 'worker004', 'TRANSFER_REQUEST', '该灭火器需要外部消防维保单位到场更换。', NULL, NOW() - INTERVAL 1 DAY);

INSERT INTO `sys_notification`
    (`receiver_id`, `title`, `content`, `related_order_id`, `read_flag`, `created_at`)
VALUES
    ('admin', '新报修工单待分配', '工单 #29 已提交，地点：五号宿舍楼 2-219，请及时分配维修人员。', 29, FALSE, NOW() - INTERVAL 30 MINUTE),
    ('admin02', '新报修工单待分配', '工单 #1 已提交，地点：一号宿舍楼 3-305，请及时分配维修人员。', 1, FALSE, NOW() - INTERVAL 2 HOUR),
    ('admin', '工单评价已提交', '工单 #30 已完成评价，评分：5 星。', 30, TRUE, NOW() - INTERVAL 1 DAY),
    ('worker001', '你有新的维修任务', '工单 #4 已分配给你，地点：三号宿舍楼 6-612。', 4, TRUE, NOW() - INTERVAL 20 HOUR),
    ('worker002', '你有新的维修任务', '工单 #23 已分配给你，地点：一号宿舍楼 3-305。', 23, FALSE, NOW() - INTERVAL 1 DAY - INTERVAL 20 HOUR),
    ('worker003', '学生已完成评价', '工单 #26 收到学生评价：4 星。', 26, FALSE, NOW() - INTERVAL 3 DAY),
    ('worker004', '维修任务已关闭', '工单 #22 已关闭归档。', 22, TRUE, NOW() - INTERVAL 4 DAY),
    ('20260001', '报修工单已完成', '你的工单 #23 已处理完成，请确认并评价本次服务。', 23, FALSE, NOW() - INTERVAL 7 HOUR),
    ('20260002', '报修工单被驳回', '你的工单 #19 已被驳回，原因：现场核查后未发现损坏，暂不受理更换。', 19, TRUE, NOW() - INTERVAL 7 DAY - INTERVAL 20 HOUR),
    ('20260005', '报修工单已受理', '你的工单 #5 已分配给维修人员：维修员李四。', 5, TRUE, NOW() - INTERVAL 16 HOUR),
    ('20260008', '报修工单被驳回', '你的工单 #18 已被驳回，原因：该事项不属于维修报修范围，请通过后勤建议渠道提交。', 18, FALSE, NOW() - INTERVAL 2 DAY);

INSERT INTO `sys_system_config`
    (`config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`)
VALUES
    ('ai.enabled', 'false', '是否启用外部大模型。未启用时系统使用本地规则引擎，可离线演示。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.provider', 'deepseek', '大模型供应商，当前预留 DeepSeek/OpenAI-Compatible 接口配置。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.model', 'deepseek-v4-flash', '默认大模型名称，可按 DeepSeek 控制台可用模型调整。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.base-url', 'https://api.deepseek.com', 'DeepSeek OpenAI-Compatible API Base URL。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.api-key', '', 'DeepSeek API Key。建议只在本地后台配置，不写入公开 SQL。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.timeout-seconds', '20', '大模型接口超时时间，单位秒。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('ai.thinking.enabled', 'false', '是否启用模型思考模式。课程演示建议关闭，响应更快。', 'admin', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
    ('upload.max-image-count', '5', '单个工单最多上传图片数量。', 'admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('upload.max-image-size-mb', '5', '单张图片大小上限，单位 MB。', 'admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('backup.auto-enabled', 'false', '是否启用定时数据库备份。', 'admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('backup.cron', '0 30 2 * * ?', '定时备份 Cron 表达式，默认每天凌晨 2:30。', 'admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('backup.retention-days', '30', '备份文件保留天数。', 'admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.high.responseHours', '2', '高优先级工单受理时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.high.completionHours', '24', '高优先级工单完成时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.medium.responseHours', '8', '中优先级工单受理时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.medium.completionHours', '72', '中优先级工单完成时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.low.responseHours', '24', '低优先级工单受理时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
    ('sla.low.completionHours', '168', '低优先级工单完成时限。', 'admin02', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

INSERT INTO `repair_knowledge_base`
    (`category_key`, `title`, `symptom_keywords`, `solution_steps`, `safety_notes`, `estimated_minutes`, `enabled`, `created_at`, `updated_at`)
VALUES
    ('水电维修', '宿舍水管漏水应急处理', '漏水,滴水,水管,水龙头,积水,阀芯', '1. 到场后先关闭局部阀门；2. 检查软管、阀芯和接口密封圈；3. 更换损坏配件并做 10 分钟通水测试；4. 清理积水并提醒学生观察。', '地面有积水时先做防滑提示，疑似水电混合时必须断电后处理。', 45, TRUE, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 3 DAY),
    ('电器故障', '照明灯频闪与镇流器更换', '照明,灯,频闪,镇流器,灯管,不亮', '1. 断电验电；2. 拆开灯具外罩检查灯管和镇流器；3. 更换匹配镇流器或灯管；4. 通电测试并检查固定螺丝。', '必须断电操作，登高作业需要扶梯稳定，禁止学生在下方停留。', 60, TRUE, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 2 DAY),
    ('网络故障', '宿舍网口接触不良处理', '网络,网口,断线,无法连接,水晶头,校园网,无线', '1. 使用测线仪检测端口；2. 重新压接水晶头或更换短网线；3. 重启 AP/交换机端口；4. 现场用学生设备测试校园网访问。', '不要随意拔插弱电间核心交换设备，必要时联系网络中心确认。', 40, TRUE, NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 2 DAY),
    ('家具维修', '桌椅松动和断裂处理', '桌,椅,床,柜,靠背,桌腿,松动,断裂', '1. 判断是否可加固；2. 补紧螺丝或更换连接件；3. 结构断裂时直接更换；4. 回收损坏家具并登记耗材。', '断裂家具存在划伤或跌倒风险，应先停止使用并移至安全区域。', 35, TRUE, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 4 DAY),
    ('公共设施', '闭门器和公共门禁异常处理', '闭门器,玻璃门,关门快,门禁,公共门', '1. 检查闭门器油压和固定件；2. 调整闭门速度；3. 损坏时更换同型号配件；4. 在人流高峰前完成复测。', '入口门异常需先放置提示牌，避免夹伤师生。', 50, TRUE, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 3 DAY),
    ('空调维修', '空调不制冷和异响排查', '空调,不制冷,异响,滤网,制冷剂,压缩机', '1. 清洗滤网；2. 检查室外机散热；3. 测试制冷剂压力；4. 记录是否需要专业维保单位进一步处理。', '登高检查室外机时必须两人配合，禁止雨天外机作业。', 90, TRUE, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 2 DAY),
    ('消防安全', '灭火器压力异常处理流程', '消防,灭火器,压力,应急灯,烟感,红区', '1. 核对消防设备编号；2. 判断压力表状态；3. 红区或过期设备立即登记更换；4. 通知消防维保单位并跟踪闭环。', '消防设备不得挪作他用，异常设备需保证现场仍有替代消防器材。', 30, TRUE, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 1 DAY),
    ('卫生清洁', '地漏堵塞与返味处理', '地漏,堵塞,返味,积水,排水,异味', '1. 疏通地漏和排水沟；2. 清理毛发杂物；3. 使用消毒除味剂；4. 观察排水速度并提醒保洁加强巡检。', '清洁剂使用后保持通风，积水区域先做防滑处理。', 35, TRUE, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 1 DAY);

INSERT INTO `ai_ticket_analysis`
    (`source_text`, `title`, `category_key`, `location_text`, `priority`, `summary`, `safety_tips`, `provider`, `model`, `raw_response`, `created_at`)
VALUES
    ('宿舍 3 楼卫生间水一直漏，地面很滑。', '宿舍卫生间水管漏水', '水电维修', '宿舍 3 楼卫生间', 'high', '识别为水电维修，疑似漏水并存在防滑风险。', '请暂时避开积水区域，防止滑倒或触电风险。', '本地规则引擎', 'rule-fallback', '演示缓存数据', NOW() - INTERVAL 1 DAY),
    ('教学楼 B203 有线网络无法连接，投影电脑上不了校园网。', '教学楼网络连接异常', '网络故障', '教学楼 B203', 'medium', '识别为网络故障，影响教室多媒体使用。', '请保留现场端口和设备照片，等待网络维修人员处理。', '本地规则引擎', 'rule-fallback', '演示缓存数据', NOW() - INTERVAL 12 HOUR);

INSERT INTO `sys_audit_log`
    (`actor_id`, `action`, `module`, `target_type`, `target_id`, `detail`, `request_method`, `request_path`, `success`, `ip_address`, `created_at`)
VALUES
    ('admin', '分配工单', '工单管理', 'REPAIR_ORDER', '4', '将工单 #4 分配给维修员张三。', 'PUT', '/api/admin/repair-orders/4/assign', TRUE, '127.0.0.1', NOW() - INTERVAL 20 HOUR),
    ('admin02', '更新配置', '系统配置', 'SYSTEM_CONFIG', 'sla.high.responseHours', '调整高优先级受理时限配置。', 'PUT', '/api/admin/system-config/sla.high.responseHours', TRUE, '127.0.0.1', NOW() - INTERVAL 1 DAY),
    ('admin', '新增知识库', '知识库', 'KNOWLEDGE', '1', '新增宿舍水管漏水应急处理。', 'POST', '/api/admin/knowledge-base', TRUE, '127.0.0.1', NOW() - INTERVAL 3 DAY),
    ('admin', '重置密码', '用户管理', 'USER', '20260008', '演示前重置测试学生密码。', 'POST', '/api/admin/users/20260008/reset-password', TRUE, '127.0.0.1', NOW() - INTERVAL 2 DAY);

CREATE OR REPLACE VIEW `vw_ticket_summary` AS
SELECT
    o.id AS ticket_id,
    o.`status` AS `status`,
    o.priority AS priority,
    o.location AS location_text,
    o.description AS description,
    o.estimated_completion_time,
    o.created_at,
    o.assigned_at,
    o.completed_at,
    o.closed_at,
    o.is_deleted,
    o.deleted_at,
    o.student_number AS student_id,
    stu.name AS student_name,
    stu.phone AS student_phone,
    o.repairman_id AS repairman_id,
    w.name AS repairman_name,
    w.phone AS repairman_phone,
    o.category_key AS category_key,
    c.category_key AS category_name,
    f.rating AS rating_score,
    f.comment AS rating_comment,
    f.created_at AS rating_time
FROM repair_order o
LEFT JOIN sys_user stu ON o.student_number = stu.user_number
LEFT JOIN sys_user w ON o.repairman_id = w.user_number
LEFT JOIN repair_category c ON o.category_key = c.category_key
LEFT JOIN repair_feedback f ON f.repair_order_id = o.id;

INSERT INTO `category_stats`
    (`category_key`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`)
SELECT
    c.category_key,
    COUNT(o.id) AS total_tickets,
    SUM(CASE WHEN o.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END) AS completed_tickets,
    SUM(CASE WHEN f.id IS NOT NULL THEN 1 ELSE 0 END) AS rated_tickets,
    COALESCE(AVG(f.rating), 0.00) AS avg_rating
FROM repair_category c
LEFT JOIN repair_order o
       ON o.category_key = c.category_key
      AND (o.is_deleted IS NULL OR o.is_deleted = FALSE)
LEFT JOIN repair_feedback f
       ON f.repair_order_id = o.id
GROUP BY c.category_key;

INSERT INTO `repairman_stats`
    (`repairman_id`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`, `last_completed_at`)
SELECT
    u.user_number,
    COUNT(o.id) AS total_tickets,
    SUM(CASE WHEN o.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END) AS completed_tickets,
    SUM(CASE WHEN f.id IS NOT NULL THEN 1 ELSE 0 END) AS rated_tickets,
    COALESCE(AVG(f.rating), 0.00) AS avg_rating,
    MAX(o.completed_at) AS last_completed_at
FROM sys_user u
LEFT JOIN repair_order o
       ON o.repairman_id = u.user_number
      AND (o.is_deleted IS NULL OR o.is_deleted = FALSE)
LEFT JOIN repair_feedback f
       ON f.repair_order_id = o.id
      AND f.repairman_id = u.user_number
WHERE u.role = 'STAFF'
GROUP BY u.user_number;

ALTER TABLE `repair_category` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_order` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_order_image` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_order_status_log` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_feedback` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_order_comment` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_process_record` AUTO_INCREMENT = 1001;
ALTER TABLE `sys_notification` AUTO_INCREMENT = 1001;
ALTER TABLE `sys_audit_log` AUTO_INCREMENT = 1001;
ALTER TABLE `repair_knowledge_base` AUTO_INCREMENT = 1001;
ALTER TABLE `ai_ticket_analysis` AUTO_INCREMENT = 1001;

DELIMITER $$

CREATE TRIGGER `trg_repair_order_after_insert`
AFTER INSERT ON `repair_order`
FOR EACH ROW
BEGIN
    IF NEW.is_deleted IS NULL OR NEW.is_deleted = FALSE THEN
        IF NEW.category_key IS NOT NULL THEN
            INSERT INTO `category_stats`
                (`category_key`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`)
            VALUES
                (NEW.category_key, 1,
                 CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                 0, 0.00)
            ON DUPLICATE KEY UPDATE
                total_tickets = total_tickets + 1,
                completed_tickets = completed_tickets
                    + CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END;
        END IF;

        IF NEW.repairman_id IS NOT NULL THEN
            INSERT INTO `repairman_stats`
                (`repairman_id`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`, `last_completed_at`)
            VALUES
                (NEW.repairman_id, 1,
                 CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                 0, 0.00, NEW.completed_at)
            ON DUPLICATE KEY UPDATE
                total_tickets = total_tickets + 1,
                completed_tickets = completed_tickets
                    + CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                last_completed_at = GREATEST(IFNULL(last_completed_at, NEW.completed_at), IFNULL(NEW.completed_at, last_completed_at));
        END IF;
    END IF;
END$$

CREATE TRIGGER `trg_repair_order_after_update_repairman`
AFTER UPDATE ON `repair_order`
FOR EACH ROW
BEGIN
    IF NOT (NEW.repairman_id <=> OLD.repairman_id) THEN
        IF NEW.repairman_id IS NOT NULL AND (NEW.is_deleted IS NULL OR NEW.is_deleted = FALSE) THEN
            INSERT INTO `repairman_stats`
                (`repairman_id`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`, `last_completed_at`)
            VALUES
                (NEW.repairman_id, 1,
                 CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                 0, 0.00, NEW.completed_at)
            ON DUPLICATE KEY UPDATE
                total_tickets = total_tickets + 1,
                completed_tickets = completed_tickets
                    + CASE WHEN NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                last_completed_at = GREATEST(IFNULL(last_completed_at, NEW.completed_at), IFNULL(NEW.completed_at, last_completed_at));
        END IF;

        IF OLD.repairman_id IS NOT NULL AND (OLD.is_deleted IS NULL OR OLD.is_deleted = FALSE) THEN
            UPDATE `repairman_stats`
            SET
                total_tickets = GREATEST(total_tickets - 1, 0),
                completed_tickets = GREATEST(
                    completed_tickets - CASE WHEN OLD.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN 1 ELSE 0 END,
                    0
                )
            WHERE repairman_id = OLD.repairman_id;
        END IF;
    END IF;
END$$

CREATE TRIGGER `trg_repair_order_after_update_status`
AFTER UPDATE ON `repair_order`
FOR EACH ROW
BEGIN
    DECLARE old_done TINYINT DEFAULT 0;
    DECLARE new_done TINYINT DEFAULT 0;

    IF OLD.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN
        SET old_done = 1;
    END IF;

    IF NEW.status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED') THEN
        SET new_done = 1;
    END IF;

    IF old_done <> new_done AND (NEW.is_deleted IS NULL OR NEW.is_deleted = FALSE) THEN
        IF NEW.category_key IS NOT NULL THEN
            INSERT INTO `category_stats`
                (`category_key`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`)
            VALUES
                (NEW.category_key, 0, new_done - old_done, 0, 0.00)
            ON DUPLICATE KEY UPDATE
                completed_tickets = GREATEST(completed_tickets + new_done - old_done, 0);
        END IF;

        IF NEW.repairman_id IS NOT NULL THEN
            INSERT INTO `repairman_stats`
                (`repairman_id`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`, `last_completed_at`)
            VALUES
                (NEW.repairman_id, 0, new_done - old_done, 0, 0.00, NEW.completed_at)
            ON DUPLICATE KEY UPDATE
                completed_tickets = GREATEST(completed_tickets + new_done - old_done, 0),
                last_completed_at = GREATEST(IFNULL(last_completed_at, NEW.completed_at), IFNULL(NEW.completed_at, last_completed_at));
        END IF;
    END IF;
END$$

CREATE TRIGGER `trg_repair_feedback_after_insert`
AFTER INSERT ON `repair_feedback`
FOR EACH ROW
BEGIN
    DECLARE v_category_key VARCHAR(50);

    SELECT o.category_key
    INTO v_category_key
    FROM repair_order o
    WHERE o.id = NEW.repair_order_id
    LIMIT 1;

    IF NEW.repairman_id IS NOT NULL THEN
        INSERT INTO `repairman_stats`
            (`repairman_id`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`, `last_completed_at`)
        VALUES
            (NEW.repairman_id, 0, 0, 1, NEW.rating, NULL)
        ON DUPLICATE KEY UPDATE
            avg_rating = (avg_rating * rated_tickets + NEW.rating) / (rated_tickets + 1),
            rated_tickets = rated_tickets + 1;
    END IF;

    IF v_category_key IS NOT NULL THEN
        INSERT INTO `category_stats`
            (`category_key`, `total_tickets`, `completed_tickets`, `rated_tickets`, `avg_rating`)
        VALUES
            (v_category_key, 0, 0, 1, NEW.rating)
        ON DUPLICATE KEY UPDATE
            avg_rating = (avg_rating * rated_tickets + NEW.rating) / (rated_tickets + 1),
            rated_tickets = rated_tickets + 1;
    END IF;
END$$

DELIMITER ;

SELECT '数据库初始化完成' AS message;
SELECT `role`, COUNT(*) AS user_count FROM `sys_user` GROUP BY `role`;
SELECT `status`, COUNT(*) AS ticket_count FROM `repair_order` GROUP BY `status` ORDER BY `status`;
SELECT '统一登录密码: 123456' AS login_password;

