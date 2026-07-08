-- 测试学生报修数据
USE repairdb;

-- 1. 检查报修单表数据
SELECT '报修单总数:' AS info;
SELECT COUNT(*) AS total_count FROM repair_order;

SELECT '报修单详细数据:' AS info;
SELECT
    id,
    student_number,
    category_key,
    repairman_id,
    status,
    location,
    description,
    priority,
    created_at,
    assigned_at,
    completed_at,
    is_deleted
FROM repair_order
ORDER BY created_at DESC
LIMIT 10;

-- 2. 检查学生用户数据
SELECT '学生用户:' AS info;
SELECT user_number, name, role, enabled FROM sys_user WHERE role = 'STUDENT';

-- 3. 检查特定学生的报修单
SELECT '学号20260001的报修单:' AS info;
SELECT
    id,
    status,
    location,
    description,
    created_at,
    repairman_id
FROM repair_order
WHERE student_number = '20260001'
ORDER BY created_at DESC;

-- 4. 检查各状态的报修单数量
SELECT '各状态报修单数量:' AS info;
SELECT
    status,
    COUNT(*) AS count
FROM repair_order
GROUP BY status
ORDER BY status;
