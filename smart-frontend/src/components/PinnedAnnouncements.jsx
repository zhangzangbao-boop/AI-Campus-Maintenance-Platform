import React, { useEffect, useMemo, useState } from "react";
import { Alert, Button, Space } from "antd";
import { NotificationOutlined } from "@ant-design/icons";
import api from "../services/api";

function PinnedAnnouncements({ onOpen }) {
  const [items, setItems] = useState([]);

  useEffect(() => {
    let mounted = true;
    api.announcements.list()
      .then((response) => {
        if (mounted) setItems(response?.data || []);
      })
      .catch(() => {
        if (mounted) setItems([]);
      });
    return () => {
      mounted = false;
    };
  }, []);

  const pinned = useMemo(() => items.filter((item) => item.pinned).slice(0, 2), [items]);
  if (!pinned.length) return null;

  return (
    <Space direction="vertical" size={8} style={{ width: "100%", marginBottom: 16 }}>
      {pinned.map((item) => (
        <Alert
          key={item.id}
          showIcon
          icon={<NotificationOutlined />}
          type={item.priority === "URGENT" ? "error" : item.priority === "HIGH" ? "warning" : "info"}
          message={item.title}
          description={item.content?.length > 80 ? `${item.content.slice(0, 80)}...` : item.content}
          action={onOpen ? <Button size="small" onClick={() => onOpen("announcements")}>查看</Button> : null}
        />
      ))}
    </Space>
  );
}

export default PinnedAnnouncements;