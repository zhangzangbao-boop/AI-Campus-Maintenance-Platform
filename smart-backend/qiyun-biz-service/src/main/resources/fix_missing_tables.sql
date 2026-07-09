-- 修复缺失的统计表
-- 请在MySQL中执行此脚本

USE repairdb;

-- 1. 创建分类统计汇总表（如果不存在）
CREATE TABLE IF NOT EXISTS category_stats (
    category_key       VARCHAR(50) PRIMARY KEY,
    total_tickets      INT NOT NULL DEFAULT 0,
    completed_tickets  INT NOT NULL DEFAULT 0,
    rated_tickets      INT NOT NULL DEFAULT 0,
    avg_rating         DECIMAL(4,2) NOT NULL DEFAULT 0.00
);

-- 2. 创建维修人员统计汇总表（如果不存在）
CREATE TABLE IF NOT EXISTS repairman_stats (
    repairman_id       VARCHAR(50) PRIMARY KEY,
    total_tickets      INT NOT NULL DEFAULT 0,
    completed_tickets  INT NOT NULL DEFAULT 0,
    rated_tickets      INT NOT NULL DEFAULT 0,
    avg_rating         DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    last_completed_at  DATETIME NULL
);

-- 3. 根据现有数据初始化分类统计
INSERT INTO category_stats (category_key, total_tickets, completed_tickets, rated_tickets, avg_rating)
SELECT
    o.category_key,
    COUNT(*) AS total_tickets,
    SUM(CASE WHEN o.status IN ('RESOLVED','WAITING_FEEDBACK','FEEDBACKED','CLOSED') THEN 1 ELSE 0 END) AS completed_tickets,
    0 AS rated_tickets,
    0.00 AS avg_rating
FROM repair_order o
WHERE o.category_key IS NOT NULL
GROUP BY o.category_key
ON DUPLICATE KEY UPDATE
    total_tickets = VALUES(total_tickets),
    completed_tickets = VALUES(completed_tickets);

-- 4. 根据现有数据初始化维修人员统计
INSERT INTO repairman_stats (repairman_id, total_tickets, completed_tickets, rated_tickets, avg_rating, last_completed_at)
SELECT
    o.repairman_id,
    COUNT(*) AS total_tickets,
    SUM(CASE WHEN o.status IN ('RESOLVED','WAITING_FEEDBACK','FEEDBACKED','CLOSED') THEN 1 ELSE 0 END) AS completed_tickets,
    0 AS rated_tickets,
    0.00 AS avg_rating,
    MAX(o.completed_at) AS last_completed_at
FROM repair_order o
WHERE o.repairman_id IS NOT NULL
GROUP BY o.repairman_id
ON DUPLICATE KEY UPDATE
    total_tickets = VALUES(total_tickets),
    completed_tickets = VALUES(completed_tickets),
    last_completed_at = VALUES(last_completed_at);

-- 验证表已创建
SELECT '分类统计表:' AS '表名';
SELECT * FROM category_stats;
SELECT '维修人员统计表:' AS '表名';
SELECT * FROM repairman_stats;
