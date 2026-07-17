const BASE_URL = import.meta.env.VITE_API_BASE_URL || "/api";

const request = async (endpoint, options = {}) => {
  const token = localStorage.getItem("token");

  const headers = {};
  // 先复制用户自定义的 headers（但排除 Content-Type）
  if (options.headers) {
    Object.keys(options.headers).forEach(key => {
      if (key.toLowerCase() !== 'content-type') {
        headers[key] = options.headers[key];
      }
    });
  }
  
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // 对于 FormData 请求，完全不设置 Content-Type，让浏览器自动设置
  if (options.body instanceof FormData) {
    // 不设置任何 Content-Type，让浏览器自动添加 multipart/form-data; boundary=...
  } else if (!headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  const url = `${BASE_URL}${endpoint}`;
  
  // 添加超时控制（默认30秒，登录请求使用10秒）
  const timeout = options.timeout || 30000;
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  
  let response;
  try {
    response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error('请求超时，请检查网络连接');
    }
    // 处理连接重置等网络错误
    if (error.message && (error.message.includes('Failed to fetch') || error.message.includes('ERR_CONNECTION_RESET'))) {
      throw new Error('无法连接到服务器，请检查后端服务是否正常运行');
    }
    throw new Error(`网络错误: ${error.message}`);
  }

  if (!response.ok) {
    let errorMessage = `请求失败: ${response.status}`;
    const contentType = response.headers.get("content-type");

    if (contentType && contentType.includes("application/json")) {
      try {
        const errorData = await response.json();
        errorMessage = errorData.message || errorMessage;
      } catch (e) {
        console.warn("无法解析 JSON 错误响应:", e);
      }
    } else {
      try {
        const text = await response.text();
        console.warn("非 JSON 响应:", text.substring(0, 100));
        errorMessage = `服务器返回了非 JSON 数据 (${response.status})`;
      } catch (e) {
        console.warn("无法读取错误响应文本:", e);
      }
    }

    // 特别处理403错误
    if (response.status === 403) {
      console.warn("访问被拒绝，请检查您的权限");
      // 清除本地存储的令牌
    }

    if (response.status === 401) {
      console.warn("Token 已过期或无效");
      // 清除本地存储的令牌
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/login"; // 重定向到登录页
    }

    throw new Error(errorMessage);
  }
  const contentType = response.headers.get("content-type");
  // 对于 204 No Content，直接返回空对象，避免 JSON 解析错误
  if (response.status === 204) {
    return {};
  }

  if (!contentType || !contentType.includes("application/json")) {
    const text = await response.text();
    console.warn("API 返回了非 JSON 数据:", text.substring(0, 200));
    throw new SyntaxError("服务器返回了非 JSON 数据");
  }
  return response.json();
};

const toQueryString = (params) => {
  if (!params) return "";
  const searchParams = new URLSearchParams();
  Object.keys(params).forEach((key) => {
    const value = params[key];
    // 跳过 undefined 和 null，但允许空字符串和0等falsy值
    if (value !== undefined && value !== null) {
      // 布尔值转换为字符串
      if (typeof value === 'boolean') {
        searchParams.append(key, value.toString());
      } else {
        // 对于字符串，如果为空字符串则跳过（清空搜索时不传递参数）
        // 对于其他类型（数字等），正常传递
        if (typeof value === 'string') {
          const trimmed = value.trim();
          if (trimmed !== '') {
            searchParams.append(key, trimmed);
          }
        } else {
          searchParams.append(key, String(value));
        }
      }
    }
  });
  const str = searchParams.toString();
  return str ? `?${str}` : "";
};

const api = {
  request,
  auth: {
    login: ({ userId, password }) =>
      request("/auth/login", {
        method: "POST",
        body: JSON.stringify({ userId, password }),
        timeout: 10000, // 登录请求10秒超时
      }),
    register: (userData) =>
      request("/auth/register", {
        method: "POST",
        body: JSON.stringify(userData),
      }),
  },

  users: {
    getMe: () => request("/users/me"),

    //用户信息更新（含头像）
    updateMe: (data) => {
      // 如果传入的是 FormData，直接使用；否则转换为 JSON
      if (data instanceof FormData) {
        return request("/users/me", {
          method: "PUT",
          body: data,
        });
      } else {
        // 发送 JSON 格式数据
        return request("/users/me", {
          method: "PUT",
          body: JSON.stringify(data),
        });
      }
    },

    changePassword: (data) =>
      request("/users/me/password", {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    // 获取指定用户信息
    getUser: (userId) => request(`/users/${userId}`),

    // 使用已有的 GET /api/users?role=STUDENT 接口替代需要新增的接口
    getStudents: (params) => {
      const queryParams = { ...params, role: "STUDENT" };
      return request(`/users${toQueryString(queryParams)}`);
    },

  },

  student: {
    createOrder: (formData) =>
      request("/repair-orders", {
        method: "POST",
        body: formData,
      }),

    getMyOrders: (params) =>
      request(`/repair-orders/my${toQueryString(params)}`),

    getOrderDetail: (id) => request(`/repair-orders/${id}`),

    deleteOrder: (id) =>
      request(`/repair-orders/${id}`, {
        method: "DELETE",
      }),

    evaluateOrder: (id, data) => {
      const requestBody = {
        studentId: data.studentId,
        score: data.score || data.rating,
        comment: data.comment || data.feedback || '',
        speedRating: data.speedRating,
        qualityRating: data.qualityRating,
        attitudeRating: data.attitudeRating,
        resolved: data.resolved,
        anonymous: data.anonymous,
      };
      return request(`/repair-orders/${id}/evaluate`, {
        method: "POST",
        body: JSON.stringify(requestBody),
      });
    },

    confirmCompletion: (id) =>
      request(`/repair-orders/${id}/confirm-completion`, {
        method: "PUT",
      }),

    rejectCompletion: (id, reason) =>
      request(`/repair-orders/${id}/reject-completion`, {
        method: "PUT",
        body: JSON.stringify({ reason }),
      }),
  },

  repairman: {
    getMyTasks: (params) => request(`/tasks/my${toQueryString(params)}`),

    // 修正 startTask 调用，使用后端已有的 status 更新接口
    startTask: (id, estimatedCompletionTime) =>
      request(`/tasks/${id}/status`, {
        method: "PUT",
        body: JSON.stringify({
          newStatus: "IN_PROGRESS", // 后端枚举值：IN_PROGRESS
          rejectionReason: null, // 开始任务不需要驳回理由
        }),
      }),

    completeTask: (id, notes) =>
      request(`/tasks/${id}/complete`, {
        method: "PUT", // 后端是 PUT 方法
        body: JSON.stringify({
          newStatus: "RESOLVED", // 后端枚举值：RESOLVED
          rejectionReason: null, // 完成任务不需要驳回理由
          notes: notes || "",
        }),
      }),

    getTaskDetail: (id) => request(`/tasks/${id}`),

    arriveTask: (id, data = {}) =>
      request(`/tasks/${id}/arrive`, {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    addProcessRecord: (id, data) =>
      request(`/tasks/${id}/process-records`, {
        method: "POST",
        body: JSON.stringify(data),
      }),

    addProcessRecordWithImages: (id, data, files = []) => {
      const formData = new FormData();
      formData.append("record", new Blob([JSON.stringify(data)], { type: "application/json" }));
      files.forEach((file) => {
        formData.append("images", file);
      });
      return request(`/tasks/${id}/process-records/with-images`, {
        method: "POST",
        body: formData,
      });
    },

    requestTransfer: (id, data) =>
      request(`/tasks/${id}/transfer-request`, {
        method: "POST",
        body: JSON.stringify(data),
      }),
  },

  admin: {
    // 使用后端 AdminController 提供的接口
    getAllOrders: (params) =>
      request(`/admin/repair-orders${toQueryString(params)}`),

    // 修正分配工单接口，使用后端已有的接口
    assignOrder: (orderId, repairmanId) =>
      request(`/admin/repair-orders/${orderId}/assign`, {
        method: "PUT", // 后端是 PUT 方法
        body: JSON.stringify({ repairmanId }), // 确保字段名与后端一致
      }),

    recommendStaff: (orderId) =>
      request(`/admin/repair-orders/${orderId}/recommend-staff`),

    // 修正驳回工单接口，使用通用状态更新接口
    rejectOrder: (orderId, reason) =>
      request(`/tasks/${orderId}/status`, {
        method: "PUT",
        body: JSON.stringify({
          newStatus: "REJECTED",        // 后端期望字段：newStatus，值为枚举名
          rejectionReason: reason || "", // 驳回原因
        }),
      }),

    // 获取学生列表 - 使用 /api/users?role=STUDENT
    getStudents: (params) => {
      const queryParams = { ...params, role: "STUDENT" };
      return request(`/users${toQueryString(queryParams)}`);
    },

    // 获取维修人员列表 - 使用 /api/users?role=STAFF (后端使用STAFF而不是REPAIRMAN)
    getRepairmen: (params) => {
      const queryParams = { ...params, role: "STAFF" };
      return request(`/users${toQueryString(queryParams)}`);
    },
    
    updateUser: (id, updateRequest) => {
      // 后端需要完整的UserRegisterRequest
      return request(`/admin/users/${id}`, {
        method: "PUT",
        body: JSON.stringify(updateRequest),
      });
    },

    resetPassword: (id) =>
      request(`/admin/users/${id}/reset-password`, {
        method: "POST",
      }),

    deleteUser: (id) =>
      request(`/admin/users/${id}`, {
        method: "DELETE",
      }),

    getAllFeedbacks: (params) =>
      request(`/admin/feedbacks${toQueryString(params)}`),

    deleteFeedback: (id) =>
      request(`/admin/feedbacks/${id}`, {
        method: "DELETE",
      }),

    updateFeedbackFollowUp: (id, data) =>
      request(`/admin/feedbacks/${id}/follow-up`, {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    getStatsCategory: () => request("/admin/stats/category"),

    getStatsLocation: () => request("/admin/stats/location"),

    getStatsRepairmanRating: () => request("/admin/stats/repairman-rating"),

    getStatsMonthly: () => request("/admin/stats/monthly"),

    // 新增：获取工单状态统计
    getStatsOrderStatus: () => request("/admin/stats/status"),

    // 新增：获取平均处理时间统计
    getStatsProcessingTime: () => request("/admin/stats/processing-time"),

    // 新增：获取 SLA 超时预警统计
    getStatsSla: () => request("/admin/stats/sla"),

    // 新增：获取热点问题分析
    getStatsHotspot: () => request("/admin/stats/hotspot"),

    // 新增：获取校园设施健康指数
    getStatsFacilityHealth: () => request("/admin/stats/facility-health"),

    // 新增：运维中心
    getAuditLogs: (params) => request(`/admin/audit-logs${toQueryString(params)}`),

    getSystemConfig: () => request("/admin/system-config"),

    updateSystemConfig: (key, data) =>
      request(`/admin/system-config/${encodeURIComponent(key)}`, {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    getKnowledgeBase: (params) =>
      request(`/admin/knowledge-base${toQueryString(params)}`),

    createKnowledgeBase: (data) =>
      request("/admin/knowledge-base", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    updateKnowledgeBase: (id, data) =>
      request(`/admin/knowledge-base/${id}`, {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    deleteKnowledgeBase: (id) =>
      request(`/admin/knowledge-base/${id}`, {
        method: "DELETE",
      }),

    rebuildKnowledgeBaseIndex: () =>
      request("/admin/knowledge-base/rebuild-index", {
        method: "POST",
      }),

    getTransferRequests: (params) =>
      request(`/admin/transfer-requests${toQueryString(params)}`),

    decideTransferRequest: (recordId, data) =>
      request(`/admin/transfer-requests/${recordId}/decision`, {
        method: "PUT",
        body: JSON.stringify(data),
      }),

    // 备份相关接口
    createBackup: () => request("/admin/backup/create", { method: "POST" }),
    listBackups: () => request("/admin/backup/list"),
    getBackupStatus: () => request("/admin/backup/status"),
    restoreBackup: (fileName) =>
      request("/admin/backup/restore", {
        method: "POST",
        body: JSON.stringify({ fileName }),
      }),
    deleteBackup: (fileName) =>
      request(`/admin/backup/${encodeURIComponent(fileName)}`, {
        method: "DELETE",
      }),
  },

  notifications: {
    list: () => request("/notifications"),

    unreadCount: () => request("/notifications/unread-count"),

    markRead: (id) =>
      request(`/notifications/${id}/read`, {
        method: "PUT",
      }),

    markAllRead: () =>
      request("/notifications/read-all", {
        method: "PUT",
      }),
  },

  ticketComments: {
    list: (ticketId) => request(`/repair-orders/${ticketId}/comments`),

    add: (ticketId, data) =>
      request(`/repair-orders/${ticketId}/comments`, {
        method: "POST",
        body: JSON.stringify(data),
      }),

    urge: (ticketId, data) =>
      request(`/repair-orders/${ticketId}/comments/urge`, {
        method: "POST",
        body: JSON.stringify(data),
      }),
  },

  processRecords: {
    list: (ticketId) => request(`/repair-orders/${ticketId}/process-records`),

    add: (ticketId, data) =>
      request(`/repair-orders/${ticketId}/process-records`, {
        method: "POST",
        body: JSON.stringify(data),
      }),

    addWithImages: (ticketId, data, files = []) => {
      const formData = new FormData();
      formData.append("record", new Blob([JSON.stringify(data)], { type: "application/json" }));
      files.forEach((file) => {
        formData.append("images", file);
      });
      return request(`/repair-orders/${ticketId}/process-records/with-images`, {
        method: "POST",
        body: formData,
      });
    },
  },

  ai: {
    analyzeTicket: (data) =>
      request("/ai/ticket/analyze", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    // 新增：AI 智能报修分析接口（调用 qiyun-ai-service）
    analyzeTicketV2: (data) =>
      request("/ai/analyze-ticket", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    findSimilarTickets: (data) =>
      request("/ai/ticket/similar", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    generateRepairReport: (data) =>
      request("/ai/repair-report/generate", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    summarizeTicket: (ticketId) => request(`/ai/ticket/${ticketId}/summary`),

    regenerateCompletionSummary: (ticketId) =>
      request(`/repair-orders/${ticketId}/completion-summary/regenerate`, {
        method: "POST",
      }),

    draftKnowledge: (data) =>
      request("/ai/knowledge/draft", {
        method: "POST",
        body: JSON.stringify(data),
      }),

    status: () => request("/ai/status"),

    // RAG 知识问答
    ragAsk: (question, categoryKey) => {
      const params = new URLSearchParams({ question });
      if (categoryKey) {
        params.append('categoryKey', categoryKey);
      }
      return request(`/ai/rag/ask?${params.toString()}`);
    },
  },

  knowledgeBase: {
    search: (params) => request(`/knowledge-base/search${toQueryString(params)}`),

    recommend: (params) => request(`/knowledge-base/recommend${toQueryString(params)}`),
  },

  common: {
    uploadImages: (files) => {
      const formData = new FormData();
      // 后端期望的参数名是 "files"，不是 "files[]"
      const filesArray = Array.isArray(files) ? files : [files];
      filesArray.forEach((file) => {
        formData.append("files", file);
      });

      return request("/upload/images", {
        method: "POST",
        body: formData,
      });
    },
  },
};

export default api;
