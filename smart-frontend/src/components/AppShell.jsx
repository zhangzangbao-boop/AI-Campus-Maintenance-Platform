import React, { useMemo, useState } from "react";
import { Avatar, Dropdown, Layout, Menu, Space, Typography } from "antd";
import {
  BellOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from "@ant-design/icons";
import NotificationCenter from "./NotificationCenter";
import websocketService from "../services/websocketService";
import schoolLogo from "../assets/school-logo.png";

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const roleLabels = {
  STUDENT: "学生服务端",
  STAFF: "维修工作台",
  ADMIN: "运营管理端",
};

const AppShell = ({
  role = "STUDENT",
  user,
  title,
  subtitle,
  menuItems,
  selectedKey,
  onMenuClick,
  avatarMenuItems = [],
  onAvatarMenuClick,
  children,
}) => {
  const [collapsed, setCollapsed] = useState(false);

  const shellMenuItems = useMemo(() => menuItems || [], [menuItems]);
  const displayName = user?.username || user?.nickname || user?.userId || "用户";
  const roleLabel = roleLabels[role] || "智慧报修平台";

  const accountMenu = [
    ...avatarMenuItems,
    ...(avatarMenuItems.length ? [{ type: "divider" }] : []),
    {
      key: "logout",
      icon: <LogoutOutlined />,
      label: "退出登录",
      danger: true,
    },
  ];

  const handleAvatarClick = (event) => {
    if (event.key === "logout") {
      // 断开 WebSocket 连接
      websocketService.disconnect();
      // 清除本地存储
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      // 跳转到登录页
      window.location.href = "/login";
      return;
    }
    onAvatarMenuClick?.(event);
  };

  return (
    <Layout className="app-shell">
      <Sider
        width={248}
        collapsible
        collapsed={collapsed}
        trigger={null}
        className="app-sidebar"
      >
        <div className="app-brand">
          <div className="app-brand-mark">
            <img src={schoolLogo} alt="校徽" />
          </div>
          {!collapsed && (
            <div className="app-brand-copy">
              <div className="app-brand-title">理工管家</div>
              <div className="app-brand-subtitle">{roleLabel}</div>
            </div>
          )}
        </div>

        <Menu
          className="app-menu"
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={shellMenuItems}
          onClick={onMenuClick}
        />
      </Sider>

      <Layout className={collapsed ? "app-main app-main-collapsed" : "app-main"}>
        <Header className="app-header">
          <Space size={14} className="app-header-left">
            <button
              type="button"
              className="app-collapse-btn"
              onClick={() => setCollapsed((value) => !value)}
              aria-label={collapsed ? "展开菜单" : "收起菜单"}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </button>
            <div>
              <div className="app-header-title">{title}</div>
              {subtitle && <div className="app-header-subtitle">{subtitle}</div>}
            </div>
          </Space>

          <Space size={14} className="app-header-actions">
            <NotificationCenter icon={<BellOutlined />} />
            <Text className="app-user-name">欢迎，{displayName}</Text>
            <Dropdown
              menu={{
                items: accountMenu,
                onClick: handleAvatarClick,
              }}
              placement="bottomRight"
              arrow
            >
              <Avatar className="app-avatar" size={36} icon={<UserOutlined />} />
            </Dropdown>
          </Space>
        </Header>

        <Content className="app-content">
          <div className="app-content-inner fade-in">{children}</div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppShell;
