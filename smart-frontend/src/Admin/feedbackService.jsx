import api from '../services/api';
import { message } from 'antd';

export const feedbackService = {
  getAllFeedbacks: async (params = {}) => {
    try {
      const response = await api.admin.getAllFeedbacks(params);
      if (!response) {
        throw new Error('评价响应为空');
      }

      const listRaw =
        response.list ||
        response.data?.list ||
        response.data ||
        [];

      if (!Array.isArray(listRaw)) {
        throw new Error('评价响应格式不正确');
      }

      return listRaw.map(item => ({
        id: item.ratingId ?? item.id,
        rating: item.score ?? item.rating ?? 0,
        comment: item.comment ?? '',
        studentId: item.studentId ?? item.studentID,
        studentName: item.studentName ?? null,
        repairmanId: item.staffId ?? item.repairmanId,
        repairmanName: item.staffName ?? item.repairmanName ?? null,
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
        followUpStatus: item.followUpStatus ?? null,
        followUpNote: item.followUpNote ?? '',
        followUpOperatorId: item.followUpOperatorId ?? null,
        followUpOperatorName: item.followUpOperatorName ?? null,
        followUpUpdatedAt: item.followUpUpdatedAt ?? null,
        createdAt: item.ratedAt ?? item.createdAt ?? item.created_at,
      }));
    } catch (error) {
      console.error('加载评价失败:', error);
      message.error('加载评价失败');
      return [];
    }
  },

  deleteFeedback: async (feedbackId) => {
    try {
      await api.admin.deleteFeedback(feedbackId);
      message.success('评价已删除');
      return true;
    } catch (error) {
      console.error('删除评价失败:', error);
      message.error('删除评价失败：' + error.message);
      throw error;
    }
  },

  updateFollowUp: async (feedbackId, data) => {
    try {
      await api.admin.updateFeedbackFollowUp(feedbackId, data);
      message.success('回访记录已更新');
      return true;
    } catch (error) {
      console.error('更新回访记录失败:', error);
      message.error('更新回访记录失败：' + error.message);
      throw error;
    }
  },

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
