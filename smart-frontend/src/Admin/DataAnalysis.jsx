import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Row, Col, Card, Table, Tag, Skeleton, Statistic, Alert, Button, Space, Popconfirm, message, Descriptions } from 'antd';
import { Line } from '@ant-design/charts';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { statisticsService } from './statisticsService';
import { TASK_STATUS } from '../Worker/mytaskService';
import { backupService } from './backupService';
import api from '../services/api';

const chartColors = ['#0f62fe', '#00c2d1', '#7c3aed', '#16a34a', '#f59e0b', '#ef4444', '#64748b'];

const polarToCartesian = (center, radius, angleInDegrees) => {
  const angleInRadians = (angleInDegrees - 90) * Math.PI / 180;
  return {
    x: center + (radius * Math.cos(angleInRadians)),
    y: center + (radius * Math.sin(angleInRadians)),
  };
};

const describeArc = (center, radius, startAngle, endAngle) => {
  const start = polarToCartesian(center, radius, endAngle);
  const end = polarToCartesian(center, radius, startAngle);
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';
  return [
    'M', start.x, start.y,
    'A', radius, radius, 0, largeArcFlag, 0, end.x, end.y,
  ].join(' ');
};

const getChartPercent = (value, total) => total > 0 ? (value / total * 100) : 0;

const DataAnalysis = () => {
  const [categoryData, setCategoryData] = useState([]);
  const [locationData, setLocationData] = useState([]);
  const [ratingData, setRatingData] = useState([]);
  const [statusData, setStatusData] = useState([]);
  const [monthlyData, setMonthlyData] = useState([]); // 新增：月度统计数据
  const [hotspotData, setHotspotData] = useState({
    hotAreas: [],
    categoryGrowth: [],
    repeatedLocations: [],
    staffWorkload: [],
    categoryProcessingTime: [],
    generatedAt: null,
  });
  const [facilityHealth, setFacilityHealth] = useState({
    overallHealthScore: 100,
    overallRiskLevel: '健康',
    areaHealth: [],
    categoryRisk: [],
    suggestions: [],
    generatedAt: null,
  });
  const [faultTrends, setFaultTrends] = useState({
    trends: [],
    alerts: [],
    generatedAt: null,
    sevenDayThreshold: 3,
    thirtyDayThreshold: 6,
  });
  const [overallStats, setOverallStats] = useState({
    totalRepairs: 0,
    avgProcessingTime: '暂无数据',
    userSatisfaction: '暂无数据'
  });
  const [loading, setLoading] = useState(true);
  const [backupLoading, setBackupLoading] = useState(false);
  const [exportLoading, setExportLoading] = useState(false);
  const [backupList, setBackupList] = useState([]);
  const [backupStatus, setBackupStatus] = useState(null);
  const [lastUpdateTime, setLastUpdateTime] = useState(null); // 新增：记录最后更新时间

  // 加载统计数据 - 使用 useCallback 包装
  const loadStatistics = useCallback(async () => {
    console.log('========================================');
    console.log('管理员端数据统计 - 开始加载统计数据...');
    console.log('当前时间:', new Date().toLocaleString());
    console.log('========================================');

    setLoading(true);
    setLastUpdateTime(new Date());

    try {
      // 新增：获取月度统计数据
      const [categoryStats, locationStats, ratingStats, statusStats, monthlyStats, overallStatsData, hotspotStats, facilityHealthStats, faultTrendStats] = await Promise.all([
        statisticsService.getRepairCategoryStats(),
        statisticsService.getLocationRepairStats(),
        statisticsService.getRepairmanRatingStats(),
        statisticsService.getOrderStatusStats(),
        statisticsService.getMonthlyStats(), // 新增：获取月度统计
        statisticsService.getOverallStats(),
        statisticsService.getHotspotAnalysis(),
        statisticsService.getFacilityHealth(),
        statisticsService.getFaultTrends()
      ]);

      console.log('========================================');
      console.log('管理员端数据统计 - 所有数据加载完成');
      console.log('分类统计数据:', categoryStats);
      console.log('位置统计数据:', locationStats);
      console.log('评分统计数据:', ratingStats);
      console.log('状态统计数据:', statusStats);
      console.log('月度统计数据:', monthlyStats);
      console.log('总体统计数据:', overallStatsData);
      console.log('热点问题分析:', hotspotStats);
      console.log('========================================');

      setCategoryData(categoryStats);
      setLocationData(locationStats);
      setRatingData(ratingStats);
      setStatusData(statusStats);
      setMonthlyData(monthlyStats); // 设置月度统计数据
      setOverallStats(overallStatsData);
      setHotspotData(hotspotStats);
      setFacilityHealth(facilityHealthStats);
      setFaultTrends(faultTrendStats);

      // 显示数据来源提示
      const dataSourceInfo = `
数据来源验证：
- 总报修数：${overallStatsData.totalRepairs} 条（从数据库repair_order表汇总）
- 报修分类：${categoryStats.length} 个分类（从数据库repair_order.category_key字段统计）
- 位置统计：${locationStats.length} 个位置（从数据库repair_order.location字段统计）
- 维修工评分：${ratingStats.length} 名维修工（从数据库repair_feedback表计算）
- 工单状态：${statusStats.length} 种状态（从数据库repair_order.status字段统计）
      `;
      console.log(dataSourceInfo);

      // 不再每次加载都弹提示，用户可以从图表和数据看到加载成功
      console.log('统计数据加载成功（数据来源：数据库实时查询）');

    } catch (error) {
      console.error('========================================');
      console.error('管理员端数据统计 - 加载统计数据失败:', error);
      console.error('错误消息:', error.message);
      console.error('错误堆栈:', error.stack);
      console.error('========================================');

      message.error(`加载统计数据失败: ${error.message}`);
    } finally {
      setLoading(false);
      console.log('管理员端数据统计 - 加载完成，loading 状态设置为 false');
    }
  }, []);

  const loadBackups = async () => {
    setBackupLoading(true);
    try {
      const [list, status] = await Promise.all([
        backupService.list(),
        backupService.status().catch(() => null),
      ]);
      setBackupList(Array.isArray(list) ? list : []);
      setBackupStatus(status);
    } catch (error) {
      console.error('加载备份列表失败:', error);
    } finally {
      setBackupLoading(false);
    }
  };

  const handleCreateBackup = async () => {
    setBackupLoading(true);
    try {
      await backupService.create();
      await loadBackups();
    } catch (error) {
      console.error('手动备份失败:', error);
    } finally {
      setBackupLoading(false);
    }
  };

  const handleRestore = async (fileName) => {
    if (!fileName) return;
    setBackupLoading(true);
    try {
      await backupService.restore(fileName);
      message.success('恢复完成，系统已在恢复前创建保护备份，建议刷新页面并核对数据');
    } catch (error) {
      console.error('恢复失败:', error);
    } finally {
      setBackupLoading(false);
    }
  };

  const handleDelete = async (fileName) => {
    if (!fileName) {
      message.error('文件名不能为空');
      return;
    }

    setBackupLoading(true);

    try {
      await backupService.remove(fileName);
      await loadBackups();
    } catch (error) {
      console.error('删除备份失败:', error);
      message.error(`删除备份失败: ${error.message}`);
    } finally {
      setBackupLoading(false);
    }
  };

  const handleExportStats = async () => {
    setExportLoading(true);
    try {
      const result = await api.admin.exportData('stats', {});
      message.success(`统计数据已导出${result.rowCount ? `，共 ${result.rowCount} 行` : ''}`);
    } catch (error) {
      message.error(`导出统计失败: ${error.message}`);
    } finally {
      setExportLoading(false);
    }
  };

  useEffect(() => {
    console.log('========================================');
    console.log('管理员端数据统计 - 组件初始化');
    console.log('========================================');
    loadStatistics();
    loadBackups();
  }, [loadStatistics]);

  // 添加轮询刷新，每30秒刷新一次统计数据
  useEffect(() => {
    const interval = setInterval(() => {
      console.log('========================================');
      console.log('管理员端数据统计 - 轮询刷新（30秒）');
      console.log('当前时间:', new Date().toLocaleString());
      console.log('========================================');
      loadStatistics();
    }, 30000); // 30秒刷新一次

    return () => {
      console.log('管理员端数据统计 - 清除轮询定时器');
      clearInterval(interval);
    };
  }, [loadStatistics]);

  if (loading) {
    return (
      <div className="analytics-page analytics-loading">
        <div className="analytics-header">
          <div>
            <div className="page-hero-eyebrow">实时统计</div>
            <h2>图表分析驾驶舱</h2>
            <p>正在从数据库汇总工单、评价、区域热点和设施健康数据。</p>
          </div>
        </div>
        <Row gutter={[16, 16]}>
          {[1, 2, 3].map((item) => (
            <Col xs={24} md={8} key={item}>
              <Card>
                <Skeleton active paragraph={{ rows: 2 }} />
              </Card>
            </Col>
          ))}
          {[1, 2, 3, 4].map((item) => (
            <Col xs={24} md={12} key={`chart-${item}`}>
              <Card>
                <Skeleton active paragraph={{ rows: 6 }} />
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    );
  }

  return (
    <div className="analytics-page">
      <div className="analytics-header">
        <div>
          <div className="page-hero-eyebrow">图表分析</div>
          <h2>数据统计与分析</h2>
          <p>从工单池、评价反馈、维修负载和设施健康维度观察校园运维状态。</p>
        </div>

        <Space size="middle">
          {/* 显示最后更新时间 */}
          {lastUpdateTime && (
            <span style={{ fontSize: '12px', color: '#666' }}>
              最后更新: {lastUpdateTime.toLocaleString()}
            </span>
          )}

          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => {
              console.log('管理员端数据统计 - 手动刷新数据');
              loadStatistics();
            }}
            loading={loading}
            style={{
              backgroundColor: "#0F52BA",
              borderColor: "#0F52BA",
            }}
          >
            刷新数据
          </Button>
          <Button icon={<DownloadOutlined />} onClick={handleExportStats} loading={exportLoading}>
            导出统计 CSV
          </Button>
        </Space>
      </div>

      {/* 备份与恢复 */}
      <Card
        size="small"
        title="数据备份与恢复"
        style={{ marginBottom: 16 }}
        extra={
          <Space>
            <Button type="primary" onClick={handleCreateBackup} loading={backupLoading}>
              立即备份
            </Button>
            <Button onClick={loadBackups} loading={backupLoading}>
              刷新列表
            </Button>
          </Space>
        }
        >
        {backupStatus && (
          <Descriptions
            size="small"
            column={{ xs: 1, sm: 2, md: 4 }}
            style={{ marginBottom: 12 }}
            items={[
              { key: 'directory', label: '备份目录', children: backupStatus.directory || '-' },
              { key: 'retentionDays', label: '保留天数', children: `${backupStatus.retentionDays ?? '-'} 天` },
              { key: 'backupCount', label: '备份数量', children: `${backupStatus.backupCount ?? 0} 个` },
              { key: 'database', label: '数据库', children: backupStatus.database || 'repairdb' },
            ]}
          />
        )}
        <Table
          size="small"
          loading={backupLoading}
          dataSource={backupList}
          rowKey={(item) => item.fileName || item.file_path || item.file_name}
          pagination={{ pageSize: 5 }}
          columns={[
            { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
            { title: '备份时间', dataIndex: 'backupTime', key: 'backupTime', render: (t) => t || '-' },
            { title: '大小', dataIndex: 'fileSize', key: 'fileSize', render: (s) => formatSize(s) },
            {
              title: '操作',
              key: 'action',
              width: 200,
              render: (_, record) => (
                <Space>
                  <Popconfirm
                    title="确认恢复？"
                    description="恢复会覆盖当前数据，系统会先自动创建一份保护备份。"
                    onConfirm={() => handleRestore(record.fileName)}
                  >
                    <Button size="small" type="primary" danger loading={backupLoading}>
                      恢复
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title="确认删除此备份？"
                    onConfirm={() => handleDelete(record.fileName)}
                  >
                    <Button size="small" danger loading={backupLoading}>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
          locale={{ emptyText: '暂无备份记录' }}
        />
        <Alert
          type="warning"
          style={{ marginTop: 12 }}
          message="恢复操作会覆盖当前数据库数据；系统会在恢复前自动创建保护备份。"
          showIcon
        />
      </Card>
      
      {/* 统计摘要 */}
      <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
        <Col xs={24} sm={8}>
          <Card size="small" title="总报修数" variant="borderless">
            <Statistic
              value={overallStats.totalRepairs}
              valueStyle={{ color: '#1890ff', fontSize: '24px', fontWeight: 'bold' }}
              suffix="条"
            />
            <div style={{ fontSize: '12px', color: '#666', marginTop: '8px' }}>
              数据来源: repair_order表实时汇总
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small" title="平均处理时间" variant="borderless">
            <Statistic
              value={overallStats.avgProcessingTime}
              valueStyle={{ color: '#52c41a', fontSize: '24px', fontWeight: 'bold' }}
            />
            <div style={{ fontSize: '12px', color: '#666', marginTop: '8px' }}>
              {overallStats.avgProcessingTime === '暂无数据' ?
                '需要已完成工单数据' :
                '基于数据库repair_order表completed_at字段实时计算'
              }
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small" title="用户满意度" variant="borderless">
            <Statistic
              value={overallStats.userSatisfaction}
              valueStyle={{ color: '#fa541c', fontSize: '24px', fontWeight: 'bold' }}
            />
            <div style={{ fontSize: '12px', color: '#666', marginTop: '8px' }}>
              {overallStats.userSatisfaction === '暂无数据' ?
                '需要评价数据（repair_feedback表）' :
                '基于维修工评分加权平均计算'
              }
            </div>
          </Card>
        </Col>
      </Row>

      {/* 2×2田字格布局 */}
      <Row gutter={[16, 16]}>
        {/* 第一行第一列：报修分类占比 */}
        <Col xs={24} md={12}>
          <Card
            className="analytics-chart-card"
            title="报修分类占比" 
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                总计: {categoryData.reduce((sum, item) => sum + item.value, 0)} 次报修
              </div>
            }
          >
            <RepairCategoryPieChart data={categoryData} />
          </Card>
        </Col>

        {/* 第一行第二列：工单状态分布（新增） */}
        <Col xs={24} md={12}>
          <Card
            className="analytics-chart-card"
            title="工单状态分布" 
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                总计: {statusData.reduce((sum, item) => sum + item.value, 0)} 个工单
              </div>
            }
          >
            <OrderStatusPieChart data={statusData} />
          </Card>
        </Col>

        {/* 第二行第一列：具体位置报修数量排行 */}
        <Col xs={24} md={12}>
          <Card
            className="analytics-chart-card"
            title="位置报修数量排行" 
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                前{locationData.length}个位置报修统计
              </div>
            }
          >
            <LocationRepairColumnChart data={locationData} />
          </Card>
        </Col>

        {/* 第二行第二列：维修人员平均评分排行 */}
        <Col xs={24} md={12}>
          <Card
            className="analytics-chart-card"
            title="维修人员评分排行" 
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                基于用户评价计算的平均分
              </div>
            }
          >
            <RepairmanRatingTable data={ratingData} />
          </Card>
        </Col>
      </Row>

      {/* 新增：月度统计折线图，放在田字格下面 */}
      <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
        <Col xs={24}>
          <Card
            className="analytics-chart-card"
            title="月度统计" 
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                近六个月工单数量统计
              </div>
            }
          >
            <MonthlyLineChart data={monthlyData} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
        <Col xs={24}>
          <Card
            className="analytics-chart-card analytics-composite-card"
            title="热点问题分析"
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                高频区域、异常增长、重复报修和任务负载实时分析
              </div>
            }
          >
            <HotspotAnalysis data={hotspotData} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
        <Col xs={24}>
          <Card
            className="analytics-chart-card analytics-composite-card"
            title="高频故障趋势与AI预警"
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                近7天/30天按地点和分类识别异常增长
              </div>
            }
          >
            <FaultTrendAlertPanel data={faultTrends} onChange={setFaultTrends} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
        <Col xs={24}>
          <Card
            className="analytics-chart-card analytics-composite-card"
            title="校园设施健康指数"
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                基于报修频次、未闭环工单、重复报修、满意度和处理时长综合评分
              </div>
            }
          >
            <FacilityHealthPanel data={facilityHealth} />
          </Card>
        </Col>
      </Row>

      {/* 新增：SLA超时告警面板 */}
      <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
        <Col xs={24}>
          <Card
            className="analytics-chart-card analytics-composite-card"
            title="SLA超时告警"
            variant="borderless"
            extra={
              <div style={{ fontSize: '12px', color: '#666' }}>
                受理时限和完成时限的实时监控与告警
              </div>
            }
          >
            <SlaAlertPanel />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

const FaultTrendAlertPanel = ({ data, onChange }) => {
  const [refreshing, setRefreshing] = useState(false);
  const trends = data?.trends || [];
  const alerts = data?.alerts || [];

  const chartData = trends.map(item => ({
    scope: `${item.location}/${item.category}`,
    period: `${item.periodDays}天`,
    count: item.ticketCount,
  }));

  const riskColor = (risk) => ({
    CRITICAL: 'red',
    HIGH: 'volcano',
    MEDIUM: 'orange',
    LOW: 'green',
  }[risk] || 'default');

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const nextData = await statisticsService.refreshFaultTrends();
      onChange(nextData);
      message.success('高频故障趋势预警已刷新');
    } catch (error) {
      message.error(error.message || '刷新高频故障趋势失败');
    } finally {
      setRefreshing(false);
    }
  };

  const config = {
    data: chartData,
    xField: 'period',
    yField: 'count',
    seriesField: 'scope',
    smooth: true,
    height: 260,
    point: { size: 4 },
    tooltip: {
      formatter: (datum) => ({ name: datum.scope, value: `${datum.count} 单` }),
    },
    yAxis: {
      label: { formatter: (text) => `${text}单` },
    },
  };

  const columns = [
    { title: '地点', dataIndex: 'location', key: 'location', ellipsis: true },
    { title: '分类', dataIndex: 'category', key: 'category', width: 110 },
    { title: '周期', dataIndex: 'periodDays', key: 'periodDays', width: 80, render: (days) => `${days}天` },
    { title: '工单数', dataIndex: 'ticketCount', key: 'ticketCount', width: 90, render: (value) => `${value}单` },
    { title: '增长率', dataIndex: 'growthRate', key: 'growthRate', width: 100, render: (value) => `${value}%` },
    {
      title: '风险',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 100,
      render: (risk) => <Tag color={riskColor(risk)}>{risk}</Tag>,
    },
    { title: '原因解释', dataIndex: 'aiReason', key: 'aiReason', ellipsis: true },
    { title: '处置建议', dataIndex: 'suggestion', key: 'suggestion', ellipsis: true },
    {
      title: '检测时间',
      dataIndex: 'lastDetectedAt',
      key: 'lastDetectedAt',
      width: 160,
      render: (value) => value ? new Date(value).toLocaleString() : '-',
    },
  ];

  return (
    <div>
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="趋势组合" value={trends.length} suffix="组" />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="有效预警" value={alerts.length} suffix="条" valueStyle={{ color: alerts.length ? '#dc2626' : '#16a34a' }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="7天阈值" value={data?.sevenDayThreshold || 3} suffix="单" />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="30天阈值" value={data?.thirtyDayThreshold || 6} suffix="单" />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 12 }} wrap>
        <Button type="primary" icon={<ReloadOutlined />} onClick={handleRefresh} loading={refreshing}>
          手动刷新预警
        </Button>
        <span style={{ color: '#666', fontSize: 12 }}>
          7天≥3单且增长≥50%；30天≥6单且增长≥30%；同地点/分类/周期自动去重
        </span>
      </Space>

      {chartData.length > 0 ? (
        <Line {...config} />
      ) : (
        <Alert type="success" showIcon message="暂无高频故障趋势" description="当前近7天和近30天未发现达到预警阈值的地点/分类组合。" />
      )}

      <Table
        style={{ marginTop: 16 }}
        size="small"
        dataSource={alerts}
        rowKey={(record) => record.id}
        columns={columns}
        pagination={{ pageSize: 6 }}
        locale={{ emptyText: '暂无高频故障预警' }}
      />
    </div>
  );
};

const FacilityHealthPanel = ({ data }) => {
  const areaHealth = data?.areaHealth || [];
  const categoryRisk = data?.categoryRisk || [];
  const suggestions = data?.suggestions || [];

  const scoreColor = (score) => {
    if (score >= 80) return '#16a34a';
    if (score >= 60) return '#d97706';
    return '#dc2626';
  };

  return (
    <div>
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }} align="stretch" className="facility-top-row">
        <Col xs={24} md={12}>
          <Card size="small" variant="borderless" className="analysis-sub-card health-score-card">
            <Statistic
              title="综合健康分"
              value={data?.overallHealthScore ?? 100}
              suffix="分"
              valueStyle={{ color: scoreColor(data?.overallHealthScore ?? 100) }}
            />
            <Tag style={{ marginTop: 8 }} color={(data?.overallRiskLevel || '健康') === '健康' ? 'green' : 'orange'}>
              {data?.overallRiskLevel || '健康'}
            </Tag>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" variant="borderless" className="analysis-sub-card inspection-card">
            <Alert
              type="info"
              showIcon
              message="巡检建议"
              description={suggestions.length > 0 ? suggestions.join('；') : '当前设施运行平稳，可保持常规巡检频率。'}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col xs={24} md={14}>
          <Card size="small" title="楼栋/区域健康分" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={areaHealth}
              rowKey={(record) => record.area}
              pagination={{ pageSize: 6 }}
              columns={[
                { title: '区域', dataIndex: 'area', key: 'area', ellipsis: true },
                {
                  title: '健康分',
                  dataIndex: 'healthScore',
                  key: 'healthScore',
                  width: 90,
                  render: (value) => <Tag color={value >= 80 ? 'green' : value >= 60 ? 'orange' : 'red'}>{value}</Tag>,
                },
                { title: '报修数', dataIndex: 'totalTickets', key: 'totalTickets', width: 80 },
                { title: '未闭环', dataIndex: 'activeTickets', key: 'activeTickets', width: 80 },
                { title: '重复', dataIndex: 'repeatedTickets', key: 'repeatedTickets', width: 70 },
                { title: '平均评分', dataIndex: 'avgRating', key: 'avgRating', width: 90 },
                { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 90 },
              ]}
              locale={{ emptyText: '暂无区域健康数据' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={10}>
          <Card size="small" title="分类风险排行" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={categoryRisk}
              rowKey={(record) => record.category}
              pagination={{ pageSize: 6 }}
              columns={[
                { title: '分类', dataIndex: 'category', key: 'category', ellipsis: true },
                { title: '总数', dataIndex: 'totalTickets', key: 'totalTickets', width: 70 },
                { title: '未闭环', dataIndex: 'activeTickets', key: 'activeTickets', width: 80 },
                { title: '高优先级', dataIndex: 'highPriorityTickets', key: 'highPriorityTickets', width: 90 },
                {
                  title: '风险分',
                  dataIndex: 'riskScore',
                  key: 'riskScore',
                  width: 90,
                  render: (value) => <Tag color={value >= 60 ? 'red' : value >= 35 ? 'orange' : 'green'}>{value}</Tag>,
                },
              ]}
              locale={{ emptyText: '暂无分类风险数据' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

const SlaAlertPanel = () => {
  const [slaData, setSlaData] = useState({
    activeCount: 0,
    overdueAcceptCount: 0,
    overdueCompletionCount: 0,
    warningCount: 0,
    alertTotal: 0,
    overdueRate: 0,
    alertTickets: [],
    rules: [],
  });
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadSlaData();
  }, []);

  const loadSlaData = async () => {
    setLoading(true);
    try {
      const data = await statisticsService.getSlaOverview();
      setSlaData(data);
    } catch (error) {
      console.error('加载SLA数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleViewTicket = (ticketId) => {
    navigate(`/admin/orders?id=${ticketId}`);
  };

  const columns = [
    {
      title: '工单ID',
      dataIndex: 'ticketId',
      key: 'ticketId',
      width: 80,
      render: (id) => (
        <Button type="link" size="small" onClick={() => handleViewTicket(id)}>
          #{id}
        </Button>
      ),
    },
    { title: '位置', dataIndex: 'locationText', key: 'locationText', ellipsis: true },
    { title: '分类', dataIndex: 'categoryName', key: 'categoryName', width: 100 },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 80,
      render: (p) => (
        <Tag color={p === 'high' ? 'red' : p === 'medium' ? 'orange' : 'green'}>
          {p === 'high' ? '高' : p === 'medium' ? '中' : '低'}
        </Tag>
      ),
    },
    {
      title: 'SLA类型',
      dataIndex: 'slaLabel',
      key: 'slaLabel',
      width: 100,
    },
    {
      title: '状态',
      dataIndex: 'slaStatus',
      key: 'slaStatus',
      width: 90,
      render: (status) => (
        <Tag color={status === 'OVERDUE' ? 'red' : 'orange'}>
          {status === 'OVERDUE' ? '已超时' : '即将超时'}
        </Tag>
      ),
    },
    {
      title: '剩余/超时',
      key: 'timeInfo',
      width: 100,
      render: (_, record) => {
        const hours = record.remainingHours < 0 ? Math.abs(record.overdueHours) : record.remainingHours;
        const label = record.remainingHours < 0 ? `超时 ${hours}h` : `剩余 ${hours}h`;
        return (
          <span style={{ color: record.remainingHours < 0 ? '#dc2626' : '#d97706', fontWeight: 500 }}>
            {label}
          </span>
        );
      },
    },
    {
      title: '截止时间',
      dataIndex: 'dueAt',
      key: 'dueAt',
      width: 130,
      render: (t) => t ? new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-',
    },
  ];

  return (
    <div>
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="活跃工单" value={slaData.activeCount} suffix="个" />
          </Card>
        </Col>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="已超时" value={slaData.alertTotal} suffix="个" valueStyle={{ color: '#dc2626' }} />
          </Card>
        </Col>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="受理超时" value={slaData.overdueAcceptCount} suffix="个" valueStyle={{ color: '#ef4444' }} />
          </Card>
        </Col>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="完成超时" value={slaData.overdueCompletionCount} suffix="个" valueStyle={{ color: '#f87171' }} />
          </Card>
        </Col>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="即将超时" value={slaData.warningCount} suffix="个" valueStyle={{ color: '#d97706' }} />
          </Card>
        </Col>
        <Col xs={12} md={4}>
          <Card size="small" variant="borderless" className="analysis-sub-card">
            <Statistic title="超时率" value={slaData.overdueRate} suffix="%" precision={1} />
          </Card>
        </Col>
      </Row>

      <Table
        size="small"
        loading={loading}
        dataSource={slaData.alertTickets}
        rowKey="ticketId"
        pagination={{ pageSize: 10 }}
        columns={columns}
        locale={{ emptyText: '暂无SLA告警' }}
        scroll={{ x: 900 }}
      />
    </div>
  );
};

const HotspotAnalysis = ({ data }) => {
  const hotAreas = data?.hotAreas || [];
  const categoryGrowth = data?.categoryGrowth || [];
  const repeatedLocations = data?.repeatedLocations || [];
  const staffWorkload = data?.staffWorkload || [];
  const categoryProcessingTime = data?.categoryProcessingTime || [];

  const formatTime = (value) => {
    if (!value) return '-';
    try {
      return new Date(value).toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch (error) {
      return value;
    }
  };

  return (
    <div>
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={8}>
          <Card size="small" title="高频楼栋/区域" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={hotAreas}
              rowKey={(record) => record.area}
              pagination={false}
              columns={[
                { title: '区域', dataIndex: 'area', key: 'area', ellipsis: true },
                { title: '报修数', dataIndex: 'totalTickets', key: 'totalTickets', width: 80 },
                {
                  title: '处理中',
                  dataIndex: 'activeTickets',
                  key: 'activeTickets',
                  width: 80,
                  render: (value) => value > 0 ? <Tag color="orange">{value}</Tag> : <Tag>{value}</Tag>,
                },
              ]}
              locale={{ emptyText: '暂无热点区域' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" title="近 7 天异常增长分类" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={categoryGrowth}
              rowKey={(record) => record.category}
              pagination={false}
              columns={[
                { title: '分类', dataIndex: 'category', key: 'category', ellipsis: true },
                { title: '近7天', dataIndex: 'recentCount', key: 'recentCount', width: 70 },
                {
                  title: '增长',
                  dataIndex: 'growth',
                  key: 'growth',
                  width: 80,
                  render: (value) => (
                    <Tag color={value > 0 ? 'red' : value < 0 ? 'green' : 'default'}>
                      {value > 0 ? `+${value}` : value}
                    </Tag>
                  ),
                },
              ]}
              locale={{ emptyText: '暂无增长数据' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" title="分类平均处理时长" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={categoryProcessingTime}
              rowKey={(record) => record.category}
              pagination={false}
              columns={[
                { title: '分类', dataIndex: 'category', key: 'category', ellipsis: true },
                { title: '已完成', dataIndex: 'completedTickets', key: 'completedTickets', width: 80 },
                { title: '平均时长', dataIndex: 'displayText', key: 'displayText', width: 100 },
              ]}
              locale={{ emptyText: '暂无处理时长数据' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col xs={24} md={12}>
          <Card size="small" title="重复报修地点" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={repeatedLocations}
              rowKey={(record) => `${record.location}-${record.category}`}
              pagination={{ pageSize: 5 }}
              columns={[
                { title: '地点', dataIndex: 'location', key: 'location', ellipsis: true },
                { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
                { title: '次数', dataIndex: 'totalTickets', key: 'totalTickets', width: 70 },
                {
                  title: '未闭环',
                  dataIndex: 'activeTickets',
                  key: 'activeTickets',
                  width: 80,
                  render: (value) => value > 0 ? <Tag color="red">{value}</Tag> : <Tag>{value}</Tag>,
                },
                { title: '最近报修', dataIndex: 'lastCreatedAt', key: 'lastCreatedAt', width: 130, render: formatTime },
              ]}
              locale={{ emptyText: '暂无重复报修地点' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" title="维修人员任务负载" variant="borderless" className="analysis-sub-card">
            <Table
              size="small"
              dataSource={staffWorkload}
              rowKey={(record) => record.staffId}
              pagination={{ pageSize: 5 }}
              columns={[
                { title: '维修员', dataIndex: 'staffName', key: 'staffName', ellipsis: true },
                { title: '累计分配', dataIndex: 'totalAssigned', key: 'totalAssigned', width: 90 },
                {
                  title: '当前待办',
                  dataIndex: 'activeTickets',
                  key: 'activeTickets',
                  width: 90,
                  render: (value) => (
                    <Tag color={value >= 3 ? 'red' : value > 0 ? 'orange' : 'green'}>{value}</Tag>
                  ),
                },
                { title: '已完成', dataIndex: 'completedTickets', key: 'completedTickets', width: 80 },
              ]}
              locale={{ emptyText: '暂无维修员负载数据' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

const InteractiveDonutChart = ({ data, labelKey, valueKey, centerTitle }) => {
  const [activeIndex, setActiveIndex] = React.useState(0);
  const chartData = (data || [])
    .map((item, index) => ({
      ...item,
      label: item[labelKey] || '未分类',
      value: Number(item[valueKey] || 0),
      color: chartColors[index % chartColors.length],
    }))
    .filter((item) => item.value > 0);

  if (!chartData.length) {
    return <div className="chart-empty">暂无数据</div>;
  }

  const total = chartData.reduce((sum, item) => sum + item.value, 0);
  const active = chartData[Math.min(activeIndex, chartData.length - 1)] || chartData[0];
  let currentAngle = 0;
  const center = 120;
  const radius = 82;

  const segments = chartData.map((item, index) => {
    const angle = getChartPercent(item.value, total) * 3.6;
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;
    currentAngle = endAngle;
    return { ...item, index, startAngle, endAngle };
  });

  return (
    <div className="custom-donut">
      <div className="custom-donut-visual">
        <svg viewBox="0 0 240 240" className="custom-donut-svg" role="img">
          <circle cx={center} cy={center} r={radius} className="custom-donut-track" />
          {segments.map((segment) => {
            const percent = getChartPercent(segment.value, total);
            const isActive = segment.index === activeIndex;
            return (
              <path
                key={segment.label}
                d={describeArc(center, radius, segment.startAngle, segment.endAngle)}
                stroke={segment.color}
                strokeWidth={isActive ? 28 : 22}
                strokeLinecap="round"
                fill="none"
                className="custom-donut-segment"
                style={{ opacity: isActive ? 1 : 0.72 }}
                onMouseEnter={() => setActiveIndex(segment.index)}
                onClick={() => setActiveIndex(segment.index)}
              >
                <title>{`${segment.label}: ${segment.value}，${percent.toFixed(1)}%`}</title>
              </path>
            );
          })}
          <text x="120" y="112" textAnchor="middle" className="custom-donut-center-title">
            {centerTitle}
          </text>
          <text x="120" y="140" textAnchor="middle" className="custom-donut-center-value">
            {total}
          </text>
        </svg>
        <div className="custom-chart-tooltip">
          <strong>{active.label}</strong>
          <span>{active.value} 条，占比 {getChartPercent(active.value, total).toFixed(1)}%</span>
        </div>
      </div>
      <div className="custom-donut-legend">
        {segments.map((item) => (
          <button
            type="button"
            key={item.label}
            className={item.index === activeIndex ? "donut-legend-item active" : "donut-legend-item"}
            onMouseEnter={() => setActiveIndex(item.index)}
            onFocus={() => setActiveIndex(item.index)}
            onClick={() => setActiveIndex(item.index)}
          >
            <span style={{ background: item.color }} />
            <strong>{item.label}</strong>
            <em>{getChartPercent(item.value, total).toFixed(1)}%</em>
          </button>
        ))}
      </div>
    </div>
  );
};

const RepairCategoryPieChart = ({ data }) => (
  <InteractiveDonutChart data={data} labelKey="type" valueKey="value" centerTitle="报修数" />
);

// 新增：工单状态分布饼图组件
const OrderStatusPieChart = ({ data }) => {
  console.log('状态饼图接收到的数据:', data);
  
  if (!data || data.length === 0) {
    return <div style={{ textAlign: 'center', padding: '50px 0', color: '#999' }}>暂无数据</div>;
  }

  // 后端返回的是枚举名（WAITING_ACCEPT 等），这里先映射到前端状态值，再映射到中文标签
  const backendToFrontendStatus = {
    WAITING_ACCEPT: 'pending',
    IN_PROGRESS: 'processing',
    RESOLVED: 'completed',
    WAITING_FEEDBACK: 'to_be_evaluated',
    FEEDBACKED: 'closed',
    CLOSED: 'closed',
    REJECTED: 'rejected',
  };

  // 准备饼图数据，将状态值转换为对应的中文标签
  const chartData = data.map(item => {
    const rawStatus = item.status;
    // 先将枚举名映射为前端状态值（pending/processing/...）
    const frontendStatus =
      backendToFrontendStatus[rawStatus] ||
      (rawStatus ? rawStatus.toLowerCase() : rawStatus);

    // 在 TASK_STATUS 常量中查找对应的配置
    const statusKey = Object.keys(TASK_STATUS).find(
      key => TASK_STATUS[key].value === frontendStatus
    );

    let statusConfig;
    if (statusKey && TASK_STATUS[statusKey]) {
      statusConfig = TASK_STATUS[statusKey];
    } else {
      // 如果找不到对应状态，使用默认值（直接展示后端给的状态字符串）
      console.warn(`未找到状态 ${rawStatus} / ${frontendStatus} 的配置，使用默认值`);
      statusConfig = { label: rawStatus || '未知状态', color: '#d9d9d9' };
    }

    return {
      ...item,
      type: statusConfig.label, // 使用中文标签
      color: statusConfig.color,
    };
  });

  return <InteractiveDonutChart data={chartData} labelKey="type" valueKey="value" centerTitle="工单数" />;
};

const LocationRepairColumnChart = ({ data }) => {
  if (!data || data.length === 0) {
    return <div className="chart-empty">暂无数据</div>;
  }

  const sorted = [...data].sort((a, b) => Number(b.count || 0) - Number(a.count || 0)).slice(0, 10);
  const max = Math.max(...sorted.map((item) => Number(item.count || 0)), 1);

  return (
    <div className="custom-bar-list">
      {sorted.map((item, index) => {
        const value = Number(item.count || 0);
        const percent = Math.max(6, value / max * 100);
        return (
          <div className="custom-bar-row" key={`${item.location}-${index}`}>
            <div className="custom-bar-label" title={item.location}>{item.location}</div>
            <div className="custom-bar-track">
              <div
                className="custom-bar-fill"
                style={{
                  width: `${percent}%`,
                  background: `linear-gradient(90deg, ${chartColors[index % chartColors.length]}, #00c2d1)`,
                }}
              />
            </div>
            <div className="custom-bar-value">{value}</div>
          </div>
        );
      })}
    </div>
  );
};

// 新增：月度统计折线图组件
const MonthlyLineChart = ({ data }) => {
  console.log('折线图接收到的数据:', data);
  
  if (!data || data.length === 0) {
    return <div style={{ textAlign: 'center', padding: '50px 0', color: '#999' }}>暂无数据</div>;
  }

  const config = {
    data: data,
    xField: 'month',
    yField: 'orders',
    smooth: true,
    color: '#0f62fe',
    lineStyle: {
      lineWidth: 3,
    },
    area: {
      style: {
        fill: 'l(270) 0:#dff8ff 1:#ffffff',
      },
    },
    point: {
      size: 5,
      shape: 'circle',
      style: {
        fill: 'white',
        stroke: '#0f62fe',
        lineWidth: 2,
      },
    },
    xAxis: {
      title: {
        text: '月份',
        style: {
          fontSize: 14,
        },
      },
    },
    yAxis: {
      title: {
        text: '工单数量',
        style: {
          fontSize: 14,
        },
      },
      label: {
        formatter: (text) => `${text} 单`,
      },
    },
    height: 360,
    interactions: [{ type: 'element-active' }],
    tooltip: {
      formatter: (datum) => {
        return { name: '工单数量', value: `${datum.orders} 单` };
      },
    },
    meta: {
      month: {
        alias: '月份',
      },
      orders: {
        alias: '工单数量',
      },
    },
  };

  try {
    return <Line {...config} />;
  } catch (error) {
    console.error('折线图渲染错误:', error);
    return (
      <div style={{ textAlign: 'center', padding: '50px 0', color: '#ff4d4f' }}>
        图表渲染失败，请检查数据格式
      </div>
    );
  }
};

// 表格组件保持不变
const RepairmanRatingTable = ({ data }) => {
  const columns = [
    {
      title: '排名',
      key: 'rank',
      width: 60,
      render: (_, __, index) => (
        <Tag color={index < 3 ? 'gold' : 'default'}>
          {index + 1}
        </Tag>
      ),
    },
    {
      title: '维修人员',
      dataIndex: 'name',
      key: 'name',
      width: 100,
    },
    {
      title: '平均评分',
      dataIndex: 'rating',
      key: 'rating',
      width: 100,
      render: (rating) => (
        <Tag color={rating >= 4.8 ? 'green' : rating >= 4.5 ? 'blue' : 'orange'}>
          {rating} 分
        </Tag>
      ),
    },
    {
      title: '完成工单数',
      dataIndex: 'completedOrders',
      key: 'completedOrders',
      width: 100,
      render: (count) => `${count} 单`,
    },
    {
      title: '评级',
      key: 'level',
      width: 80,
      render: (record) => {
        if (record.rating >= 4.8) return <Tag color="green">优秀</Tag>;
        if (record.rating >= 4.5) return <Tag color="blue">良好</Tag>;
        return <Tag color="orange">一般</Tag>;
      },
    },
  ];

  return data && data.length > 0 ? (
    <Table
      dataSource={data}
      columns={columns}
      pagination={false}
      size="small"
      rowKey="id"
      scroll={{ y: 240 }}
    />
  ) : (
    <div style={{ textAlign: 'center', padding: '50px 0', color: '#999' }}>暂无数据</div>
  );
};

const formatSize = (bytes) => {
  if (!bytes && bytes !== 0) return '-';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
};

export default DataAnalysis;
