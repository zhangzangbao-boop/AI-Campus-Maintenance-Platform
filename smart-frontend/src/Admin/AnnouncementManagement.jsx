import React, { useEffect, useState } from "react";
import { Button, DatePicker, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message } from "antd";
import { EditOutlined, NotificationOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import api from "../services/api";
import { SectionCard } from "../components/DashboardWidgets";

const { TextArea } = Input;

const STATUS_COLOR = {
  DRAFT: "default",
  PUBLISHED: "green",
  WITHDRAWN: "orange",
};

function toFormValues(row) {
  return {
    title: row?.title,
    content: row?.content,
    type: row?.type || "GENERAL",
    priority: row?.priority || "NORMAL",
    publishTime: row?.publishTime ? dayjs(row.publishTime) : null,
    expireTime: row?.expireTime ? dayjs(row.expireTime) : null,
    pinned: Boolean(row?.pinned),
  };
}

function toPayload(values) {
  return {
    title: values.title,
    content: values.content,
    type: values.type,
    priority: values.priority,
    publishTime: values.publishTime ? values.publishTime.format("YYYY-MM-DDTHH:mm:ss") : null,
    expireTime: values.expireTime ? values.expireTime.format("YYYY-MM-DDTHH:mm:ss") : null,
    pinned: Boolean(values.pinned),
  };
}

function AnnouncementManagement() {
  const [form] = Form.useForm();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const response = await api.admin.getAnnouncements();
      setItems(response?.data || []);
    } catch (error) {
      message.error(error.message || "公告加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openEditor = (row = null) => {
    setEditing(row);
    form.setFieldsValue(toFormValues(row));
    setModalOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const payload = toPayload(values);
      if (editing?.id) {
        await api.admin.updateAnnouncement(editing.id, payload);
      } else {
        await api.admin.createAnnouncement(payload);
      }
      message.success("公告已保存");
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error.message || "公告保存失败");
    } finally {
      setSaving(false);
    }
  };

  const action = async (handler, successText) => {
    try {
      await handler();
      message.success(successText);
      await load();
    } catch (error) {
      message.error(error.message || successText.replace("已", "失败"));
    }
  };

  const columns = [
    {
      title: "标题",
      dataIndex: "title",
      render: (text, row) => (
        <Space wrap>
          <span>{text}</span>
          {row.pinned && <Tag color="gold">置顶</Tag>}
        </Space>
      ),
    },
    { title: "类型", dataIndex: "type", width: 120 },
    { title: "优先级", dataIndex: "priority", width: 110 },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      render: (status) => <Tag color={STATUS_COLOR[status] || "default"}>{status}</Tag>,
    },
    { title: "发布时间", dataIndex: "publishTime", width: 180, render: (value) => value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "-" },
    { title: "到期时间", dataIndex: "expireTime", width: 180, render: (value) => value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "长期" },
    {
      title: "操作",
      key: "actions",
      width: 260,
      render: (_, row) => (
        <Space wrap>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEditor(row)}>编辑</Button>
          {row.status !== "PUBLISHED" ? (
            <Button size="small" type="primary" onClick={() => action(() => api.admin.publishAnnouncement(row.id), "公告已发布")}>发布</Button>
          ) : (
            <Button size="small" onClick={() => action(() => api.admin.withdrawAnnouncement(row.id), "公告已撤回")}>撤回</Button>
          )}
          <Popconfirm title="确认删除该公告？" onConfirm={() => action(() => api.admin.deleteAnnouncement(row.id), "公告已删除")}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <SectionCard
      title="校园后勤公告管理"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>新增公告</Button>
        </Space>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={items} pagination={{ pageSize: 8 }} />
      <Modal
        open={modalOpen}
        title={editing ? "编辑公告" : "新增公告"}
        onCancel={() => setModalOpen(false)}
        onOk={save}
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false} initialValues={{ type: "GENERAL", priority: "NORMAL", pinned: false }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: "请输入公告标题" }, { max: 200, message: "标题不能超过200字" }]}>
            <Input prefix={<NotificationOutlined />} />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: "请输入公告内容" }]}>
            <TextArea rows={5} />
          </Form.Item>
          <Space size={12} style={{ width: "100%" }} align="start">
            <Form.Item name="type" label="类型" style={{ flex: 1 }}>
              <Select options={[
                { value: "GENERAL", label: "综合公告" },
                { value: "MAINTENANCE", label: "维修维护" },
                { value: "OUTAGE", label: "服务中断" },
                { value: "SAFETY", label: "安全提醒" },
              ]} />
            </Form.Item>
            <Form.Item name="priority" label="优先级" style={{ flex: 1 }}>
              <Select options={[
                { value: "LOW", label: "低" },
                { value: "NORMAL", label: "普通" },
                { value: "HIGH", label: "重要" },
                { value: "URGENT", label: "紧急" },
              ]} />
            </Form.Item>
          </Space>
          <Form.Item name="publishTime" label="发布时间">
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item name="expireTime" label="到期时间">
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item name="pinned" label="置顶" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </SectionCard>
  );
}

export default AnnouncementManagement;