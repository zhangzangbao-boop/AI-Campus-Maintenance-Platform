import React, { useEffect, useMemo, useState } from "react";
import { Button, Col, message, Row, Skeleton, Space, Tag, Typography } from "antd";
import {
  AlertOutlined,
  ClockCircleOutlined,
  EditOutlined,
  FieldTimeOutlined,
  FireOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SettingOutlined,
  ToolOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import MyTask from "./myTask.jsx";
import { mytaskService, mytaskUtils } from "./mytaskService.jsx";
import PersonalInfoEd from "../services/PersonalInfoEd.jsx";
import AppShell from "../components/AppShell";
import { MetricCard, MiniList, PageHero, QuickActionGrid, SectionCard, StatusTag, PriorityTag } from "../components/DashboardWidgets";

const { Text, Title } = Typography;

const getStoredWorker = () => {
  const storedUser = localStorage.getItem("user");
  if (storedUser) {
    const user = JSON.parse(storedUser);
    return {
      username: user.nickname || user.userId || "维修工",
      userId: user.userId,
      email: user.email || "worker@repair.com",
      phone: user.contactPhone || "",
      department: "维修部",
      position: "维修工",
      role: user.role,
    };
  }
  return {
    username: "维修工",
    email: "worker@repair.com",
    phone: "",
    department: "维修部",
    position: "维修工",
  };
};

const isToday = (value) => {
  if (!value) return false;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  const now = new Date();
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
};

const WorkerDashboard = ({ worker, onNavigate }) => {
  const [tasks, setTasks] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  const loadOverview = async () => {
    if (!worker?.userId) return;
    setLoading(true);
    try {
      const [taskResult, statsResult] = await Promise.all([
        mytaskService.getMyTasks(worker.userId, {}),
        mytaskService.getRepairmanStats(worker.userId),
      ]);
      setTasks(taskResult.data || []);
      setStats(statsResult);
    } catch (error) {
      console.error("加载维修工总览失败:", error);
      message.error(error.message || "加载维修工总览失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOverview();
  }, [worker?.userId]);

  const derived = useMemo(() => {
    const source = tasks || [];
    const todayTasks = source.filter((item) => isToday(item.assigned_at || item.created_at));
    const overdueTasks = source.filter((item) => mytaskUtils.isTaskOverdue(item));
    const highPriority = source.filter((item) => item.priority === "high");
    return {
      today: todayTasks.length,
      overdue: overdueTasks.length,
      high: highPriority.length,
      active: source.filter((item) => item.status === "processing").length,
      focusTasks: [...overdueTasks, ...highPriority, ...todayTasks].slice(0, 5),
    };
  }, [tasks]);

  return (
    <div className="dashboard-page worker-dashboard">
      <PageHero
        eyebrow="维修执行端"
        title="今日任务与风险优先处理"
        description="围绕待处理、处理中、高优先级和即将超时任务组织维修动作，减少遗漏。"
        actions={
          <Space wrap>
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => onNavigate("today")}>
              今日任务
            </Button>
            <Button icon={<AlertOutlined />} onClick={() => onNavigate("overdue")}>
              超时预警
            </Button>
            <Button icon={<ReloadOutlined />} onClick={loadOverview} loading={loading}>
              刷新
            </Button>
          </Space>
        }
      >
        <div className="hero-status-card">
          <div>平均评分</div>
          <strong>{stats?.averageRating || 0}</strong>
          <span>分</span>
        </div>
      </PageHero>

      <Row gutter={[16, 16]} className="metric-grid">
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} title="今日任务" value={derived.today} icon={<FieldTimeOutlined />} description="今日新增或分派" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="cyan" title="处理中" value={derived.active} icon={<ToolOutlined />} description="正在维修" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="red" title="高优先级" value={derived.high} icon={<FireOutlined />} description="建议优先响应" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="amber" title="即将/已经超时" value={derived.overdue} icon={<ClockCircleOutlined />} description="需尽快处理" />
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <SectionCard title="任务入口">
            <QuickActionGrid
              items={[
                {
                  key: "processing",
                  title: "处理中",
                  description: "继续当前维修任务",
                  icon: <ToolOutlined />,
                  color: "cyan",
                  onClick: () => onNavigate("processing"),
                },
                {
                  key: "high",
                  title: "高优先级",
                  description: "优先处理紧急问题",
                  icon: <FireOutlined />,
                  color: "red",
                  onClick: () => onNavigate("high-priority"),
                },
                {
                  key: "records",
                  title: "维修记录",
                  description: "查看完成和历史工单",
                  icon: <HistoryOutlined />,
                  onClick: () => onNavigate("records"),
                },
              ]}
            />
          </SectionCard>
        </Col>
        <Col xs={24} lg={14}>
          <SectionCard title="重点关注任务" extra={<Button type="link" onClick={() => onNavigate("today")}>今日任务</Button>}>
            {loading ? (
              <Skeleton active paragraph={{ rows: 4 }} />
            ) : derived.focusTasks.length ? (
              <MiniList
                items={derived.focusTasks}
                renderItem={(item) => (
                  <>
                    <div className="mini-list-main">
                      <strong>#{item.ticketId || item.id} {item.location || "维修任务"}</strong>
                      <Text type="secondary">{item.description || "暂无描述"}</Text>
                    </div>
                    <Space wrap size={6}>
                      <StatusTag status={item.status} />
                      <PriorityTag priority={item.priority} />
                      {mytaskUtils.isTaskOverdue(item) && <Tag color="red">已超时</Tag>}
                    </Space>
                  </>
                )}
              />
            ) : (
              <div className="empty-inline">暂无需要重点关注的任务。</div>
            )}
          </SectionCard>
        </Col>
      </Row>
    </div>
  );
};

const WorkerHome = () => {
  const [currentMenu, setCurrentMenu] = useState("dashboard");
  const [targetTaskId, setTargetTaskId] = useState(null);
  const [personalInfoModalVisible, setPersonalInfoModalVisible] = useState(false);
  const [currentWorker, setCurrentWorker] = useState(getStoredWorker);

  const sideMenuItems = [
    {
      key: "overview",
      label: "任务工作台",
      type: "group",
      children: [
        { key: "dashboard", icon: <UnorderedListOutlined />, label: "任务总览" },
        { key: "today", icon: <FieldTimeOutlined />, label: "今日任务" },
        { key: "processing", icon: <ToolOutlined />, label: "处理中" },
      ],
    },
    {
      key: "risk",
      label: "优先处理",
      type: "group",
      children: [
        { key: "high-priority", icon: <FireOutlined />, label: "高优先级" },
        { key: "overdue", icon: <AlertOutlined />, label: "即将超时" },
        { key: "to-evaluate", icon: <ClockCircleOutlined />, label: "待评价" },
      ],
    },
    {
      key: "records",
      label: "记录与设置",
      type: "group",
      children: [
        { key: "records", icon: <HistoryOutlined />, label: "维修记录" },
        { key: "profile", icon: <SettingOutlined />, label: "个人资料" },
      ],
    },
  ];

  const avatarMenuItems = [
    {
      key: "edit-profile",
      icon: <EditOutlined />,
      label: "编辑个人信息",
    },
  ];

  const handleMenuClick = (event) => {
    if (event.key === "profile") {
      setPersonalInfoModalVisible(true);
      return;
    }
    setCurrentMenu(event.key);
  };

  const handleAvatarMenuClick = (event) => {
    if (event.key === "edit-profile") {
      setPersonalInfoModalVisible(true);
    }
  };

  const handlePersonalInfoUpdate = (updatedInfo) => {
    setCurrentWorker(updatedInfo);
    message.success("个人信息更新成功！");
  };

  useEffect(() => {
    const handleOpenRelatedOrder = (event) => {
      const orderId = event.detail?.orderId;
      if (!orderId) return;
      setCurrentMenu("today");
      setTargetTaskId(orderId);
    };
    window.addEventListener("open-related-order", handleOpenRelatedOrder);
    return () => window.removeEventListener("open-related-order", handleOpenRelatedOrder);
  }, []);

  const taskFilters = {
    today: { today: true },
    processing: { status: "processing" },
    "high-priority": { priority: "high" },
    "to-evaluate": { status: "to_be_evaluated" },
    records: { status: "closed" },
  }[currentMenu];

  const moduleTitle = {
    today: "今日任务",
    processing: "处理中任务",
    "high-priority": "高优先级任务",
    overdue: "即将超时任务",
    "to-evaluate": "待评价任务",
    records: "维修记录",
  }[currentMenu];

  return (
    <>
      <AppShell
        role="STAFF"
        user={currentWorker}
        title="理工管家 - 维修工作台"
        subtitle="今日任务、处理中、高优先级、超时预警与维修记录"
        menuItems={sideMenuItems}
        selectedKey={currentMenu}
        onMenuClick={handleMenuClick}
        avatarMenuItems={avatarMenuItems}
        onAvatarMenuClick={handleAvatarMenuClick}
      >
        {currentMenu === "dashboard" ? (
          <WorkerDashboard worker={currentWorker} onNavigate={setCurrentMenu} />
        ) : (
          <div>
            <div className="module-intro">
              <Title level={4}>{moduleTitle}</Title>
              {currentMenu === "overdue" && (
                <Text type="secondary">优先检查预计完成时间已过或临近的任务。</Text>
              )}
            </div>
            <MyTask
              initialFilters={taskFilters}
              overdueOnly={currentMenu === "overdue"}
              todayOnly={currentMenu === "today"}
              title={moduleTitle}
              targetTaskId={targetTaskId}
              onTargetTaskHandled={() => setTargetTaskId(null)}
            />
          </div>
        )}
      </AppShell>

      <PersonalInfoEd
        visible={personalInfoModalVisible}
        onCancel={() => setPersonalInfoModalVisible(false)}
        userInfo={currentWorker}
        onUpdate={handlePersonalInfoUpdate}
      />
    </>
  );
};

export default WorkerHome;
