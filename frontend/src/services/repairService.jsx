// src/services/repairService.js
import api from './api';
import { message } from 'antd';

// 工单状态枚举
export const REPAIR_STATUS = {
  PENDING: { value: "pending", label: "待受理", color: "orange" },
  PROCESSING: { value: "processing", label: "处理中", color: "blue" },
  COMPLETED: { value: "completed", label: "已完成", color: "green" },
  TO_BE_EVALUATED: {value: "to_be_evaluated",label: "待评价", color: "purple"},
  CLOSED: { value: "closed", label: "已关闭", color: "default" },
  REJECTED: { value: "rejected", label: "已驳回", color: "red" },
};

// 报修分类枚举
export const REPAIR_CATEGORIES = {
  waterAndElectricity: { value: "waterAndElectricity", label: "水电维修" },
  networkIssues: { value: "networkIssues", label: "网络故障" },
  furnitureRepair: { value: "furnitureRepair", label: "家具维修" },
  applianceIssues: { value: "applianceIssues", label: "电器故障" },
  publicFacilities: { value: "publicFacilities", label: "公共设施" },
};

// 维修人员数据
export const REPAIRMEN = {
  1: { id: 1, name: "张师傅" },
  2: { id: 2, name: "李师傅" },
  3: { id: 3, name: "王师傅" },
};

// 11.18添加紧急程度类型
export const priority_LEVELS = {
  LOW: { value: "low", label: "一般", color: "blue" },
  MEDIUM: { value: "medium", label: "较紧急", color: "orange" },
  HIGH: { value: "high", label: "紧急", color: "red" },
};

// 状态映射函数：将后端枚举值转换为前端状态值
const mapStatusToFrontend = (backendStatus) => {
  if (!backendStatus) return 'pending';
  
  const statusMap = {
    'WAITING_ACCEPT': 'pending',
    'IN_PROGRESS': 'processing',
    'RESOLVED': 'completed',
    'WAITING_FEEDBACK': 'to_be_evaluated',
    'FEEDBACKED': 'closed',
    'CLOSED': 'closed',
    'REJECTED': 'rejected',
  };
  
  // 如果已经是前端格式，直接返回
  if (statusMap[backendStatus]) {
    return statusMap[backendStatus];
  }
  
  // 如果已经是小写格式（前端格式），直接返回
  if (['pending', 'processing', 'completed', 'to_be_evaluated', 'closed', 'rejected'].includes(backendStatus.toLowerCase())) {
    return backendStatus.toLowerCase();
  }
  
  return 'pending'; // 默认值
};

const mapOrderSummary = (order = {}) => {
  const description = order.description || '';
  const rawTitle = order.title || '';
  const title = (rawTitle && rawTitle !== description)
    ? rawTitle
    : (order.locationText ? `报修-${order.locationText}` : '报修单');

  return {
    ...order,
    id: order.ticketId || order.id,
    ticketId: order.ticketId || order.id,
    category: order.categoryName || order.category || '',
    location: order.locationText || order.location || '',
    title,
    description,
    priority: order.priority || 'low',
    status: mapStatusToFrontend(order.status),
    originalStatus: order.status,
    studentID: order.studentId || order.studentID || '',
    repairmanId: order.staffId || order.repairmanId || null,
    created_at: order.createdAt || order.created_at || '',
    assigned_at: order.assignedAt || order.assigned_at || '',
    estimated_completion_time: order.estimatedCompletionTime || order.estimated_completion_time || '',
    deleted: order.deleted || false,
    deletedAt: order.deletedAt || null,
  };
};

// 数据服务方法 - 全部改为调用API
export const repairService = {
  // 获取所有工单（管理员端使用）
  getRepairOrders: async (params = {}) => {
    try {
      console.log('获取工单，请求参数:', params);
      const response = await api.admin.getAllOrders(params);
      console.log('获取工单响应:', response);
      
      if (response.code === 200) {
        const rawData = response.data.list || response.data || [];
        console.log('原始工单数据:', rawData);
        
        // 映射后端字段到前端字段
        // 注意：后端 TicketSummaryDto 使用 record，JSON 字段名与 record 字段名一致（驼峰命名）
        // 字段名：ticketId, categoryName, locationText, studentId, staffId, createdAt, deleted, deletedAt
        const mappedData = Array.isArray(rawData) ? rawData.map(order => {
          console.log('原始订单数据:', order);
          const mapped = mapOrderSummary(order);
          console.log('映射后的订单数据:', mapped);
          return mapped;
        }) : [];
        
        console.log('映射后的工单数据:', mappedData);
        
        return {
          data: mappedData,
          total: response.data.total || mappedData.length,
          page: response.data.page || 0,
          pageSize: response.data.pageSize || 10,
        };
      } else {
        throw new Error(response.message || '获取工单失败');
      }
    } catch (error) {
      console.error('获取工单失败:', error);
      message.error('获取工单失败: ' + error.message);
      throw error;
    }
  },

  // 获取 SLA 超时预警概览
  getSlaOverview: async () => {
    try {
      const response = await api.admin.getStatsSla();
      if (response.code === 200) {
        const data = response.data || {};
        const alertTickets = Array.isArray(data.alertTickets)
          ? data.alertTickets.map(item => mapOrderSummary(item))
          : [];

        return {
          ...data,
          activeCount: Number(data.activeCount || 0),
          overdueAcceptCount: Number(data.overdueAcceptCount || 0),
          overdueCompletionCount: Number(data.overdueCompletionCount || 0),
          warningCount: Number(data.warningCount || 0),
          alertTotal: Number(data.alertTotal || 0),
          overdueRate: Number(data.overdueRate || 0),
          alertTickets,
        };
      }
      throw new Error(response.message || '获取SLA预警失败');
    } catch (error) {
      console.error('获取SLA预警失败:', error);
      throw error;
    }
  },

  // 获取我的报修（学生端使用）
  getMyRepairOrders: async (params = {}) => {
    try {
      const response = await api.student.getMyOrders(params);
      console.log('获取我的报修响应:', response);
      
      if (response.code === 200) {
        const rawData = response.data.list || response.data || [];
        console.log('原始报修数据:', rawData);
        
        // 映射后端字段到前端字段
        const mappedData = Array.isArray(rawData) ? rawData.map(order => {
          // 确保title和description正确区分
          const description = order.description || '';
          // 如果后端返回的title和description相同，说明后端没有正确存储title，使用位置信息生成标题
          const rawTitle = order.title || '';
          const title = (rawTitle && rawTitle !== description) 
            ? rawTitle 
            : (order.locationText ? `报修-${order.locationText}` : '报修单');
          
          // 映射状态，添加调试日志
          const backendStatus = order.status;
          const frontendStatus = mapStatusToFrontend(backendStatus);
          if (backendStatus && backendStatus !== frontendStatus) {
            console.log(`状态映射: ${backendStatus} -> ${frontendStatus} (订单ID: ${order.ticketId || order.id})`);
          }
          
          return {
            ...order,
            id: order.ticketId || order.id,
            category: order.categoryName || order.category,
            location: order.locationText || order.location,
            description: description, // 确保description字段存在且完整
            created_at: order.createdAt || order.created_at,
            assigned_at: order.assignedAt || order.assigned_at,
            completed_at: order.completedAt || order.completed_at,
            repairmanId: order.staffId || order.repairmanId || null,
            repairmanName: order.staffName || null, // 添加维修人员名称
            status: frontendStatus, // 映射状态
            title: title, // 确保标题正确生成，与description区分
            rating: order.ratingScore || order.rating || null, // 映射评价分数
          };
        }) : [];
        
        console.log('映射后的报修数据:', mappedData);
        
        return {
          data: mappedData,
          total: response.data.total || mappedData.length,
          page: response.data.page || 0,
          pageSize: response.data.pageSize || 10,
        };
      } else {
        throw new Error(response.message || '获取我的报修失败');
      }
    } catch (error) {
      console.error('获取我的报修失败:', error);
      message.error('获取我的报修失败: ' + error.message);
      throw error;
    }
  },

  // 根据ID获取单个工单
  getRepairOrderById: async (id) => {
    try {
      console.log('获取工单详情，工单ID:', id);
      const response = await api.student.getOrderDetail(id);
      console.log('获取工单详情响应:', response);

      if (response.code === 200) {
        const orderDetail = response.data;
        console.log('原始工单详情数据:', orderDetail);

        // 映射后端字段到前端字段，确保数据格式一致
        const mappedDetail = {
          ...orderDetail,
          id: orderDetail.ticketId || orderDetail.id,
          ticketId: orderDetail.ticketId || orderDetail.id,
          category: orderDetail.categoryName || orderDetail.category,
          location: orderDetail.locationText || orderDetail.location,
          description: orderDetail.description || '',
          created_at: orderDetail.createdAt || orderDetail.created_at,
          assigned_at: orderDetail.assignedAt || orderDetail.assigned_at,
          completed_at: orderDetail.completedAt || orderDetail.completed_at,
          closedAt: orderDetail.closedAt || orderDetail.closed_at,
          rejection_reason: orderDetail.rejectionReason || orderDetail.rejection_reason,
          studentID: orderDetail.studentId || orderDetail.studentID,
          studentName: orderDetail.studentNickname || orderDetail.studentName || '未知',
          repairmanId: orderDetail.staffId || orderDetail.repairmanId || null,
          repairmanName: orderDetail.staffNickname || orderDetail.staffName || null,
          status: mapStatusToFrontend(orderDetail.status), // 映射状态
          // 确保 title 正确
          title: (orderDetail.title && orderDetail.title !== orderDetail.description)
            ? orderDetail.title
            : (orderDetail.locationText ? `报修-${orderDetail.locationText}` : '报修单'),
          // 评价信息
          rating: orderDetail.rating?.score || orderDetail.rating || null,
          feedback: orderDetail.rating?.comment || orderDetail.feedback || null,
          ratingTime: orderDetail.rating?.ratedAt || orderDetail.ratingTime || null,
          logs: orderDetail.logs || [],
          // 维修备注
          repairNotes: orderDetail.repairNotes || null,
          processNotes: orderDetail.processNotes || null,
          // 处理图片
          images: (orderDetail.images || []).map(img => {
            if (typeof img === 'string') {
              return img.startsWith('http') ? img : `http://localhost:8080${img.startsWith('/') ? '' : '/'}${img}`;
            }
            const imageUrl = img.imageUrl || img.url || img;
            return imageUrl.startsWith('http') ? imageUrl : `http://localhost:8080${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
          }),
        };

        console.log('映射后的工单详情:', mappedDetail);
        return mappedDetail;
      } else {
        throw new Error(response.message || '获取工单详情失败');
      }
    } catch (error) {
      console.error('获取工单详情失败:', error);
      message.error('获取工单详情失败: ' + error.message);
      throw error;
    }
  },

  // 创建新工单
  createRepairOrder: async (orderData, fileList = []) => {
    try {
      // 创建FormData对象
      const formData = new FormData();
      
      // 添加文本字段
      Object.keys(orderData).forEach(key => {
        if (key !== 'images' && orderData[key] !== undefined && orderData[key] !== null) {
          // 确保所有的值都是字符串类型
          formData.append(key, String(orderData[key]));
        }
      });
      
      // 添加图片文件
      if (fileList && Array.isArray(fileList) && fileList.length > 0) {
        fileList.forEach((file) => {
          if (file.originFileObj) {
            formData.append('images', file.originFileObj);
          } else {
            formData.append('images', file);
          }
        });
      }
      
      // 确保formData确实是FormData实例
      console.log('FormData内容:', formData);
      for (let pair of formData.entries()) {
        console.log(pair[0] + ', ' + pair[1]);
      }
      
      const response = await api.student.createOrder(formData);
      if (response.code === 200) {
        message.success('报修申请提交成功！');
        return response.data;
      } else {
        throw new Error(response.message || '创建工单失败');
      }
    } catch (error) {
      console.error('创建工单失败:', error);
      message.error('创建工单失败: ' + (error.message || '未知错误'));
      throw error;
    }
  },

  // 更新工单状态
  updateRepairOrderStatus: async (id, status, repairmanId = null) => {
    try {
      // 根据状态调用不同的API
      let response;
      if (status === 'processing' && repairmanId) {
        response = await api.admin.assignOrder(id, repairmanId);
      } else {
        // 其他状态更新可能需要单独的API，这里暂时模拟
        await new Promise(resolve => setTimeout(resolve, 500));
        response = { code: 200, data: { id, status, repairmanId } };
      }
      
      if (response.code === 200) {
        message.success('工单状态更新成功');
        return response.data;
      } else {
        throw new Error(response.message || '更新工单状态失败');
      }
    } catch (error) {
      console.error('更新工单状态失败:', error);
      message.error('更新工单状态失败: ' + error.message);
      throw error;
    }
  },

  // 获取维修人员列表
  getRepairmen: async () => {
    try {
      const response = await api.admin.getRepairmen();
      console.log('获取维修人员列表响应:', response);
      
      // 后端返回的是数组，需要转换为统一格式
      let data = [];
      if (Array.isArray(response)) {
        data = response.map(user => ({
          id: user.userId || user.id,
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
        }));
      } else if (response.code === 200) {
        const rawData = response.data?.list || response.data || [];
        data = Array.isArray(rawData) ? rawData.map(user => ({
          id: user.userId || user.id,
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
        })) : [];
      } else {
        throw new Error(response.message || '获取维修人员列表失败');
      }
      
      return data;
    } catch (error) {
      console.error('获取维修人员列表失败:', error);
      message.error('获取维修人员列表失败: ' + (error.message || '未知错误'));
      throw error; // 不再返回默认数据，直接抛出错误
    }
  },

  // 获取智能派单推荐
  getRecommendedRepairmen: async (orderId) => {
    try {
      const response = await api.admin.recommendStaff(orderId);
      if (response.code === 200) {
        const rawData = Array.isArray(response.data) ? response.data : [];
        return rawData.map(item => ({
          ...item,
          staffId: item.staffId || item.userId || item.id,
          staffName: item.staffName || item.nickname || item.name || '未知维修人员',
          score: Number(item.score || 0),
          activeTaskCount: Number(item.activeTaskCount || 0),
          sameCategoryCompletedCount: Number(item.sameCategoryCompletedCount || 0),
          completedTaskCount: Number(item.completedTaskCount || 0),
          averageRating: Number(item.averageRating || 0),
          averageProcessingHours: Number(item.averageProcessingHours || 0),
          reason: item.reason || '暂无推荐依据',
        }));
      }
      throw new Error(response.message || '获取智能派单推荐失败');
    } catch (error) {
      console.error('获取智能派单推荐失败:', error);
      throw error;
    }
  },

  // 分配维修人员
  assignRepairman: async (orderId, repairmanId) => {
    try {
      const response = await api.admin.assignOrder(orderId, repairmanId);
      if (response.code === 200) {
        message.success('维修人员分配成功');
        return response.data;
      } else {
        throw new Error(response.message || '分配维修人员失败');
      }
    } catch (error) {
      console.error('分配维修人员失败:', error);
      message.error('分配维修人员失败: ' + error.message);
      throw error;
    }
  },

  // 驳回工单
  rejectRepairOrder: async (orderId, reason) => {
    try {
      const response = await api.admin.rejectOrder(orderId, reason);
      if (response.code === 200) {
        message.success('工单已驳回');
        return response.data;
      } else {
        throw new Error(response.message || '驳回工单失败');
      }
    } catch (error) {
      console.error('驳回工单失败:', error);
      message.error('驳回工单失败: ' + error.message);
      throw error;
    }
  },

  // 搜索工单
  searchRepairOrders: async (filters = {}) => {
    try {
      console.log('🔍 [searchRepairOrders] 搜索工单，请求参数:', filters);
      const response = await api.admin.getAllOrders(filters);
      console.log('🔍 [searchRepairOrders] 搜索工单响应:', response);
      
      if (response.code === 200) {
        const rawData = response.data.list || response.data || [];
        console.log('🔍 [searchRepairOrders] 搜索原始工单数据:', rawData);
        console.log('🔍 [searchRepairOrders] 原始数据类型:', Array.isArray(rawData) ? '数组' : typeof rawData);
        console.log('🔍 [searchRepairOrders] 原始数据长度:', Array.isArray(rawData) ? rawData.length : 0);
        
        if (Array.isArray(rawData) && rawData.length > 0) {
          console.log('🔍 [searchRepairOrders] 第一个原始订单:', rawData[0]);
          console.log('🔍 [searchRepairOrders] 第一个订单的字段:', Object.keys(rawData[0]));
        }
        
        // 映射后端字段到前端字段（与 getRepairOrders 保持一致）
        const mappedData = Array.isArray(rawData) ? rawData.map((order, index) => {
          console.log(`🔍 [searchRepairOrders] 映射订单 ${index}:`, order);
          // 确保title和description正确区分
          const description = order.description || '';
          // 如果后端返回的title和description相同，说明后端没有正确存储title，使用位置信息生成标题
          const rawTitle = order.title || '';
          const title = (rawTitle && rawTitle !== description) 
            ? rawTitle 
            : (order.locationText ? `报修-${order.locationText}` : '报修单');
          
          const mapped = {
            ...order,
            // 工单ID：后端字段是 ticketId
            id: order.ticketId || order.id,
            ticketId: order.ticketId || order.id,
            // 分类：后端字段是 categoryName
            category: order.categoryName || order.category || '',
            // 位置：后端字段是 locationText
            location: order.locationText || order.location || '',
            // 标题：确保title字段存在且与description区分
            title: title,
            // 描述：后端字段是 description，确保与title不同
            description: description,
            // 优先级：后端字段是 priority
            priority: order.priority || 'low',
            // 状态：后端字段是 status，需要映射
            status: mapStatusToFrontend(order.status),
            // 学生ID：后端字段是 studentId
            studentID: order.studentId || order.studentID || '',
            // 维修工ID：后端字段是 staffId
            repairmanId: order.staffId || order.repairmanId || null,
            // 创建时间：后端字段是 createdAt（LocalDateTime）
            created_at: order.createdAt || order.createdAt || order.created_at || '',
            // 删除标记：后端字段是 deleted
            deleted: order.deleted || false,
            deletedAt: order.deletedAt || null,
          };
          console.log(`🔍 [searchRepairOrders] 映射后的订单 ${index}:`, mapped);
          console.log(`🔍 [searchRepairOrders] 映射后的订单 ${index} 字段:`, Object.keys(mapped));
          return mapped;
        }) : [];
        
        console.log('🔍 [searchRepairOrders] 搜索映射后的工单数据:', mappedData);
        console.log('🔍 [searchRepairOrders] 映射后的数据长度:', mappedData.length);
        
        if (mappedData.length > 0) {
          console.log('🔍 [searchRepairOrders] 第一个映射后的订单:', mappedData[0]);
          console.log('🔍 [searchRepairOrders] 第一个映射后的订单字段:', Object.keys(mappedData[0]));
        }
        
        return {
          data: mappedData,
          total: response.data.total || mappedData.length,
          page: response.data.page || 0,
          pageSize: response.data.pageSize || 10,
        };
      } else {
        throw new Error(response.message || '搜索工单失败');
      }
    } catch (error) {
      console.error('🔍 [searchRepairOrders] 搜索工单失败:', error);
      message.error('搜索工单失败: ' + error.message);
      throw error;
    }
  },

  // 搜索我的报修（学生端）
  searchMyRepairOrders: async (filters = {}) => {
    try {
      const response = await api.student.getMyOrders(filters);
      
      if (response.code === 200) {
        return {
          data: response.data.list || response.data,
          total: response.data.total || (response.data.list ? response.data.list.length : 0),
          page: response.data.page || 1,
          pageSize: response.data.pageSize || 10,
        };
      } else {
        throw new Error(response.message || '搜索我的报修失败');
      }
    } catch (error) {
      console.error('搜索我的报修失败:', error);
      message.error('搜索我的报修失败: ' + error.message);
      throw error;
    }
  },

  // 删除报修（学生端）
  deleteRepairOrder: async (orderId) => {
    try {
      const response = await api.student.deleteOrder(orderId);
      if (response.code === 200) {
        message.success('报修单删除成功');
        return response.data;
      } else {
        throw new Error(response.message || '删除报修单失败');
      }
    } catch (error) {
      console.error('删除报修单失败:', error);
      message.error('删除报修单失败: ' + error.message);
      throw error;
    }
  },

  // 提交评价（学生端）
  evaluateRepairOrder: async (orderId, studentId, ratingOrData, feedback) => {
    try {
      if (!studentId) {
        throw new Error('学生ID不能为空');
      }
      const payload = typeof ratingOrData === 'object'
        ? { ...ratingOrData }
        : { score: ratingOrData, comment: feedback || '' };
      const response = await api.student.evaluateOrder(orderId, { 
        studentId,
        ...payload,
      });
      
      if (response.code === 200) {
        message.success('评价提交成功');
        return response.data;
      } else {
        throw new Error(response.message || '提交评价失败');
      }
    } catch (error) {
      console.error('提交评价失败:', error);
      message.error('提交评价失败: ' + error.message);
      throw error;
    }
  },
};

// 工具函数
export const repairUtils = {
  // 获取状态信息
  getStatusInfo: (status) => {
    const statusMap = {
      pending: { label: "待受理", color: "orange" },
      processing: { label: "处理中", color: "blue" },
      completed: { label: "已完成", color: "green" },
      to_be_evaluated: { label: "待评价", color: "purple" },
      closed: { label: "已关闭", color: "default" },
      rejected: { label: "已驳回", color: "red" },
    };
    return statusMap[status] || { label: status, color: "default" };
  },

  // 获取分类信息
  getCategoryInfo: (category) => {
    const categoryMap = {
      waterAndElectricity: { value: "waterAndElectricity", label: "水电维修" },
      networkIssues: { value: "networkIssues", label: "网络故障" },
      furnitureRepair: { value: "furnitureRepair", label: "家具维修" },
      applianceIssues: { value: "applianceIssues", label: "电器故障" },
      publicFacilities: { value: "publicFacilities", label: "公共设施" },
    };
    return categoryMap[category] || { label: category };
  },

  // 获取维修人员信息
  getRepairmanInfo: (repairmanId) => {
    const repairmen = {
      1: { id: 1, name: "张师傅" },
      2: { id: 2, name: "李师傅" },
      3: { id: 3, name: "王师傅" },
    };
    return repairmen[repairmanId] || null;
  },

  // 11.18获取紧急程度信息
  getPriorityInfo: (priority) => {
    const priorityMap = {
      low: { label: "低", color: "blue" },
      medium: { label: "中", color: "orange" },
      high: { label: "高", color: "red" },
    };
    return priorityMap[priority] || { label: priority, color: "default" };
  },
};
