import api from '../services/api';
import { message } from 'antd';

export const feedbackService = {
  getAllFeedbacks: async (params = {}) => {
    try {
      const response = await api.admin.getAllFeedbacks(params);
      if (!response) {
        throw new Error('Empty feedback response');
      }

      const listRaw =
        response.list ||
        response.data?.list ||
        response.data ||
        [];

      if (!Array.isArray(listRaw)) {
        throw new Error('Invalid feedback response format');
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
      console.error('Load feedbacks failed:', error);
      message.error('Load feedbacks failed');
      return [];
    }
  },

  deleteFeedback: async (feedbackId) => {
    try {
      await api.admin.deleteFeedback(feedbackId);
      message.success('Feedback deleted');
      return true;
    } catch (error) {
      console.error('Delete feedback failed:', error);
      message.error('Delete feedback failed: ' + error.message);
      throw error;
    }
  },

  updateFollowUp: async (feedbackId, data) => {
    try {
      await api.admin.updateFeedbackFollowUp(feedbackId, data);
      message.success('Follow-up updated');
      return true;
    } catch (error) {
      console.error('Update follow-up failed:', error);
      message.error('Update follow-up failed: ' + error.message);
      throw error;
    }
  },

  getRepairmanInfo: (repairmanId) => {
    const repairmen = {
      1: { id: 1, name: 'Repairman Zhang' },
      2: { id: 2, name: 'Repairman Li' },
      3: { id: 3, name: 'Repairman Wang' },
      4: { id: 4, name: 'Repairman Zhao' },
    };
    return repairmen[repairmanId] || { id: repairmanId, name: 'Unknown repairman' };
  },
};
