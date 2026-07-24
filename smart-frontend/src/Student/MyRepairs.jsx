import React, { useState, useEffect, useCallback } from "react";
import {
  Table,
  Tag,
  Button,
  Modal,
  Descriptions,
  Image,
  Space,
  Spin,
  Card,
  Select,
  Row,
  Col,
  Statistic,
  Popconfirm,
  message,
  Rate,
  Input,
  Checkbox,
} from "antd";
import {
  EyeOutlined,
  ClockCircleOutlined,
  UserOutlined,
  DeleteOutlined,
  FilterOutlined,
  CheckCircleOutlined,
  StarOutlined,
  SearchOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { repairUtils, repairService } from "../services/repairService";
import RepairTimeline from "../components/RepairTimeline";
import TicketComments from "../components/TicketComments";
import RepairProcessRecords from "../components/RepairProcessRecords";

// 状态映射函数：将后端枚举值转换为前端状态值
const mapStatusToFrontend = (backendStatus) => {
  if (!backendStatus) return 'pending';
  
  const statusMap = {
    'WAITING_ACCEPT': 'pending',
    'IN_PROGRESS': 'processing',
    'RESOLVED': 'awaiting_confirmation',
    'WAITING_FEEDBACK': 'to_be_evaluated',
    'FEEDBACKED': 'feedbacked',
    'CLOSED': 'closed',
    'REJECTED': 'rejected',
  };
  
  // 如果已经是前端格式，直接返回
  if (statusMap[backendStatus]) {
    return statusMap[backendStatus];
  }
  
  // 如果已经是小写格式（前端格式），直接返回
  if (['pending', 'processing', 'awaiting_confirmation', 'completed', 'to_be_evaluated', 'feedbacked', 'closed', 'rejected'].includes(backendStatus.toLowerCase())) {
    return backendStatus.toLowerCase();
  }
  
  return 'pending'; // 默认值
};

const formatDisplayTime = (value) => {
  if (!value) return "-";
  if (typeof value === "string") {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN");
  }
  return new Date(value).toLocaleString("zh-CN");
};

const { Option } = Select;
const { TextArea } = Input;

const MyRepairs = ({ onRefresh, onStatsChange, targetOrderId, onTargetOrderHandled, initialFilters, scope = "progress" }) => {
  const [repairOrders, setRepairOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [filteredOrders, setFilteredOrders] = useState([]);
  const [serverStats, setServerStats] = useState(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState({
    status: "all",
    category: "all",
    priority: "all",
    keyword: "",
  });

  // 新增：评价模态框状态
  const [evaluateModalVisible, setEvaluateModalVisible] = useState(false);
  const [evaluatingOrder, setEvaluatingOrder] = useState(null);
  const [rating, setRating] = useState(0);
  const [speedRating, setSpeedRating] = useState(5);
  const [qualityRating, setQualityRating] = useState(5);
  const [attitudeRating, setAttitudeRating] = useState(5);
  const [resolved, setResolved] = useState(true);
  const [anonymous, setAnonymous] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [evaluating, setEvaluating] = useState(false);

  const isAwaitingStudentConfirmation = (order) => {
    const originalStatus = order?.originalStatus || order?.status;
    return originalStatus === 'RESOLVED' || order?.status === 'awaiting_confirmation';
  };

  const canEvaluateOrder = (order) => {
    const originalStatus = order?.originalStatus || order?.status;
    return !order?.rating && (
      originalStatus === 'WAITING_FEEDBACK'
      || order?.status === 'to_be_evaluated'
    );
  };

  const isStudentFeedbackTodo = (order) => {
    const originalStatus = order?.originalStatus || order?.status;
    return !order?.rating && (
      originalStatus === 'WAITING_FEEDBACK'
      || order?.status === 'to_be_evaluated'
    );
  };

  // 分类选项配置 - 使用 repairService 中的变量名
  const categoryOptions = [
    { value: "waterAndElectricity", label: "水电维修", backendValue: "水电维修" },
    { value: "networkIssues", label: "网络故障", backendValue: "网络故障" },
    { value: "furnitureRepair", label: "家具维修", backendValue: "家具维修" },
    { value: "applianceIssues", label: "电器故障", backendValue: "电器故障" },
    { value: "publicFacilities", label: "公共设施", backendValue: "公共设施" },
  ];

  // 分类值映射：前端值 -> 后端值
  const categoryValueMap = {
    "waterAndElectricity": "水电维修",
    "networkIssues": "网络故障",
    "furnitureRepair": "家具维修",
    "applianceIssues": "电器故障",
    "publicFacilities": "公共设施",
  };

  // 紧急程度值映射：前端值 -> 后端值
  const priorityValueMap = {
    "low": "LOW",
    "medium": "MEDIUM",
    "high": "HIGH",
  };

  // 加载我的报修记录：由后端按当前学生、页面范围和筛选条件分页查询
  const fetchMyRepairs = useCallback(async (options = {}) => {
    const current = options.page || pagination.current || 1;
    const pageSize = options.pageSize || pagination.pageSize || 10;
    setLoading(true);
    try {
      const result = await repairService.getMyRepairOrders({
        scope,
        status: filters.status,
        category: filters.category === "all" ? "all" : (categoryValueMap[filters.category] || filters.category),
        priority: filters.priority,
        keyword: filters.keyword,
        page: current - 1,
        size: pageSize,
      });

      const mappedData = result.data || [];
      setRepairOrders(mappedData);
      setFilteredOrders(mappedData);
      setServerStats(result.stats || null);
      if (result.stats && onStatsChange) {
        onStatsChange(result.stats);
      }
      setPagination({ current, pageSize, total: result.total || 0 });
    } catch (error) {
      console.error('获取报修记录失败:', error);
      message.error(`获取报修记录失败: ${error.message}`);
      setRepairOrders([]);
      setFilteredOrders([]);
      setServerStats(null);
      setPagination((prev) => ({ ...prev, total: 0 }));
    } finally {
      setLoading(false);
    }
  }, [filters, onStatsChange, pagination.current, pagination.pageSize, scope]);

  // 搜索我的报修记录

  // 搜索我的报修记录
  const searchMyRepairs = async (keyword = "") => {
    setLoading(true);
    try {
      const searchParams = { ...filters };
      if (keyword) {
        searchParams.keyword = keyword;
      }
      
      const result = await repairService.searchMyRepairOrders(searchParams);
      setFilteredOrders(result.data || []);
    } catch (error) {
      console.error("搜索报修记录失败:", error);
      message.error("搜索报修记录失败");
    } finally {
      setLoading(false);
    }
  };

  // 应用筛选：触发后端按当前页面允许状态重新查询
  const applyFilters = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchMyRepairs({ page: 1 });
  };

  useEffect(() => {
    console.log('========================================');
    console.log('组件初始化，开始加载报修数据...');
    console.log('========================================');
    fetchMyRepairs();
  }, [fetchMyRepairs]); // 依赖 fetchMyRepairs

  // 添加轮询刷新，每10秒刷新一次数据，确保分配和完成情况同步
  useEffect(() => {
    const interval = setInterval(() => {
      console.log('========================================');
      console.log('轮询刷新订单状态...');
      console.log('当前时间:', new Date().toLocaleString());
      console.log('========================================');
      fetchMyRepairs();
    }, 10000); // 10秒刷新一次，更快地同步状态

    return () => {
      console.log('清除轮询定时器');
      clearInterval(interval);
    };
  }, [fetchMyRepairs]); // 依赖 fetchMyRepairs


  useEffect(() => {
    if (!initialFilters) return;
    setFilters((prev) => ({
      ...prev,
      status: initialFilters.status || "all",
      category: initialFilters.category || "all",
      priority: initialFilters.priority || "all",
      keyword: initialFilters.keyword || "",
    }));
  }, [
    initialFilters?.status,
    initialFilters?.category,
    initialFilters?.priority,
    initialFilters?.keyword,
  ]);

  // 处理筛选条件变化
  const handleFilterChange = (key, value) => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    setFilters((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  // 重置筛选
  const handleResetFilters = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    setFilters({
      status: "all",
      category: "all",
      priority: "all",
      keyword: "",
    });
  };

  // 打开详情模态框
  const handleViewDetail = async (record) => {
    setDetailModalVisible(true);
    setDetailLoading(true);

    try {
      // 使用 ticketId 或 id 作为工单ID
      const orderId = record.ticketId || record.id;
      if (!orderId) {
        message.error("工单ID不存在");
        setDetailLoading(false);
        return;
      }

      console.log('查看工单详情，工单ID:', orderId);

      // repairService 已经完成了字段映射，直接使用返回的数据
      const orderDetail = await repairService.getRepairOrderById(orderId);
      console.log('获取到的订单详情（已映射）:', orderDetail);

      setSelectedOrder(orderDetail);
    } catch (error) {
      console.error("获取工单详情失败:", error);
      message.error("获取详情失败: " + error.message);
    } finally {
      setDetailLoading(false);
    }
  };

  // 关闭详情模态框
  const handleCloseDetail = () => {
    setDetailModalVisible(false);
    setSelectedOrder(null);
  };

  useEffect(() => {
    if (!targetOrderId) return;
    handleViewDetail({ id: targetOrderId, ticketId: targetOrderId });
    if (onTargetOrderHandled) {
      onTargetOrderHandled();
    }
  }, [targetOrderId]);

  // 删除报修记录
  const handleDeleteRepair = async (record) => {
    try {
      const orderId = record.ticketId || record.id;
      if (!orderId) {
        message.error("工单ID不存在");
        return;
      }
      await repairService.deleteRepairOrder(orderId);
      message.success("报修记录删除成功");

      // 刷新数据
      fetchMyRepairs();
      
      // 如果有传入刷新函数，则调用父组件的刷新
      if (onRefresh) {
        onRefresh();
      }
    } catch (error) {
      console.error("删除报修记录失败:", error);
      message.error("删除失败");
    }
  };

  const refreshAfterCompletionAction = async (orderId) => {
    await fetchMyRepairs();
    if (detailModalVisible && selectedOrder) {
      const detail = await repairService.getRepairOrderById(orderId);
      setSelectedOrder(detail);
    }
    if (onRefresh) {
      onRefresh();
    }
  };

  const handleConfirmCompletion = async (record) => {
    const orderId = record?.ticketId || record?.id;
    if (!orderId) {
      message.error("工单ID不存在");
      return;
    }
    await repairService.confirmCompletion(orderId);
    await refreshAfterCompletionAction(orderId);
    let evaluationOrder = {
      ...record,
      status: 'to_be_evaluated',
      originalStatus: 'WAITING_FEEDBACK',
    };
    try {
      evaluationOrder = await repairService.getRepairOrderById(orderId);
    } catch (error) {
      console.warn('Failed to refresh order before evaluation:', error);
    }
    if (detailModalVisible) {
      handleCloseDetail();
    }
    handleEvaluate(evaluationOrder);
  };

  const handleRejectCompletion = (record) => {
    const orderId = record?.ticketId || record?.id;
    if (!orderId) {
      message.error("工单ID不存在");
      return;
    }
    let reason = '';
    Modal.confirm({
      title: '问题未解决',
      content: (
        <TextArea
          rows={4}
          maxLength={500}
          showCount
          placeholder="请填写未解决原因"
          onChange={(event) => { reason = event.target.value; }}
        />
      ),
      okText: '提交',
      cancelText: '取消',
      async onOk() {
        if (!reason.trim()) {
          message.error('请填写未解决原因');
          return Promise.reject(new Error('reason required'));
        }
        await repairService.rejectCompletion(orderId, reason.trim());
        await refreshAfterCompletionAction(orderId);
      },
    });
  };

  // 新增：打开评价模态框
  const handleEvaluate = (record) => {
    setEvaluatingOrder(record);
    setRating(0);
    setFeedback("");
    setEvaluateModalVisible(true);
  };

  // 新增：关闭评价模态框
  const handleCloseEvaluate = () => {
    setEvaluateModalVisible(false);
    setEvaluatingOrder(null);
    setRating(0);
    setSpeedRating(5);
    setQualityRating(5);
    setAttitudeRating(5);
    setResolved(true);
    setAnonymous(false);
    setFeedback("");
  };

  // 新增：提交评价
  const handleSubmitEvaluation = async () => {
    if (rating === 0) {
      message.error("请至少给一星评价");
      return;
    }

    setEvaluating(true);
    try {
      const orderId = evaluatingOrder.ticketId || evaluatingOrder.id;
      if (!orderId) {
        message.error("工单ID不存在");
        return;
      }
      
      // 获取当前学生ID
      const userStr = localStorage.getItem('user');
      let studentId = null;
      if (userStr) {
        try {
          const user = JSON.parse(userStr);
          console.log('解析的用户信息:', user);
          // 尝试多种可能的字段名
          studentId = user.userId || user.id || user.user_id || user.studentId || null;
          console.log('提取的学生ID:', studentId);
        } catch (e) {
          console.error('解析用户信息失败:', e);
        }
      } else {
        console.warn('localStorage中没有user信息');
      }
      
      if (!studentId) {
        console.error('无法获取学生ID，localStorage中的user:', userStr);
        message.error("无法获取学生ID，请重新登录");
        setEvaluating(false);
        return;
      }
      
      await repairService.evaluateRepairOrder(orderId, studentId, {
        score: rating,
        comment: feedback,
        speedRating,
        qualityRating,
        attitudeRating,
        resolved,
        anonymous,
      });
      
      message.success("评价提交成功！");
      handleCloseEvaluate();

      // 刷新数据
      fetchMyRepairs();
      
      // 如果有传入刷新函数，则调用父组件的刷新
      if (onRefresh) {
        onRefresh();
      }
    } catch (error) {
      console.error("提交评价失败:", error);
      message.error("提交评价失败，请重试！");
    } finally {
      setEvaluating(false);
    }
  };

  // 统计信息 - 基于实际的repairOrders数据动态计算，添加调试日志
  const getStats = () => {
    console.log('========================================');
    console.log('学生端统计信息计算开始...');
    console.log('当前报修订单数组:', repairOrders);
    console.log('报修订单数量:', repairOrders.length);

    if (!repairOrders || repairOrders.length === 0) {
      console.warn('警告：报修订单数组为空，返回默认统计数据');
      console.log('========================================');
      return {
        total: 0,
        pending: 0,
        processing: 0,
        awaitingConfirmation: 0,
        completed: 0,
        toBeEvaluated: 0,
        rejected: 0
      };
    }

    const total = repairOrders.length;

    console.log('========================================');
    console.log('开始统计各状态订单数量...');
    console.log('订单状态详情:');
    repairOrders.forEach((order, index) => {
      console.log(`订单 ${index + 1}: ID=${order.id}, 状态="${order.status}", 原始状态="${order.originalStatus}", 标题="${order.title}"`);
    });
    console.log('========================================');

    // 统计各状态数量 - 从学生视角正确统计
    // 学生端视角：
    // - pending（待受理）= WAITING_ACCEPT（刚提交，等待维修工接单）
    // - processing（处理中）= IN_PROGRESS（维修工正在处理）
    // - awaitingConfirmation（待确认）= RESOLVED（维修工已完成，等待学生确认）
    // - toBeEvaluated（待评价）= WAITING_FEEDBACK（学生已确认，等待评价）
    // - completed（已完成）= FEEDBACKED（已评价）或 CLOSED（已关闭）
    // - rejected（已驳回）= REJECTED

    console.log('========================================');
    console.log('开始分类统计（学生视角）...');
    console.log('========================================');

    // 待受理：WAITING_ACCEPT 状态
    const pendingOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      // 检查原始后端状态是否为 WAITING_ACCEPT
      const isPending = originalStatus === 'WAITING_ACCEPT' || order.status === 'pending';
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否待受理=${isPending}`);
      return isPending;
    });
    const pending = pendingOrders.length;
    console.log('待受理订单 (WAITING_ACCEPT):', pending, '条');
    if (pendingOrders.length > 0) {
      console.log('待受理订单详情:', pendingOrders.map(o => ({ id: o.id, title: o.title, originalStatus: o.originalStatus })));
    }

    // 处理中：IN_PROGRESS 状态
    const processingOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      const isProcessing = originalStatus === 'IN_PROGRESS' || order.status === 'processing';
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否处理中=${isProcessing}`);
      return isProcessing;
    });
    const processing = processingOrders.length;
    console.log('处理中订单 (IN_PROGRESS):', processing, '条');
    if (processingOrders.length > 0) {
      console.log('处理中订单详情:', processingOrders.map(o => ({ id: o.id, title: o.title, originalStatus: o.originalStatus })));
    }

    // 学生端"待确认"：维修工已完成，等待学生确认是否解决的 RESOLVED 状态
    const awaitingConfirmationOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      const isAwaitingConfirmation = isAwaitingStudentConfirmation(order);
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否待确认=${isAwaitingConfirmation}`);
      return isAwaitingConfirmation;
    });
    const awaitingConfirmation = awaitingConfirmationOrders.length;
    console.log('待确认订单 (RESOLVED):', awaitingConfirmation, '条');
    if (awaitingConfirmationOrders.length > 0) {
      console.log('待确认订单详情:', awaitingConfirmationOrders.map(o => ({ id: o.id, title: o.title, originalStatus: o.originalStatus })));
    }

    // 学生端"待评价"：学生确认完成后的 WAITING_FEEDBACK 状态
    const toBeEvaluatedOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      const isToBeEvaluated = isStudentFeedbackTodo(order);
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否待评价=${isToBeEvaluated}`);
      return isToBeEvaluated;
    });
    const toBeEvaluated = toBeEvaluatedOrders.length;
    console.log('待评价订单 (WAITING_FEEDBACK):', toBeEvaluated, '条');
    if (toBeEvaluatedOrders.length > 0) {
      console.log('待评价订单详情:', toBeEvaluatedOrders.map(o => ({
        id: o.id,
        title: o.title,
        originalStatus: o.originalStatus,
        status: o.status
      })));
    }

    // 学生端"已完成"：FEEDBACKED（已评价）或 CLOSED（已关闭）状态
    const completedOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      // 学生端"已完成"仅统计已评价或已关闭的工单
      const isCompleted = originalStatus === 'FEEDBACKED' || originalStatus === 'CLOSED' || order.status === 'closed' || (order.status === 'completed' && order.rating);
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否已完成=${isCompleted}`);
      return isCompleted;
    });
    const completed = completedOrders.length;
    console.log('已完成订单 (FEEDBACKED 或 CLOSED):', completed, '条');
    if (completedOrders.length > 0) {
      console.log('已完成订单详情:', completedOrders.map(o => ({
        id: o.id,
        title: o.title,
        originalStatus: o.originalStatus,
        rating: o.rating
      })));
    }

    // 已驳回：REJECTED 状态
    const rejectedOrders = repairOrders.filter(order => {
      const originalStatus = order.originalStatus || order.status;
      const isRejected = originalStatus === 'REJECTED' || order.status === 'rejected';
      console.log(`订单 ${order.id}: 原始状态="${originalStatus}", 是否已驳回=${isRejected}`);
      return isRejected;
    });
    const rejected = rejectedOrders.length;
    console.log('已驳回订单 (REJECTED):', rejected, '条');
    if (rejectedOrders.length > 0) {
      console.log('已驳回订单详情:', rejectedOrders.map(o => ({ id: o.id, title: o.title, originalStatus: o.originalStatus })));
    }

    const stats = { total, pending, processing, awaitingConfirmation, completed, toBeEvaluated, rejected };

    console.log('========================================');
    console.log('学生端统计结果汇总:', stats);
    console.log(`验证: total(${total}) = pending(${pending}) + processing(${processing}) + awaitingConfirmation(${awaitingConfirmation}) + toBeEvaluated(${toBeEvaluated}) + completed(${completed}) + rejected(${rejected})`);
    const verificationSum = pending + processing + awaitingConfirmation + toBeEvaluated + completed + rejected;
    console.log(`验证计算: ${total} = ${verificationSum}`);
    console.log(`验证结果: ${total === verificationSum ? '✓ 正确' : '✗ 错误（差额=' + (total - verificationSum) + '）'}`);
    console.log('========================================');

    return stats;
  };

  const stats = serverStats || getStats();

  const statusOptionsByScope = {
    progress: [
      { value: "pending", label: "待受理" },
      { value: "processing", label: "处理中" },
      { value: "awaiting_confirmation", label: "待确认" },
    ],
    to_evaluate: [
      { value: "to_be_evaluated", label: "待评价" },
    ],
    history: [
      { value: "feedbacked", label: "已评价" },
      { value: "closed", label: "已关闭" },
      { value: "rejected", label: "已驳回" },
    ],
  };
  const statusOptions = statusOptionsByScope[scope] || statusOptionsByScope.progress;
  // 表格列定义
  const columns = [
    {
      title: "报修单号",
      dataIndex: "id",
      key: "id",
      width: 100,
    },
    {
      title: "报修标题",
      dataIndex: "title",
      key: "title",
      width: 150,
    },
    {
      title: "报修分类",
      dataIndex: "category",
      key: "category",
      width: 100,
      render: (category) => {
        const categoryInfo = repairUtils.getCategoryInfo(category);
        return categoryInfo ? categoryInfo.label : category;
      },
    },
    {
      title: "具体位置",
      dataIndex: "location",
      key: "location",
      width: 120,
    },
    {
      title: "问题描述",
      dataIndex: "description",
      key: "description",
      ellipsis: true,
      width: 200,
    },
    {
      title: "紧急程度",
      dataIndex: "priority",
      key: "priority",
      width: 100,
      render: (priority) => {
        const priorityInfo = (repairUtils.getPriorityInfo || repairUtils.getpriorityInfo)?.(priority) || { label: priority || '未知', color: 'default' };
        return <Tag color={priorityInfo.color}>{priorityInfo.label}</Tag>;
      },
    },
    {
      title: "提交时间",
      dataIndex: "created_at",
      key: "created_at",
      width: 150,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (status) => {
        const statusInfo = repairUtils.getStatusInfo(status);
        return <Tag color={statusInfo.color}>{statusInfo.label}</Tag>;
      },
    },
    {
      title: "操作",
      key: "action",
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
            size="small"
          >
            详情
          </Button>

          {/* 只有待受理状态可以删除 */}
          {record.status === "pending" && (
            <Popconfirm
              title="确定要删除这条报修记录吗？"
              description="删除后无法恢复，请谨慎操作！"
              onConfirm={() => handleDeleteRepair(record)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" danger icon={<DeleteOutlined />} size="small">
                删除
              </Button>
            </Popconfirm>
          )}

          {isAwaitingStudentConfirmation(record) && (
            <>
              <Popconfirm
                title="确认维修已完成？"
                onConfirm={() => handleConfirmCompletion(record)}
                okText="确认"
                cancelText="取消"
              >
                <Button type="link" icon={<CheckCircleOutlined />} size="small">
                  确认完成
                </Button>
              </Popconfirm>
              <Button type="link" danger size="small" onClick={() => handleRejectCompletion(record)}>
                问题未解决
              </Button>
            </>
          )}

          {canEvaluateOrder(record) && (
            <Button
              type="link"
              icon={<StarOutlined />}
              onClick={() => handleEvaluate(record)}
              size="small"
              style={{ color: "#faad14" }}
            >
              评价
            </Button>
          )}

          {/* 已评价显示已评价标签：有评价分数或状态为FEEDBACKED */}
          {(record.rating || record.originalStatus === 'FEEDBACKED') && (
            <Tag color="success" style={{ marginLeft: 8 }}>
              已评价
            </Tag>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "16px",
        paddingBottom: "12px",
        borderBottom: "1px solid #e8e8e8",
      }}>
        <h2 style={{
          fontSize: "16px",
          fontWeight: "600",
          color: "#1f1f1f",
          margin: 0,
        }}>我的报修</h2>

        <Button
          type="primary"
          icon={<ReloadOutlined />}
          onClick={() => {
            console.log('手动刷新报修数据...');
            fetchMyRepairs();
          }}
          loading={loading}
          style={{
            backgroundColor: "#0F52BA",
            borderColor: "#0F52BA",
          }}
        >
          刷新数据
        </Button>
      </div>

      {/* 统计信息 */}
      <Row
        gutter={16}
        style={{
          marginBottom: "16px",
          display: "flex",
          justifyContent: "space-between",
        }}
      >
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>总报修数</span>}
              value={stats.total}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>待受理</span>}
              value={stats.pending}
              prefix={<ClockCircleOutlined style={{ color: "#f5c26b" }} />}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>处理中</span>}
              value={stats.processing}
              prefix={<ClockCircleOutlined style={{ color: "#7eb8da" }} />}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>待确认</span>}
              value={stats.awaitingConfirmation}
              prefix={<CheckCircleOutlined style={{ color: "#13c2c2" }} />}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>待评价</span>}
              value={stats.toBeEvaluated}
              prefix={<StarOutlined style={{ color: "#7eb8da" }} />}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card style={{ borderRadius: "8px", border: "none", boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)", background: "#FFFFFF" }}>
            <Statistic
              title={<span style={{ fontSize: "13px", color: "#8c8c8c", fontWeight: "400" }}>已评价</span>}
              value={stats.completed}
              prefix={<CheckCircleOutlined style={{ color: "#86c8bc" }} />}
              valueStyle={{ color: "#1f1f1f", fontSize: "28px", fontWeight: "600" }}
            />
          </Card>
        </Col>
      </Row>

      {/* 筛选器 */}
      <Card
        title={<span style={{ fontSize: "15px", fontWeight: "600", color: "#1f1f1f" }}>筛选条件</span>}
        style={{ marginBottom: "16px", borderRadius: "8px", border: "none", background: "#FFFFFF" }}
      >
        <Space size="middle" wrap>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ fontSize: "15px", fontWeight: "500", color: "#5c5c5c", minWidth: "50px" }}>状态：</span>
            <Select
              value={filters.status}
              style={{ width: 140 }}
              onChange={(value) => handleFilterChange("status", value)}
            >
              <Option value="all">全部状态</Option>
              {statusOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ fontSize: "15px", fontWeight: "500", color: "#5c5c5c", minWidth: "50px" }}>分类：</span>
            <Select
              value={filters.category}
              style={{ width: 140 }}
              onChange={(value) => handleFilterChange("category", value)}
            >
              <Option value="all">全部分类</Option>
              {categoryOptions.map((option) => (
                <Option key={option.value} value={option.value}>
                  {option.label}
                </Option>
              ))}
            </Select>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span style={{ fontSize: "15px", fontWeight: "500", color: "#5c5c5c", minWidth: "70px" }}>紧急程度：</span>
            <Select
              value={filters.priority}
              style={{ width: 140 }}
              onChange={(value) => handleFilterChange("priority", value)}
            >
              <Option value="all">全部</Option>
              <Option value="low">低</Option>
              <Option value="medium">中</Option>
              <Option value="high">高</Option>
            </Select>
          </div>

          <div style={{ display: "flex", alignItems: "center" }}>
            <Input
              placeholder="按标题/描述/位置搜索"
              allowClear
              style={{ width: 200, height: 36 }}
              onChange={(e) => {
                setFilters(prev => ({ ...prev, keyword: e.target.value }));
              }}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              style={{ height: 36, marginLeft: "-1px" }}
              onClick={() => {
                applyFilters();
              }}
            >
              搜索
            </Button>
          </div>

          <Button icon={<FilterOutlined />} onClick={handleResetFilters}>
            重置筛选
          </Button>
        </Space>
      </Card>

      {/* 报修记录表格 */}
      <Card style={{ borderRadius: "8px", border: "none", background: "#FFFFFF" }}>
        <Table
          style={{
            height: "80vh",
          }}
          columns={columns}
          dataSource={filteredOrders}
          rowKey={(record) => record.ticketId || record.id || record.key || Math.random()}
          loading={loading}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) =>
              total === 0 ? "共 0 条" : `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
          }}
          onChange={(nextPagination) => {
            fetchMyRepairs({
              page: nextPagination.current,
              pageSize: nextPagination.pageSize,
            });
          }}
          scroll={{ x: 1000 }}
          locale={{ emptyText: "暂无报修记录" }}
        />
      </Card>

      {/* 报修单详情模态框 */}
      <Modal
        title={
          <Space>
            <EyeOutlined />
            报修单详情
          </Space>
        }
        open={detailModalVisible}
        onCancel={handleCloseDetail}
        footer={[
          <Button key="close" onClick={handleCloseDetail}>
            关闭
          </Button>,
        ]}
        width={800}
        styles={{ body: { padding: "20px" } }}
      >
        {detailLoading ? (
          <div style={{ textAlign: "center", padding: "50px" }}>
            <Spin size="large" />
            <div style={{ marginTop: 16 }}>加载详情中...</div>
          </div>
        ) : selectedOrder ? (
          <div>
            {/* 基本信息 */}
            <Descriptions
              title="基本信息"
              bordered
              column={2}
              size="small"
              style={{ marginBottom: 24 }}
            >
              <Descriptions.Item label="报修单号" span={1}>
                {selectedOrder.id}
              </Descriptions.Item>
              <Descriptions.Item label="报修标题" span={1}>
                {selectedOrder.title || (selectedOrder.locationText ? `报修-${selectedOrder.locationText}` : '报修单')}
              </Descriptions.Item>
              <Descriptions.Item label="报修分类" span={1}>
                {repairUtils.getCategoryInfo(selectedOrder.category)?.label ||
                  selectedOrder.category}
              </Descriptions.Item>
              <Descriptions.Item label="紧急程度" span={1}>
                <Tag
                  color={
                    (repairUtils.getPriorityInfo || repairUtils.getpriorityInfo)?.(selectedOrder.priority)?.color || 'default'
                  }
                >
                  {(repairUtils.getPriorityInfo || repairUtils.getpriorityInfo)?.(selectedOrder.priority)?.label || selectedOrder.priority || '未知'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="具体位置" span={2}>
                {selectedOrder.location}
              </Descriptions.Item>
              <Descriptions.Item label="问题描述" span={2}>
                {selectedOrder.description}
              </Descriptions.Item>
              <Descriptions.Item label="当前状态" span={1}>
                <Tag
                  color={repairUtils.getStatusInfo(selectedOrder.status).color}
                >
                  {repairUtils.getStatusInfo(selectedOrder.status).label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提交时间" span={1}>
                {selectedOrder.created_at}
              </Descriptions.Item>
            </Descriptions>

            {/* 人员信息 */}
            <Descriptions
              title="人员信息"
              bordered
              column={2}
              size="small"
              style={{ marginBottom: 24 }}
            >
              <Descriptions.Item label="报修学生" span={1}>
                <Space>
                  <UserOutlined />
                  {selectedOrder.studentName || "未知"}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="联系电话" span={1}>
                {selectedOrder.contactPhone || "未提供"}
              </Descriptions.Item>
              <Descriptions.Item label="维修人员" span={1}>
                {selectedOrder.repairmanName || selectedOrder.staffName || (selectedOrder.repairmanId || selectedOrder.staffId)
                  ? (selectedOrder.repairmanName || selectedOrder.staffName || repairUtils.getRepairmanInfo(selectedOrder.repairmanId || selectedOrder.staffId)?.name || selectedOrder.repairmanId || selectedOrder.staffId || '未知')
                  : "未分配"}
              </Descriptions.Item>
              <Descriptions.Item label="学号" span={1}>
                {selectedOrder.studentID}
              </Descriptions.Item>
            </Descriptions>

            {/* 时间线信息 */}
            <Descriptions
              title="处理进度"
              bordered
              column={1}
              size="small"
              style={{ marginBottom: 24 }}
            >
              <Descriptions.Item label="提交时间">
                <Space>
                  <ClockCircleOutlined />
                  {selectedOrder.created_at ? (typeof selectedOrder.created_at === 'string' ? selectedOrder.created_at : new Date(selectedOrder.created_at).toLocaleString('zh-CN')) : '未知'}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="分配时间">
                <Space>
                  <ClockCircleOutlined />
                  {selectedOrder.assigned_at ? (typeof selectedOrder.assigned_at === 'string' ? selectedOrder.assigned_at : new Date(selectedOrder.assigned_at).toLocaleString('zh-CN')) : "未分配"}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="完成时间">
                <Space>
                  <ClockCircleOutlined />
                  {selectedOrder.completed_at ? (typeof selectedOrder.completed_at === 'string' ? selectedOrder.completed_at : new Date(selectedOrder.completed_at).toLocaleString('zh-CN')) : "未完成"}
                </Space>
              </Descriptions.Item>
              {selectedOrder.studentConfirmedAt && (
                <Descriptions.Item label="学生确认时间">
                  {typeof selectedOrder.studentConfirmedAt === 'string' ? selectedOrder.studentConfirmedAt : new Date(selectedOrder.studentConfirmedAt).toLocaleString('zh-CN')}
                </Descriptions.Item>
              )}
              {selectedOrder.studentRejectionReason && (
                <Descriptions.Item label="未解决原因">
                  {selectedOrder.studentRejectionReason}
                </Descriptions.Item>
              )}
              {selectedOrder.rejection_reason && (
                <Descriptions.Item label="驳回原因">
                  {selectedOrder.rejection_reason}
                </Descriptions.Item>
              )}
              {selectedOrder.repairNotes && (
                <Descriptions.Item label="维修备注">
                  {selectedOrder.repairNotes}
                </Descriptions.Item>
              )}
            </Descriptions>

            {/* 新增：现场照片展示 */}
            <Card
              size="small"
              title="完成总结"
              style={{ marginBottom: 24 }}
            >
              {selectedOrder.completionSummary?.summary ? (
                <div style={{ lineHeight: 1.7, whiteSpace: 'pre-wrap' }}>{selectedOrder.completionSummary.summary}</div>
              ) : (
                <div style={{ color: '#8c8c8c' }}>暂无完成总结</div>
              )}
            </Card>
            <RepairTimeline order={selectedOrder} />
            <RepairProcessRecords ticketId={selectedOrder.ticketId || selectedOrder.id} role="STUDENT" />
            <TicketComments ticketId={selectedOrder.ticketId || selectedOrder.id} role="STUDENT" />

            {selectedOrder.images && selectedOrder.images.length > 0 ? (
              <div style={{ marginBottom: 24 }}>
                <h4 style={{ marginBottom: 16, fontSize: "15px", fontWeight: "600", color: "#1f1f1f" }}>现场照片</h4>
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                  {selectedOrder.images.map((image, index) => {
                    let imageUrl = typeof image === 'string' ? image : (image.imageUrl || image.url || image);
                    // 确保URL完整
                    if (imageUrl && !imageUrl.startsWith('http')) {
                      imageUrl = `http://localhost:8070${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
                    }
                    console.log('图片URL:', imageUrl, '原始图片:', image);
                    return (
                      <Image
                        key={index}
                        width={120}
                        height={90}
                        src={imageUrl}
                        style={{
                          borderRadius: 6,
                          objectFit: "cover",
                          border: "1px solid #d9d9d9",
                          cursor: "pointer",
                        }}
                        placeholder={
                          <div
                            style={{
                              width: 120,
                              height: 90,
                              background: "#f5f5f5",
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "center",
                              borderRadius: 6,
                            }}
                          >
                            加载中...
                          </div>
                        }
                      />
                    );
                  })}
                </div>
              </div>
            ) : (
              <div style={{ marginBottom: 24 }}>
                <h4 style={{ marginBottom: 16, fontSize: "15px", fontWeight: "600", color: "#1f1f1f" }}>现场照片</h4>
                <div style={{ color: '#8c8c8c', textAlign: 'center', padding: '20px', background: '#f8fafc', borderRadius: '6px' }}>暂无照片</div>
              </div>
            )}

            {/* 评价信息（如果已完成且有评价） */}
            {selectedOrder.rating ? (
              <Descriptions title="评价信息" bordered column={1} size="small" style={{ marginBottom: 24 }}>
                <Descriptions.Item label="评分">
                  {"★".repeat(selectedOrder.rating)}
                  {"☆".repeat(5 - selectedOrder.rating)}
                  <span style={{ marginLeft: 8, color: "#faad14" }}>
                    ({selectedOrder.rating}分)
                  </span>
                </Descriptions.Item>
                {selectedOrder.feedback && (
                  <Descriptions.Item label="评价内容">
                    {selectedOrder.feedback}
                  </Descriptions.Item>
                )}
                {selectedOrder.followUpStatus === "HANDLED" && (
                  <>
                    <Descriptions.Item label="管理员回访说明">
                      {selectedOrder.followUpNote || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="回访处理时间">
                      {formatDisplayTime(selectedOrder.followUpUpdatedAt)}
                    </Descriptions.Item>
                  </>
                )}
              </Descriptions>
            ) : (
              <div style={{ marginBottom: 24, textAlign: 'center' }}>
                {isAwaitingStudentConfirmation(selectedOrder) && (
                  <Space>
                    <Popconfirm
                      title="确认维修已完成？"
                      onConfirm={() => handleConfirmCompletion(selectedOrder)}
                      okText="确认"
                      cancelText="取消"
                    >
                      <Button type="primary" icon={<CheckCircleOutlined />}>
                        确认完成
                      </Button>
                    </Popconfirm>
                    <Button danger onClick={() => handleRejectCompletion(selectedOrder)}>
                      问题未解决
                    </Button>
                  </Space>
                )}
                {canEvaluateOrder(selectedOrder) && (
                  <Button
                    type="primary"
                    icon={<StarOutlined />}
                    onClick={() => {
                      handleCloseDetail();
                      handleEvaluate(selectedOrder);
                    }}
                    style={{ backgroundColor: "#faad14", borderColor: "#faad14" }}
                  >
                    立即评价
                  </Button>
                )}
              </div>
            )}
          </div>
        ) : (
          <div style={{ textAlign: "center", padding: "50px", color: "#999" }}>
            未找到报修单详情
          </div>
        )}
      </Modal>

      {/* 新增：评价模态框 */}
      <Modal
        title={
          <Space>
            <StarOutlined />
            服务评价
          </Space>
        }
        open={evaluateModalVisible}
        onCancel={handleCloseEvaluate}
        onOk={handleSubmitEvaluation}
        confirmLoading={evaluating}
        okText="提交评价"
        cancelText="取消"
        width={560}
      >
        {evaluatingOrder && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <strong>报修单：</strong>
              {evaluatingOrder.title}
            </div>

            <div style={{ marginBottom: 24 }}>
              <div style={{ marginBottom: 8 }}>
                <strong>
                  评分 <span style={{ color: "#ff4d4f" }}>*</span>
                </strong>
              </div>
              <Rate
                value={rating}
                onChange={setRating}
                style={{ fontSize: 24 }}
              />
              <div style={{ marginTop: 8, color: "#999" }}>
                {rating > 0 ? `您选择了 ${rating} 星` : "请选择评分"}
              </div>
            </div>

            <div style={{ marginBottom: 20 }}>
              <Row gutter={[16, 12]}>
                <Col span={8}>
                  <div style={{ marginBottom: 6 }}>维修速度</div>
                  <Rate value={speedRating} onChange={setSpeedRating} />
                </Col>
                <Col span={8}>
                  <div style={{ marginBottom: 6 }}>维修质量</div>
                  <Rate value={qualityRating} onChange={setQualityRating} />
                </Col>
                <Col span={8}>
                  <div style={{ marginBottom: 6 }}>服务态度</div>
                  <Rate value={attitudeRating} onChange={setAttitudeRating} />
                </Col>
              </Row>
            </div>

            <Space style={{ marginBottom: 16 }} wrap>
              <Checkbox checked={resolved} onChange={(event) => setResolved(event.target.checked)}>
                问题已彻底解决
              </Checkbox>
              <Checkbox checked={anonymous} onChange={(event) => setAnonymous(event.target.checked)}>
                匿名展示评价
              </Checkbox>
            </Space>

            <div style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 8 }}>
                <strong>评价内容</strong>（选填）
              </div>
              <TextArea
                rows={4}
                placeholder="请写下您对本次服务的评价..."
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                maxLength={500}
                showCount
              />
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default MyRepairs;
