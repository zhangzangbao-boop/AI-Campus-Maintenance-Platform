import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Image,
  Input,
  List,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
} from "antd";
import {
  CameraOutlined,
  CheckCircleOutlined,
  EnvironmentOutlined,
  ReloadOutlined,
  SendOutlined,
  SwapOutlined,
  ToolOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import api from "../services/api";

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

const ACTION_META = {
  ARRIVED: { label: "到场确认", color: "blue", icon: <EnvironmentOutlined /> },
  MATERIAL_USED: { label: "耗材记录", color: "gold", icon: <ToolOutlined /> },
  FINISHED: { label: "维修记录", color: "green", icon: <CheckCircleOutlined /> },
  TRANSFER_REQUEST: { label: "转派申请", color: "red", icon: <SwapOutlined /> },
  TRANSFER_APPROVED: { label: "转派通过", color: "green", icon: <SwapOutlined /> },
  TRANSFER_REJECTED: { label: "转派驳回", color: "orange", icon: <SwapOutlined /> },
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
  return `http://localhost:8080${imageUrl.startsWith("/") ? "" : "/"}${imageUrl}`;
};

function RepairProcessRecords({ ticketId, role, editable = false }) {
  const [form] = Form.useForm();
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fileList, setFileList] = useState([]);
  const userRole = normalizeRole(role) || getCurrentRole();
  const canAdd = editable && userRole === "STAFF";

  const loadRecords = useCallback(async () => {
    if (!ticketId) {
      setRecords([]);
      return;
    }

    setLoading(true);
    try {
      const response = await api.processRecords.list(ticketId);
      const data = getResponseData(response, []);
      setRecords(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error("Load repair process records failed:", error);
      message.error(error.message || "获取维修过程记录失败");
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

  const actionOptions = useMemo(
    () =>
      Object.entries(ACTION_META)
      .filter(([value]) => !["TRANSFER_APPROVED", "TRANSFER_REJECTED"].includes(value))
      .map(([value, meta]) => ({
        value,
        label: meta.label,
      })),
    []
  );

  const uploadImageIfNeeded = async () => {
    const file = fileList[0]?.originFileObj;
    if (!file) {
      return "";
    }
    const response = await api.common.uploadImages([file]);
    const first = Array.isArray(response) ? response[0] : response?.data?.[0];
    return first?.url || first?.imageUrl || "";
  };

  const handleSubmit = async (values) => {
    if (!ticketId) {
      message.error("缺少工单ID，无法提交维修过程记录");
      return;
    }

    setSubmitting(true);
    try {
      const imageUrl = await uploadImageIfNeeded();
      if (values.actionType === "ARRIVED") {
        await api.repairman.arriveTask(ticketId, {
          content: values.content,
          imageUrl,
        });
      } else if (values.actionType === "TRANSFER_REQUEST") {
        await api.repairman.requestTransfer(ticketId, {
          reason: values.content,
          imageUrl,
        });
      } else {
        await api.repairman.addProcessRecord(ticketId, {
          actionType: values.actionType,
          content: values.content,
          imageUrl,
        });
      }
      message.success("维修过程记录已提交");
      form.resetFields();
      setFileList([]);
      await loadRecords();
    } catch (error) {
      message.error(error.message || "提交维修过程记录失败");
    } finally {
      setSubmitting(false);
    }
  };

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
          <CameraOutlined style={{ color: "#1677ff" }} />
          <h4 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: "#1f1f1f" }}>
            维修过程记录
          </h4>
          <Tag color="default">{records.length} 条</Tag>
        </Space>
        <Tooltip title="刷新">
          <Button
            type="text"
            size="small"
            icon={<ReloadOutlined />}
            onClick={loadRecords}
            disabled={!ticketId}
          />
        </Tooltip>
      </div>

      {!ticketId ? (
        <Alert type="warning" showIcon message="缺少工单ID，暂时无法加载维修过程记录。" />
      ) : (
        <>
          <Spin spinning={loading}>
            {records.length > 0 ? (
              <List
                dataSource={records}
                style={{ marginBottom: canAdd ? 12 : 0 }}
                renderItem={(item) => {
                  const meta = ACTION_META[item.actionType] || ACTION_META.FINISHED;
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
                        avatar={<span style={{ color: "#64748b", marginTop: 4 }}>{meta.icon}</span>}
                        title={
                          <Space size={8} wrap>
                            <Text strong>{item.staffName || item.staffId || "维修人员"}</Text>
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
                description="暂无维修过程记录"
                style={{ marginBottom: canAdd ? 12 : 0 }}
              />
            )}
          </Spin>

          {canAdd && (
            <div style={{ border: "1px solid #eef2f7", borderRadius: 8, padding: 12, background: "#fbfdff" }}>
              <Form form={form} layout="vertical" onFinish={handleSubmit}>
                <Form.Item
                  label="记录类型"
                  name="actionType"
                  initialValue="ARRIVED"
                  rules={[{ required: true, message: "请选择记录类型" }]}
                >
                  <Select options={actionOptions} />
                </Form.Item>
                <Form.Item
                  label="过程说明"
                  name="content"
                  rules={[
                    { required: true, message: "请填写过程说明" },
                    { max: 1000, message: "过程说明不能超过1000字" },
                  ]}
                >
                  <TextArea
                    rows={3}
                    maxLength={1000}
                    showCount
                    placeholder="记录到场情况、维修过程、使用耗材、完成结果或转派原因。"
                  />
                </Form.Item>
                <Form.Item label="现场图片">
                  <Upload
                    beforeUpload={() => false}
                    maxCount={1}
                    accept="image/*"
                    fileList={fileList}
                    onChange={({ fileList: nextFileList }) => setFileList(nextFileList)}
                  >
                    <Button icon={<UploadOutlined />}>选择图片</Button>
                  </Upload>
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting}>
                  提交过程记录
                </Button>
              </Form>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default RepairProcessRecords;
