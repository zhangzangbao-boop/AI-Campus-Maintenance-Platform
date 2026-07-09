import React, { useState, useEffect } from 'react';
import { Card, Table, Space, Select, Row, Col, Statistic, Tag,
  Button, Modal, Form, Input, message, Popconfirm
} from 'antd';
import { UserOutlined, ToolOutlined, TeamOutlined, PhoneOutlined,
  EditOutlined, DeleteOutlined, KeyOutlined, SearchOutlined
 } from '@ant-design/icons';
import { userService } from './userService';

const { Option } = Select;

const UserManagement = () => {
  const [currentUserType, setCurrentUserType] = useState('students');
  const [loading, setLoading] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [form] = Form.useForm();

  // 状态管理
  const [studentAccounts, setStudentAccounts] = useState([]);
  const [repairmanAccounts, setRepairmanAccounts] = useState([]);
  const [searchField, setSearchField] = useState('name');
  const [searchKeyword, setSearchKeyword] = useState('');

  // 加载学生账号数据
  const loadStudents = async (filters = {}) => {
    setLoading(true);
    try {
      const result = await userService.getStudents(filters);
      setStudentAccounts(result.data);
    } catch (error) {
      console.error('加载学生数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 加载维修人员账号数据
  const loadRepairmen = async (filters = {}) => {
    setLoading(true);
    try {
      const result = await userService.getRepairmen(filters);
      setRepairmanAccounts(result.data);
    } catch (error) {
      console.error('加载维修人员数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 处理下拉菜单选择
  const handleUserTypeChange = (value) => {
    setCurrentUserType(value);
    setSearchKeyword(''); // 切换类型时清空搜索关键词
  };

  // 获取当前显示的数据
  const getCurrentData = () => {
    return currentUserType === 'students' ? studentAccounts : repairmanAccounts;
  };

  // 获取当前标题
  const getCurrentTitle = () => {
    return currentUserType === 'students' ? '学生账号' : '维修人员账号';
  };

  // 删除用户
  const handleDelete = async (record) => {
    try {
      await userService.deleteUser(record.id, record.type);
      // 重新加载数据
      if (currentUserType === 'students') {
        loadStudents();
      } else {
        loadRepairmen();
      }
    } catch (error) {
      console.error('删除用户失败:', error);
    }
  };

  // 重置密码函数
  const handleResetPassword = async (record) => {
    try {
      const result = await userService.resetPassword(record.id);
      // userService 中已经通过 message.success 显示了新密码
      // 这里可以额外处理，比如复制到剪贴板等
      if (result && result.newPassword) {
        console.log(`用户 ${record.name} (${record.id}) 的新密码：${result.newPassword}`);
      }
    } catch (error) {
      console.error('重置密码失败:', error);
    }
  };

  // 修改数据：打开编辑模态框
  const handleEdit = (record) => {
    setEditingUser(record);
    form.setFieldsValue({
      phone: record.phone,
      type: record.type
    });
    setEditModalVisible(true);
  };

  // 保存编辑
  const handleSaveEdit = async () => {
    try {
      const values = await form.validateFields();
      await userService.updateUser(editingUser.id, values);
      
      setEditModalVisible(false);
      setEditingUser(null);
      form.resetFields();
      
      // 重新加载数据
      if (currentUserType === 'students') {
        loadStudents();
      } else {
        loadRepairmen();
      }
    } catch (error) {
      console.error('保存编辑失败:', error);
    }
  };

  // 取消编辑
  const handleCancelEdit = () => {
    setEditModalVisible(false);
    setEditingUser(null);
    form.resetFields();
  };

  // 处理搜索
  const handleSearch = (value) => {
    const keyword = value ? value.trim() : '';
    setSearchKeyword(keyword);
    const filters = {};
    if (keyword) {
      // 根据选择的搜索字段设置不同的参数
      // 后端支持 keyword（通用搜索，支持昵称、用户ID、电话）、name（姓名）、userId（用户ID）
      if (searchField === 'name') {
        filters.name = keyword;
      } else if (searchField === 'id') {
        filters.userId = keyword;
      } else {
        // 默认使用 keyword 进行通用搜索（支持昵称、用户ID、电话号码等）
        filters.keyword = keyword;
      }
    }
    console.log('搜索用户，关键词:', keyword, '筛选条件:', filters, '搜索字段:', searchField);
    
    if (currentUserType === 'students') {
      loadStudents(filters);
    } else {
      loadRepairmen(filters);
    }
  };

  // 重置搜索
  const handleResetSearch = () => {
    setSearchKeyword('');
    if (currentUserType === 'students') {
      loadStudents();
    } else {
      loadRepairmen();
    }
    message.success('已重置搜索');
  };

  // 初始化加载数据
  useEffect(() => {
    loadStudents();
    loadRepairmen();
  }, []);

  // 表格列定义
  const columns = [
    {
      title: '用户ID',
      dataIndex: 'id',
      key: 'id',
      width: 150,
      render: (id) => (
        <Space>
          <UserOutlined />
          <span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>
            {id}
          </span>
        </Space>
      ),
    },
    {
      title: '学号/工号',
      dataIndex: 'number',
      key: 'number',
      width: 150,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      key: 'name',
      width: 120,
    },
    {
      title: '联系电话',
      dataIndex: 'phone',
      key: 'phone',
      width: 150,
      render: (phone) => (
        <Space>
          <PhoneOutlined />
          {phone}
        </Space>
      ),
    },
    {
      title: '用户类型',
      key: 'type',
      width: 100,
      render: () => (
        <Tag color={currentUserType === 'students' ? 'blue' : 'green'}>
          {currentUserType === 'students' ? '学生' : '维修人员'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Popconfirm
            title="确定要删除这个用户吗？"
            description="此操作不可恢复，请谨慎操作！"
            onConfirm={() => handleDelete(record)}
            okText="确定"
            cancelText="取消"
            okType="danger"
          >
            <Button 
              type="link" 
              danger 
              icon={<DeleteOutlined />}
              size="small"
            >
              删除
            </Button>
          </Popconfirm>
          
          <Popconfirm
            title="重置密码"
            description="确定要将密码重置为默认密码吗？"
            onConfirm={() => handleResetPassword(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button 
              type="link" 
              icon={<KeyOutlined />}
              size="small"
            >
              重置密码
            </Button>
          </Popconfirm>

          <Button 
            type="link" 
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            size="small"
          >
            修改
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2>用户管理</h2>
      
      {/* 统计信息 */}
      <Row gutter={16} style={{ marginBottom: '16px' }}>
        <Col span={8}>
          <Card>
            <Statistic
              title="学生总数"
              value={studentAccounts.length}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="维修人员总数"
              value={repairmanAccounts.length}
              prefix={<ToolOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="总用户数"
              value={studentAccounts.length + repairmanAccounts.length}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 用户类型选择和表格 */}
      <Card 
        title={getCurrentTitle()}
      >
        <Row gutter={12} style={{ marginBottom: 16, alignItems: 'center' }}>
          <Col>
            <Select
              value={searchField}
              onChange={(val) => setSearchField(val)}
              style={{ width: 140 }}
              size="middle"
            >
              <Option value="id">用户ID</Option>
              <Option value="number">学号/工号</Option>
              <Option value="name">姓名</Option>
              <Option value="phone">联系电话</Option>
            </Select>
          </Col>

          <Col flex="auto">
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder="请输入关键词（回车或点击搜索）"
                allowClear
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onPressEnter={(e) => handleSearch(e.target.value)}
                size="middle"
              />
              <Button 
                type="primary" 
                icon={<SearchOutlined />} 
                onClick={() => handleSearch(searchKeyword)}
                size="middle"
              >
                搜索
              </Button>
            </Space.Compact>
          </Col>

          <Col>
            <Button
              onClick={handleResetSearch}
              size="middle"
            >
              重置
            </Button>
          </Col>

          <Col>
            <Select
              value={currentUserType}
              style={{ width: 200 }}
              onChange={handleUserTypeChange}
              size="large"
            >
              <Option value="students">
                <Space>
                  <UserOutlined />
                  学生账号
                </Space>
              </Option>
              <Option value="repairman">
                <Space>
                  <ToolOutlined />
                  维修人员账号
                </Space>
              </Option>
            </Select>
          </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={getCurrentData()}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => 
              `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
          }}
          size="middle"
        />
      </Card>

      {/* 编辑用户模态框 */}
      <Modal
        title="编辑用户信息"
        open={editModalVisible}
        onOk={handleSaveEdit}
        onCancel={handleCancelEdit}
        okText="保存"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          autoComplete="off"
        >
          <Form.Item
            label="用户ID"
          >
            <Input value={editingUser?.id} disabled />
          </Form.Item>

          <Form.Item
            label="姓名"
          >
            <Input value={editingUser?.name} disabled />
          </Form.Item>
          
          <Form.Item
            label="学号/工号"
          >
            <Input value={editingUser?.number} disabled />
          </Form.Item>
          
          <Form.Item
            label="联系电话"
            name="phone"
            rules={[
              { required: true, message: '请输入联系电话!' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码!' }
            ]}
          >
            <Input placeholder="请输入联系电话" />
          </Form.Item>

          <Form.Item
            label="用户类型"
          >
            <Input value={editingUser?.type === 'student' ? '学生' : '维修人员'} disabled />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default UserManagement;