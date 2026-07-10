import React, { useState, useEffect, useCallback } from 'react';
import {
  Row, Col, Card, Table, Tag, Skeleton, Statistic, Alert, Button, Space,
  Tabs, Progress, Descriptions, message, Badge, Timeline, Empty
} from 'antd';
import {
  ReloadOutlined, ThunderboltOutlined, DashboardOutlined,
  TrophyOutlined, ClockCircleOutlined, ExperimentOutlined,
  ApiOutlined, ToolOutlined, CheckCircleOutlined, SyncOutlined
} from '@ant-design/icons';
import api from '../services/api';

const handleApiResponse = async (apiCall) => {
  const response = await apiCall();
  if (response && response.code === 200) {
    return response.data;
  }
  throw new Error(response?.message || '请求失败');
};

const formatTime = (val) => {
  if (!val) return '-';
  try {
    const d = new Date(val);
    if (!isNaN(d.getTime())) {
      return d.toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit',
      });
    }
  } catch (e) { /* ignore */ }
  return val;
};

const AdminAnalysis = () => {
  const [activeKey, setActiveKey] = useState('dashboard');
  const [loading, setLoading] = useState(false);
  const [dashboard, setDashboard] = useState(null);
  const [efficiency, setEfficiency] = useState([]);
  const [progressTracking, setProgressTracking] = useState(null);
  const [aiStatus, setAiStatus] = useState(null);

  const loadAllData = useCallback(async () => {
    setLoading(true);
    try {
      const [dashData, effData, progData, aiStatusData] = await Promise.all([
        handleApiResponse(() => api.admin.getAnalysisDashboard()).catch(() => null),
        handleApiResponse(() => api.admin.getAnalysisEfficiency()).catch(() => []),
        handleApiResponse(() => api.admin.getAnalysisProgressTracking()).catch(() => null),
        handleApiResponse(() => api.admin.getAiStatus()).catch(() => null),
      ]);
      setDashboard(dashData);
      setEfficiency(Array.isArray(effData) ? effData : []);
      setProgressTracking(progData);
      setAiStatus(aiStatusData);
    } catch (error) {
      console.error('加载分析数据失败:', error);
      message.error('加载分析数据失败: ' + (error.message || '未知错误'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAllData();
  }, [loadAllData]);

  useEffect(() => {
    const interval = setInterval(() => {
      loadAllData();
    }, 60000); // 每60秒刷新
    return () => clearInterval(interval);
  }, [loadAllData]);

  const tabItems = [
    {
      key: 'dashboard',
      label: <span><DashboardOutlined /> 分析仪表盘</span>,
      children: <DashboardTab dashboard={dashboard} loading={loading} />,
    },
    {
      key: 'efficiency',
      label: <span><TrophyOutlined /> 维修效率</span>,
      children: <EfficiencyTab efficiency={efficiency} loading={loading} />,
    },
    {
      key: 'progress',
      label: <span><SyncOutlined /> 处理进度</span>,
      children: <ProgressTab progressTracking={progressTracking} loading={loading} />,
    },
    {
      key: 'ai',
      label: <span><ExperimentOutlined /> AI增强分析</span>,
      children: <AiEnhanceTab aiStatus={aiStatus} loading={loading} />,
    },
  ];

  return (
    <div className="analytics-page">
      <div className="analytics-header">
        <div>
          <div className="page-hero-eyebrow">智慧运维</div>
          <h2>智慧运维分析中心</h2>
          <p>集数据仪表盘、效率分析、进度追踪和AI增强分析于一体的管理决策平台</p>
        </div>
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          onClick={loadAllData}
          loading={loading}
          style={{ backgroundColor: '#0F52BA', borderColor: '#0F52BA' }}
        >
          刷新数据
        </Button>
      </div>

      <Card variant="borderless">
        <Tabs activeKey={activeKey} onChange={setActiveKey} items={tabItems} />
      </Card>
    </div>
  );
};

// ===== 分析仪表盘 Tab =====
const DashboardTab = ({ dashboard, loading }) => {
  if (loading && !dashboard) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  if (!dashboard) {
    return <Empty description="暂无数据，请刷新重试" />;
  }

  const categoryData = dashboard.categoryDistribution || [];
  const highFreq = dashboard.highFrequencyFaults || [];
  const monthlyData = dashboard.monthlyTrend || [];
  const aiInsights = dashboard.aiInsights || {};

  return (
    <div>
      {/* 概览统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="工单总数" value={dashboard.totalTickets} suffix="条"
              valueStyle={{ color: '#1890ff', fontSize: 22 }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="待受理" value={dashboard.pendingTickets} suffix="条"
              valueStyle={{ color: '#faad14', fontSize: 22 }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="处理中" value={dashboard.inProgressTickets} suffix="条"
              valueStyle={{ color: '#1890ff', fontSize: 22 }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="已解决" value={dashboard.resolvedTickets} suffix="条"
              valueStyle={{ color: '#52c41a', fontSize: 22 }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="平均处理" value={dashboard.avgProcessingDisplay}
              valueStyle={{ fontSize: 18 }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card size="small">
            <Statistic title="SLA达标率" value={dashboard.slaComplianceRate} suffix="%"
              valueStyle={{ color: dashboard.slaComplianceRate >= 80 ? '#52c41a' : '#faad14', fontSize: 22 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* 故障分类分布 */}
        <Col xs={24} md={12}>
          <Card size="small" title="故障分类分布">
            <Table
              size="small"
              dataSource={categoryData}
              rowKey={(r) => r.type || r.name}
              pagination={false}
              columns={[
                { title: '分类', dataIndex: 'type', key: 'type', ellipsis: true },
                {
                  title: '数量', dataIndex: 'value', key: 'value', width: 80,
                  render: (v) => <Tag color="blue">{v}</Tag>,
                },
                {
                  title: '占比', key: 'percent', width: 80,
                  render: (_, r) => {
                    const total = categoryData.reduce((s, i) => s + (i.value || 0), 0);
                    const pct = total > 0 ? ((r.value / total) * 100).toFixed(1) : 0;
                    return <Progress percent={Number(pct)} size="small" />;
                  },
                },
              ]}
            />
          </Card>
        </Col>

        {/* 高频故障 Top 5 */}
        <Col xs={24} md={12}>
          <Card size="small" title="高频故障 Top 5">
            <Table
              size="small"
              dataSource={highFreq}
              rowKey={(r) => r.fault}
              pagination={false}
              columns={[
                { title: '故障描述', dataIndex: 'fault', key: 'fault', ellipsis: true },
                {
                  title: '次数', dataIndex: 'count', key: 'count', width: 80,
                  render: (v, _, idx) => (
                    <Tag color={idx === 0 ? 'red' : idx === 1 ? 'orange' : 'blue'}>{v}</Tag>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>

      {/* AI 分析预留提示 */}
      <Card
        size="small"
        style={{ marginTop: 16, background: '#f0f5ff', border: '1px solid #adc6ff' }}
      >
        <Space>
          <ExperimentOutlined style={{ color: '#597ef7', fontSize: 20 }} />
          <div>
            <strong>AI增强分析预留</strong>
            <div style={{ color: '#666', fontSize: 12 }}>
              {aiInsights.message || '后续接入DeepSeek等大模型，可自动识别故障根因、预测工单趋势、推荐维修方案。'}
            </div>
          </div>
          <Tag color="processing">PENDING</Tag>
        </Space>
      </Card>
    </div>
  );
};

// ===== 维修效率 Tab =====
const EfficiencyTab = ({ efficiency, loading }) => {
  if (loading && !efficiency.length) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="维修效率评分基于完成率、处理速度和用户评分综合计算"
        description="综合效率分 = 完成率×30% + 处理速度×30% + 用户评分×40%"
      />

      <Table
        dataSource={efficiency}
        rowKey={(r) => r.staffId}
        pagination={{ pageSize: 10 }}
        columns={[
          {
            title: '排名', key: 'rank', width: 60,
            render: (_, __, idx) => {
              const colors = { 0: 'gold', 1: 'silver', 2: 'bronze' };
              return <Tag color={colors[idx] || 'default'}>{idx + 1}</Tag>;
            },
          },
          {
            title: '维修人员', dataIndex: 'staffName', key: 'staffName', width: 120,
          },
          {
            title: '综合效率分', dataIndex: 'efficiencyScore', key: 'efficiencyScore', width: 120,
            sorter: (a, b) => a.efficiencyScore - b.efficiencyScore,
            render: (v) => (
              <Progress
                percent={Math.round(v)}
                size="small"
                strokeColor={v >= 80 ? '#52c41a' : v >= 60 ? '#faad14' : '#ff4d4f'}
                format={() => `${v}分`}
              />
            ),
          },
          {
            title: '完成率', dataIndex: 'completionRate', key: 'completionRate', width: 100,
            sorter: (a, b) => a.completionRate - b.completionRate,
            render: (v) => `${v}%`,
          },
          {
            title: '已完成', dataIndex: 'completedTickets', key: 'completedTickets', width: 90,
            render: (v) => <Tag color="green">{v} 单</Tag>,
          },
          {
            title: '进行中', dataIndex: 'activeTickets', key: 'activeTickets', width: 90,
            render: (v) => <Tag color={v > 3 ? 'red' : 'orange'}>{v} 单</Tag>,
          },
          {
            title: '平均处理时长', dataIndex: 'avgProcessingDisplay', key: 'avgProcessingDisplay', width: 130,
            sorter: (a, b) => a.avgProcessingHours - b.avgProcessingHours,
          },
          {
            title: '平均评分', dataIndex: 'avgRating', key: 'avgRating', width: 100,
            render: (v) => (
              <Tag color={v >= 4.5 ? 'green' : v >= 3.5 ? 'blue' : 'orange'}>
                {v > 0 ? `${v} 分` : '暂无'}
              </Tag>
            ),
          },
        ]}
      />
    </div>
  );
};

// ===== 处理进度 Tab =====
const ProgressTab = ({ progressTracking, loading }) => {
  if (loading && !progressTracking) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  if (!progressTracking) {
    return <Empty description="暂无工单数据" />;
  }

  const summary = progressTracking.summary || {};
  const waitingAccept = progressTracking.waitingAccept || [];
  const inProgress = progressTracking.inProgress || [];
  const statusCount = progressTracking.statusCount || {};

  return (
    <div>
      {/* 进度概览 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title="活跃工单总数" value={summary.totalActive || 0} suffix="条"
              valueStyle={{ color: '#1890ff', fontSize: 24 }} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title="待接单"
              value={<span style={{ color: '#faad14' }}>{summary.waitingAcceptCount || 0}</span>}
              suffix="条" />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title="维修中"
              value={<span style={{ color: '#1890ff' }}>{summary.inProgressCount || 0}</span>}
              suffix="条" />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        {/* 待接单工单 */}
        <Col xs={24} md={12}>
          <Card
            size="small"
            title={<span><ClockCircleOutlined /> 待接单工单 ({waitingAccept.length})</span>}
          >
            {waitingAccept.length > 0 ? (
              <Table
                size="small"
                dataSource={waitingAccept}
                rowKey={(r) => r.ticketId}
                pagination={{ pageSize: 5 }}
                columns={[
                  { title: 'ID', dataIndex: 'ticketId', key: 'ticketId', width: 60 },
                  { title: '分类', dataIndex: 'categoryName', key: 'categoryName', width: 90 },
                  { title: '位置', dataIndex: 'locationText', key: 'locationText', ellipsis: true },
                  {
                    title: '紧急', dataIndex: 'priority', key: 'priority', width: 70,
                    render: (v) => {
                      const colors = { high: 'red', medium: 'orange', low: 'blue' };
                      return <Tag color={colors[v] || 'default'}>{v || '普通'}</Tag>;
                    },
                  },
                  {
                    title: '提交时间', dataIndex: 'createdAt', key: 'createdAt', width: 140,
                    render: formatTime,
                  },
                ]}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待接单工单" />
            )}
          </Card>
        </Col>

        {/* 维修中工单 */}
        <Col xs={24} md={12}>
          <Card
            size="small"
            title={<span><ToolOutlined /> 维修中工单 ({inProgress.length})</span>}
          >
            {inProgress.length > 0 ? (
              <Table
                size="small"
                dataSource={inProgress}
                rowKey={(r) => r.ticketId}
                pagination={{ pageSize: 5 }}
                columns={[
                  { title: 'ID', dataIndex: 'ticketId', key: 'ticketId', width: 60 },
                  { title: '分类', dataIndex: 'categoryName', key: 'categoryName', width: 90 },
                  {
                    title: '维修员', dataIndex: 'staffId', key: 'staffId', width: 100,
                    render: (v) => v || '未分配',
                  },
                  {
                    title: '分配时间', dataIndex: 'assignedAt', key: 'assignedAt', width: 140,
                    render: formatTime,
                  },
                ]}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无维修中工单" />
            )}
          </Card>
        </Col>
      </Row>

      {/* 全部状态统计 */}
      <Card size="small" title="全部状态统计" style={{ marginTop: 16 }}>
        <Row gutter={[8, 8]}>
          {Object.entries(statusCount).map(([status, count]) => (
            <Col key={status} xs={12} sm={8} md={6} lg={4}>
              <Tag style={{ marginBottom: 4 }}>{status}</Tag>
              <Badge count={count} showZero style={{ backgroundColor: '#1677ff' }} />
            </Col>
          ))}
        </Row>
      </Card>
    </div>
  );
};

// ===== AI增强分析 Tab =====
const AiEnhanceTab = ({ aiStatus, loading }) => {
  const [analysisType, setAnalysisType] = useState('fault_analysis');
  const [aiResult, setAiResult] = useState(null);
  const [analyzing, setAnalyzing] = useState(false);

  const handleAiEnhance = async () => {
    setAnalyzing(true);
    try {
      const response = await api.admin.aiEnhance({
        analysisType,
        useKnowledgeBase: true,
        temperature: 0.7,
      });
      if (response?.code === 200) {
        setAiResult(response.data);
        message.success('分析完成');
      }
    } catch (error) {
      message.error('AI分析请求失败: ' + (error.message || '未知错误'));
    } finally {
      setAnalyzing(false);
    }
  };

  const capabilities = aiStatus?.capabilities || [
    '故障根因分析', '维修效率优化建议', '工单趋势预测', '设施健康评估', '知识库智能匹配'
  ];

  return (
    <div>
      {/* AI 状态卡片 */}
      <Card
        size="small"
        title={<span><ApiOutlined /> AI增强分析服务状态</span>}
        style={{ marginBottom: 16 }}
      >
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="服务状态">
            <Tag color={aiStatus?.aiEnabled ? 'green' : 'gold'}>
              {aiStatus?.aiEnabled ? '已激活' : '待配置（预留）'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="模型接口">
            {aiStatus?.modelProvider || 'DeepSeek/OpenAI兼容（预留）'}
          </Descriptions.Item>
          <Descriptions.Item label="当前状态">
            {aiStatus?.status || 'PENDING'}
          </Descriptions.Item>
          <Descriptions.Item label="AI能力数量">
            {capabilities.length} 项
          </Descriptions.Item>
        </Descriptions>

        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          message={aiStatus?.message || 'AI增强分析模块已预留，配置API Key后即可激活'}
        />
      </Card>

      {/* AI 能力列表 */}
      <Card size="small" title="预留AI分析能力" style={{ marginBottom: 16 }}>
        <Row gutter={[8, 8]}>
          {capabilities.map((cap) => (
            <Col key={cap}>
              <Tag color="blue" style={{ fontSize: 14, padding: '4px 12px' }}>
                <ThunderboltOutlined /> {cap}
              </Tag>
            </Col>
          ))}
        </Row>
      </Card>

      {/* 分析类型选择与结果展示 */}
      <Card size="small" title="规则引擎分析（AI增强前端）">
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space wrap>
            <span>分析类型：</span>
            {[
              { key: 'fault_analysis', label: '故障分析' },
              { key: 'efficiency_analysis', label: '效率分析' },
              { key: 'trend_prediction', label: '趋势预测' },
              { key: 'hotspot_analysis', label: '热点分析' },
            ].map(({ key, label }) => (
              <Button
                key={key}
                type={analysisType === key ? 'primary' : 'default'}
                onClick={() => setAnalysisType(key)}
                size="small"
              >
                {label}
              </Button>
            ))}
          </Space>
          <Button
            type="primary"
            icon={<ExperimentOutlined />}
            onClick={handleAiEnhance}
            loading={analyzing}
          >
            执行分析
          </Button>
        </Space>

        {aiResult && (
          <Card
            size="small"
            style={{ marginTop: 16, background: '#f6ffed', border: '1px solid #b7eb8f' }}
            title={`分析结果: ${aiResult.analysisType}`}
          >
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="状态">
                <Tag color={aiResult.status === 'READY' ? 'green' : 'gold'}>{aiResult.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="说明">{aiResult.message}</Descriptions.Item>
              <Descriptions.Item label="数据来源">
                {aiResult.result?.dataSource || '规则引擎'}
              </Descriptions.Item>
              <Descriptions.Item label="模型">
                {aiResult.modelInfo || '预留'}
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 12 }}>
              <pre style={{
                background: '#f5f5f5',
                padding: 16,
                borderRadius: 6,
                maxHeight: 300,
                overflow: 'auto',
                fontSize: 12,
              }}>
                {JSON.stringify(aiResult.result, null, 2)}
              </pre>
            </div>
          </Card>
        )}
      </Card>
    </div>
  );
};

export default AdminAnalysis;
