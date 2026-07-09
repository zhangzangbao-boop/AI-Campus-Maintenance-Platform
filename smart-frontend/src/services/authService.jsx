// src/services/authService.js
import request from '../utils/request';

const authService = {
  // 登录
  async login(username, password) {
    const res = await request('/api/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    return res; // {token, userId, role, userInfo}
  },

  // 获取当前用户信息
  async getMe() {
    const res = await request('/api/users/me', {
      method: 'GET',
    });
    return res; // {id, username, nickname, phone, email, avatar, role, studentID/workerID...}
  },

  // 更新个人信息（含头像）
  async updateProfile(data) {
    const formData = new FormData();
    Object.keys(data).forEach(key => {
      if (key === 'avatar' && data[key] instanceof File) {
        formData.append('avatar', data[key]);
      } else {
        formData.append(key, data[key]);
      }
    });
    const res = await fetch('/api/users/me', {
      method: 'PUT',
      body: formData,
      headers: { // request.js 已处理 token，但 FormData 不设 Content-Type
        Authorization: `Bearer ${localStorage.getItem('token')}`,
      },
    });
    if (!res.ok) throw new Error('Update failed');
    return res.json();
  },
};

export default authService;