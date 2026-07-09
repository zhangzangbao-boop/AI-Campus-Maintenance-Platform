import "./Login.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Form, Input, message, Select, Space, Tabs, Typography } from "antd";
import {
  LockOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
  UserOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import backgroundImage from "./assets/1001.jpg";
import api from "./services/api";

const { Option } = Select;
const { Text, Title } = Typography;

const demoAccounts = [
  { role: "管理员", username: "admin", password: "123456", icon: <SafetyCertificateOutlined /> },
  { role: "维修员", username: "worker001", password: "123456", icon: <ToolOutlined /> },
  { role: "学生", username: "20260001", password: "123456", icon: <UserOutlined /> },
];

function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState("STUDENT");
  const [activeTab, setActiveTab] = useState("login");
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async () => {
    setSubmitting(true);
    try {
      const timeoutPromise = new Promise((_, reject) => {
        setTimeout(() => reject(new Error("登录超时，请检查网络连接")), 10000);
      });

      const loginPromise = api.auth.login({ userId: username, password });
      const data = await Promise.race([loginPromise, timeoutPromise]);

      localStorage.setItem("token", data.token);
      localStorage.setItem("user", JSON.stringify(data.user));

      switch (data.user.role) {
        case "STUDENT":
          navigate("/stuhome");
          break;
        case "STAFF":
          navigate("/workerhome");
          break;
        case "ADMIN":
          navigate("/adminhome");
          break;
        default:
          message.error("未知用户角色");
      }
    } catch (error) {
      let errorMessage = "登录失败";
      if (error.message.includes("超时")) {
        errorMessage = "登录超时，请检查网络连接后重试";
      } else if (error.message.includes("401") || error.message.includes("密码错误") || error.message.includes("账号不存在")) {
        errorMessage = "用户名或密码错误";
      } else if (error.message.includes("网络") || error.message.includes("fetch")) {
        errorMessage = "网络连接失败，请检查网络后重试";
      } else {
        errorMessage = error.message || "登录失败，请重试";
      }
      message.error(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegister = async () => {
    if (password !== confirmPassword) {
      message.error("两次输入的密码不一致");
      return;
    }

    setSubmitting(true);
    try {
      await api.auth.register({
        userId: username,
        password,
        nickname: name,
        contactPhone: phone,
        role,
      });

      message.success("注册成功，请登录");
      setActiveTab("login");
    } catch (error) {
      message.error(error.message || "注册失败");
    } finally {
      setSubmitting(false);
    }
  };

  const fillDemoAccount = (account) => {
    setActiveTab("login");
    setUsername(account.username);
    setPassword(account.password);
    message.success(`已填入${account.role}测试账号`);
  };

  return (
    <div
      className="login-page"
      style={{ "--login-bg-image": `url(${backgroundImage})` }}
    >
      <div className="login-visual">
        <div className="login-brand">
          <div className="login-brand-mark">
            <ThunderboltOutlined />
          </div>
          <Text>智慧校园服务平台</Text>
          <Title level={1}>理工管家 - 校园智能报修中心</Title>
          <p>面向学生、维修人员和管理员的全流程报修协同平台。</p>
        </div>

        <div className="login-feature-grid">
          <div>
            <strong>AI 填写</strong>
            <span>一句话识别分类、位置和紧急程度</span>
          </div>
          <div>
            <strong>工单闭环</strong>
            <span>受理、派单、维修、评价全链路跟踪</span>
          </div>
          <div>
            <strong>运营分析</strong>
            <span>统计图表、热点区域和服务质量可视化</span>
          </div>
        </div>
      </div>

      <Card className="login-card">
        <div className="login-card-head">
          <Title level={3}>账号登录</Title>
          <Text type="secondary">请选择角色测试账号或输入自己的账号</Text>
        </div>

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          centered
          items={[
            {
              key: "login",
              label: "登录",
              children: (
                <Form layout="vertical" onFinish={handleLogin} className="login-form">
                  <Form.Item label="用户名" required>
                    <Input
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="请输入学号、工号或管理员账号"
                      prefix={<UserOutlined />}
                      size="large"
                      autoComplete="username"
                    />
                  </Form.Item>

                  <Form.Item label="密码" required>
                    <Input.Password
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="请输入密码"
                      prefix={<LockOutlined />}
                      size="large"
                      autoComplete="current-password"
                    />
                  </Form.Item>

                  <div className="demo-account-row">
                    {demoAccounts.map((account) => (
                      <Button
                        key={account.username}
                        type="default"
                        icon={account.icon}
                        onClick={() => fillDemoAccount(account)}
                      >
                        {account.role}
                      </Button>
                    ))}
                  </div>

                  <Button
                    htmlType="submit"
                    type="primary"
                    size="large"
                    block
                    loading={submitting}
                    className="login-submit"
                  >
                    登录系统
                  </Button>
                </Form>
              ),
            },
            {
              key: "register",
              label: "注册",
              children: (
                <Form layout="vertical" onFinish={handleRegister} className="login-form">
                  <Form.Item label="学号/工号" required>
                    <Input
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="请输入学号或工号"
                      prefix={<UserOutlined />}
                      size="large"
                    />
                  </Form.Item>

                  <Form.Item label="真实姓名" required>
                    <Input
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      placeholder="请输入真实姓名"
                      size="large"
                    />
                  </Form.Item>

                  <Form.Item label="手机号" required>
                    <Input
                      value={phone}
                      onChange={(event) => setPhone(event.target.value)}
                      placeholder="请输入手机号"
                      size="large"
                    />
                  </Form.Item>

                  <Form.Item label="角色" required>
                    <Select value={role} onChange={setRole} size="large">
                      <Option value="STUDENT">学生</Option>
                      <Option value="STAFF">维修工</Option>
                      <Option value="ADMIN">管理员</Option>
                    </Select>
                  </Form.Item>

                  <Space className="register-password-grid" size={12}>
                    <Form.Item label="密码" required>
                      <Input.Password
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        placeholder="请输入密码"
                        size="large"
                      />
                    </Form.Item>
                    <Form.Item label="确认密码" required>
                      <Input.Password
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        placeholder="请再次输入密码"
                        size="large"
                      />
                    </Form.Item>
                  </Space>

                  <Button
                    htmlType="submit"
                    type="primary"
                    size="large"
                    block
                    loading={submitting}
                    className="login-submit"
                  >
                    注册账号
                  </Button>
                </Form>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}

export default Login;
