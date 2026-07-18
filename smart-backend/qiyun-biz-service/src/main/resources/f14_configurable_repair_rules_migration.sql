-- F14: 分类、紧急程度与预警规则可配置化
-- 复用 sys_system_config 保存结构化规则；未配置时后端仍使用同等默认值。

INSERT INTO sys_system_config (config_key, config_value, description, created_at, updated_at)
VALUES
('ai.ticket.category-keywords',
'[
  {"category":"空调故障","keywords":["空调","制冷","制热","风机","遥控器"]},
  {"category":"管道故障","keywords":["漏水","滴水","水管","水龙头","下水","地漏","积水","堵塞"]},
  {"category":"电力故障","keywords":["断电","跳闸","插座","电路","电线","开关","照明","灯","频闪"]},
  {"category":"网络故障","keywords":["网络","wifi","无线","网口","断线","校园网","无法连接"]},
  {"category":"家具故障","keywords":["桌","椅","床","柜","门锁","家具"]},
  {"category":"门窗故障","keywords":["门","窗","玻璃","闭门器"]}
]',
'AI规则引擎：关键词与工单分类映射，结构化编辑。',
NOW(), NOW()),
('ai.ticket.urgency-rules',
'[
  {"urgency":"紧急","keywords":["漏水","积水","触电","烧焦","冒烟","火花","异味","总闸","消防","灭火器","玻璃","危险"]},
  {"urgency":"普通","keywords":["无法使用","不能用","断线","频闪","损坏","不制冷","堵塞","松动","脱落"]}
]',
'AI规则引擎：紧急程度判定关键词，结构化编辑。',
NOW(), NOW()),
('sla.ticket.rules',
'{
  "warningRatio":0.25,
  "priorities":{
    "high":{"responseHours":2,"completionHours":24},
    "medium":{"responseHours":8,"completionHours":72},
    "low":{"responseHours":24,"completionHours":168}
  }
}',
'SLA受理/完成/预警阈值，结构化编辑。',
NOW(), NOW()),
('fault-trend.rules',
'{
  "sevenDays":{"countThreshold":3,"growthThreshold":50.0},
  "thirtyDays":{"countThreshold":6,"growthThreshold":30.0}
}',
'高频故障7天、30天触发阈值，结构化编辑。',
NOW(), NOW())
ON DUPLICATE KEY UPDATE
  description = VALUES(description),
  updated_at = updated_at;
