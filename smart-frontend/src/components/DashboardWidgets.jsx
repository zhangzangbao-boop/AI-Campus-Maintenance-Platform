import React from "react";
import { Button, Card, Empty, Skeleton, Space, Statistic, Tag, Typography } from "antd";
import { ArrowRightOutlined } from "@ant-design/icons";
import { repairUtils } from "../services/repairService";

const { Text, Title } = Typography;

export const PageHero = ({ eyebrow, title, description, actions, children }) => (
  <div className="page-hero">
    <div className="page-hero-main">
      {eyebrow && <div className="page-hero-eyebrow">{eyebrow}</div>}
      <Title level={2}>{title}</Title>
      {description && <Text>{description}</Text>}
      {actions && <div className="page-hero-actions">{actions}</div>}
    </div>
    {children && <div className="page-hero-extra">{children}</div>}
  </div>
);

export const SectionCard = ({ title, extra, children, className = "", ...props }) => (
  <Card
    className={`section-card hover-lift ${className}`.trim()}
    title={title}
    extra={extra}
    {...props}
  >
    {children}
  </Card>
);

export const MetricCard = ({
  title,
  value,
  suffix,
  icon,
  color = "blue",
  description,
  loading = false,
}) => (
  <Card className={`metric-card metric-card-${color} hover-lift`}>
    {loading ? (
      <Skeleton active paragraph={{ rows: 1 }} title={false} />
    ) : (
      <>
        <div className="metric-card-top">
          <span className="metric-icon">{icon}</span>
          <Statistic title={title} value={value} suffix={suffix} />
        </div>
        {description && <div className="metric-description">{description}</div>}
      </>
    )}
  </Card>
);

export const StatusTag = ({ status }) => {
  const info = repairUtils.getStatusInfo(status);
  return <Tag className={`status-tag status-tag-${status || "default"}`}>{info.label}</Tag>;
};

export const PriorityTag = ({ priority }) => {
  const info = repairUtils.getPriorityInfo(priority);
  return <Tag className={`priority-tag priority-tag-${priority || "default"}`}>{info.label}</Tag>;
};

export const EmptyBlock = ({ description = "暂无数据", actionText, onAction }) => (
  <div className="empty-block">
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description}>
      {actionText && (
        <Button type="primary" onClick={onAction} icon={<ArrowRightOutlined />}>
          {actionText}
        </Button>
      )}
    </Empty>
  </div>
);

export const QuickActionGrid = ({ items = [] }) => (
  <div className="quick-action-grid">
    {items.map((item) => (
      <button
        type="button"
        className="quick-action-card hover-lift"
        key={item.key || item.title}
        onClick={item.onClick}
      >
        <span className={`quick-action-icon quick-action-${item.color || "blue"}`}>
          {item.icon}
        </span>
        <span className="quick-action-copy">
          <strong>{item.title}</strong>
          <small>{item.description}</small>
        </span>
        <ArrowRightOutlined />
      </button>
    ))}
  </div>
);

export const MiniList = ({ items = [], renderItem }) => (
  <Space direction="vertical" size={10} className="mini-list">
    {items.map((item, index) => (
      <div className="mini-list-item" key={item.id || item.ticketId || index}>
        {renderItem(item, index)}
      </div>
    ))}
  </Space>
);
