-- 检查评价表数据和关联关系
USE repairdb;

-- 1. 检查 repair_feedback 表是否存在
SELECT '检查 repair_feedback 表:' AS '步骤';
SHOW TABLES LIKE 'repair_feedback';

-- 2. 查看 repair_feedback 表结构
SELECT 'repair_feedback 表结构:' AS '信息';
DESC repair_feedback;

-- 3. 检查 repair_feedback 表数据
SELECT 'repair_feedback 表数据:' AS '信息';
SELECT * FROM repair_feedback;

-- 4. 检查 repair_order 表是否存在（评价的外键关联）
SELECT '检查 repair_order 表:' AS '步骤';
SHOW TABLES LIKE 'repair_order';

-- 5. 查看 repair_order 表结构
SELECT 'repair_order 表结构:' AS '信息';
DESC repair_order;

-- 6. 检查是否有已完成的工单（可以评价的工单）
SELECT '已完成的工单数量:' AS '信息';
SELECT COUNT(*) AS completed_count
FROM repair_order
WHERE status IN ('RESOLVED', 'WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED');

-- 7. 检查 sys_user 表是否存在（评价的学生和维修工外键关联）
SELECT '检查 sys_user 表:' AS '步骤';
SHOW TABLES LIKE 'sys_user';

-- 8. 查看 sys_user 表数据
SELECT 'sys_user 表数据:' AS '信息';
SELECT * FROM sys_user;

-- 9. 检查外键关联是否正常（查找有问题的评价记录）
SELECT '检查外键关联:' AS '步骤';
SELECT
    f.id AS feedback_id,
    f.repair_order_id,
    f.student_number,
    f.repairman_id,
    f.rating,
    f.comment,
    o.id AS order_exists,
    u1.user_number AS student_exists,
    u2.user_number AS repairman_exists
FROM repair_feedback f
LEFT JOIN repair_order o ON f.repair_order_id = o.id
LEFT JOIN sys_user u1 ON f.student_number = u1.user_number
LEFT JOIN sys_user u2 ON f.repairman_id = u2.user_number
WHERE o.id IS NULL OR u1.user_number IS NULL OR u2.user_number IS NULL;

-- 10. 如果表不存在，创建表
SELECT '如果表不存在则创建:' AS '步骤';

-- 创建 repair_feedback 表（如果不存在）
CREATE TABLE IF NOT EXISTS `repair_feedback` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `repair_order_id` BIGINT NOT NULL UNIQUE,
    `student_number` VARCHAR(255) NOT NULL,
    `repairman_id` VARCHAR(255) NOT NULL,
    `rating` INT NOT NULL CHECK (`rating` >= 1 AND `rating` <= 5),
    `comment` TEXT,
    `created_at` DATETIME NOT NULL,
    FOREIGN KEY (`repair_order_id`) REFERENCES `repair_order`(`id`),
    FOREIGN KEY (`student_number`) REFERENCES `sys_user`(`user_number`),
    FOREIGN KEY (`repairman_id`) REFERENCES `sys_user`(`user_number`)
);

-- 验证创建结果
SELECT '最终验证 - repair_feedback 表:' AS '信息';
SELECT COUNT(*) AS feedback_count FROM repair_feedback;
