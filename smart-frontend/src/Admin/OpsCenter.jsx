import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  Card,
  Alert,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  message,
} from "antd";
import { ReloadOutlined, PlusOutlined } from "@ant-design/icons";
import api from "../services/api";

const categoryOptions = [
  "水电维修",
  "网络故障",
  "家具维修",
  "电器故障",
  "公共设施",
  "门窗维修",
  "卫生清洁",
  "消防安全",
  "空调维修",
  "其他",
].map((value) => ({ label: value, value }));

const OpsCenter = ({ initialTab = "knowledge" }) => {
  const [activeKey, setActiveKey] = useState(initialTab);

  useEffect(() => {
    setActiveKey(initialTab);
  }, [initialTab]);

  return (
    <div className="ops-page">
      <div className="ops-header">
        <div>
          <div className="page-hero-eyebrow">智能运维</div>
          <h2>智能运维中心</h2>
        </div>
        <Tag color="blue">V3</Tag>
      </div>
      <Card variant="borderless" className="ops-shell-card">
        <Tabs
          activeKey={activeKey}
          onChange={setActiveKey}
          items={[
            { key: "ai", label: "DeepSeek AI 服务", children: <AiServiceTab /> },
            { key: "knowledge", label: "维修知识库", children: <KnowledgeBaseTab /> },
            { key: "transfer", label: "转派审核", children: <TransferRequestTab /> },
            { key: "config", label: "系统配置", children: <SystemConfigTab /> },
            { key: "audit", label: "审计日志", children: <AuditLogTab /> },
          ]}
        />
      </Card>
    </div>
  );
};

const AiServiceTab = () => {
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState(null);

  const loadStatus = async () => {
    setLoading(true);
    try {
      const response = await api.ai.status();
      setStatus(response?.data || null);
    } catch (error) {
      message.error(`加载 AI 状态失败：${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const capabilities = Array.isArray(status?.capabilities) ? status.capabilities : [];

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card
        size="small"
        title="DeepSeek 服务状态"
        loading={loading}
        extra={<Button icon={<ReloadOutlined />} onClick={loadStatus} loading={loading}>刷新</Button>}
      >
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="AI 启用">{status?.aiEnabled ? "是" : "否"}</Descriptions.Item>
          <Descriptions.Item label="DeepSeek Key">{status?.aiConfigured ? "已接入" : "未接入"}</Descriptions.Item>
          <Descriptions.Item label="AI Provider">{status?.aiProvider || "-"}</Descriptions.Item>
          <Descriptions.Item label="AI Model">{status?.aiModel || "-"}</Descriptions.Item>
          <Descriptions.Item label="服务状态">{status?.deepSeekAvailable ? "在线" : "待配置"}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card size="small" title="AI 使用场景">
        <Space wrap>
          {capabilities.map((item) => (
            <Tag color="blue" key={item}>{item}</Tag>
          ))}
        </Space>
      </Card>
    </Space>
  );
};

const KnowledgeBaseTab = () => {
  const [loading, setLoading] = useState(false);
  const [draftLoading, setDraftLoading] = useState(false);
  const [rebuildLoading, setRebuildLoading] = useState(false);
  const [data, setData] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [editing, setEditing] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [draftOpen, setDraftOpen] = useState(false);
  const [form] = Form.useForm();
  const [draftForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.admin.getKnowledgeBase({ keyword, includeDisabled: true });
      setData(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      message.error(`加载知识库失败：${error.message}`);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ enabled: true, estimatedMinutes: 30 });
    setModalOpen(true);
  };

  const openAiDraft = () => {
    draftForm.resetFields();
    draftForm.setFieldsValue({ categoryKey: "水电维修" });
    setDraftOpen(true);
  };

  const openEdit = (record) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await api.admin.updateKnowledgeBase(editing.knowledgeId, values);
      } else {
        await api.admin.createKnowledgeBase(values);
      }
      message.success("保存成功");
      setModalOpen(false);
      loadData();
    } catch (error) {
      message.error(`保存失败：${error.message}`);
    }
  };

  const generateDraft = async () => {
    const values = await draftForm.validateFields();
    setDraftLoading(true);
    try {
      const response = await api.ai.draftKnowledge(values);
      const draft = response?.data || {};
      setEditing(null);
      form.resetFields();
      form.setFieldsValue({
        categoryKey: draft.categoryKey || values.categoryKey,
        title: draft.title,
        symptomKeywords: draft.symptomKeywords,
        solutionSteps: draft.solutionSteps,
        safetyNotes: draft.safetyNotes,
        estimatedMinutes: draft.estimatedMinutes || 30,
        enabled: true,
      });
      setDraftOpen(false);
      setModalOpen(true);
      message.success("AI 知识库草稿已生成，请确认后保存");
    } catch (error) {
      message.error(`生成草稿失败：${error.message}`);
    } finally {
      setDraftLoading(false);
    }
  };

  const remove = async (record) => {
    try {
      await api.admin.deleteKnowledgeBase(record.knowledgeId);
      message.success("删除成功");
      loadData();
    } catch (error) {
      message.error(`删除失败：${error.message}`);
    }
  };

  const rebuildIndex = async () => {
    setRebuildLoading(true);
    try {
      const response = await api.admin.rebuildKnowledgeBaseIndex();
      const count = response?.data?.syncedCount || 0;
      message.success(`向量索引重建成功，已同步 ${count} 条知识`);
    } catch (error) {
      message.error(`重建索引失败：${error.message}`);
    } finally {
      setRebuildLoading(false);
    }
  };

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="搜索标题、关键词、处理步骤"
          allowClear
          onSearch={setKeyword}
          style={{ width: 320 }}
        />
        <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增知识
        </Button>
        <Button icon={<PlusOutlined />} onClick={openAiDraft}>
          AI 生成知识草稿
        </Button>
        <Popconfirm
          title="确认重建向量索引？这将清空现有索引并重新同步所有启用的知识条目。"
          onConfirm={rebuildIndex}
        >
          <Button loading={rebuildLoading}>
            重建向量索引
          </Button>
        </Popconfirm>
      </Space>
      <Table
        loading={loading}
        dataSource={data}
        rowKey="knowledgeId"
        size="small"
        pagination={{ pageSize: 8 }}
        columns={[
          { title: "标题", dataIndex: "title", key: "title", ellipsis: true },
          { title: "分类", dataIndex: "categoryKey", key: "categoryKey", width: 100 },
          { title: "关键词", dataIndex: "symptomKeywords", key: "symptomKeywords", ellipsis: true },
          { title: "预计", dataIndex: "estimatedMinutes", key: "estimatedMinutes", width: 80, render: (value) => value ? `${value} 分` : "-" },
          { title: "启用", dataIndex: "enabled", key: "enabled", width: 80, render: (value) => <Tag color={value ? "green" : "default"}>{value ? "启用" : "停用"}</Tag> },
          {
            title: "操作",
            key: "action",
            width: 150,
            render: (_, record) => (
              <Space>
                <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                <Popconfirm title="确认删除该知识条目？" onConfirm={() => remove(record)}>
                  <Button size="small" danger>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? "编辑知识条目" : "新增知识条目"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: "请输入标题" }]}>
            <Input />
          </Form.Item>
          <Form.Item name="categoryKey" label="分类" rules={[{ required: true, message: "请选择分类" }]}>
            <Select options={categoryOptions} />
          </Form.Item>
          <Form.Item name="symptomKeywords" label="症状关键词">
            <Input placeholder="用逗号分隔，例如：漏水,水管,积水" />
          </Form.Item>
          <Form.Item name="solutionSteps" label="处理步骤">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="safetyNotes" label="安全注意事项">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Space>
            <Form.Item name="estimatedMinutes" label="预计处理分钟">
              <InputNumber min={1} max={10080} />
            </Form.Item>
            <Form.Item name="enabled" label="是否启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="AI 生成知识库草稿"
        open={draftOpen}
        onCancel={() => setDraftOpen(false)}
        onOk={generateDraft}
        confirmLoading={draftLoading}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="输入分类和故障现象，系统会生成可编辑的知识库条目。"
        />
        <Form form={draftForm} layout="vertical">
          <Form.Item
            name="categoryKey"
            label="知识分类"
            rules={[{ required: true, message: "请选择分类" }]}
          >
            <Select options={categoryOptions} />
          </Form.Item>
          <Form.Item
            name="symptomText"
            label="故障现象或处理目标"
            rules={[{ required: true, message: "请输入故障现象" }]}
          >
            <Input.TextArea
              rows={4}
              maxLength={500}
              showCount
              placeholder="例如：宿舍卫生间地漏堵塞返味，地面积水，学生反馈晚上异味明显。"
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const SystemConfigTab = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [editing, setEditing] = useState(null);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await api.admin.getSystemConfig();
      setData(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      message.error(`加载系统配置失败：${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openEdit = (record) => {
    setEditing(record);
    form.setFieldsValue(toConfigFormValues(record));
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      await api.admin.updateSystemConfig(editing.configKey, toConfigRequest(editing, values));
      message.success("配置已保存");
      setEditing(null);
      loadData();
    } catch (error) {
      message.error(`保存配置失败：${error.message}`);
    }
  };

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
      </Space>
      <Table
        loading={loading}
        dataSource={data}
        rowKey="configKey"
        size="small"
        pagination={{ pageSize: 10 }}
        columns={[
          { title: "配置项", dataIndex: "configKey", key: "configKey", width: 220 },
          {
            title: "配置值",
            dataIndex: "configValue",
            key: "configValue",
            ellipsis: true,
            render: (value, record) => summarizeConfigValue(record.configKey, value),
          },
          { title: "说明", dataIndex: "description", key: "description", ellipsis: true },
          { title: "更新人", dataIndex: "updatedBy", key: "updatedBy", width: 100 },
          {
            title: "操作",
            key: "action",
            width: 90,
            render: (_, record) => <Button size="small" onClick={() => openEdit(record)}>编辑</Button>,
          },
        ]}
      />

      <Modal
        title="编辑系统配置"
        open={Boolean(editing)}
        onCancel={() => setEditing(null)}
        onOk={submit}
        destroyOnHidden
        width={760}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="configKey" label="配置项">
            <Input disabled />
          </Form.Item>
          {renderConfigEditor(editing)}
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const RULE_CONFIG_KEYS = new Set([
  "ai.ticket.category-keywords",
  "ai.ticket.urgency-rules",
  "sla.ticket.rules",
  "fault-trend.rules",
]);

const parseJson = (value, fallback) => {
  try {
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
};

const splitKeywords = (value) => String(value || "")
  .split(/[,，、\s]+/)
  .map((item) => item.trim())
  .filter(Boolean);

const toConfigFormValues = (record) => {
  if (!record || !RULE_CONFIG_KEYS.has(record.configKey)) {
    return record;
  }
  const base = { configKey: record.configKey, description: record.description };
  if (record.configKey === "ai.ticket.category-keywords") {
    return {
      ...base,
      categoryRules: parseJson(record.configValue, []).map((item) => ({
        category: item.category,
        keywords: Array.isArray(item.keywords) ? item.keywords.join("，") : "",
      })),
    };
  }
  if (record.configKey === "ai.ticket.urgency-rules") {
    return {
      ...base,
      urgencyRules: parseJson(record.configValue, []).map((item) => ({
        urgency: item.urgency,
        keywords: Array.isArray(item.keywords) ? item.keywords.join("，") : "",
      })),
    };
  }
  if (record.configKey === "sla.ticket.rules") {
    const rules = parseJson(record.configValue, {});
    return {
      ...base,
      warningRatio: rules.warningRatio ?? 0.25,
      highResponseHours: rules.priorities?.high?.responseHours ?? 2,
      highCompletionHours: rules.priorities?.high?.completionHours ?? 24,
      mediumResponseHours: rules.priorities?.medium?.responseHours ?? 8,
      mediumCompletionHours: rules.priorities?.medium?.completionHours ?? 72,
      lowResponseHours: rules.priorities?.low?.responseHours ?? 24,
      lowCompletionHours: rules.priorities?.low?.completionHours ?? 168,
    };
  }
  const rules = parseJson(record.configValue, {});
  return {
    ...base,
    sevenDayCountThreshold: rules.sevenDays?.countThreshold ?? 3,
    sevenDayGrowthThreshold: rules.sevenDays?.growthThreshold ?? 50,
    thirtyDayCountThreshold: rules.thirtyDays?.countThreshold ?? 6,
    thirtyDayGrowthThreshold: rules.thirtyDays?.growthThreshold ?? 30,
  };
};

const toConfigRequest = (editing, values) => {
  if (!editing || !RULE_CONFIG_KEYS.has(editing.configKey)) {
    return values;
  }
  let configValue;
  if (editing.configKey === "ai.ticket.category-keywords") {
    configValue = JSON.stringify((values.categoryRules || []).map((item) => ({
      category: item.category,
      keywords: splitKeywords(item.keywords),
    })), null, 2);
  } else if (editing.configKey === "ai.ticket.urgency-rules") {
    configValue = JSON.stringify((values.urgencyRules || []).map((item) => ({
      urgency: item.urgency,
      keywords: splitKeywords(item.keywords),
    })), null, 2);
  } else if (editing.configKey === "sla.ticket.rules") {
    configValue = JSON.stringify({
      warningRatio: values.warningRatio,
      priorities: {
        high: { responseHours: values.highResponseHours, completionHours: values.highCompletionHours },
        medium: { responseHours: values.mediumResponseHours, completionHours: values.mediumCompletionHours },
        low: { responseHours: values.lowResponseHours, completionHours: values.lowCompletionHours },
      },
    }, null, 2);
  } else {
    configValue = JSON.stringify({
      sevenDays: {
        countThreshold: values.sevenDayCountThreshold,
        growthThreshold: values.sevenDayGrowthThreshold,
      },
      thirtyDays: {
        countThreshold: values.thirtyDayCountThreshold,
        growthThreshold: values.thirtyDayGrowthThreshold,
      },
    }, null, 2);
  }
  return { configValue, description: values.description };
};

const summarizeConfigValue = (key, value) => {
  if (key === "ai.ticket.category-keywords") {
    const rules = parseJson(value, []);
    return `${rules.length} 个分类映射`;
  }
  if (key === "ai.ticket.urgency-rules") {
    const rules = parseJson(value, []);
    return `${rules.length} 条紧急程度规则`;
  }
  if (key === "sla.ticket.rules") {
    const rules = parseJson(value, {});
    return `预警比例 ${Math.round((rules.warningRatio ?? 0.25) * 100)}%，高/中/低 SLA 已配置`;
  }
  if (key === "fault-trend.rules") {
    const rules = parseJson(value, {});
    return `7天 ${rules.sevenDays?.countThreshold ?? 3} 单，30天 ${rules.thirtyDays?.countThreshold ?? 6} 单`;
  }
  return value;
};

const renderKeywordRuleEditor = (name, labelField, labelText) => (
  <Form.List name={name}>
    {(fields, { add, remove }) => (
      <Space direction="vertical" style={{ width: "100%" }}>
        {fields.map(({ key, name: fieldName, ...restField }) => (
          <Space key={key} align="baseline" style={{ display: "flex" }}>
            <Form.Item
              {...restField}
              name={[fieldName, labelField]}
              label={labelText}
              rules={[{ required: true, message: `请输入${labelText}` }]}
            >
              <Input style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              {...restField}
              name={[fieldName, "keywords"]}
              label="关键词"
              rules={[{ required: true, message: "请输入关键词" }]}
            >
              <Input style={{ width: 420 }} placeholder="多个关键词用逗号、顿号或空格分隔" />
            </Form.Item>
            <Button danger onClick={() => remove(fieldName)}>删除</Button>
          </Space>
        ))}
        <Button type="dashed" icon={<PlusOutlined />} onClick={() => add()}>
          添加规则
        </Button>
      </Space>
    )}
  </Form.List>
);

const renderConfigEditor = (editing) => {
  if (!editing || !RULE_CONFIG_KEYS.has(editing.configKey)) {
    return (
      <Form.Item name="configValue" label="配置值">
        <Input.TextArea rows={3} />
      </Form.Item>
    );
  }
  if (editing.configKey === "ai.ticket.category-keywords") {
    return renderKeywordRuleEditor("categoryRules", "category", "分类");
  }
  if (editing.configKey === "ai.ticket.urgency-rules") {
    return renderKeywordRuleEditor("urgencyRules", "urgency", "紧急程度");
  }
  if (editing.configKey === "sla.ticket.rules") {
    return (
      <>
        <Form.Item name="warningRatio" label="预警比例" rules={[{ required: true, message: "请输入预警比例" }]}>
          <InputNumber min={0.01} max={0.9} step={0.05} style={{ width: 180 }} />
        </Form.Item>
        <Space wrap>
          {["high", "medium", "low"].map((priority) => (
            <Card key={priority} size="small" title={`${priority} 优先级`} style={{ width: 220 }}>
              <Form.Item name={`${priority}ResponseHours`} label="受理时限(小时)" rules={[{ required: true }]}>
                <InputNumber min={1} max={720} style={{ width: "100%" }} />
              </Form.Item>
              <Form.Item name={`${priority}CompletionHours`} label="完成时限(小时)" rules={[{ required: true }]}>
                <InputNumber min={1} max={2160} style={{ width: "100%" }} />
              </Form.Item>
            </Card>
          ))}
        </Space>
      </>
    );
  }
  return (
    <Space wrap>
      <Card size="small" title="7天高频故障" style={{ width: 260 }}>
        <Form.Item name="sevenDayCountThreshold" label="触发单数" rules={[{ required: true }]}>
          <InputNumber min={1} max={1000} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="sevenDayGrowthThreshold" label="增长率阈值(%)" rules={[{ required: true }]}>
          <InputNumber min={0} max={10000} style={{ width: "100%" }} />
        </Form.Item>
      </Card>
      <Card size="small" title="30天高频故障" style={{ width: 260 }}>
        <Form.Item name="thirtyDayCountThreshold" label="触发单数" rules={[{ required: true }]}>
          <InputNumber min={1} max={1000} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="thirtyDayGrowthThreshold" label="增长率阈值(%)" rules={[{ required: true }]}>
          <InputNumber min={0} max={10000} style={{ width: "100%" }} />
        </Form.Item>
      </Card>
    </Space>
  );
};

const TransferRequestTab = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [repairmen, setRepairmen] = useState([]);
  const [decisionTarget, setDecisionTarget] = useState(null);
  const [decisionType, setDecisionType] = useState("approve");
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const [requestResponse, repairmanResponse] = await Promise.all([
        api.admin.getTransferRequests({ pendingOnly: true }),
        api.admin.getRepairmen(),
      ]);
      setData(Array.isArray(requestResponse?.data) ? requestResponse.data : []);
      const list = Array.isArray(repairmanResponse) ? repairmanResponse : repairmanResponse?.data || [];
      setRepairmen(list.map(item => ({
        label: `${item.nickname || item.name || item.userId}（${item.userId}）`,
        value: item.userId,
      })));
    } catch (error) {
      message.error(`加载转派申请失败：${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openDecision = (record, type) => {
    setDecisionTarget(record);
    setDecisionType(type);
    form.resetFields();
    form.setFieldsValue({
      reason: type === "approve" ? "同意转派，已重新分配维修人员。" : "暂不同意转派，请继续处理并补充现场说明。",
    });
  };

  const submitDecision = async () => {
    const values = await form.validateFields();
    try {
      await api.admin.decideTransferRequest(decisionTarget.id, {
        approved: decisionType === "approve",
        newStaffId: values.newStaffId,
        reason: values.reason,
      });
      message.success("转派申请已处理");
      setDecisionTarget(null);
      loadData();
    } catch (error) {
      message.error(`处理失败：${error.message}`);
    }
  };

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
      </Space>
      <Table
        loading={loading}
        dataSource={data}
        rowKey="id"
        size="small"
        pagination={{ pageSize: 8 }}
        columns={[
          { title: "申请时间", dataIndex: "createdAt", key: "createdAt", width: 170, render: (value) => value ? new Date(value).toLocaleString() : "-" },
          { title: "工单", dataIndex: "ticketId", key: "ticketId", width: 90, render: (value) => `#${value}` },
          { title: "申请人", dataIndex: "staffName", key: "staffName", width: 140, render: (value, record) => value || record.staffId },
          { title: "转派原因", dataIndex: "content", key: "content", ellipsis: true },
          {
            title: "操作",
            key: "action",
            width: 170,
            render: (_, record) => (
              <Space>
                <Button type="primary" size="small" onClick={() => openDecision(record, "approve")}>
                  通过
                </Button>
                <Button size="small" danger onClick={() => openDecision(record, "reject")}>
                  驳回
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={decisionType === "approve" ? "通过转派申请" : "驳回转派申请"}
        open={Boolean(decisionTarget)}
        onCancel={() => setDecisionTarget(null)}
        onOk={submitDecision}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {decisionType === "approve" && (
            <Form.Item
              name="newStaffId"
              label="新的维修人员"
              rules={[{ required: true, message: "请选择新的维修人员" }]}
            >
              <Select options={repairmen} showSearch optionFilterProp="label" />
            </Form.Item>
          )}
          <Form.Item
            name="reason"
            label="审批说明"
            rules={[{ required: true, message: "请填写审批说明" }]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

const AuditLogTab = () => {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [keyword, setKeyword] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.admin.getAuditLogs({ keyword, limit: 200 });
      setData(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      message.error(`加载审计日志失败：${error.message}`);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  return (
    <>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="搜索模块、动作、对象或详情"
          allowClear
          onSearch={setKeyword}
          style={{ width: 320 }}
        />
        <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
          刷新
        </Button>
      </Space>
      <Table
        loading={loading}
        dataSource={data}
        rowKey="auditId"
        size="small"
        pagination={{ pageSize: 10 }}
        columns={[
          { title: "时间", dataIndex: "createdAt", key: "createdAt", width: 170, render: (value) => value ? new Date(value).toLocaleString() : "-" },
          { title: "操作人", dataIndex: "actorName", key: "actorName", width: 120, render: (value, record) => value || record.actorId || "-" },
          { title: "模块", dataIndex: "module", key: "module", width: 100 },
          { title: "动作", dataIndex: "action", key: "action", width: 110 },
          { title: "对象", dataIndex: "targetId", key: "targetId", width: 100 },
          { title: "详情", dataIndex: "detail", key: "detail", ellipsis: true },
          { title: "结果", dataIndex: "success", key: "success", width: 80, render: (value) => <Tag color={value ? "green" : "red"}>{value ? "成功" : "失败"}</Tag> },
        ]}
      />
    </>
  );
};

export default OpsCenter;
