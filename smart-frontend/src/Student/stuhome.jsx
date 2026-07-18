import React, { useEffect, useMemo, useState } from "react";
import { Button, Col, message, Row, Skeleton, Space, Typography } from "antd";
import {
  AppstoreOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EditOutlined,
  FileSearchOutlined,
  NotificationOutlined,
  PlusOutlined,
  RobotOutlined,
  SettingOutlined,
  StarOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import MyRepairs from "./MyRepairs";
import CreateRepairPage from "./CreateRepairPage";
import PersonalInfoEd from "../services/PersonalInfoEd";
import KnowledgeQA from "../components/KnowledgeQA";
import AnnouncementList from "../components/AnnouncementList";
import PinnedAnnouncements from "../components/PinnedAnnouncements";
import { repairService } from "../services/repairService";
import AppShell from "../components/AppShell";
import { MetricCard, MiniList, PageHero, QuickActionGrid, SectionCard, StatusTag } from "../components/DashboardWidgets";

const { Text, Title } = Typography;

const emptyStats = {
  total: 0,
  pending: 0,
  processing: 0,
  toEvaluate: 0,
  completed: 0,
};

const getStoredStudent = () => {
  const storedUser = localStorage.getItem("user");
  if (storedUser) {
    const user = JSON.parse(storedUser);
    return {
      username: user.nickname || user.userId || "学生用户",
      userId: user.userId,
      email: user.email || "",
      phone: user.contactPhone || "",
      department: "学生",
      position: "学生",
      studentID: user.userId,
      role: user.role,
    };
  }
  return {
    username: "学生用户",
    email: "",
    phone: "",
    department: "学生",
    position: "学生",
    studentID: "",
  };
};

const StudentDashboard = ({ orders, loading, onNavigate, onRefresh }) => {
  const stats = useMemo(() => {
    const source = Array.isArray(orders) ? orders : [];
    return source.reduce((acc, order) => {
      const status = order.status;
      acc.total += 1;
      if (status === "pending") acc.pending += 1;
      if (status === "processing") acc.processing += 1;
      if (status === "to_be_evaluated" || (status === "completed" && !order.rating)) acc.toEvaluate += 1;
      if (status === "closed" || (status === "completed" && order.rating)) acc.completed += 1;
      return acc;
    }, { ...emptyStats });
  }, [orders]);

  const activeOrders = useMemo(
    () => (orders || []).filter((item) => ["pending", "processing", "to_be_evaluated", "completed"].includes(item.status)).slice(0, 5),
    [orders]
  );

  return (
    <div className="dashboard-page student-dashboard">
      <PinnedAnnouncements onOpen={onNavigate} />
      <PageHero
        eyebrow="学生服务台"
        title="校园报修，一站式跟踪"
        description="从智能填写、提交报修、进度跟踪到满意度评价，学生端形成完整闭环。"
        actions={
          <Space wrap>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => onNavigate("create-repair")}>
              立即报修
            </Button>
            <Button icon={<RobotOutlined />} onClick={() => onNavigate("create-repair")}>
              AI 智能填写
            </Button>
            <Button icon={<FileSearchOutlined />} onClick={() => onNavigate("my-repairs")}>
              查看进度
            </Button>
          </Space>
        }
      >
        <div className="hero-status-card">
          <div>当前待处理</div>
          <strong>{stats.pending + stats.processing + stats.toEvaluate}</strong>
          <span>条需要关注</span>
        </div>
      </PageHero>

      <Row gutter={[16, 16]} className="metric-grid">
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} title="我的报修" value={stats.total} icon={<AppstoreOutlined />} description="历史提交总量" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="amber" title="待受理" value={stats.pending} icon={<ClockCircleOutlined />} description="等待管理员分派" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="cyan" title="处理中" value={stats.processing} icon={<ThunderboltOutlined />} description="维修人员正在处理" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="purple" title="待评价" value={stats.toEvaluate} icon={<StarOutlined />} description="完成闭环的关键一步" />
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <SectionCard title="快捷入口">
            <QuickActionGrid
              items={[
                {
                  key: "create",
                  title: "立即报修",
                  description: "提交文字、图片和位置",
                  icon: <PlusOutlined />,
                  onClick: () => onNavigate("create-repair"),
                },
                {
                  key: "ai",
                  title: "AI 智能填写",
                  description: "用一段话自动生成表单",
                  icon: <RobotOutlined />,
                  color: "cyan",
                  onClick: () => onNavigate("create-repair"),
                },
                {
                  key: "evaluate",
                  title: "待评价",
                  description: "确认服务质量并反馈",
                  icon: <StarOutlined />,
                  color: "purple",
                  onClick: () => onNavigate("to-evaluate"),
                },
              ]}
            />
          </SectionCard>
        </Col>
        <Col xs={24} lg={14}>
          <SectionCard
            title="我的进度"
            extra={<Button type="link" onClick={() => onNavigate("my-repairs")}>全部工单</Button>}
          >
            {loading ? (
              <Skeleton active paragraph={{ rows: 4 }} />
            ) : activeOrders.length ? (
              <MiniList
                items={activeOrders}
                renderItem={(item) => (
                  <>
                    <div className="mini-list-main">
                      <strong>#{item.ticketId || item.id} {item.title || item.location || "报修单"}</strong>
                      <Text type="secondary">{item.location || "未填写位置"}</Text>
                    </div>
                    <StatusTag status={item.status} />
                  </>
                )}
              />
            ) : (
              <div className="empty-inline">
                暂无进行中的报修，遇到问题可立即提交。
              </div>
            )}
          </SectionCard>
        </Col>
      </Row>
    </div>
  );
};

const Home = () => {
  const [currentMenu, setCurrentMenu] = useState("dashboard");
  const [repairOrders, setRepairOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [targetOrderId, setTargetOrderId] = useState(null);
  const [personalInfoModalVisible, setPersonalInfoModalVisible] = useState(false);
  const [currentUser, setCurrentUser] = useState(getStoredStudent);

  const sideMenuItems = [
    {
      key: "overview",
      label: "我的服务",
      type: "group",
      children: [
        { key: "dashboard", icon: <BarChartOutlined />, label: "学生总览" },
        { key: "create-repair", icon: <PlusOutlined />, label: "立即报修" },
        { key: "knowledge-qa", icon: <RobotOutlined />, label: "维修知识问答" },
        { key: "announcements", icon: <NotificationOutlined />, label: "校园公告" },
      ],
    },
    {
      key: "orders",
      label: "工单跟踪",
      type: "group",
      children: [
        { key: "my-repairs", icon: <FileSearchOutlined />, label: "我的进度" },
        { key: "to-evaluate", icon: <StarOutlined />, label: "待评价" },
        { key: "completed", icon: <CheckCircleOutlined />, label: "历史记录" },
      ],
    },
    {
      key: "account",
      label: "个人中心",
      type: "group",
      children: [
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

  const fetchMyRepairs = async () => {
    setLoading(true);
    try {
      const result = await repairService.getMyRepairOrders();
      setRepairOrders(result.data || []);
    } catch (error) {
      console.error("获取报修记录失败:", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMyRepairs();
  }, [refreshTrigger]);

  useEffect(() => {
    const handleOpenRelatedOrder = (event) => {
      const orderId = event.detail?.orderId;
      if (!orderId) return;
      setCurrentMenu("my-repairs");
      setTargetOrderId(orderId);
    };
    window.addEventListener("open-related-order", handleOpenRelatedOrder);
    return () => window.removeEventListener("open-related-order", handleOpenRelatedOrder);
  }, []);

  const handleSideMenuClick = (event) => {
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

  const handleUserInfoUpdate = (updatedInfo) => {
    setCurrentUser(updatedInfo);
    message.success("个人信息更新成功！");
  };

  const handleRefresh = () => {
    setRefreshTrigger((prev) => prev + 1);
  };

  const renderContent = () => {
    if (currentMenu === "dashboard") {
      return (
        <StudentDashboard
          orders={repairOrders}
          loading={loading}
          onNavigate={setCurrentMenu}
          onRefresh={handleRefresh}
        />
      );
    }

    if (currentMenu === "create-repair") {
      return (
        <CreateRepairPage
          currentUser={currentUser}
          onSubmitSuccess={() => {
            setCurrentMenu("my-repairs");
            handleRefresh();
          }}
        />
      );
    }

    if (currentMenu === "knowledge-qa") {
      return <KnowledgeQA />;
    }

    if (currentMenu === "announcements") {
      return <AnnouncementList />;
    }

    const initialFilters = {
      "to-evaluate": { status: "to_be_evaluated" },
      completed: { status: "closed" },
    }[currentMenu];

    return (
      <div>
        {currentMenu === "to-evaluate" && (
          <div className="module-intro">
            <Title level={4}>待评价工单</Title>
          </div>
        )}
        {currentMenu === "completed" && (
          <div className="module-intro">
            <Title level={4}>历史报修记录</Title>
          </div>
        )}
        <MyRepairs
          initialFilters={initialFilters}
          onRefresh={handleRefresh}
          targetOrderId={targetOrderId}
          onTargetOrderHandled={() => setTargetOrderId(null)}
        />
      </div>
    );
  };

  return (
    <>
      <AppShell
        role="STUDENT"
        user={currentUser}
        title="理工管家 - 学生服务台"
        subtitle="立即报修、进度跟踪、评价反馈和 AI 辅助填写"
        menuItems={sideMenuItems}
        selectedKey={currentMenu}
        onMenuClick={handleSideMenuClick}
        avatarMenuItems={avatarMenuItems}
        onAvatarMenuClick={handleAvatarMenuClick}
      >
        {renderContent()}
      </AppShell>

      <PersonalInfoEd
        visible={personalInfoModalVisible}
        onCancel={() => setPersonalInfoModalVisible(false)}
        userInfo={currentUser}
        onUpdate={handleUserInfoUpdate}
      />
    </>
  );
};

export default Home;
