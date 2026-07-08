// src/services/statisticsService.js
import api from '../services/api';

// 统一的响应处理函数
const handleApiResponse = async (apiCall) => {
  try {
    const response = await apiCall();

    console.log('========================================');
    console.log('API响应:', response);
    console.log('========================================');

    if (response && response.code === 200) {
      return response.data;
    } else {
      throw new Error(response?.message || '请求失败');
    }
  } catch (error) {
    // 直接向上抛出，由调用方决定如何处理
    throw error;
  }
};

export const statisticsService = {
  // 获取报修分类统计
  getRepairCategoryStats: async () => {
    try {
      console.log('========================================');
      console.log('开始获取报修分类统计...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsCategory());

      console.log('分类统计原始数据:', data);

      if (data) {
        // 转换数据格式以适配图表组件
        const categoryData = Array.isArray(data) ? data.map(item => {
          // 后端视图返回的详细字段：
          // category, name, type, count/value, totalTickets, completedTickets, ratedTickets, avgRating
          const total = Number(
            item.totalTickets ||
            item.total_tickets ||
            item.count ||
            item.value ||
            0
          );
          const avgRating = typeof item.avgRating !== 'undefined'
            ? Number(item.avgRating)
            : (item.avg_rating ? Number(item.avg_rating) : 0);
          const completed = Number(item.completedTickets || item.completed_tickets || 0);
          const rated = Number(item.ratedTickets || item.rated_tickets || 0);

          console.log(`分类 "${item.category || item.name || item.type}": 总数=${total}, 已完成=${completed}, 已评价=${rated}, 平均分=${avgRating}`);

          return {
            type: item.category || item.name || item.type,
            value: total,
            totalTickets: total,
            avgRating,
            completedTickets: completed,
            ratedTickets: rated,
          };
        }) : [];

        console.log('========================================');
        console.log('分类统计转换结果:', categoryData);
        console.log(`验证: 总报修数 = ${categoryData.reduce((sum, item) => sum + item.value, 0)}`);
        console.log('========================================');

        return categoryData;
      }

      return [];
    } catch (error) {
      console.error('========================================');
      console.error('获取分类统计失败:', error);
      console.error('========================================');
      return [];
    }
  },

  // 获取位置报修数量排行
  getLocationRepairStats: async () => {
    try {
      console.log('========================================');
      console.log('开始获取位置报修统计...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsLocation());

      console.log('位置统计原始数据:', data);

      if (data) {
        // 转换数据格式以适配图表组件
        const locationData = Array.isArray(data) ? data.map(item => {
          const location = item.location || item.name;
          const count = Number(item.count || item.value || 0);
          console.log(`位置 "${location}": 报修次数=${count}`);
          return { location, count };
        }) : [];

        console.log('========================================');
        console.log('位置统计转换结果:', locationData);
        console.log(`验证: 总报修数 = ${locationData.reduce((sum, item) => sum + item.count, 0)}`);
        console.log('========================================');

        return locationData;
      }

      return [];
    } catch (error) {
      console.error('========================================');
      console.error('获取位置统计失败:', error);
      console.error('========================================');
      return [];
    }
  },

  // 获取维修人员评分排行
  getRepairmanRatingStats: async () => {
    try {
      console.log('========================================');
      console.log('开始获取维修人员评分统计...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsRepairmanRating());

      console.log('维修人员评分原始数据:', data);

      if (data) {
        // 转换数据格式以适配表格组件
        const ratingData = Array.isArray(data) ? data.map(item => {
          const id = item.id || Math.random();
          const name = item.name;
          const rating = parseFloat(item.rating) || 0;
          const completedOrders = Number(item.completedOrders || item.count || 0);

          console.log(`维修工 "${name}": ID=${id}, 评分=${rating}, 完成工单=${completedOrders}`);

          return { id, name, rating, completedOrders };
        }) : [];

        console.log('========================================');
        console.log('维修人员评分转换结果:', ratingData);
        console.log('========================================');

        return ratingData;
      }

      return [];
    } catch (error) {
      console.error('========================================');
      console.error('获取评分统计失败:', error);
      console.error('========================================');
      return [];
    }
  },

  // 获取总体统计数据 - 改为完全动态计算
  getOverallStats: async () => {
    try {
      console.log('========================================');
      console.log('开始计算总体统计数据...');
      console.log('========================================');

      // 获取分类统计来计算总数
      const categoryStats = await statisticsService.getRepairCategoryStats();
      const totalRepairs = categoryStats.reduce((sum, item) => sum + item.value, 0);
      console.log(`总报修数（从分类统计汇总）: ${totalRepairs}`);

      // 获取维修人员评分统计来计算平均满意度
      const ratingStats = await statisticsService.getRepairmanRatingStats();
      let avgRating = 0;
      let totalCompleted = 0;
      if (ratingStats && ratingStats.length > 0) {
        // 计算加权平均评分（按完成工单数加权）
        let totalWeightedRating = 0;
        let totalOrders = 0;
        ratingStats.forEach(item => {
          if (item.completedOrders > 0) {
            totalWeightedRating += item.rating * item.completedOrders;
            totalOrders += item.completedOrders;
            console.log(`维修工 "${item.name}": 评分=${item.rating}, 完成工单=${item.completedOrders}, 加权贡献=${item.rating * item.completedOrders}`);
          }
        });
        avgRating = totalOrders > 0 ? totalWeightedRating / totalOrders : 0;
        totalCompleted = totalOrders;
        console.log(`加权平均评分计算: 总加权评分=${totalWeightedRating}, 总完成工单=${totalOrders}, 平均评分=${avgRating.toFixed(2)}`);
      }

      // 用户满意度 = 平均评分 / 5 * 100%
      const userSatisfaction = avgRating > 0 ? `${(avgRating / 5 * 100).toFixed(1)}%` : '暂无数据';
      console.log(`用户满意度: ${userSatisfaction} (基于平均评分 ${avgRating.toFixed(2)} / 5)`);

      // 平均处理时间：从后端API获取实际数据
      let avgProcessingTime = '暂无数据';
      try {
        const processingTimeData = await handleApiResponse(() => api.admin.getStatsProcessingTime());
        console.log('平均处理时间数据:', processingTimeData);

        if (processingTimeData && processingTimeData.displayText) {
          avgProcessingTime = processingTimeData.displayText;
          console.log(`平均处理时间: ${avgProcessingTime} (基于 ${processingTimeData.completedTickets || 0} 个已完成工单)`);
        }
      } catch (error) {
        console.warn('获取平均处理时间失败，使用默认值:', error.message);
        avgProcessingTime = totalCompleted > 0 ? '约 3 天' : '暂无数据';
      }

      console.log('========================================');
      console.log('总体统计结果:', { totalRepairs, avgProcessingTime, userSatisfaction });
      console.log('========================================');

      return {
        totalRepairs,
        avgProcessingTime,
        userSatisfaction
      };
    } catch (error) {
      console.error('========================================');
      console.error('获取总体统计失败:', error);
      console.error('========================================');
      return {
        totalRepairs: 0,
        avgProcessingTime: '暂无数据',
        userSatisfaction: '暂无数据'
      };
    }
  },

  // 新增：获取工单状态统计
  getOrderStatusStats: async () => {
    try {
      console.log('========================================');
      console.log('开始获取工单状态统计...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsOrderStatus());

      console.log('工单状态统计原始数据:', data);

      if (data) {
        // 转换数据格式以适配图表组件
        const statusData = Array.isArray(data) ? data.map(item => {
          const status = item.status;
          const value = Number(item.count || item.value || 0);
          console.log(`状态 "${status}": 工单数=${value}`);
          return { status, value };
        }) : [];

        console.log('========================================');
        console.log('工单状态统计转换结果:', statusData);
        console.log(`验证: 总工单数 = ${statusData.reduce((sum, item) => sum + item.value, 0)}`);
        console.log('========================================');

        return statusData;
      }

      return [];
    } catch (error) {
      console.error('========================================');
      console.error('获取工单状态统计失败:', error);
      console.error('========================================');
      return [];
    }
  },

  // 新增：获取月度统计数据
  getMonthlyStats: async () => {
    try {
      console.log('========================================');
      console.log('开始获取月度统计...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsMonthly());

      console.log('月度统计原始数据:', data);

      if (data) {
        // 后端返回结构：{ months: [...], statusDistribution: {...} }
        const monthsArray = Array.isArray(data.months) ? data.months : [];

        const monthlyData = monthsArray.map(item => {
          const month = item.month || item.date;
          const orders = Number(item.count || item.orders || item.value || 0);
          console.log(`月份 "${month}": 工单数=${orders}`);
          return { month, orders };
        });

        console.log('========================================');
        console.log('月度统计转换结果:', monthlyData);
        console.log(`验证: 总工单数 = ${monthlyData.reduce((sum, item) => sum + item.orders, 0)}`);
        console.log('========================================');

        return monthlyData;
      }

      return [];
    } catch (error) {
      console.error('========================================');
      console.error('获取月度统计失败:', error);
      console.error('========================================');
      return [];
    }
  },

  // 新增：获取热点问题分析
  getHotspotAnalysis: async () => {
    try {
      console.log('========================================');
      console.log('开始获取热点问题分析...');
      console.log('========================================');

      const data = await handleApiResponse(() => api.admin.getStatsHotspot());
      console.log('热点问题分析原始数据:', data);

      return {
        hotAreas: Array.isArray(data?.hotAreas) ? data.hotAreas.map(item => ({
          area: item.area || '未知区域',
          totalTickets: Number(item.totalTickets || 0),
          activeTickets: Number(item.activeTickets || 0),
        })) : [],
        categoryGrowth: Array.isArray(data?.categoryGrowth) ? data.categoryGrowth.map(item => ({
          category: item.category || '未分类',
          recentCount: Number(item.recentCount || 0),
          previousCount: Number(item.previousCount || 0),
          growth: Number(item.growth || 0),
          growthRate: Number(item.growthRate || 0),
        })) : [],
        repeatedLocations: Array.isArray(data?.repeatedLocations) ? data.repeatedLocations.map(item => ({
          location: item.location || '未知位置',
          category: item.category || '未分类',
          totalTickets: Number(item.totalTickets || 0),
          activeTickets: Number(item.activeTickets || 0),
          lastCreatedAt: item.lastCreatedAt || null,
        })) : [],
        staffWorkload: Array.isArray(data?.staffWorkload) ? data.staffWorkload.map(item => ({
          staffId: item.staffId || '',
          staffName: item.staffName || '未知维修员',
          totalAssigned: Number(item.totalAssigned || 0),
          activeTickets: Number(item.activeTickets || 0),
          completedTickets: Number(item.completedTickets || 0),
        })) : [],
        categoryProcessingTime: Array.isArray(data?.categoryProcessingTime) ? data.categoryProcessingTime.map(item => ({
          category: item.category || '未分类',
          completedTickets: Number(item.completedTickets || 0),
          avgHours: Number(item.avgHours || 0),
          displayText: item.displayText || '暂无数据',
        })) : [],
        generatedAt: data?.generatedAt || null,
      };
    } catch (error) {
      console.error('========================================');
      console.error('获取热点问题分析失败:', error);
      console.error('========================================');
      return {
        hotAreas: [],
        categoryGrowth: [],
        repeatedLocations: [],
        staffWorkload: [],
        categoryProcessingTime: [],
        generatedAt: null,
      };
    }
  },

  // 新增：获取校园设施健康指数
  getFacilityHealth: async () => {
    try {
      console.log('开始获取校园设施健康指数...');
      const data = await handleApiResponse(() => api.admin.getStatsFacilityHealth());
      return {
        overallHealthScore: Number(data?.overallHealthScore || 100),
        overallRiskLevel: data?.overallRiskLevel || '健康',
        areaHealth: Array.isArray(data?.areaHealth) ? data.areaHealth : [],
        categoryRisk: Array.isArray(data?.categoryRisk) ? data.categoryRisk : [],
        suggestions: Array.isArray(data?.suggestions) ? data.suggestions : [],
        generatedAt: data?.generatedAt || null,
      };
    } catch (error) {
      console.error('获取校园设施健康指数失败:', error);
      return {
        overallHealthScore: 100,
        overallRiskLevel: '健康',
        areaHealth: [],
        categoryRisk: [],
        suggestions: [],
        generatedAt: null,
      };
    }
  },
};
