// src/services/feedbackService.js
import api from '../services/api';
import { message } from 'antd';

export const feedbackService = {
  // 获取所有评价
  getAllFeedbacks: async (params = {}) => {
    try {
      const response = await api.admin.getAllFeedbacks(params);

      // 后端返回的是 PagedResult<RatingDto>，结构为 { list, total }
      // 不再使用前端模拟数据
      if (!response) {
        throw new Error('获取评价列表失败：响应为空');
      }

      // 兼容多种返回结构，先取出原始列表
      const listRaw =
        response.list ||
        response.data?.list ||
        response.data ||
        [];

      if (!Array.isArray(listRaw)) {
        throw new Error('获取评价列表失败：返回数据格式不正确');
      }

      // 将后端 RatingDto 映射为前端使用的字段格式
      const list = listRaw.map(item => ({
        // 评价 ID
        id: item.ratingId ?? item.id,
        // 评分（1~5）
        rating: item.score ?? item.rating ?? 0,
        // 评论内容
        comment: item.comment ?? '',
        // 学生信息
        studentId: item.studentId ?? item.studentID,
        studentName: item.studentName ?? null, // 使用后端返回的学生姓名
        // 维修人员信息
        repairmanId: item.staffId ?? item.repairmanId,
        repairmanName: item.staffName ?? item.repairmanName ?? null, // 使用后端返回的维修人员姓名
        // 报修单ID
        repairOrderId: item.repairOrderId ?? null,
        speedRating: item.speedRating ?? null,
        qualityRating: item.qualityRating ?? null,
        attitudeRating: item.attitudeRating ?? null,
        resolved: item.resolved,
        anonymous: item.anonymous,
        sentiment: item.sentiment ?? null,
        sentimentScore: item.sentimentScore ?? null,
        sentimentKeywords: item.sentimentKeywords ?? item.keywords ?? [],
        sentimentSummary: item.sentimentSummary ?? item.summary ?? '',
        sentimentAnalyzedAt: item.sentimentAnalyzedAt ?? null,
        // 时间字段
        createdAt: item.ratedAt ?? item.createdAt ?? item.created_at,
      }));

      return list;
    } catch (error) {
      console.error('获取评价列表失败:', error);
      message.error('获取评价列表失败');
      // 出错时返回空数组，让前端显示“暂无评价数据”，而不是测试数据
      return [];
    }
  },

  // 根据ID删除评价
  deleteFeedback: async (feedbackId) => {
    try {
      await api.admin.deleteFeedback(feedbackId);
      message.success('评价删除成功');
      return true;
    } catch (error) {
      console.error('删除评价失败:', error);
      message.error('删除评价失败: ' + error.message);
      throw error;
    }
  },

  // 获取维修人员信息（备用方法，如果API返回的数据中不包含维修人员姓名）
  getRepairmanInfo: (repairmanId) => {
    const repairmen = {
      1: { id: 1, name: '张师傅' },
      2: { id: 2, name: '李师傅' },
      3: { id: 3, name: '王师傅' },
      4: { id: 4, name: '赵师傅' },
    };
    return repairmen[repairmanId] || { id: repairmanId, name: '未知维修工' };
  },
};
