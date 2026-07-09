import React from 'react';
import { Empty, Space, Tag, Timeline } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  FlagOutlined,
  StarOutlined,
  ToolOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';

const STATUS_META = {
  WAITING_ACCEPT: { label: '待受理', title: '学生提交报修', color: 'orange', icon: <FlagOutlined /> },
  IN_PROGRESS: { label: '处理中', title: '管理员派单', color: 'blue', icon: <UserSwitchOutlined /> },
  RESOLVED: { label: '已处理', title: '维修人员完成处理', color: 'green', icon: <ToolOutlined /> },
  WAITING_FEEDBACK: { label: '待评价', title: '等待学生评价', color: 'purple', icon: <ClockCircleOutlined /> },
  FEEDBACKED: { label: '已评价', title: '学生完成评价', color: 'green', icon: <StarOutlined /> },
  CLOSED: { label: '已关闭', title: '工单归档关闭', color: 'default', icon: <CheckCircleOutlined /> },
  REJECTED: { label: '已驳回', title: '工单被驳回', color: 'red', icon: <CloseCircleOutlined /> },
};

const normalizeStatus = (status) => {
  if (!status) return '';
  const value = String(status).trim();
  const map = {
    pending: 'WAITING_ACCEPT',
    processing: 'IN_PROGRESS',
    completed: 'RESOLVED',
    to_be_evaluated: 'WAITING_FEEDBACK',
    closed: 'CLOSED',
    rejected: 'REJECTED',
  };
  return map[value.toLowerCase()] || value.toUpperCase();
};

const formatTime = (value) => {
  if (!value) return '时间未记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const statusLabel = (status) => {
  const normalized = normalizeStatus(status);
  return STATUS_META[normalized]?.label || status || '未知状态';
};

const eventContent = ({ title, time, operatorId, oldStatus, newStatus, note }) => (
  <div style={{ lineHeight: 1.7 }}>
    <div style={{ fontWeight: 600, color: '#1f2937' }}>{title}</div>
    <div style={{ color: '#64748b', fontSize: 13 }}>{formatTime(time)}</div>
    {(oldStatus || newStatus) && (
      <div style={{ marginTop: 4 }}>
        {oldStatus && <Tag>{statusLabel(oldStatus)}</Tag>}
        {oldStatus && newStatus && <span style={{ marginRight: 8, color: '#94a3b8' }}>到</span>}
        {newStatus && <Tag color={STATUS_META[normalizeStatus(newStatus)]?.color}>{statusLabel(newStatus)}</Tag>}
      </div>
    )}
    {operatorId && <div style={{ color: '#64748b', fontSize: 13 }}>操作人：{operatorId}</div>}
    {note && <div style={{ color: '#475569', fontSize: 13 }}>{note}</div>}
  </div>
);

const logToTimelineItem = (log, index) => {
  const oldStatus = normalizeStatus(log.oldStatus);
  const newStatus = normalizeStatus(log.newStatus);
  const meta = STATUS_META[newStatus] || STATUS_META[oldStatus] || {
    title: '状态更新',
    color: 'blue',
    icon: <ClockCircleOutlined />,
  };
  const isSameStatusUpdate = oldStatus && newStatus && oldStatus === newStatus;
  const title = isSameStatusUpdate ? '处理记录更新' : meta.title;

  return {
    key: log.logId || `log-${index}`,
    color: meta.color,
    dot: meta.icon,
    children: eventContent({
      title,
      time: log.logTime,
      operatorId: log.operatorId,
      oldStatus: log.oldStatus,
      newStatus: log.newStatus,
    }),
  };
};

const fallbackItems = (order) => {
  const createdAt = order.createdAt || order.created_at;
  const assignedAt = order.assignedAt || order.assigned_at;
  const completedAt = order.completedAt || order.completed_at;
  const closedAt = order.closedAt || order.closed_at;
  const ratingTime = order.rating?.ratedAt || order.ratingTime || order.ratedAt;

  return [
    createdAt && {
      key: 'created',
      color: 'orange',
      dot: <FlagOutlined />,
      children: eventContent({ title: '学生提交报修', time: createdAt, newStatus: 'WAITING_ACCEPT' }),
    },
    assignedAt && {
      key: 'assigned',
      color: 'blue',
      dot: <UserSwitchOutlined />,
      children: eventContent({ title: '管理员派单', time: assignedAt, newStatus: 'IN_PROGRESS' }),
    },
    completedAt && {
      key: 'completed',
      color: 'green',
      dot: <ToolOutlined />,
      children: eventContent({ title: '维修人员完成处理', time: completedAt, newStatus: 'RESOLVED' }),
    },
    ratingTime && {
      key: 'rated',
      color: 'green',
      dot: <StarOutlined />,
      children: eventContent({ title: '学生完成评价', time: ratingTime, newStatus: 'FEEDBACKED' }),
    },
    closedAt && {
      key: 'closed',
      color: 'default',
      dot: <CheckCircleOutlined />,
      children: eventContent({ title: '工单归档关闭', time: closedAt, newStatus: 'CLOSED' }),
    },
  ].filter(Boolean);
};

function RepairTimeline({ order }) {
  const logs = Array.isArray(order?.logs) ? order.logs : [];
  const items = logs.length > 0 ? logs.map(logToTimelineItem) : fallbackItems(order || {});

  return (
    <div style={{ marginBottom: 24 }}>
      <Space style={{ marginBottom: 12 }}>
        <ClockCircleOutlined style={{ color: '#1677ff' }} />
        <h4 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: '#1f1f1f' }}>工单时间线</h4>
      </Space>
      {items.length > 0 ? (
        <Timeline items={items} style={{ marginTop: 8 }} />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无时间线记录" />
      )}
    </div>
  );
}

export default RepairTimeline;
