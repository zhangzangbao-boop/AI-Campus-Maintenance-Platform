import React, { useEffect, useMemo, useState } from "react";
import { Button, Empty, List, Modal, Skeleton, Space, Tag, Typography, message } from "antd";
import { NotificationOutlined, PushpinFilled, ReloadOutlined } from "@ant-design/icons";
import api from "../services/api";
import { SectionCard } from "./DashboardWidgets";

const { Paragraph, Text, Title } = Typography;

const TYPE_META = {
  GENERAL: { label: "综合公告", color: "blue" },
  MAINTENANCE: { label: "维修维护", color: "gold" },
  OUTAGE: { label: "服务中断", color: "red" },
  SAFETY: { label: "安全提醒", color: "volcano" },
};

const PRIORITY_META = {
  LOW: { label: "低", color: "default" },
  NORMAL: { label: "普通", color: "blue" },
  HIGH: { label: "重要", color: "orange" },
  URGENT: { label: "紧急", color: "red" },
};

const formatTime = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getData = (response, fallback) => response?.data ?? fallback;

function AnnouncementList() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);

  const load = async () => {
    setLoading(true);
    try {
      const response = await api.announcements.list();
      setItems(getData(response, []));
    } catch (error) {
      message.error(error.message || "公告加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const pinned = useMemo(() => items.filter((item) => item.pinned), [items]);

  return (
    <div className="dashboard-page">
      <SectionCard
        title="校园后勤公告"
        extra={<Button icon={<ReloadOutlined />} onClick={load} loading={loading}>刷新</Button>}
      >
        {loading ? (
          <Skeleton active paragraph={{ rows: 5 }} />
        ) : items.length ? (
          <List
            dataSource={items}
            renderItem={(item) => {
              const type = TYPE_META[item.type] || TYPE_META.GENERAL;
              const priority = PRIORITY_META[item.priority] || PRIORITY_META.NORMAL;
              return (
                <List.Item
                  actions={[<Button type="link" onClick={() => setSelected(item)}>查看详情</Button>]}
                >
                  <List.Item.Meta
                    avatar={<NotificationOutlined style={{ color: item.pinned ? "#faad14" : "#1677ff", fontSize: 18 }} />}
                    title={
                      <Space wrap>
                        <Text strong>{item.title}</Text>
                        {item.pinned && <Tag color="gold" icon={<PushpinFilled />}>置顶</Tag>}
                        <Tag color={type.color}>{type.label}</Tag>
                        <Tag color={priority.color}>{priority.label}</Tag>
                      </Space>
                    }
                    description={
                      <Space direction="vertical" size={4}>
                        <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>{item.content}</Paragraph>
                        <Text type="secondary">发布时间：{formatTime(item.publishTime || item.updatedAt)}{item.expireTime ? ` · 到期：${formatTime(item.expireTime)}` : ""}</Text>
                      </Space>
                    }
                  />
                </List.Item>
              );
            }}
          />
        ) : (
          <Empty description="暂无公告" />
        )}
      </SectionCard>

      {pinned.length > 0 && (
        <SectionCard title="置顶公告" style={{ marginTop: 16 }}>
          <Space direction="vertical" size={8} style={{ width: "100%" }}>
            {pinned.slice(0, 3).map((item) => (
              <Button key={item.id} type="link" onClick={() => setSelected(item)} style={{ paddingInline: 0 }}>
                <PushpinFilled /> {item.title}
              </Button>
            ))}
          </Space>
        </SectionCard>
      )}

      <Modal
        open={Boolean(selected)}
        title={selected?.title}
        onCancel={() => setSelected(null)}
        footer={<Button type="primary" onClick={() => setSelected(null)}>知道了</Button>}
      >
        {selected && (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Space wrap>
              <Tag color={(TYPE_META[selected.type] || TYPE_META.GENERAL).color}>{(TYPE_META[selected.type] || TYPE_META.GENERAL).label}</Tag>
              <Tag color={(PRIORITY_META[selected.priority] || PRIORITY_META.NORMAL).color}>{(PRIORITY_META[selected.priority] || PRIORITY_META.NORMAL).label}</Tag>
              {selected.pinned && <Tag color="gold">置顶</Tag>}
            </Space>
            <Paragraph style={{ whiteSpace: "pre-wrap" }}>{selected.content}</Paragraph>
            <Text type="secondary">发布时间：{formatTime(selected.publishTime || selected.updatedAt)}</Text>
            {selected.expireTime && <Text type="secondary">到期时间：{formatTime(selected.expireTime)}</Text>}
          </Space>
        )}
      </Modal>
    </div>
  );
}

export default AnnouncementList;