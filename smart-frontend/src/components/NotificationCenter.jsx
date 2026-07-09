import React, { useEffect, useState } from "react";
import { Badge, Button, Empty, List, Popover, Space, Tag, Tooltip, Typography, message } from "antd";
import { BellOutlined, CheckOutlined, ReloadOutlined } from "@ant-design/icons";
import api from "../services/api";

const { Text } = Typography;

const formatTime = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getPayload = (response, fallback) => response?.data ?? fallback;

const NotificationCenter = () => {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const fetchUnreadCount = async () => {
    try {
      const response = await api.notifications.unreadCount();
      const data = getPayload(response, {});
      setUnreadCount(Number(data.count || 0));
    } catch (error) {
      console.error("获取未读通知数量失败:", error);
    }
  };

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const response = await api.notifications.list();
      const data = getPayload(response, []);
      setNotifications(Array.isArray(data) ? data : []);
      setUnreadCount(Array.isArray(data) ? data.filter((item) => !item.readFlag).length : 0);
    } catch (error) {
      console.error("获取通知失败:", error);
      message.error(error.message || "获取通知失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUnreadCount();
    const timer = window.setInterval(fetchUnreadCount, 60000);
    return () => window.clearInterval(timer);
  }, []);

  const handleOpenChange = (nextOpen) => {
    setOpen(nextOpen);
    if (nextOpen) {
      fetchNotifications();
    }
  };

  const handleMarkRead = async (id) => {
    try {
      await api.notifications.markRead(id);
      setNotifications((items) =>
        items.map((item) => (item.id === id ? { ...item, readFlag: true } : item))
      );
      setUnreadCount((count) => Math.max(0, count - 1));
    } catch (error) {
      message.error(error.message || "标记通知失败");
    }
  };

  const handleNotificationClick = async (item) => {
    if (!item.readFlag) {
      await handleMarkRead(item.id);
    }

    if (item.relatedOrderId) {
      window.dispatchEvent(new CustomEvent("open-related-order", {
        detail: { orderId: item.relatedOrderId },
      }));
      setOpen(false);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.notifications.markAllRead();
      setNotifications((items) => items.map((item) => ({ ...item, readFlag: true })));
      setUnreadCount(0);
    } catch (error) {
      message.error(error.message || "标记全部已读失败");
    }
  };

  const content = (
    <div style={{ width: 380, maxWidth: "calc(100vw - 48px)" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 12,
        }}
      >
        <Space>
          <Text strong>通知中心</Text>
          {unreadCount > 0 && <Tag color="blue">{unreadCount} 条未读</Tag>}
        </Space>
        <Space size={4}>
          <Tooltip title="刷新">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              onClick={fetchNotifications}
            />
          </Tooltip>
          <Tooltip title="全部已读">
            <Button
              type="text"
              size="small"
              icon={<CheckOutlined />}
              disabled={unreadCount === 0}
              onClick={handleMarkAllRead}
            />
          </Tooltip>
        </Space>
      </div>

      {notifications.length === 0 && !loading ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" />
      ) : (
        <List
          loading={loading}
          dataSource={notifications}
          style={{ maxHeight: 420, overflow: "auto" }}
          renderItem={(item) => (
            <List.Item
              onClick={() => handleNotificationClick(item)}
              style={{
                alignItems: "flex-start",
                background: item.readFlag ? "#fff" : "#f0f7ff",
                borderRadius: 6,
                marginBottom: 8,
                padding: "10px 12px",
                border: "1px solid #eef2f7",
                cursor: item.relatedOrderId ? "pointer" : "default",
              }}
              actions={[
                !item.readFlag ? (
                  <Button
                    type="link"
                    size="small"
                    onClick={(event) => {
                      event.stopPropagation();
                      handleMarkRead(item.id);
                    }}
                  >
                    已读
                  </Button>
                ) : null,
              ].filter(Boolean)}
            >
              <List.Item.Meta
                title={
                  <Space size={6} wrap>
                    <Text strong={!item.readFlag}>{item.title}</Text>
                    {!item.readFlag && <Badge status="processing" />}
                  </Space>
                }
                description={
                  <Space direction="vertical" size={4} style={{ width: "100%" }}>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      {item.content}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {item.relatedOrderId ? `工单 #${item.relatedOrderId} · ` : ""}
                      {formatTime(item.createdAt)}
                    </Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      )}
    </div>
  );

  return (
    <Popover
      content={content}
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={handleOpenChange}
    >
      <Tooltip title="通知中心">
        <Badge count={unreadCount} size="small" overflowCount={99}>
          <Button
            type="text"
            shape="circle"
            icon={<BellOutlined />}
            style={{ color: "#1f1f1f" }}
          />
        </Badge>
      </Tooltip>
    </Popover>
  );
};

export default NotificationCenter;
