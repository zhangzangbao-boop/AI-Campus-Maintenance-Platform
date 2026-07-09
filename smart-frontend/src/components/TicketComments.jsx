import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Image,
  Input,
  List,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import {
  AlertOutlined,
  MessageOutlined,
  ReloadOutlined,
  SendOutlined,
  UserOutlined,
} from "@ant-design/icons";
import api from "../services/api";

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const TYPE_META = {
  SUPPLEMENT: { label: "补充说明", color: "blue" },
  REPLY: { label: "处理回复", color: "green" },
  URGE: { label: "催单提醒", color: "red" },
};

const getResponseData = (response, fallback) => response?.data ?? fallback;

const normalizeRole = (role) => String(role || "").replace(/^ROLE_/i, "").toUpperCase();

const getCurrentRole = () => {
  try {
    const user = JSON.parse(localStorage.getItem("user") || "{}");
    return normalizeRole(user.role);
  } catch (error) {
    return "";
  }
};

const formatTime = (value) => {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const getImageUrl = (imageUrl) => {
  if (!imageUrl) {
    return "";
  }
  if (imageUrl.startsWith("http")) {
    return imageUrl;
  }
  return `http://localhost:8070${imageUrl.startsWith("/") ? "" : "/"}${imageUrl}`;
};

function TicketComments({ ticketId, role }) {
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [content, setContent] = useState("");
  const userRole = normalizeRole(role) || getCurrentRole();
  const isStudent = userRole === "STUDENT";
  const canReply = userRole === "STAFF" || userRole === "ADMIN";

  const loadComments = useCallback(async () => {
    if (!ticketId) {
      setComments([]);
      return;
    }

    setLoading(true);
    try {
      const response = await api.ticketComments.list(ticketId);
      const data = getResponseData(response, []);
      setComments(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error("Load ticket comments failed:", error);
      message.error(error.message || "获取沟通记录失败");
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    loadComments();
  }, [loadComments]);

  const placeholder = useMemo(() => {
    if (isStudent) {
      return "补充现场情况、影响范围或可维修时间，方便管理员和维修人员判断。";
    }
    if (canReply) {
      return "回复处理进展、预计完成时间或需要学生配合的信息。";
    }
    return "当前账号不可提交沟通记录。";
  }, [canReply, isStudent]);

  const submitComment = async (commentType) => {
    const text = content.trim();
    if (!text) {
      message.warning("请先填写沟通内容");
      return;
    }
    if (!ticketId) {
      message.error("缺少工单ID，无法提交沟通记录");
      return;
    }

    setSubmitting(true);
    try {
      if (commentType === "URGE") {
        await api.ticketComments.urge(ticketId, { content: text });
        message.success("催单已提交");
      } else {
        await api.ticketComments.add(ticketId, { content: text, commentType });
        message.success("沟通记录已提交");
      }
      setContent("");
      await loadComments();
    } catch (error) {
      message.error(error.message || "提交沟通记录失败");
    } finally {
      setSubmitting(false);
    }
  };

  const actionArea = (
    <div style={{ border: "1px solid #eef2f7", borderRadius: 8, padding: 12, background: "#fbfdff" }}>
      <TextArea
        value={content}
        onChange={(event) => setContent(event.target.value)}
        placeholder={placeholder}
        maxLength={1000}
        showCount
        autoSize={{ minRows: 3, maxRows: 6 }}
        disabled={!isStudent && !canReply}
      />
      <Space style={{ marginTop: 12 }}>
        {isStudent && (
          <>
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={submitting}
              onClick={() => submitComment("SUPPLEMENT")}
            >
              补充说明
            </Button>
            <Button
              danger
              icon={<AlertOutlined />}
              loading={submitting}
              onClick={() => submitComment("URGE")}
            >
              催单
            </Button>
          </>
        )}
        {canReply && (
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={submitting}
            onClick={() => submitComment("REPLY")}
          >
            回复
          </Button>
        )}
      </Space>
    </div>
  );

  return (
    <div style={{ marginBottom: 24 }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 12,
        }}
      >
        <Space>
          <MessageOutlined style={{ color: "#1677ff" }} />
          <h4 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#1f1f1f" }}>
            工单沟通记录
          </h4>
          <Tag color="default">{comments.length} 条</Tag>
        </Space>
        <Tooltip title="刷新">
          <Button
            type="text"
            size="small"
            icon={<ReloadOutlined />}
            onClick={loadComments}
            disabled={!ticketId}
          />
        </Tooltip>
      </div>

      {!ticketId ? (
        <Alert type="warning" showIcon message="缺少工单ID，暂时无法加载沟通记录。" />
      ) : (
        <>
          <Spin spinning={loading}>
            {comments.length > 0 ? (
              <List
                dataSource={comments}
                style={{ marginBottom: 12 }}
                renderItem={(item) => {
                  const meta = TYPE_META[item.commentType] || TYPE_META.REPLY;
                  const imageUrl = getImageUrl(item.imageUrl);

                  return (
                    <List.Item
                      style={{
                        alignItems: "flex-start",
                        border: "1px solid #eef2f7",
                        borderRadius: 8,
                        padding: "10px 12px",
                        marginBottom: 8,
                        background: "#fff",
                      }}
                    >
                      <List.Item.Meta
                        avatar={<UserOutlined style={{ color: "#64748b", marginTop: 4 }} />}
                        title={
                          <Space size={8} wrap>
                            <Text strong>{item.authorName || item.authorId || "未知用户"}</Text>
                            <Tag color={meta.color}>{meta.label}</Tag>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatTime(item.createdAt)}
                            </Text>
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={8} style={{ width: "100%" }}>
                            <Paragraph style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}>
                              {item.content}
                            </Paragraph>
                            {imageUrl && (
                              <Image
                                width={120}
                                height={90}
                                src={imageUrl}
                                style={{ objectFit: "cover", borderRadius: 6 }}
                              />
                            )}
                          </Space>
                        }
                      />
                    </List.Item>
                  );
                }}
              />
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无沟通记录"
                style={{ marginBottom: 12 }}
              />
            )}
          </Spin>
          {actionArea}
        </>
      )}
    </div>
  );
}

export default TicketComments;
