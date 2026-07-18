// src/components/MyTask.jsx
import React, { useState, useEffect } from 'react';
import {
  Table, Tag, Button, Space, Select, Input,
  Modal, Form,  Card, Row, Col,
  Statistic, Progress, Descriptions, Image, message, List, Spin, Alert
} from 'antd';
import { SearchOutlined, PlayCircleOutlined, CheckCircleOutlined,
  EditOutlined, EyeOutlined, ClockCircleOutlined,
  ExclamationCircleOutlined, StarOutlined
} from '@ant-design/icons';
import { mytaskService, mytaskUtils } from './mytaskService.jsx';
import RepairTimeline from '../components/RepairTimeline';
import TicketComments from '../components/TicketComments';
import RepairProcessRecords from '../components/RepairProcessRecords';
import api from '../services/api';

const { Option } = Select;
const { Search } = Input;

const isSameDay = (value) => {
  if (!value) return false;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return false;
  const now = new Date();
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
};

const MyTask = ({ targetTaskId, onTargetTaskHandled, initialFilters, overdueOnly = false, todayOnly = false, title = "任务列表" }) => {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState(null);
  const [filteredTasks, setFilteredTasks] = useState([]);
  const [filters, setFilters] = useState({
    status: 'all',
    category: 'all',
    priority: 'all',
    keyword: '',
  });
  const [startModalVisible, setStartModalVisible] = useState(false);
  const [completeModalVisible, setCompleteModalVisible] = useState(false);
  const [notesModalVisible, setNotesModalVisible] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedTask, setSelectedTask] = useState(null);
  const [reportGenerating, setReportGenerating] = useState(false);
  const [knowledgeRecommendations, setKnowledgeRecommendations] = useState([]);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [startForm] = Form.useForm();
  const [completeForm] = Form.useForm();
  const [notesForm] = Form.useForm();

  // 当前维修工人ID - 从登录信息中获取
  const getCurrentRepairmanId = () => {
    try {
      const userStr = localStorage.getItem('user');
      console.log('从localStorage获取的用户信息字符串:', userStr);
      if (userStr) {
        const user = JSON.parse(userStr);
        console.log('解析后的用户对象:', user);
        const userId = user.userId || user.id || null;
        console.log('提取的维修工ID:', userId);
        return userId;
      }
    } catch (e) {
      console.error('获取用户信息失败:', e);
    }
    return null;
  };
  
  const currentRepairmanId = getCurrentRepairmanId();
  console.log('当前维修工ID (组件级别):', currentRepairmanId);

  // 加载任务列表
  const loadTasks = async (searchFilters = {}) => {
    if (!currentRepairmanId) {
      console.warn('无法加载任务：维修工ID为空');
      message.warn('无法获取用户信息，请重新登录');
      setTasks([]);
      setFilteredTasks([]);
      return;
    }

    setLoading(true);
    try {
      // 仅将状态筛选传给后端，其它筛选在前端本地处理
      const mergedFilters = { ...filters, ...searchFilters };
      const requestFilters = {};
      if (mergedFilters.status && mergedFilters.status !== 'all') {
        requestFilters.status = mergedFilters.status;
      }

      console.log('开始加载任务，维修工ID:', currentRepairmanId, '请求参数:', requestFilters);
      const result = await mytaskService.getMyTasks(currentRepairmanId, requestFilters);
      console.log('获取到的任务数据:', result);
      console.log('任务列表数量:', result.data?.length || 0);

      if (result.data && result.data.length > 0) {
        console.log('任务详情:', result.data.map(t => ({
          id: t.id || t.ticketId,
          status: t.status,
          originalStatus: t.originalStatus,
          location: t.location
        })));
      }

      // 设置任务列表
      const newTasks = result.data || [];
      setTasks(newTasks);

      // 立即应用本地筛选（确保 filteredTasks 同步更新）
      let filtered = [...newTasks];

      // 应用状态筛选
      if (mergedFilters.status && mergedFilters.status !== 'all') {
        filtered = filtered.filter(task => {
          const taskStatus = task.status || task.originalStatus;
          return taskStatus === mergedFilters.status;
        });
      }

      // 应用分类筛选
      if (mergedFilters.category && mergedFilters.category !== 'all') {
        filtered = filtered.filter(task => task.category === mergedFilters.category);
      }

      // 应用优先级筛选
      if (mergedFilters.priority && mergedFilters.priority !== 'all') {
        filtered = filtered.filter(task => task.priority === mergedFilters.priority);
      }

      // 应用关键词筛选
      if (mergedFilters.keyword && mergedFilters.keyword.trim() !== '') {
        const kw = mergedFilters.keyword.trim().toLowerCase();
        filtered = filtered.filter(task => {
          const location = (task.location || '').toLowerCase();
          const desc = (task.description || '').toLowerCase();
          const studentName = (task.studentName || task.studentId || '').toLowerCase();
          return location.includes(kw) || desc.includes(kw) || studentName.includes(kw);
        });
      }

      setFilteredTasks(filtered);
      console.log('设置过滤后的任务列表:', filtered.length, '条');

    } catch (error) {
      console.error('加载任务失败:', error);
      setTasks([]);
      setFilteredTasks([]);
      message.error('加载任务失败: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  // 加载统计数据
  const loadStats = async () => {
    if (!currentRepairmanId) {
      console.warn('无法加载统计：维修工ID为空');
      setStats({
        total: 0,
        pending: 0,
        processing: 0,
        completed: 0,
        to_be_evaluated: 0,
        closed: 0,
        averageRating: 0,
      });
      return;
    }
    
    try {
      console.log('开始加载统计数据，维修工ID:', currentRepairmanId);
      const statsData = await mytaskService.getRepairmanStats(currentRepairmanId);
      console.log('获取到的统计数据:', statsData);
      setStats(statsData);
    } catch (error) {
      console.error('加载统计数据失败:', error);
      // 设置默认统计数据
      setStats({
        total: 0,
        pending: 0,
        processing: 0,
        completed: 0,
        to_be_evaluated: 0,
        closed: 0,
        averageRating: 0,
      });
    }
  };

  // 处理筛选条件变化
  const handleFilterChange = (key, value) => {
    const newFilters = { ...filters, [key]: value };
    setFilters(newFilters);
    // 仅当状态变化时才重新请求后端，其它筛选在前端本地处理
    if (key === 'status') {
      loadTasks({ status: value });
    }
  };

  // 处理关键词搜索
  const handleSearch = (value) => {
    const newFilters = { ...filters, keyword: value };
    setFilters(newFilters);
    // 关键词搜索在前端本地过滤，不重新请求后端
  };

  // 本地应用分类、优先级、关键词筛选
  const applyFilters = () => {
    let result = [...tasks];

    // 状态筛选：后端已按状态过滤，但这里再兜底一次
    if (filters.status && filters.status !== 'all') {
      result = result.filter(task => {
        const taskStatus = task.status || task.originalStatus;
        return taskStatus === filters.status;
      });
    }

    // 分类筛选
    if (filters.category && filters.category !== 'all') {
      result = result.filter(task => task.category === filters.category);
    }

    // 优先级筛选
    if (filters.priority && filters.priority !== 'all') {
      result = result.filter(task => task.priority === filters.priority);
    }

    // 关键词筛选（位置 / 描述 / 学生姓名）
    if (filters.keyword && filters.keyword.trim() !== '') {
      const kw = filters.keyword.trim().toLowerCase();
      result = result.filter(task => {
        const location = (task.location || '').toLowerCase();
        const desc = (task.description || '').toLowerCase();
        const studentName = (task.studentName || task.studentId || '').toLowerCase();
        return (
          location.includes(kw) ||
          desc.includes(kw) ||
          studentName.includes(kw)
        );
      });
    }

    if (todayOnly) {
      result = result.filter(task => isSameDay(task.assigned_at || task.created_at));
    }

    if (overdueOnly) {
      result = result.filter(task => mytaskUtils.isTaskOverdue(task));
    }

    setFilteredTasks(result);
  };

  // 当任务列表或筛选条件变化时，重新计算本地筛选结果
  useEffect(() => {
    applyFilters();
  }, [tasks, filters, todayOnly, overdueOnly]);

  useEffect(() => {
    if (!initialFilters) return;
    setFilters((prev) => ({
      ...prev,
      status: initialFilters.status || 'all',
      category: initialFilters.category || 'all',
      priority: initialFilters.priority || 'all',
      keyword: initialFilters.keyword || '',
    }));
  }, [
    initialFilters?.status,
    initialFilters?.category,
    initialFilters?.priority,
    initialFilters?.keyword,
  ]);

  // 打开开始任务模态框
  const handleStartClick = (task) => {
    setSelectedTask(task);
    setStartModalVisible(true);
    startForm.setFieldsValue({
      estimated_completion_time: task.estimated_completion_time || task.estimatedCompletionTime || '',
    });
  };

  // 打开完成任务模态框
  const handleCompleteClick = (task) => {
    setSelectedTask(task);
    setCompleteModalVisible(true);
    completeForm.setFieldsValue({
      notes: task.notes || task.processNotes || '',
    });
  };

  const handleGenerateReport = async () => {
    if (!selectedTask) return;
    setReportGenerating(true);
    try {
      const values = completeForm.getFieldsValue();
      const response = await api.ai.generateRepairReport({
        description: selectedTask.description || selectedTask.problemDescription || '',
        processNotes: values.notes || selectedTask.processNotes || selectedTask.notes || '',
      });
      const report = response?.data?.report;
      if (report) {
        completeForm.setFieldsValue({ notes: report });
        message.success('AI 维修报告已生成');
      } else {
        message.warning('未生成有效报告，请手动填写');
      }
    } catch (error) {
      console.error('AI 维修报告生成失败:', error);
      message.warning(`AI 维修报告生成失败：${error.message}`);
    } finally {
      setReportGenerating(false);
    }
  };

  const loadKnowledgeRecommendations = async (task) => {
    if (!task) {
      setKnowledgeRecommendations([]);
      return;
    }
    setKnowledgeLoading(true);
    try {
      const response = await api.knowledgeBase.recommend({
        categoryKey: task.category,
        text: `${task.location || ''} ${task.description || ''} ${task.notes || task.processNotes || ''}`,
        limit: 3,
      });
      setKnowledgeRecommendations(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      console.warn('维修方案推荐加载失败:', error);
      setKnowledgeRecommendations([]);
    } finally {
      setKnowledgeLoading(false);
    }
  };

  // 打开更新备注模态框
  const handleNotesClick = (task) => {
    setSelectedTask(task);
    setNotesModalVisible(true);
    notesForm.setFieldsValue({
      notes: task.notes || '',
    });
  };

  // 打开详情模态框
  const handleDetailClick = async (task) => {
    try {
      // 从API获取任务详情
      const taskId = task.ticketId || task.id;
      if (!taskId) {
        message.error('任务ID不存在');
        return;
      }
      const taskDetail = await mytaskService.getTaskById(taskId, currentRepairmanId);
      setSelectedTask(taskDetail);
      loadKnowledgeRecommendations(taskDetail);
      setDetailModalVisible(true);
    } catch (error) {
      console.error('获取任务详情失败:', error);
    }
  };

  // 处理开始任务
  useEffect(() => {
    if (!targetTaskId) return;
    handleDetailClick({ id: targetTaskId, ticketId: targetTaskId });
    if (onTargetTaskHandled) {
      onTargetTaskHandled();
    }
  }, [targetTaskId]);

  const handleStartSubmit = async (values) => {
    try {
      const taskId = selectedTask.ticketId || selectedTask.id;
      if (!taskId) {
        message.error('任务ID不存在');
        return;
      }

      console.log('开始处理任务，任务ID:', taskId);

      await mytaskService.startTask(
        taskId,
        currentRepairmanId,
        values.estimated_completion_time
      );

      setStartModalVisible(false);
      startForm.resetFields();

      message.success('任务开始处理成功！');

      // 刷新任务列表和统计数据
      console.log('开始刷新任务列表...');
      await loadTasks();
      console.log('任务列表刷新完成');

      console.log('开始刷新统计数据...');
      await loadStats();
      console.log('统计数据刷新完成');

    } catch (error) {
      console.error('开始任务失败:', error);
      message.error('开始任务失败: ' + error.message);
      // 错误时不关闭模态框，让用户可以重试
    }
  };

  // 处理完成任务
  const handleCompleteSubmit = async (values) => {
    try {
      const taskId = selectedTask.ticketId || selectedTask.id;
      if (!taskId) {
        message.error('任务ID不存在');
        return;
      }

      console.log('开始完成任务，任务ID:', taskId, '备注:', values.notes);

      // 调用完成任务API
      const result = await mytaskService.completeTask(
        taskId,
        currentRepairmanId,
        values.notes
      );

      console.log('完成任务API返回结果:', result);

      // 关闭模态框
      setCompleteModalVisible(false);
      completeForm.resetFields();

      message.success('任务已完成！');

      // 刷新任务列表和统计数据
      console.log('开始刷新任务列表...');
      await loadTasks();
      console.log('任务列表刷新完成');

      console.log('开始刷新统计数据...');
      await loadStats();
      console.log('统计数据刷新完成');

    } catch (error) {
      console.error('完成任务失败:', error);
      message.error('完成任务失败: ' + error.message);
      // 错误时不关闭模态框，让用户可以重试
    }
  };

  // 处理更新备注
  const handleNotesSubmit = async (values) => {
    try {
      await mytaskService.updateTaskNotes(
        selectedTask.id, 
        currentRepairmanId, 
        values.notes
      );
      setNotesModalVisible(false);
      loadTasks(); // 刷新任务列表
    } catch (error) {
      console.error('更新备注失败:', error);
      // 错误信息已经在service中显示
    }
  };

  // 初始化加载数据

  const handleRegenerateCompletionSummary = async () => {
    const taskId = selectedTask?.ticketId || selectedTask?.id;
    if (!taskId) return;
    setReportGenerating(true);
    try {
      const response = await api.ai.regenerateCompletionSummary(taskId);
      const summary = response?.data || null;
      setSelectedTask((prev) => prev ? { ...prev, completionSummary: summary } : prev);
      message.success('完成总结已重新生成');
    } catch (error) {
      console.warn('完成总结重新生成失败:', error);
      message.error(error.message || '完成总结重新生成失败');
    } finally {
      setReportGenerating(false);
    }
  };
  useEffect(() => {
    if (currentRepairmanId) {
      console.log('========================================');
      console.log('维修工端 - 组件初始化');
      console.log('维修工ID:', currentRepairmanId);
      console.log('========================================');
      loadTasks();
      loadStats();
    } else {
      console.warn('无法获取维修工ID，请先登录');
      message.warning('无法获取用户信息，请重新登录');
    }
  }, []);

  // ⚠️ 重要：添加轮询刷新机制，每10秒自动刷新统计数据
  useEffect(() => {
    if (!currentRepairmanId) return;

    const interval = setInterval(() => {
      console.log('========================================');
      console.log('维修工端 - 轮询刷新统计数据（10秒）');
      console.log('当前时间:', new Date().toLocaleString());
      console.log('维修工ID:', currentRepairmanId);
      console.log('========================================');
      loadStats();
    }, 10000); // 10秒刷新一次

    return () => {
      console.log('维修工端 - 清除轮询定时器');
      clearInterval(interval);
    };
  }, [currentRepairmanId]);

  // 表格列定义
  const columns = [
    {
      title: '任务ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      render: (id, record) => id || record.ticketId || '未知',
    },
    {
      title: '报修分类',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (category) => {
        if (!category) return '未知';
        const categoryInfo = mytaskUtils.getCategoryInfo(category);
        return categoryInfo ? categoryInfo.label : category;
      },
    },
    {
      title: '具体位置',
      dataIndex: 'location',
      key: 'location',
      width: 120,
      render: (location) => location || '未指定',
    },
    {
      title: '问题描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      width: 200,
      render: (description) => description || '无描述',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status, record) => {
        // 优先使用映射后的前端状态，如果没有则使用原始状态再映射一次
        const displayStatus = status || record.status || record.originalStatus;
        let statusInfo = { label: displayStatus || '未知', color: 'default' };
        let isOverdue = false;
        try {
          if (mytaskUtils && typeof mytaskUtils.getStatusInfo === 'function') {
            statusInfo = mytaskUtils.getStatusInfo(displayStatus) || statusInfo;
          }
          if (mytaskUtils && typeof mytaskUtils.isTaskOverdue === 'function') {
            isOverdue = !!mytaskUtils.isTaskOverdue(record);
          }
        } catch (e) {
          console.error('渲染状态信息出错:', e, { status, record });
        }

        return (
          <Space direction="vertical" size="small">
            <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
            {isOverdue && (
              <Tag color="red" icon={<ExclamationCircleOutlined />}>
                已超时
              </Tag>
            )}
          </Space>
        );
      },
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 80,
      render: (priority) => {
        let priorityInfo = { label: priority || '未知', color: 'default' };
        try {
          if (mytaskUtils && typeof mytaskUtils.getPriorityInfo === 'function') {
            priorityInfo = mytaskUtils.getPriorityInfo(priority) || priorityInfo;
          }
        } catch (e) {
          console.error('渲染优先级出错:', e, { priority });
        }
        return <Tag color={priorityInfo.color}>{priorityInfo.label}</Tag>;
      },
    },
    {
      title: '学生信息',
      dataIndex: 'studentName',
      key: 'studentName',
      width: 120,
      render: (name, record) => {
        const studentName = name || record.studentName || record.studentId || '未知';
        const contactPhone = record.contactPhone || '';
        return (
          <div>
            <div>{studentName}</div>
            {contactPhone && (
              <div style={{ fontSize: '12px', color: '#666' }}>
                {contactPhone}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '分配时间',
      dataIndex: 'assigned_at',
      key: 'assigned_at',
      width: 150,
      render: (time, record) => {
        const assignedTime = time || record.assignedAt;
        if (assignedTime) {
          // 如果是字符串，直接显示；如果是日期对象，格式化
          return typeof assignedTime === 'string' ? assignedTime : new Date(assignedTime).toLocaleString('zh-CN');
        }
        return '未分配';
      },
    },
    {
      title: '预计完成',
      dataIndex: 'estimated_completion_time',
      key: 'estimated_completion_time',
      width: 150,
      render: (time, record) => {
        const estimatedTime = time || record.estimatedCompletionTime || record.estimated_completion_time;
        if (estimatedTime) {
          // 如果是字符串，直接显示；如果是日期对象，格式化
          return typeof estimatedTime === 'string' ? estimatedTime : new Date(estimatedTime).toLocaleString('zh-CN');
        }
        return '未设置';
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => {
        const isPending = record.status === 'pending';
        const isProcessing = record.status === 'processing';
        
        return (
          <Space size="small" direction="vertical">
            <Space size="small">
              {isPending && (
                <Button 
                  type="primary" 
                  size="small"
                  icon={<PlayCircleOutlined />}
                  onClick={() => handleStartClick(record)}
                >
                  开始处理
                </Button>
              )}
              {isProcessing && (
                <Button 
                  type="primary" 
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => handleCompleteClick(record)}
                >
                  完成任务
                </Button>
              )}
              <Button 
                size="small"
                icon={<EditOutlined />}
                onClick={() => handleNotesClick(record)}
              >
                备注
              </Button>
            </Space>
            <Button 
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleDetailClick(record)}
            >
              详情
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <div className="task-list-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2>{title}</h2>
        <Button
          type="primary"
          icon={<SearchOutlined />}
          onClick={() => {
            console.log('手动刷新任务列表...');
            loadTasks();
            loadStats();
          }}
          loading={loading}
        >
          刷新数据
        </Button>
      </div>

      {/* 统计卡片 */}
      {stats && (
        <Card className="task-stats-card" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={4}>
              <Statistic title="总任务数" value={stats.total} />
            </Col>
            <Col span={4}>
              <Statistic title="待受理" value={stats.pending} valueStyle={{ color: '#faad14' }} />
            </Col>
            <Col span={4}>
              <Statistic title="处理中" value={stats.processing} valueStyle={{ color: '#1890ff' }} />
            </Col>
            <Col span={4}>
              <Statistic title="待评价" value={stats.to_be_evaluated} valueStyle={{ color: '#722ed1' }} />
            </Col>
            <Col span={4}>
              <Statistic title="已完成" value={stats.completed} valueStyle={{ color: '#52c41a' }} />
            </Col>
            <Col span={4}>
              <Statistic
                title="平均评分"
                value={stats.averageRating}
                prefix={<StarOutlined />}
                valueStyle={{ color: '#fadb14' }}
              />
            </Col>
          </Row>
        </Card>
      )}

      {/* 搜索和筛选区域 */}
      <Card className="task-filter-card" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <div style={{ marginBottom: 8 }}>状态筛选</div>
            <Select
              style={{ width: '100%' }}
              value={filters.status}
              onChange={(value) => handleFilterChange('status', value)}
            >
              <Option value="all">全部状态</Option>
              <Option value="pending">待受理</Option>
              <Option value="processing">处理中</Option>
              <Option value="to_be_evaluated">待评价</Option>
              <Option value="completed">已完成</Option>
            </Select>
          </Col>

          <Col span={6}>
            <div style={{ marginBottom: 8 }}>分类筛选</div>
            <Select style={{ width: '100%' }} value={filters.category}
              onChange={(value) => handleFilterChange('category', value)}
            >
              <Option value="all">全部分类</Option>
              <Option value="waterAndElectricity">水电维修</Option>
              <Option value="networkIssues">网络故障</Option>
              <Option value="furnitureRepair">家具维修</Option>
              <Option value="applianceIssues">电器故障</Option>
              <Option value="publicFacilities">公共设施</Option>
            </Select>
          </Col>

          <Col span={6}>
            <div style={{ marginBottom: 8 }}>优先级筛选</div>
            <Select
              style={{ width: '100%' }}
              value={filters.priority}
              onChange={(value) => handleFilterChange('priority', value)}
            >
              <Option value="all">全部优先级</Option>
              <Option value="high">高</Option>
              <Option value="medium">中</Option>
              <Option value="low">低</Option>
            </Select>
          </Col>

          <Col span={6}>
            <div style={{ marginBottom: 8 }}>关键词搜索</div>
            <Search
              placeholder="搜索位置、描述或学生姓名..."
              allowClear
              enterButton={<SearchOutlined />}
              onSearch={handleSearch}
              onChange={(e) => {
                if (!e.target.value) {
                  handleSearch('');
                }
              }}
            />
          </Col>
        </Row>
      </Card>

      {/* 任务表格 */}
          <Table
            className="task-data-table"
            columns={columns}
            dataSource={filteredTasks}
            rowKey={(record) => record.ticketId || record.id || record.key || Math.random()}
            loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total, range) => 
            `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
        }}
        scroll={{ x: 1500 }}
      />

      {/* 开始任务模态框 */}
      <Modal
        title="开始处理任务"
        open={startModalVisible}
        onCancel={() => setStartModalVisible(false)}
        footer={null}
        width={600}
      >
        {selectedTask && (
          <Form
            form={startForm}
            layout="vertical"
            onFinish={handleStartSubmit}
          >
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="任务ID">{selectedTask.ticketId || selectedTask.id}</Descriptions.Item>
              <Descriptions.Item label="报修位置">{selectedTask.location || selectedTask.locationText}</Descriptions.Item>
              <Descriptions.Item label="问题描述">{selectedTask.description || '无描述'}</Descriptions.Item>
              <Descriptions.Item label="学生信息">
                {selectedTask.studentName || selectedTask.studentId || '未知'} 
                {selectedTask.contactPhone && ` (${selectedTask.contactPhone})`}
              </Descriptions.Item>
            </Descriptions>

            <Form.Item
              label="预计完成时间"
              name="estimated_completion_time"
              rules={[{ required: true, message: '请选择预计完成时间!' }]}
            >
              <Input type="datetime-local" />
            </Form.Item>
            
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<PlayCircleOutlined />}>
                  开始处理
                </Button>
                <Button onClick={() => setStartModalVisible(false)}>
                  取消
                </Button>
              </Space>
            </Form.Item>
          </Form>
        )}
      </Modal>

      {/* 完成任务模态框 */}
      <Modal
        title="完成任务"
        open={completeModalVisible}
        onCancel={() => setCompleteModalVisible(false)}
        footer={null}
        width={600}
      >
        {selectedTask && (
          <Form
            form={completeForm}
            layout="vertical"
            onFinish={handleCompleteSubmit}
          >
            <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="任务ID">{selectedTask.id}</Descriptions.Item>
              <Descriptions.Item label="报修位置">{selectedTask.location}</Descriptions.Item>
              <Descriptions.Item label="问题描述">{selectedTask.description}</Descriptions.Item>
            </Descriptions>

            <Button
              style={{ marginBottom: 12 }}
              loading={reportGenerating}
              onClick={handleGenerateReport}
            >
              AI 生成维修报告
            </Button>

            <Form.Item
              label="完成备注"
              name="notes"
              rules={[{ required: true, message: '请输入完成备注!' }]}
            >
              <Input.TextArea 
                rows={4} 
                placeholder="请描述维修过程和结果..."
              />
            </Form.Item>
            
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<CheckCircleOutlined />}>
                  确认完成
                </Button>
                <Button onClick={() => setCompleteModalVisible(false)}>
                  取消
                </Button>
              </Space>
            </Form.Item>
          </Form>
        )}
      </Modal>

      {/* 更新备注模态框 */}
      <Modal
        title="更新任务备注"
        open={notesModalVisible}
        onCancel={() => setNotesModalVisible(false)}
        footer={null}
        width={500}
      >
        {selectedTask && (
          <Form
            form={notesForm}
            layout="vertical"
            onFinish={handleNotesSubmit}
          >
            <Form.Item
              label="任务备注"
              name="notes"
            >
              <Input.TextArea 
                rows={4} 
                placeholder="请输入任务备注信息..."
              />
            </Form.Item>
            
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  保存备注
                </Button>
                <Button onClick={() => setNotesModalVisible(false)}>
                  取消
                </Button>
              </Space>
            </Form.Item>
          </Form>
        )}
      </Modal>

      {/* 任务详情模态框 */}
      <Modal
        title="任务详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>
        ]}
        width={700}
      >
        {selectedTask && (
          <>
          <Descriptions column={2} bordered>
            <Descriptions.Item label="任务ID" span={1}>
              {selectedTask.id}
            </Descriptions.Item>
            <Descriptions.Item label="状态" span={1}>
              {(() => {
                let statusInfo = { label: selectedTask.status || '未知', color: 'default' };
                try {
                  if (mytaskUtils && typeof mytaskUtils.getStatusInfo === 'function') {
                    statusInfo = mytaskUtils.getStatusInfo(selectedTask.status) || statusInfo;
                  }
                } catch (e) {
                  console.error('详情中渲染状态出错:', e, { selectedTask });
                }
                return (
                  <Tag color={statusInfo.color}>
                    {statusInfo.label}
                  </Tag>
                );
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="报修分类" span={1}>
              {(() => {
                try {
                  if (mytaskUtils && typeof mytaskUtils.getCategoryInfo === 'function') {
                    const info = mytaskUtils.getCategoryInfo(selectedTask.category);
                    return info?.label || selectedTask.category || '未知';
                  }
                } catch (e) {
                  console.error('详情中渲染分类出错:', e, { selectedTask });
                }
                return selectedTask.category || '未知';
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="优先级" span={1}>
              {(() => {
                let priorityInfo = { label: selectedTask.priority || '未知', color: 'default' };
                try {
                  if (mytaskUtils && typeof mytaskUtils.getPriorityInfo === 'function') {
                    priorityInfo = mytaskUtils.getPriorityInfo(selectedTask.priority) || priorityInfo;
                  }
                } catch (e) {
                  console.error('详情中渲染优先级出错:', e, { selectedTask });
                }
                return (
                  <Tag color={priorityInfo.color}>
                    {priorityInfo.label}
                  </Tag>
                );
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="具体位置" span={2}>
              {selectedTask.location}
            </Descriptions.Item>
            <Descriptions.Item label="问题描述" span={2}>
              {selectedTask.description}
            </Descriptions.Item>
            <Descriptions.Item label="学生信息" span={1}>
              {selectedTask.studentName} (学号: {selectedTask.studentID})
            </Descriptions.Item>
            <Descriptions.Item label="联系电话" span={1}>
              {selectedTask.contactPhone}
            </Descriptions.Item>
            <Descriptions.Item label="提交时间" span={1}>
              {selectedTask.created_at}
            </Descriptions.Item>
            <Descriptions.Item label="分配时间" span={1}>
              {selectedTask.assigned_at || selectedTask.assignedAt ? 
                (typeof (selectedTask.assigned_at || selectedTask.assignedAt) === 'string' ? 
                  (selectedTask.assigned_at || selectedTask.assignedAt) : 
                  new Date(selectedTask.assigned_at || selectedTask.assignedAt).toLocaleString('zh-CN')) : 
                '未分配'}
            </Descriptions.Item>
            <Descriptions.Item label="预计完成时间" span={1}>
              {selectedTask.estimated_completion_time || selectedTask.estimatedCompletionTime ? 
                (typeof (selectedTask.estimated_completion_time || selectedTask.estimatedCompletionTime) === 'string' ? 
                  (selectedTask.estimated_completion_time || selectedTask.estimatedCompletionTime) : 
                  new Date(selectedTask.estimated_completion_time || selectedTask.estimatedCompletionTime).toLocaleString('zh-CN')) : 
                '未设置'}
            </Descriptions.Item>
            <Descriptions.Item label="实际完成时间" span={1}>
              {selectedTask.completed_at || '未完成'}
            </Descriptions.Item>
            <Descriptions.Item label="任务耗时" span={1}>
              {(() => {
                try {
                  if (mytaskUtils && typeof mytaskUtils.calculateTaskDuration === 'function') {
                    return mytaskUtils.calculateTaskDuration(selectedTask) || '未开始';
                  }
                } catch (e) {
                  console.error('详情中计算任务耗时出错:', e, { selectedTask });
                }
                return '未开始';
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="是否超时" span={1}>
              {(() => {
                let overdue = false;
                try {
                  if (mytaskUtils && typeof mytaskUtils.isTaskOverdue === 'function') {
                    overdue = !!mytaskUtils.isTaskOverdue(selectedTask);
                  }
                } catch (e) {
                  console.error('详情中判断是否超时出错:', e, { selectedTask });
                }
                return overdue ? (
                  <Tag color="red" icon={<ExclamationCircleOutlined />}>已超时</Tag>
                ) : (
                  <Tag color="green">正常</Tag>
                );
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="维修备注" span={2}>
              {selectedTask.notes || '无'}
            </Descriptions.Item>
            {selectedTask.images && selectedTask.images.length > 0 && (
              <Descriptions.Item label="报修图片" span={2}>
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                  {selectedTask.images.map((img, index) => {
                    // 处理图片URL
                    let imageUrl = img;
                    if (typeof img === 'object') {
                      imageUrl = img.imageUrl || img.url || img;
                    }
                    // 确保URL完整
                    if (imageUrl && !imageUrl.startsWith('http')) {
                      imageUrl = `http://localhost:8070${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
                    }
                    console.log('图片URL:', imageUrl, '原始图片:', img);
                    return (
                      <Image
                        key={index}
                        width={100}
                        height={75}
                        src={imageUrl}
                        style={{
                          borderRadius: 6,
                          objectFit: 'cover',
                          border: '1px solid #d9d9d9',
                          cursor: 'pointer',
                        }}
                        placeholder={
                          <div style={{
                            width: 100,
                            height: 75,
                            background: '#f5f5f5',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: 6,
                          }}>
                            加载中...
                          </div>
                        }
                      />
                    );
                  })}
                </div>
              </Descriptions.Item>
            )}
            {selectedTask.rating && (
              <Descriptions.Item label="用户评分" span={1}>
                <Space>
                  <StarOutlined style={{ color: '#fadb14' }} />
                  {Number(selectedTask.rating) || 0} 分
                </Space>
              </Descriptions.Item>
            )}
            {selectedTask.feedback && (
              <Descriptions.Item label="用户反馈" span={2}>
                {selectedTask.feedback}
              </Descriptions.Item>
            )}
          </Descriptions>
          <Card
            size="small"
            title="完成总结"
            style={{ marginTop: 16, marginBottom: 16 }}
            extra={<Button size="small" loading={reportGenerating} onClick={handleRegenerateCompletionSummary}>重新生成</Button>}
          >
            {selectedTask.completionSummary?.summary ? (
              <div style={{ lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{selectedTask.completionSummary.summary}</div>
            ) : (
              <div style={{ color: '#8c8c8c' }}>暂无完成总结</div>
            )}
          </Card>
          <Card
            size="small"
            title="相似维修案例与建议"
            style={{ marginTop: 16, marginBottom: 16 }}
          >
            {selectedTask.similarRepairCases?.length > 0 ? (
              <List
                size="small"
                dataSource={selectedTask.similarRepairCases}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={
                        <Space wrap>
                          <span>{item.categoryName || '历史案例'}</span>
                          {item.similarity !== null && item.similarity !== undefined ? (
                            <Tag color="green">相似度 {Math.round(item.similarity * 100)}%</Tag>
                          ) : (
                            <Tag color="orange">分类历史案例</Tag>
                          )}
                        </Space>
                      }
                      description={
                        <Space direction="vertical" size={4} style={{ width: '100%' }}>
                          {item.failureCause && <div>故障原因：{item.failureCause}</div>}
                          {item.repairMethod && <div>维修方法：{item.repairMethod}</div>}
                          {item.materials && <div>材料：{item.materials}</div>}
                          {item.result && <div>处理结果：{item.result}</div>}
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ color: '#64748b' }}>暂无相似历史案例，按现场情况处理并补充维修记录即可。</div>
            )}
          </Card>
          <Card
            size="small"
            title="推荐维修方案"
            style={{ marginTop: 16, marginBottom: 16 }}
          >
            <Spin spinning={knowledgeLoading}>
              {knowledgeRecommendations.length > 0 ? (
                <List
                  size="small"
                  dataSource={knowledgeRecommendations}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space wrap>
                            <span>{item.title}</span>
                            {item.estimatedMinutes && (
                              <Tag color="blue">预计 {item.estimatedMinutes} 分钟</Tag>
                            )}
                          </Space>
                        }
                        description={
                          <Space direction="vertical" size={4} style={{ width: '100%' }}>
                            {item.solutionSteps && (
                              <div style={{ whiteSpace: 'pre-wrap' }}>处理步骤：{item.solutionSteps}</div>
                            )}
                            {item.safetyNotes && (
                              <Alert
                                type="warning"
                                showIcon
                                message="安全注意"
                                description={item.safetyNotes}
                              />
                            )}
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <div style={{ color: '#64748b' }}>
                  暂无匹配的知识库方案，可按现场情况处理并在完成后补充维修记录。
                </div>
              )}
            </Spin>
          </Card>
          <RepairTimeline order={selectedTask} />
          <RepairProcessRecords ticketId={selectedTask.ticketId || selectedTask.id} role="STAFF" editable />
          <TicketComments ticketId={selectedTask.ticketId || selectedTask.id} role="STAFF" />
          </>
        )}
      </Modal>
    </div>
  );
};

export default MyTask;
