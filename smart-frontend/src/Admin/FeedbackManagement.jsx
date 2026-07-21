import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Modal, Rate, Row, Select, Space, Statistic, Tag, message } from 'antd';
import { DownloadOutlined, EditOutlined, MessageOutlined, StarOutlined, UserOutlined } from '@ant-design/icons';
import { feedbackService } from './feedbackService';
import api from '../services/api';

const followUpOptions = [
  { value: 'ALL', label: '全部回访状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
];

const followUpMeta = {
  PENDING: { color: 'red', label: '待处理' },
  PROCESSING: { color: 'orange', label: '处理中' },
  RESOLVED: { color: 'green', label: '已解决' },
};

const sentimentMeta = {
  POSITIVE: { color: 'green', label: '正面' },
  NEUTRAL: { color: 'default', label: '中性' },
  NEGATIVE: { color: 'red', label: '负面' },
};

const FeedbackManagement = () => {
  const [feedbacks, setFeedbacks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [sentimentFilter, setSentimentFilter] = useState('ALL');
  const [followUpFilter, setFollowUpFilter] = useState('ALL');
  const [editing, setEditing] = useState(null);
  const [followUpStatus, setFollowUpStatus] = useState('PROCESSING');
  const [followUpNote, setFollowUpNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [exportLoading, setExportLoading] = useState(false);

  const loadFeedbacks = async () => {
    setLoading(true);
    try {
      const params = {};
      if (sentimentFilter === 'NEGATIVE') {
        params.sentiment = 'NEGATIVE';
      }
      if (followUpFilter !== 'ALL') {
        params.followUpStatus = followUpFilter;
      }
      setFeedbacks(await feedbackService.getAllFeedbacks(params));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFeedbacks();
  }, [sentimentFilter, followUpFilter]);

  const openFollowUp = (feedback) => {
    setEditing(feedback);
    setFollowUpStatus(feedback.followUpStatus || 'PROCESSING');
    setFollowUpNote(feedback.followUpNote || '');
  };

  const saveFollowUp = async () => {
    if (!editing) {
      return;
    }
    setSaving(true);
    try {
      await feedbackService.updateFollowUp(editing.id, {
        status: followUpStatus,
        note: followUpNote,
      });
      setEditing(null);
      await loadFeedbacks();
    } finally {
      setSaving(false);
    }
  };

  const currentExportParams = () => {
    const params = {};
    if (sentimentFilter === 'NEGATIVE') {
      params.sentiment = 'NEGATIVE';
    }
    if (followUpFilter !== 'ALL') {
      params.followUpStatus = followUpFilter;
    }
    return params;
  };

  const handleExport = async () => {
    setExportLoading(true);
    try {
      const result = await api.admin.exportData('feedbacks', currentExportParams());
      message.success(`评价与回访已导出${result.rowCount ? `，共 ${result.rowCount} 行` : ''}`);
    } catch (error) {
      message.error(`导出失败：${error.message}`);
    } finally {
      setExportLoading(false);
    }
  };

  const keywordsOf = (feedback) => Array.isArray(feedback.sentimentKeywords)
    ? feedback.sentimentKeywords
    : String(feedback.sentimentKeywords || '').split(/[,\s]+/).filter(Boolean);

  const totalFeedbacks = feedbacks.length;
  const averageRating = totalFeedbacks > 0
    ? (feedbacks.reduce((sum, item) => sum + item.rating, 0) / totalFeedbacks).toFixed(1)
    : 0;
  const followUpCount = feedbacks.filter(item => item.followUpStatus).length;
  const negativeCount = feedbacks.filter(item => item.sentiment === 'NEGATIVE' || item.rating <= 2).length;

  const renderFeedbackCard = (feedback) => {
    const sentiment = sentimentMeta[feedback.sentiment] || { color: 'default', label: '未分析' };
    const followUp = followUpMeta[feedback.followUpStatus] || { color: 'default', label: '无需回访' };
    const keywords = keywordsOf(feedback);

    return (
      <Card key={feedback.id} size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            <Space wrap style={{ marginBottom: 8 }}>
              <strong>评价 #{feedback.id}</strong>
              <Tag color="blue">工单 {feedback.repairOrderId || '-'}</Tag>
              <Tag color={feedback.rating >= 4 ? 'green' : feedback.rating >= 3 ? 'orange' : 'red'}>
                {feedback.rating} 星
              </Tag>
              <Tag color={sentiment.color}>情感：{sentiment.label}</Tag>
              <Tag color={followUp.color}>回访：{followUp.label}</Tag>
            </Space>

            <div style={{ marginBottom: 8 }}>
              <Rate disabled value={feedback.rating} />
            </div>

            <Space wrap style={{ marginBottom: 8 }}>
              {feedback.speedRating && <Tag>响应速度 {feedback.speedRating}</Tag>}
              {feedback.qualityRating && <Tag>维修质量 {feedback.qualityRating}</Tag>}
              {feedback.attitudeRating && <Tag>服务态度 {feedback.attitudeRating}</Tag>}
              {typeof feedback.sentimentScore === 'number' && <Tag>置信度 {Math.round(feedback.sentimentScore * 100)}%</Tag>}
              {keywords.map(keyword => <Tag key={keyword}>{keyword}</Tag>)}
            </Space>

            <Row gutter={[16, 8]} style={{ marginBottom: 8 }}>
              <Col xs={24} md={12}>
                <UserOutlined /> 学生：{feedback.anonymous ? '匿名' : (feedback.studentName || feedback.studentId || '-')}
              </Col>
              <Col xs={24} md={12}>
                <UserOutlined /> 维修工：{feedback.repairmanName || feedback.repairmanId || '-'}
              </Col>
            </Row>

            {feedback.comment && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#f5f5f5', borderRadius: 4 }}>
                <strong><MessageOutlined /> 评价内容：</strong>{feedback.comment}
              </div>
            )}

            {feedback.sentimentSummary && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#fafafa', borderRadius: 4 }}>
                {feedback.sentimentSummary}
              </div>
            )}

            {feedback.followUpStatus && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#fff7e6', borderRadius: 4 }}>
                <div><strong>回访记录：</strong>{feedback.followUpNote || '-'}</div>
                <div style={{ color: '#666', fontSize: 12 }}>
                  处理人：{feedback.followUpOperatorName || feedback.followUpOperatorId || '-'}
                  {' '}| 更新时间：{feedback.followUpUpdatedAt || '-'}
                </div>
              </div>
            )}

            <div style={{ color: '#666', fontSize: 12 }}>创建时间：{feedback.createdAt || '-'}</div>
          </div>

          <Button icon={<EditOutlined />} onClick={() => openFollowUp(feedback)}>
            处理回访
          </Button>
        </div>
      </Card>
    );
  };

  return (
    <div style={{ padding: 16 }}>
      <h2>反馈管理</h2>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="评价总数" value={totalFeedbacks} prefix={<MessageOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="平均评分" value={averageRating} prefix={<StarOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="需关注评价" value={negativeCount} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="已回访/处理中" value={followUpCount} />
          </Card>
        </Col>
      </Row>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          <span>情感倾向</span>
          <Select
            value={sentimentFilter}
            style={{ width: 160 }}
            onChange={setSentimentFilter}
            options={[
              { value: 'ALL', label: '全部评价' },
              { value: 'NEGATIVE', label: '仅负面评价' },
            ]}
          />
          <span>回访状态</span>
          <Select
            value={followUpFilter}
            style={{ width: 160 }}
            onChange={setFollowUpFilter}
            options={followUpOptions}
          />
          <Button onClick={loadFeedbacks} loading={loading}>刷新</Button>
          <Button icon={<DownloadOutlined />} onClick={handleExport} loading={exportLoading}>
            导出
          </Button>
        </Space>
      </Card>

      {loading ? (
        <Card><div style={{ textAlign: 'center', padding: '40px 0' }}>正在加载评价...</div></Card>
      ) : feedbacks.length === 0 ? (
        <Card><div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>暂无评价数据</div></Card>
      ) : (
        feedbacks.map(renderFeedbackCard)
      )}

      <Modal
        title={editing ? `处理评价 #${editing.id}` : '处理评价'}
        open={Boolean(editing)}
        onCancel={() => setEditing(null)}
        onOk={saveFollowUp}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            value={followUpStatus}
            onChange={setFollowUpStatus}
            options={followUpOptions.filter(item => item.value !== 'ALL')}
            style={{ width: '100%' }}
          />
          <Input.TextArea
            rows={4}
            value={followUpNote}
            onChange={event => setFollowUpNote(event.target.value)}
            placeholder="请输入回访处理记录"
            maxLength={1000}
            showCount
          />
        </Space>
      </Modal>
    </div>
  );
};

export default FeedbackManagement;
