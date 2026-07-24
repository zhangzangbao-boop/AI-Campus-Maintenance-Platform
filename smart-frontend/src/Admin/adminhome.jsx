import React, { useEffect, useMemo, useState } from "react";
import { Button, Col, message, Row, Skeleton, Space, Typography } from "antd";
import {
  AlertOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  ClusterOutlined,
  DashboardOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  NotificationOutlined,
  PieChartOutlined,
  ReloadOutlined,
  RobotOutlined,
  SettingOutlined,
  StarOutlined,
  TeamOutlined,
  UserSwitchOutlined,
} from "@ant-design/icons";
import RepairOrderList from "./RepairOrderList";
import UserManagement from "./UserManagement";
import DataAnalysis from "./DataAnalysis";
import FeedbackManagement from "./FeedbackManagement";
import OpsCenter from "./OpsCenter";
import AnnouncementManagement from "./AnnouncementManagement";
import PinnedAnnouncements from "../components/PinnedAnnouncements";
import PersonalInfoEd from "../services/PersonalInfoEd";
import { repairService } from "../services/repairService";
import AppShell from "../components/AppShell";
import { MetricCard, MiniList, PageHero, QuickActionGrid, SectionCard, StatusTag, PriorityTag } from "../components/DashboardWidgets";

const { Text, Title } = Typography;

const getStoredAdmin = () => {
  const storedUser = localStorage.getItem("user");
  if (storedUser) {
    const user = JSON.parse(storedUser);
    return {
      username: user.nickname || user.userId || "管理员",
      userId: user.userId,
      email: user.email || "admin@manage.edu.cn",
      phone: user.contactPhone || "",
      avatarUrl: user.avatarUrl || user.avatar || "",
      avatar: user.avatarUrl || user.avatar || "",
      department: "系统管理部",
      position: "系统管理员",
      role: user.role,
    };
  }
  return {
    username: "管理员",
    email: "admin@manage.edu.cn",
    phone: "",
    department: "系统管理部",
    position: "系统管理员",
  };
};

const CommandCenter = ({ orders, loading, onRefresh, onNavigate }) => {
  const stats = useMemo(() => {
    const source = Array.isArray(orders) ? orders : [];
    return source.reduce((acc, order) => {
      acc.total += 1;
      if (order.status === "pending") acc.pending += 1;
      if (order.status === "processing") acc.processing += 1;
      if (order.status === "to_be_evaluated" || order.status === "completed") acc.waitingFeedback += 1;
      if (order.priority === "high") acc.high += 1;
      return acc;
    }, { total: 0, pending: 0, processing: 0, waitingFeedback: 0, high: 0 });
  }, [orders]);

  const focusOrders = useMemo(
    () => (orders || [])
      .filter((item) => item.status === "pending" || item.priority === "high" || item.status === "processing")
      .slice(0, 6),
    [orders]
  );

  return (
    <div className="dashboard-page admin-dashboard">
      <PinnedAnnouncements onOpen={onNavigate} />
      <PageHero
        eyebrow="管理员运营指挥台"
        title="工单池、服务质量与智能运维统一调度"
        description="面向管理员的全局视角，优先暴露待分派、高优先级、处理中和评价闭环数据。"
        actions={
          <Space wrap>
            <Button type="primary" icon={<FileSearchOutlined />} onClick={() => onNavigate("order-management")}>
              进入工单池
            </Button>
            <Button icon={<PieChartOutlined />} onClick={() => onNavigate("data-analysis")}>
              图表分析
            </Button>
            <Button icon={<ReloadOutlined />} onClick={onRefresh} loading={loading}>
              刷新
            </Button>
          </Space>
        }
      >
        <div className="hero-status-card">
          <div>待分派</div>
          <strong>{stats.pending}</strong>
          <span>条</span>
        </div>
      </PageHero>

      <Row gutter={[16, 16]} className="metric-grid">
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} title="工单总量" value={stats.total} icon={<AppstoreOutlined />} description="当前可见工单池" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="amber" title="待受理" value={stats.pending} icon={<AlertOutlined />} description="需要管理员分派" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="cyan" title="处理中" value={stats.processing} icon={<ClusterOutlined />} description="正在维修流转" />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard loading={loading} color="red" title="高优先级" value={stats.high} icon={<AlertOutlined />} description="建议优先调度" />
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <SectionCard title="运营模块">
            <QuickActionGrid
              items={[
                {
                  key: "orders",
                  title: "工单池",
                  description: "受理、驳回、智能派单",
                  icon: <FileTextOutlined />,
                  onClick: () => onNavigate("order-management"),
                },
                {
                  key: "users",
                  title: "用户管理",
                  description: "维护学生、维修工、管理员",
                  icon: <TeamOutlined />,
                  color: "cyan",
                  onClick: () => onNavigate("user-management"),
                },
                {
                  key: "ops",
                  title: "智能运维",
                  description: "DeepSeek、知识库与系统配置",
                  icon: <RobotOutlined />,
                  color: "purple",
                  onClick: () => onNavigate("ops-center"),
                },
              ]}
            />
          </SectionCard>
        </Col>
        <Col xs={24} lg={14}>
          <SectionCard title="重点工单" extra={<Button type="link" onClick={() => onNavigate("order-management")}>进入处理</Button>}>
            {loading ? (
              <Skeleton active paragraph={{ rows: 5 }} />
            ) : focusOrders.length ? (
              <MiniList
                items={focusOrders}
                renderItem={(item) => (
                  <>
                    <div className="mini-list-main">
                      <strong>#{item.ticketId || item.id} {item.title || item.location || "工单"}</strong>
                      <Text type="secondary">{item.location || "未填写位置"}</Text>
                    </div>
                    <Space wrap size={6}>
                      <StatusTag status={item.status} />
                      <PriorityTag priority={item.priority} />
                    </Space>
                  </>
                )}
              />
            ) : (
              <div className="empty-inline">暂无需要重点调度的工单。</div>
            )}
          </SectionCard>
        </Col>
      </Row>
    </div>
  );
};

const AdminHome = () => {
  const [currentMenu, setCurrentMenu] = useState("command-center");
  const [repairOrders, setRepairOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [targetOrderId, setTargetOrderId] = useState(null);
  const [personalInfoModalVisible, setPersonalInfoModalVisible] = useState(false);
  const [currentAdmin, setCurrentAdmin] = useState(getStoredAdmin);

  const sideMenuItems = [
    {
      key: "overview",
      label: "运营总览",
      type: "group",
      children: [
        { key: "command-center", icon: <DashboardOutlined />, label: "运营指挥台" },
        { key: "order-management", icon: <FileTextOutlined />, label: "工单池" },
        { key: "data-analysis", icon: <BarChartOutlined />, label: "图表分析" },
      ],
    },
    {
      key: "governance",
      label: "平台管理",
      type: "group",
      children: [
        { key: "user-management", icon: <UserSwitchOutlined />, label: "用户管理" },
        { key: "feedback-management", icon: <StarOutlined />, label: "反馈管理" },
        { key: "ops-center", icon: <RobotOutlined />, label: "智能运维" },
        { key: "announcement-management", icon: <NotificationOutlined />, label: "校园公告" },
      ],
    },
    {
      key: "system",
      label: "系统设置",
      type: "group",
      children: [
        { key: "system-config", icon: <SettingOutlined />, label: "系统配置" },
        { key: "profile", icon: <EditOutlined />, label: "个人资料" },
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

  const fetchRepairOrders = async () => {
    setLoading(true);
    try {
      const result = await repairService.getRepairOrders();
      setRepairOrders(result.data || []);
    } catch (error) {
      console.error("获取工单数据失败:", error);
      message.error("获取工单数据失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (currentMenu === "command-center" || currentMenu === "order-management") {
      fetchRepairOrders();
    }
  }, [currentMenu]);

  useEffect(() => {
    const handleOpenRelatedOrder = (event) => {
      const orderId = event.detail?.orderId;
      if (!orderId) return;
      setCurrentMenu("order-management");
      setTargetOrderId(orderId);
    };
    window.addEventListener("open-related-order", handleOpenRelatedOrder);
    return () => window.removeEventListener("open-related-order", handleOpenRelatedOrder);
  }, []);

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

  const handleAdminInfoUpdate = (updatedInfo) => {
    setCurrentAdmin(updatedInfo);
    message.success("个人信息更新成功！");
  };

  const renderContent = () => {
    if (currentMenu === "command-center") {
      return (
        <CommandCenter
          orders={repairOrders}
          loading={loading}
          onRefresh={fetchRepairOrders}
          onNavigate={setCurrentMenu}
        />
      );
    }

    if (currentMenu === "user-management") return <UserManagement />;

    if (currentMenu === "order-management") {
      return (
        <RepairOrderList
          repairOrders={repairOrders}
          loading={loading}
          targetOrderId={targetOrderId}
          onTargetOrderHandled={() => setTargetOrderId(null)}
        />
      );
    }

    if (currentMenu === "feedback-management") return <FeedbackManagement />;
    if (currentMenu === "data-analysis") return <DataAnalysis />;
    if (currentMenu === "ops-center") return <OpsCenter initialTab="knowledge" />;
    if (currentMenu === "announcement-management") return <AnnouncementManagement />;
    if (currentMenu === "system-config") return <OpsCenter initialTab="config" />;
    return null;
  };

  return (
    <>
      <AppShell
        role="ADMIN"
        user={currentAdmin}
        title="理工管家 - 管理员运营端"
        subtitle="运营指挥台、工单池、图表分析、智能运维与用户治理"
        menuItems={sideMenuItems}
        selectedKey={currentMenu}
        onMenuClick={handleMenuClick}
        avatarMenuItems={avatarMenuItems}
        onAvatarMenuClick={handleAvatarMenuClick}
      >
        {renderContent()}
      </AppShell>

      <PersonalInfoEd
        visible={personalInfoModalVisible}
        onCancel={() => setPersonalInfoModalVisible(false)}
        userInfo={currentAdmin}
        onUpdate={handleAdminInfoUpdate}
      />
    </>
  );
};

export default AdminHome;
