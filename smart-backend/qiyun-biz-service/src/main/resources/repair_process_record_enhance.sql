-- 维修过程记录表增强
-- 添加到场时间、维修描述、使用材料、完成时间等字段

-- 1. 添加到场时间字段
ALTER TABLE repair_process_record
ADD COLUMN arrived_at DATETIME COMMENT '到场时间';

-- 2. 添加维修描述字段
ALTER TABLE repair_process_record
ADD COLUMN repair_description TEXT COMMENT '维修描述';

-- 3. 添加使用材料字段
ALTER TABLE repair_process_record
ADD COLUMN materials_used TEXT COMMENT '使用材料（JSON格式）';

-- 4. 添加完成时间字段
ALTER TABLE repair_process_record
ADD COLUMN finished_at DATETIME COMMENT '完成时间';

-- 5. 添加耗时字段
ALTER TABLE repair_process_record
ADD COLUMN duration_minutes INT COMMENT '耗时（分钟）';

-- 6. 添加备注字段
ALTER TABLE repair_process_record
ADD COLUMN remarks TEXT COMMENT '备注';

-- 7. 修改content字段为可空（某些操作类型可能不需要content）
ALTER TABLE repair_process_record
MODIFY COLUMN content TEXT COMMENT '操作内容';

-- 示例数据说明：
-- materials_used 字段建议使用 JSON 格式存储，例如：
-- [
--   {"name": "空调滤网", "quantity": 1, "unit": "个", "price": 50.00},
--   {"name": "制冷剂", "quantity": 2, "unit": "瓶", "price": 80.00}
-- ]

-- 查看表结构
DESCRIBE repair_process_record;