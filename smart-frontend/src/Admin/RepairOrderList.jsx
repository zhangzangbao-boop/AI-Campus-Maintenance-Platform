import React, { useState, useEffect } from 'react';
import { 
  Table, Tag, Button, Space, Select, Input, 
  Modal, Form, message, Card, Row, Col, Switch, Descriptions, Image, Spin, Empty
} from 'antd';
import { 
  SearchOutlined, UserOutlined, CloseOutlined, CheckOutlined 
  , ExclamationCircleOutlined, ClockCircleOutlined
} from '@ant-design/icons';
import { repairService, repairUtils } from '../services/repairService';
import api from '../services/api';
import RepairTimeline from '../components/RepairTimeline';
import TicketComments from '../components/TicketComments';
import RepairProcessRecords from '../components/RepairProcessRecords';

const { Option } = Select;
const { Search } = Input;

const RepairOrderList = ({ onRefresh, targetOrderId, onTargetOrderHandled }) => {
  const [repairOrders, setRepairOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState({
    status: 'all',
    category: 'all',
    keyword: '',
    includeDeleted: false,
  });
  const [assignModalVisible, setAssignModalVisible] = useState(false);
  const [rejectModalVisible, setRejectModalVisible] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [repairmen, setRepairmen] = useState([]);
  const [recommendations, setRecommendations] = useState([]);
  const [recommendLoading, setRecommendLoading] = useState(false);
  const [slaOverview, setSlaOverview] = useState(null);
  const [slaLoading, setSlaLoading] = useState(false);
  const [slaFilter, setSlaFilter] = useState('all');
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState(null);
  const [aiSummary, setAiSummary] = useState(null);
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false);
  const [aiCorrectionLoading, setAiCorrectionLoading] = useState(false);
  const [assignForm] = Form.useForm();
  const [rejectForm] = Form.useForm();
  const [aiCorrectionForm] = Form.useForm();

  const mapStatusToFrontend = (status) => {
    if (!status) return 'pending';
    const map = {
      WAITING_ACCEPT: 'pending',
      IN_PROGRESS: 'processing',
      RESOLVED: 'completed',
      WAITING_FEEDBACK: 'to_be_evaluated',
      FEEDBACKED: 'closed',
      CLOSED: 'closed',
      REJECTED: 'rejected',
    };
    return map[status] || status;
  };

  const mapPriorityToFrontend = (priority) => {
    if (!priority) return 'low';
    const map = {
      LOW: 'low',
      MEDIUM: 'medium',
      HIGH: 'high',
    };
    return map[priority] || priority.toLowerCase?.() || priority;
  };

  const formatTime = (val) => {
    if (!val) return '';
    try {
      const d = new Date(val);
      if (!isNaN(d.getTime())) {
        return d.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        });
      }
    } catch (e) {
      console.error('时间格式化失败:', val, e);
    }
    return val;
  };

  const formatHours = (hours) => {
    const value = Number(hours || 0);
    if (value >= 24) {
      const days = Math.floor(value / 24);
      const restHours = value % 24;
      return restHours > 0 ? `${days}天${restHours}小时` : `${days}天`;
    }
    return `${value}小时`;
  };

  const getSlaInfo = (record) => {
    const ticketId = String(record.ticketId || record.id || '');
    if (!ticketId || !Array.isArray(slaOverview?.alertTickets)) {
      return null;
    }
    return slaOverview.alertTickets.find(item => String(item.ticketId || item.id) === ticketId) || null;
  };

  const getSlaFilteredOrders = () => {
    if (slaFilter === 'all') {
      return repairOrders;
    }
    const alertTickets = Array.isArray(slaOverview?.alertTickets) ? slaOverview.alertTickets : [];
    return alertTickets.filter(item => {
      if (slaFilter === 'overdue') {
        return item.slaStatus === 'OVERDUE';
      }
      if (slaFilter === 'overdue_accept') {
        return item.slaStatus === 'OVERDUE' && item.slaType === 'ACCEPTANCE';
      }
      if (slaFilter === 'overdue_completion') {
        return item.slaStatus === 'OVERDUE' && item.slaType === 'COMPLETION';
      }
      if (slaFilter === 'warning') {
        return item.slaStatus === 'WARNING';
      }
      return true;
    });
  };

  const renderSlaStatus = (record) => {
    const slaInfo = getSlaInfo(record) || record;
    if (!slaInfo?.slaStatus) {
      const active = record.status === 'pending' || record.status === 'processing'
        || record.status === 'WAITING_ACCEPT' || record.status === 'IN_PROGRESS';
      return active ? <Tag color="green">SLA正常</Tag> : <Tag color="default">已结束</Tag>;
    }

    if (slaInfo.slaStatus === 'OVERDUE') {
      return (
        <Space direction="vertical" size={2}>
          <Tag color="red" icon={<ExclamationCircleOutlined />}>已超时</Tag>
          <span style={{ color: '#991b1b', fontSize: 12 }}>
            {slaInfo.slaLabel || '处理时限'}超出 {formatHours(slaInfo.overdueHours)}
          </span>
        </Space>
      );
    }

    return (
      <Space direction="vertical" size={2}>
        <Tag color="orange" icon={<ClockCircleOutlined />}>即将超时</Tag>
        <span style={{ color: '#92400e', fontSize: 12 }}>
          剩余 {formatHours(slaInfo.remainingHours)}
        </span>
      </Space>
    );
  };

  const loadSlaOverview = async () => {
    setSlaLoading(true);
    try {
      const data = await repairService.getSlaOverview();
      setSlaOverview(data);
    } catch (error) {
      console.error('获取SLA预警失败:', error);
      message.warning(`获取SLA预警失败：${error.message || '未知错误'}`);
    } finally {
      setSlaLoading(false);
    }
  };

  // 加载工单数据
  const loadRepairOrders = async (searchFilters = {}) => {
    setLoading(true);
    try {
      const result = await repairService.getRepairOrders({
        ...filters,
        ...searchFilters,
      });
      setRepairOrders(result.data);
    } catch (error) {
      console.error('获取工单失败:', error);
      message.error('获取工单失败');
    } finally {
      setLoading(false);
    }
  };

  // 加载维修人员列表
  const loadRepairmen = async () => {
    try {
      const repairmenList = await repairService.getRepairmen();
      setRepairmen(repairmenList);
    } catch (error) {
      console.error('获取维修人员列表失败:', error);
      message.error('获取维修人员列表失败');
      // 设置默认维修人员列表作为fallback
      setRepairmen([
        { id: 1, name: '张师傅' },
        { id: 2, name: '李师傅' },
        { id: 3, name: '王师傅' },
        { id: 4, name: '赵师傅' },
      ]);
    }
  };

  // 搜索和筛选工单
  const searchRepairOrders = async (searchFilters = {}) => {
    setLoading(true);
    try {
      // 将 categoryKey 转换为 categoryName（如果存在）
      const processedFilters = { ...filters, ...searchFilters };
      if (processedFilters.category && processedFilters.category !== 'all' && categoryKeyToNameMap[processedFilters.category]) {
        processedFilters.category = categoryKeyToNameMap[processedFilters.category];
        console.log('搜索前分类Key转换:', searchFilters.category, '->', processedFilters.category);
      }
      
      const result = await repairService.searchRepairOrders(processedFilters);
      console.log('搜索工单结果:', result);
      console.log('工单数据（原始）:', result.data);
      
      // 如果数据还没有映射（检查是否有 categoryName 但没有 category），则手动映射
      let mappedData = result.data || [];
      if (mappedData.length > 0 && mappedData[0].categoryName && !mappedData[0].category) {
        console.log('⚠️ 检测到数据未映射，执行手动映射...');
        mappedData = mappedData.map(order => ({
          ...order,
          id: order.ticketId || order.id,
          ticketId: order.ticketId || order.id,
          category: order.categoryName || order.category || '',
          location: order.locationText || order.location || '',
          description: order.description || '',
          priority: order.priority || 'low',
          status: order.status === 'WAITING_ACCEPT' ? 'pending' : 
                  order.status === 'IN_PROGRESS' ? 'processing' :
                  order.status === 'RESOLVED' ? 'completed' :
                  order.status === 'WAITING_FEEDBACK' ? 'to_be_evaluated' :
                  order.status === 'FEEDBACKED' || order.status === 'CLOSED' ? 'closed' :
                  order.status === 'REJECTED' ? 'rejected' : order.status,
          studentID: order.studentId || order.studentID || '',
          repairmanId: order.staffId || order.repairmanId || null,
          created_at: order.createdAt || order.created_at || '',
          deleted: order.deleted || false,
          deletedAt: order.deletedAt || null,
        }));
        console.log('✅ 手动映射完成:', mappedData);
      }
      
      console.log('工单数量:', mappedData?.length || 0);
      setRepairOrders(mappedData);
    } catch (error) {
      console.error('搜索工单失败:', error);
      message.error('搜索工单失败');
      setRepairOrders([]);
    } finally {
      setLoading(false);
    }
  };

  // 切换显示已删除工单
  const handleIncludeDeletedChange = (checked) => {
    const newFilters = { ...filters, includeDeleted: checked };
    setFilters(newFilters);
    searchRepairOrders(newFilters);
  };

  // 分类Key到分类名称的映射
  const categoryKeyToNameMap = {
    'waterAndElectricity': '水电维修',
    'networkIssues': '网络故障',
    'furnitureRepair': '家具维修',
    'applianceIssues': '电器故障',
    'publicFacilities': '公共设施',
  };

  // 处理筛选条件变化
  const handleFilterChange = (key, value) => {
    // 保留现有的所有筛选条件（包括 keyword），只更新指定的筛选条件
    let filterValue = value;
    
    // 如果是分类筛选，将 categoryKey 转换为 categoryName
    if (key === 'category' && value !== 'all' && categoryKeyToNameMap[value]) {
      filterValue = categoryKeyToNameMap[value];
      console.log('分类Key转换:', value, '->', filterValue);
    }
    
    const newFilters = { ...filters, [key]: filterValue };
    setFilters({ ...filters, [key]: value }); // UI状态保持使用categoryKey
    console.log('筛选条件变化:', key, '=', value, '->', filterValue, '完整筛选条件:', newFilters);
    searchRepairOrders(newFilters);
  };

  // 处理关键词搜索
  const handleSearch = (value) => {
    const keyword = value ? value.trim() : '';
    // 保留现有的所有筛选条件（包括 status、category 等），只更新 keyword
    const newFilters = { ...filters, keyword };
    setFilters(newFilters);
    console.log('搜索工单，关键词:', keyword, '完整筛选条件:', newFilters);
    searchRepairOrders(newFilters);
  };

  // 打开分配模态框
  const handleAssignClick = async (order) => {
    setSelectedOrder(order);
    setAssignModalVisible(true);
    setRecommendations([]);
    assignForm.setFieldsValue({
      repairmanId: order.repairmanId || order.staffId || undefined,
    });

    const orderId = order.ticketId || order.id;
    if (!orderId) {
      message.warning('工单ID不存在，无法加载智能推荐');
      return;
    }

    setRecommendLoading(true);
    try {
      const data = await repairService.getRecommendedRepairmen(orderId);
      setRecommendations(data);
    } catch (error) {
      console.error('智能派单推荐加载失败:', error);
      message.warning(`智能派单推荐加载失败：${error.message || '未知错误'}，可继续手动选择维修人员`);
      setRecommendations([]);
    } finally {
      setRecommendLoading(false);
    }
  };

  // 打开驳回模态框
  const handleRejectClick = (order) => {
    setSelectedOrder(order);
    setRejectModalVisible(true);
    rejectForm.resetFields();
  };

  // 查看工单详情
  const handleViewDetail = async (order) => {
    try {
      setDetailLoading(true);
      setDetailVisible(true);
      const id = order.ticketId || order.id;
      if (!id) {
        message.error('工单ID不存在');
        return;
      }
      const detail = await repairService.getRepairOrderById(id);
      setAiSummary(detail.completionSummary || null);
      setAiSummaryLoading(false);
      aiCorrectionForm.setFieldsValue({
        categoryKey: detail.aiAnalysis?.finalCategoryKey,
        urgency: detail.aiAnalysis?.finalUrgency,
        suggestion: detail.aiAnalysis?.finalSuggestion,
        reason: undefined,
      });
      setDetailData({
        ...detail,
        id: detail.ticketId || detail.id,
        title: detail.title || (detail.locationText ? `报修-${detail.locationText}` : '报修单'),
        description: detail.description || '',
        location: detail.locationText || detail.location || '',
        status: mapStatusToFrontend(detail.status),
        images: detail.images || detail.imageList || [],
        studentId: detail.studentId || detail.studentNumber,
        repairmanId: detail.staffId || detail.repairmanId,
        category: detail.categoryName || detail.category,
        priority: mapPriorityToFrontend(detail.priority || 'low'),
        createdAt: detail.createdAt || detail.created_at,
        assignedAt: detail.assignedAt || detail.assigned_at,
        completedAt: detail.completedAt || detail.completed_at,
        closedAt: detail.closedAt || detail.closed_at,
        rejectionReason: detail.rejectionReason || detail.rejection_reason,
        repairNotes: detail.repairNotes || detail.repair_notes,
        studentName: detail.studentName || detail.student_name || '',
        repairmanName: detail.repairmanName || detail.staffName || detail.repairman_name || '',
        ratingTime: detail.rating?.ratedAt || detail.ratingTime || null,
        logs: detail.logs || [],
        completionSummary: detail.completionSummary || null,
      });
    } catch (err) {
      console.error('获取工单详情失败:', err);
      message.error('获取工单详情失败');
      setDetailVisible(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleRegenerateCompletionSummary = async () => {
    const id = detailData?.ticketId || detailData?.id;
    if (!id) return;
    setAiSummaryLoading(true);
    try {
      const response = await api.ai.regenerateCompletionSummary(id);
      const summary = response?.data || null;
      setAiSummary(summary);
      setDetailData((prev) => prev ? { ...prev, completionSummary: summary } : prev);
      message.success('完成总结已重新生成');
    } catch (error) {
      console.warn('完成总结重新生成失败:', error);
      message.error(error.message || '完成总结重新生成失败');
    } finally {
      setAiSummaryLoading(false);
    }
  };

  const handleCorrectAiAnalysis = async (values) => {
    const id = detailData?.ticketId || detailData?.id;
    if (!id) return;
    setAiCorrectionLoading(true);
    try {
      const response = await api.admin.correctAiAnalysis(id, values);
      const aiAnalysis = response?.data || response;
      setDetailData((prev) => prev ? { ...prev, aiAnalysis } : prev);
      aiCorrectionForm.setFieldsValue({
        categoryKey: aiAnalysis.finalCategoryKey,
        urgency: aiAnalysis.finalUrgency,
        suggestion: aiAnalysis.finalSuggestion,
        reason: undefined,
      });
      message.success('AI分析修正已保存');
    } catch (error) {
      message.error(error.message || 'AI分析修正保存失败');
    } finally {
      setAiCorrectionLoading(false);
    }
  };
  useEffect(() => {
    if (!targetOrderId) return;
    handleViewDetail({ id: targetOrderId, ticketId: targetOrderId });
    if (onTargetOrderHandled) {
      onTargetOrderHandled();
    }
  }, [targetOrderId]);

  // 处理分配维修人员
  const handleAssignSubmit = async (values) => {
    try {
      const orderId = selectedOrder.ticketId || selectedOrder.id;
      if (!orderId) {
        message.error('工单ID不存在');
        return;
      }
      await repairService.assignRepairman(orderId, values.repairmanId);
      message.success('工单分配成功');
      setAssignModalVisible(false);
      loadRepairOrders(); // 刷新数据
      loadSlaOverview();
      
      // 通知父组件刷新
      if (onRefresh) {
        onRefresh();
      }
    } catch (error) {
      console.error('分配工单失败:', error);
      message.error('分配工单失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理驳回工单
  const handleRejectSubmit = async (values) => {
    try {
      await repairService.rejectRepairOrder(selectedOrder.id, values.reason);
      message.success('工单已驳回');
      setRejectModalVisible(false);
      loadRepairOrders(); // 刷新数据
      loadSlaOverview();
      
      // 通知父组件刷新
      if (onRefresh) {
        onRefresh();
      }
    } catch (error) {
      console.error('驳回工单失败:', error);
      message.error('驳回工单失败');
    }
  };

  // 初始化加载数据
  useEffect(() => {
    loadRepairOrders();
    loadRepairmen();
    loadSlaOverview();
  }, []);

  // 表格列定义
  const columns = [
    {
      title: '工单ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      render: (id, record) => {
        const ticketId = id || record.ticketId || record.id;
        return ticketId ? String(ticketId) : '未知';
      },
    },
    {
      title: '报修标题',
      dataIndex: 'title',
      key: 'title',
      width: 150,
      ellipsis: true,
      render: (title, record) => {
        // 如果title和description相同，说明后端没有正确存储title，使用位置信息生成标题
        const description = record.description || '';
        if (title && title !== description) {
          return title;
        }
        return record.locationText ? `报修-${record.locationText}` : '报修单';
      },
    },
    {
      title: '报修分类',
      dataIndex: 'category',
      key: 'category',
      width: 100,
      render: (category) => {
        try {
          if (!category) return '未知';
          const categoryInfo = repairUtils.getCategoryInfo ? repairUtils.getCategoryInfo(category) : null;
          return categoryInfo ? categoryInfo.label : category;
        } catch (error) {
          console.error('获取分类信息失败:', error);
          return category || '未知';
        }
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
      render: (description) => description || '无描述',
    },
    {
      title: '紧急程度',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (priority) => {
        try {
          if (!repairUtils || typeof repairUtils.getPriorityInfo !== 'function') {
            console.error('repairUtils.getPriorityInfo 不存在');
            // 使用内联的优先级映射
            const priorityMap = {
              low: { label: "低", color: "blue" },
              medium: { label: "中", color: "orange" },
              high: { label: "高", color: "red" },
            };
            const priorityInfo = priorityMap[priority] || { label: priority || '未知', color: 'default' };
            return <Tag color={priorityInfo.color}>{priorityInfo.label}</Tag>;
          }
          const priorityInfo = repairUtils.getPriorityInfo(priority);
          return <Tag color={priorityInfo.color}>{priorityInfo.label}</Tag>;
        } catch (error) {
          console.error('获取优先级信息失败:', error);
          return <Tag color="default">{priority || '未知'}</Tag>;
        }
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status, record) => {
        try {
          // 如果状态是后端格式，先映射
          const frontendStatus = status === 'WAITING_ACCEPT' ? 'pending' : 
                                status === 'IN_PROGRESS' ? 'processing' :
                                status === 'RESOLVED' ? 'completed' :
                                status === 'WAITING_FEEDBACK' ? 'to_be_evaluated' :
                                status === 'FEEDBACKED' || status === 'CLOSED' ? 'closed' :
                                status === 'REJECTED' ? 'rejected' : status;
          const statusInfo = repairUtils.getStatusInfo ? repairUtils.getStatusInfo(frontendStatus) : { label: status || '未知', color: 'default' };
          return (
            <Space direction="vertical" size="small">
              <Tag color={statusInfo.color}>{statusInfo.label}</Tag>
              {!record.repairmanId && <Tag color="default">未分配</Tag>}
              {record.deleted && <Tag color="red">已删除</Tag>}
            </Space>
          );
        } catch (error) {
          console.error('获取状态信息失败:', error);
          return <Tag color="default">{status || '未知'}</Tag>;
        }
      },
    },
    {
      title: 'SLA状态',
      key: 'slaStatus',
      width: 150,
      render: (_, record) => renderSlaStatus(record),
    },
    {
      title: '维修人员',
      dataIndex: 'repairmanId',
      key: 'repairmanId',
      width: 100,
      render: (repairmanId, record) => {
        if (!repairmanId) {
          return '未分配';
        }
        // 从维修人员列表中查找
        const repairman = repairmen.find(r => (r.id || r.userId) === repairmanId);
        return repairman ? (repairman.name || repairman.nickname || '未知') : '未分配';
      },
    },
    {
      title: '提交人学号',
      dataIndex: 'studentID',
      key: 'studentID',
      width: 120,
      render: (studentID) => studentID || '未知',
    },
    {
      title: '提交时间',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 150,
      render: (time, record) => {
        // 尝试多个可能的字段名
        const createdAt = time || record.createdAt || record.created_at || '';
        if (!createdAt) return '未知';
        // 如果是字符串，尝试解析；如果是日期对象，格式化
        if (typeof createdAt === 'string') {
          // 如果是 ISO 8601 格式的字符串，解析并格式化
          if (createdAt.includes('T') || createdAt.includes('-')) {
            try {
              const date = new Date(createdAt);
              if (!isNaN(date.getTime())) {
                return date.toLocaleString('zh-CN', {
                  year: 'numeric',
                  month: '2-digit',
                  day: '2-digit',
                  hour: '2-digit',
                  minute: '2-digit',
                });
              }
            } catch (e) {
              console.error('日期解析失败:', e);
            }
          }
          return createdAt;
        }
        // 如果是日期对象
        try {
          return new Date(createdAt).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          });
        } catch (e) {
          console.error('日期格式化失败:', e);
          return '未知';
        }
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => {
        // 检查状态：pending 或 WAITING_ACCEPT 都可以分配
        const isPending = record.status === 'pending' || record.status === 'WAITING_ACCEPT';
        
        return (
          <Space size="small">
            <Button
              size="small"
              onClick={() => handleViewDetail(record)}
            >
              查看详情
            </Button>
            {isPending && (
              <>
                <Button 
                  type="primary" 
                  size="small"
                  icon={<CheckOutlined />}
                  onClick={() => handleAssignClick(record)}
                >
                  分配
                </Button>
                <Button 
                  danger 
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => handleRejectClick(record)}
                >
                  驳回
                </Button>
              </>
            )}
            {!isPending && (
              <span style={{ color: '#999' }}>无操作</span>
            )}
          </Space>
        );
      },
    },
  ];

  const displayedRepairOrders = getSlaFilteredOrders();

  return (
    <div>
      <h2>工单管理</h2>

      <Card
        title="SLA 超时预警"
        loading={slaLoading}
        style={{ marginBottom: 16 }}
        extra={
          <Button size="small" onClick={loadSlaOverview}>
            刷新预警
          </Button>
        }
      >
        <Row gutter={[12, 12]}>
          <Col xs={24} sm={12} md={6}>
            <Card
              size="small"
              hoverable
              onClick={() => setSlaFilter('all')}
              style={{ borderColor: slaFilter === 'all' ? '#1677ff' : '#e5e7eb' }}
            >
              <div style={{ color: '#64748b' }}>待监控工单</div>
              <div style={{ fontSize: 24, fontWeight: 700 }}>{slaOverview?.activeCount || 0}</div>
              <div style={{ color: '#64748b' }}>当前待受理和处理中</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card
              size="small"
              hoverable
              onClick={() => setSlaFilter('overdue_accept')}
              style={{ borderColor: slaFilter === 'overdue_accept' ? '#dc2626' : '#e5e7eb' }}
            >
              <div style={{ color: '#991b1b' }}>超时未受理</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#dc2626' }}>
                {slaOverview?.overdueAcceptCount || 0}
              </div>
              <div style={{ color: '#64748b' }}>超过响应时限</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card
              size="small"
              hoverable
              onClick={() => setSlaFilter('overdue_completion')}
              style={{ borderColor: slaFilter === 'overdue_completion' ? '#dc2626' : '#e5e7eb' }}
            >
              <div style={{ color: '#991b1b' }}>超时未完成</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#dc2626' }}>
                {slaOverview?.overdueCompletionCount || 0}
              </div>
              <div style={{ color: '#64748b' }}>超过完成时限</div>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card
              size="small"
              hoverable
              onClick={() => setSlaFilter('warning')}
              style={{ borderColor: slaFilter === 'warning' ? '#f59e0b' : '#e5e7eb' }}
            >
              <div style={{ color: '#92400e' }}>即将超时</div>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#f59e0b' }}>
                {slaOverview?.warningCount || 0}
              </div>
              <div style={{ color: '#64748b' }}>进入时限预警区间</div>
            </Card>
          </Col>
        </Row>
        <div style={{ marginTop: 12 }}>
          <Space wrap>
            <span style={{ color: '#64748b' }}>
              超时率：{slaOverview?.overdueRate || 0}%
            </span>
            {slaFilter !== 'all' && (
              <Button size="small" onClick={() => setSlaFilter('all')}>
                返回全部工单
              </Button>
            )}
          </Space>
        </div>
      </Card>
      
      {/* 搜索和筛选区域 */}
      <Card style={{ marginBottom: 16 }}>
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
              <Option value="completed">已完成</Option>
              <Option value="to_be_evaluated">待评价</Option>
              <Option value="closed">已关闭</Option>
              <Option value="rejected">已驳回</Option>
            </Select>
          </Col>
          <Col span={6}>
            <div style={{ marginBottom: 8 }}>分类筛选</div>
            <Select
              style={{ width: '100%' }}
              value={filters.category}
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
          <Col span={12}>
            <div style={{ marginBottom: 8 }}>关键词搜索</div>
            <Search
              placeholder="搜索工单ID、描述、位置、学生ID、维修工ID、分类..."
              allowClear
              value={filters.keyword || ''}
              enterButton={<SearchOutlined />}
              onSearch={handleSearch}
              onChange={(e) => {
                const value = e.target.value || '';
                // 实时更新关键词，但不立即搜索（等待用户按回车或点击搜索按钮）
                setFilters({ ...filters, keyword: value });
                // 如果清空了搜索框，立即搜索
                if (!value) {
                  handleSearch('');
                }
              }}
            />
          </Col>
        </Row>
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={24}>
            <Space>
              <span>显示已删除工单：</span>
              <Switch
                checked={filters.includeDeleted}
                onChange={handleIncludeDeletedChange}
                checkedChildren="是"
                unCheckedChildren="否"
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 工单表格 */}
      <Table
        columns={columns}
        dataSource={displayedRepairOrders}
        rowKey={(record) => record.ticketId || record.id || Math.random()}
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total, range) => 
            `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
        }}
        scroll={{ x: 1200 }}
      />

      {/* 分配维修人员模态框 */}
      <Modal
        title="分配维修人员"
        open={assignModalVisible}
        width={760}
        onCancel={() => {
          setAssignModalVisible(false);
          setRecommendations([]);
        }}
        footer={null}
      >
        <Form
          form={assignForm}
          layout="vertical"
          onFinish={handleAssignSubmit}
        >
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
              <span style={{ fontWeight: 600 }}>智能派单推荐</span>
              <Tag color="blue">智能推荐</Tag>
            </div>

            <Spin spinning={recommendLoading}>
              {recommendations.length > 0 ? (
                <Row gutter={[12, 12]}>
                  {recommendations.slice(0, 3).map((item, index) => (
                    <Col span={24} key={item.staffId}>
                      <div
                        style={{
                          border: '1px solid #e5e7eb',
                          borderRadius: 8,
                          padding: 12,
                          background: index === 0 ? '#f0f7ff' : '#fff',
                        }}
                      >
                        <Row align="middle" gutter={[12, 8]}>
                          <Col flex="auto">
                            <Space wrap>
                              <Tag color={index === 0 ? 'gold' : 'default'}>推荐{index + 1}</Tag>
                              <UserOutlined />
                              <strong>{item.staffName}</strong>
                              <Tag color="green">评分 {item.score}</Tag>
                              <Tag>待办 {item.activeTaskCount} 单</Tag>
                              <Tag>同类 {item.sameCategoryCompletedCount} 单</Tag>
                            </Space>
                              {item.areaMatched && <Tag color="cyan">区域匹配</Tag>}
                              {item.specialtyMatched && <Tag color="purple">专业匹配</Tag>}
                            <div style={{ color: '#64748b', marginTop: 8, lineHeight: 1.6 }}>
                              {item.reason}
                            </div>
                            {(item.responsibleArea || item.specialties) && (
                              <div style={{ color: '#475569', marginTop: 6, fontSize: 12 }}>
                                {item.responsibleArea && <span>{'负责区域：' + item.responsibleArea}</span>}
                                {item.responsibleArea && item.specialties && <span>{' | '}</span>}
                                {item.specialties && <span>{'专业特长：' + item.specialties}</span>}
                              </div>
                            )}
                          </Col>
                          <Col>
                            <Button
                              type={index === 0 ? 'primary' : 'default'}
                              onClick={() => assignForm.setFieldsValue({ repairmanId: item.staffId })}
                            >
                              选择
                            </Button>
                          </Col>
                        </Row>
                      </div>
                    </Col>
                  ))}
                </Row>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无推荐结果，可手动选择维修人员" />
              )}
            </Spin>
          </div>

          <Form.Item
            label="选择维修人员"
            name="repairmanId"
            rules={[{ required: true, message: '请选择维修人员!' }]}
          >
            <Select placeholder="请选择维修人员">
              {repairmen.map(repairman => (
                <Option key={repairman.id || repairman.userId} value={repairman.id || repairman.userId}>
                  <Space>
                    <UserOutlined />
                    {repairman.name || repairman.nickname || '未知'}
                  </Space>
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                确认分配
              </Button>
              <Button onClick={() => setAssignModalVisible(false)}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 驳回工单模态框 */}
      <Modal
        title="驳回工单"
        open={rejectModalVisible}
        onCancel={() => setRejectModalVisible(false)}
        footer={null}
      >
        <Form
          form={rejectForm}
          layout="vertical"
          onFinish={handleRejectSubmit}
        >
          <Form.Item
            label="驳回原因"
            name="reason"
            rules={[{ required: true, message: '请输入驳回原因!' }]}
          >
            <Input.TextArea 
              rows={4} 
              placeholder="请输入驳回此工单的原因..."
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" danger htmlType="submit">
                确认驳回
              </Button>
              <Button onClick={() => setRejectModalVisible(false)}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 工单详情模态框 */}
      <Modal
        title="工单详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={720}
        destroyOnClose
      >
        {detailLoading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
          <div style={{ marginTop: 12 }}>正在加载...</div>
        </div>
      ) : detailData ? (
        <div>
          <Descriptions
            title="基本信息"
            bordered
            size="small"
            column={2}
            style={{ marginBottom: 16 }}
          >
            <Descriptions.Item label="工单ID">{detailData.id}</Descriptions.Item>
            <Descriptions.Item label="报修标题">
              {detailData.title || (detailData.location ? `报修-${detailData.location}` : '报修单')}
            </Descriptions.Item>
            <Descriptions.Item label="报修分类">
              {detailData.categoryName || detailData.category || '未分类'}
            </Descriptions.Item>
            <Descriptions.Item label="紧急程度">
              <Tag color={detailData.priority || 'default'}>
                {repairUtils.getPriorityInfo
                  ? repairUtils.getPriorityInfo(detailData.priority)?.label || detailData.priority || '未知'
                  : detailData.priority || '未知'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="具体位置" span={2}>
              {detailData.location || '未填写'}
            </Descriptions.Item>
            <Descriptions.Item label="问题描述" span={2}>
              {detailData.description || '无'}
            </Descriptions.Item>
            <Descriptions.Item label="当前状态">
              {(() => {
                const statusInfo = repairUtils.getStatusInfo
                  ? repairUtils.getStatusInfo(detailData.status)
                  : { label: detailData.status || '未知', color: 'default' };
                return (
                  <Tag color={statusInfo?.color || 'default'}>
                    {statusInfo?.label || detailData.status}
                  </Tag>
                );
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="提交时间">
              {formatTime(detailData.createdAt || detailData.created_at)}
            </Descriptions.Item>
          </Descriptions>

          <Card
            size="small"
            title="AI分析与人工修正"
            style={{ marginBottom: 16 }}
            extra={detailData.aiAnalysis?.provider ? <Tag color="blue">{detailData.aiAnalysis.provider}</Tag> : null}
          >
            {detailData.aiAnalysis ? (
              <>
                <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
                  <Descriptions.Item label="AI原始分类">{detailData.aiAnalysis.originalCategoryKey || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="最终分类"><Tag color="geekblue">{detailData.aiAnalysis.finalCategoryKey || '暂无'}</Tag></Descriptions.Item>
                  <Descriptions.Item label="AI原始紧急程度">{detailData.aiAnalysis.originalUrgency || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="最终紧急程度">{detailData.aiAnalysis.finalUrgency || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="AI原始建议" span={2}>{detailData.aiAnalysis.originalSuggestion || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="最终采用建议" span={2}>{detailData.aiAnalysis.finalSuggestion || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="最近修正人">{detailData.aiAnalysis.correctedBy || '未修正'}</Descriptions.Item>
                  <Descriptions.Item label="最近修正时间">{detailData.aiAnalysis.correctedAt ? formatTime(detailData.aiAnalysis.correctedAt) : '未修正'}</Descriptions.Item>
                  <Descriptions.Item label="模型 / Provider" span={2}>{[detailData.aiAnalysis.model, detailData.aiAnalysis.provider].filter(Boolean).join(' / ') || '暂无'}</Descriptions.Item>
                  <Descriptions.Item label="原始响应" span={2}>
                    <div style={{ maxHeight: 120, overflow: 'auto', whiteSpace: 'pre-wrap', fontSize: 12 }}>
                      {detailData.aiAnalysis.rawResponse || '暂无'}
                    </div>
                  </Descriptions.Item>
                </Descriptions>

                <Form form={aiCorrectionForm} layout="vertical" onFinish={handleCorrectAiAnalysis}>
                  <Row gutter={12}>
                    <Col xs={24} md={8}><Form.Item label="修正分类" name="categoryKey"><Input /></Form.Item></Col>
                    <Col xs={24} md={8}>
                      <Form.Item label="修正紧急程度" name="urgency">
                        <Select allowClear options={[{ value: '紧急' }, { value: '普通' }, { value: '一般' }]} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item label="修正原因" name="reason" rules={[{ required: true, message: '请填写修正原因' }]}>
                        <Input placeholder="必填" />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Form.Item label="处理建议" name="suggestion"><Input.TextArea rows={3} /></Form.Item>
                  <Button type="primary" htmlType="submit" loading={aiCorrectionLoading}>保存人工修正</Button>
                </Form>

                <Table
                  size="small"
                  style={{ marginTop: 12 }}
                  rowKey="correctionId"
                  pagination={false}
                  scroll={{ x: 900 }}
                  dataSource={detailData.aiAnalysis.corrections || []}
                  columns={[
                    { title: '修正时间', dataIndex: 'correctedAt', render: formatTime },
                    { title: '修正人', dataIndex: 'correctedBy' },
                    { title: '修正前分类', dataIndex: 'previousCategoryKey' },
                    { title: '最终分类', dataIndex: 'newCategoryKey' },
                    { title: '修正前紧急程度', dataIndex: 'previousUrgency' },
                    { title: '最终紧急程度', dataIndex: 'newUrgency' },
                    { title: '修正原因', dataIndex: 'reason' },
                  ]}
                />
              </>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无AI分析结果" />}
          </Card>

          <Card
            size="small"
            title="完成总结"
            loading={aiSummaryLoading}
            style={{ marginBottom: 16 }}
            extra={<Space><Button size="small" loading={aiSummaryLoading} onClick={handleRegenerateCompletionSummary}>重新生成</Button>{aiSummary?.source ? <Tag color="blue">{aiSummary.source}</Tag> : null}</Space>}
          >
            {aiSummary?.summary ? (
              <div style={{ lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{aiSummary.summary}</div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无摘要" />
            )}
          </Card>

          <Descriptions
            title="人员信息"
            bordered
            size="small"
            column={2}
            style={{ marginBottom: 16 }}
          >
            <Descriptions.Item label="报修学生">
              {detailData.studentName || detailData.studentId || '未知'}
            </Descriptions.Item>
            <Descriptions.Item label="维修人员">
              {detailData.repairmanName || detailData.repairmanId || detailData.staffId || '未分配'}
            </Descriptions.Item>
          </Descriptions>

          <Descriptions
            title="进度时间"
            bordered
            size="small"
            column={1}
            style={{ marginBottom: 16 }}
          >
            <Descriptions.Item label="提交时间">
              {formatTime(detailData.createdAt || detailData.created_at)}
            </Descriptions.Item>
            <Descriptions.Item label="分配时间">
              {formatTime(detailData.assignedAt || detailData.assigned_at) || '未分配'}
            </Descriptions.Item>
            <Descriptions.Item label="完成时间">
              {formatTime(detailData.completedAt || detailData.completed_at) || '未完成'}
            </Descriptions.Item>
            {detailData.rejectionReason && (
              <Descriptions.Item label="驳回原因">
                {detailData.rejectionReason}
              </Descriptions.Item>
            )}
            {detailData.repairNotes && (
              <Descriptions.Item label="维修备注">
                {detailData.repairNotes}
              </Descriptions.Item>
            )}
          </Descriptions>

          <RepairTimeline order={detailData} />
          <RepairProcessRecords ticketId={detailData.ticketId || detailData.id} role="ADMIN" />
          <TicketComments ticketId={detailData.ticketId || detailData.id} role="ADMIN" />

          <div style={{ marginBottom: 16 }}>
            <h4 style={{ marginBottom: 12 }}>现场照片</h4>
            {detailData.images && detailData.images.length > 0 ? (
              <Image.PreviewGroup>
                <Space wrap>
                  {detailData.images.map((img, idx) => {
                    let url = typeof img === 'string' ? img : (img.imageUrl || img.url || img.image_url || img);
                    if (url && !url.startsWith('http')) {
                      url = `http://localhost:8070${url.startsWith('/') ? '' : '/'}${url}`;
                    }
                    return (
                      <Image
                        key={idx}
                        width={120}
                        height={90}
                        src={url}
                        style={{ objectFit: 'cover', borderRadius: 6 }}
                        fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBS1pFSFpSg0k3RtVkww1JXSnlRYgqpJQZJFdQqg9LcjQ0PSMZxYaCAhjrH4GBs5F2QW6QAlGx6wMDMwT6ZgUJ5AcFnQcC9nUgUjGwjCx3CgPDBQcB9kShuGkYGyI1yspDg8T8jUFJgYJTA0MDAvYkDEuSykAjrvgODBbsCxKJEuAMYv7EUpxkbQBx8gfB4WbE0C4gdQyGHQf0"
                        onError={(e) => { e.target.style.display = 'none'; }}
                      />
                    );
                  })}
                </Space>
              </Image.PreviewGroup>
            ) : (
              <div style={{ color: '#999', textAlign: 'center', padding: 12 }}>暂无照片</div>
            )}
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: 20 }}>暂无数据</div>
      )}
      </Modal>
    </div>
  );
};

export default RepairOrderList;
