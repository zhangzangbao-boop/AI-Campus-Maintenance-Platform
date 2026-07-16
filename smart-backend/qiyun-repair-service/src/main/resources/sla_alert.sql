-- SLA告警表（用于告警去重）
CREATE TABLE IF NOT EXISTS sla_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL COMMENT '关联工单ID',
    alert_level VARCHAR(20) NOT NULL COMMENT '告警级别：WARNING/OVERDUE',
    sla_type VARCHAR(20) NOT NULL COMMENT 'SLA类型：ACCEPTANCE/COMPLETION',
    ticket_priority VARCHAR(10) COMMENT '工单优先级快照',
    due_at DATETIME COMMENT '截止时间',
    remaining_hours BIGINT COMMENT '剩余小时（负数表示已超时）',
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE COMMENT '通知是否发送',
    priority_upgraded BOOLEAN NOT NULL DEFAULT FALSE COMMENT '优先级是否升级',
    created_at DATETIME NOT NULL COMMENT '告警时间',
    remark TEXT COMMENT '备注',
    CONSTRAINT uk_ticket_alert_level UNIQUE (ticket_id, alert_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA告警记录表';

-- 创建索引
CREATE INDEX idx_sla_ticket_id ON sla_alert(ticket_id);
CREATE INDEX idx_sla_alert_level ON sla_alert(alert_level);
CREATE INDEX idx_sla_created_at ON sla_alert(created_at);