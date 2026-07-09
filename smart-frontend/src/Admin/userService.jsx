// src/services/userService.js
import api from '../services/api';
import { message } from 'antd';

export const userService = {
  // 获取学生账号列表
  getStudents: async (params = {}) => {
    try {
      const response = await api.admin.getStudents(params);
      console.log('获取学生列表响应:', response);
      
      // 后端返回的是数组，需要转换为统一格式
      let data = [];
      if (Array.isArray(response)) {
        data = response.map(user => ({
          id: user.userId || user.id,
          number: user.userId || user.id, // 学号就是userId
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
          type: 'student',
        }));
      } else if (response.code === 200) {
        const rawData = response.data?.list || response.data || [];
        data = Array.isArray(rawData) ? rawData.map(user => ({
          id: user.userId || user.id,
          number: user.userId || user.id,
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
          type: 'student',
        })) : [];
      } else {
        throw new Error(response.message || '获取学生列表失败');
      }
      
      return {
        data: data,
        total: data.length,
        page: 1,
        pageSize: 10,
      };
    } catch (error) {
      console.error('获取学生列表失败:', error);
      message.error('获取学生列表失败: ' + (error.message || '未知错误'));
      throw error; // 不再返回模拟数据，直接抛出错误
    }
  },

  // 获取维修人员账号列表
  getRepairmen: async (params = {}) => {
    try {
      const response = await api.admin.getRepairmen(params);
      console.log('获取维修人员列表响应:', response);
      
      // 后端返回的是数组，需要转换为统一格式
      let data = [];
      if (Array.isArray(response)) {
        data = response.map(user => ({
          id: user.userId || user.id,
          number: user.userId || user.id, // 工号就是userId
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
          type: 'repairman',
        }));
      } else if (response.code === 200) {
        const rawData = response.data?.list || response.data || [];
        data = Array.isArray(rawData) ? rawData.map(user => ({
          id: user.userId || user.id,
          number: user.userId || user.id,
          name: user.nickname || user.name || '未知',
          phone: user.contactPhone || user.phone || '',
          type: 'repairman',
        })) : [];
      } else {
        throw new Error(response.message || '获取维修人员列表失败');
      }
      
      return {
        data: data,
        total: data.length,
        page: 1,
        pageSize: 10,
      };
    } catch (error) {
      console.error('获取维修人员列表失败:', error);
      message.error('获取维修人员列表失败: ' + (error.message || '未知错误'));
      throw error; // 不再返回模拟数据，直接抛出错误
    }
  },

  // 更新用户信息
  updateUser: async (userId, userData) => {
    try {
      // 先获取用户信息，然后更新
      const currentUser = await api.users.getUser(userId);
      console.log('当前用户信息:', currentUser);
      
      // 构建更新请求，保留原有信息，只更新phone
      const updateRequest = {
        userId: currentUser.userId || userId,
        password: 'dummy123', // 后端需要，但不会更新密码（后端只更新nickname和contactPhone）
        nickname: currentUser.nickname || currentUser.name || '未知',
        contactPhone: userData.phone || userData.contactPhone || currentUser.contactPhone || '',
        role: currentUser.role || 'STUDENT',
      };
      
      console.log('更新请求:', updateRequest);
      const response = await api.admin.updateUser(userId, updateRequest);
      console.log('更新响应:', response);
      
      if (response && response.userId) { // 后端返回UserDto，不是{code, data}格式
        message.success('用户信息更新成功');
        return response;
      } else {
        throw new Error('更新用户信息失败：响应格式不正确');
      }
    } catch (error) {
      console.error('更新用户信息失败:', error);
      message.error('更新用户信息失败: ' + (error.message || '未知错误'));
      throw error;
    }
  },

  // 重置用户密码
  resetPassword: async (userId) => {
    try {
      const response = await api.admin.resetPassword(userId);

      // 后端返回结构：{ message, userId, newPassword }
      if (response && response.userId && response.newPassword) {
        // 显示新密码给管理员
        message.success({
          content: `密码重置成功！新密码：${response.newPassword}`,
          duration: 10, // 显示10秒，让管理员有时间记录
        });
        console.log('密码重置结果:', {
          userId: response.userId,
          newPassword: response.newPassword,
        });
        return response;
      } else {
        throw new Error(response?.message || '重置密码失败：返回数据格式不正确');
      }
    } catch (error) {
      console.error('重置密码失败:', error);
      message.error('重置密码失败: ' + (error.message || '未知错误'));
      throw error;
    }
  },

  // 删除用户（实际上是禁用用户）
  deleteUser: async (userId, userType) => {
    try {
      const response = await api.admin.deleteUser(userId);
      message.success('用户删除成功');
      return { success: true };
    } catch (error) {
      console.error('删除用户失败:', error);
      message.error('删除用户失败: ' + (error.message || '未知错误'));
      throw error;
    }
  },

  // 搜索用户
  searchUsers: async (userType, filters = {}) => {
    try {
      let response;
      if (userType === 'students') {
        response = await api.admin.getStudents(filters);
      } else {
        response = await api.admin.getRepairmen(filters);
      }
      
      if (response.code === 200) {
        return {
          data: response.data.list || response.data,
          total: response.data.total || (response.data.list ? response.data.list.length : 0),
          page: response.data.page || 1,
          pageSize: response.data.pageSize || 10,
        };
      } else {
        throw new Error(response.message || '搜索用户失败');
      }
    } catch (error) {
      console.error('搜索用户失败:', error);
      message.error('搜索用户失败');
      throw error;
    }
  }
};