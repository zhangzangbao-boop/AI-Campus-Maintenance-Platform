import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Input, Modal, Rate, Row, Select, Space, Statistic, Tag } from 'antd';
import { EditOutlined, MessageOutlined, StarOutlined, UserOutlined } from '@ant-design/icons';
import { feedbackService } from './feedbackService';

const followUpOptions = [
  { value: 'ALL', label: 'All follow-ups' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'RESOLVED', label: 'Resolved' },
];

const followUpMeta = {
  PENDING: { color: 'red', label: 'Pending' },
  PROCESSING: { color: 'orange', label: 'Processing' },
  RESOLVED: { color: 'green', label: 'Resolved' },
};

const sentimentMeta = {
  POSITIVE: { color: 'green', label: 'Positive' },
  NEUTRAL: { color: 'default', label: 'Neutral' },
  NEGATIVE: { color: 'red', label: 'Negative' },
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
    const sentiment = sentimentMeta[feedback.sentiment] || { color: 'default', label: 'Unanalyzed' };
    const followUp = followUpMeta[feedback.followUpStatus] || { color: 'default', label: 'Not required' };
    const keywords = keywordsOf(feedback);

    return (
      <Card key={feedback.id} size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            <Space wrap style={{ marginBottom: 8 }}>
              <strong>Feedback #{feedback.id}</strong>
              <Tag color="blue">Ticket {feedback.repairOrderId || '-'}</Tag>
              <Tag color={feedback.rating >= 4 ? 'green' : feedback.rating >= 3 ? 'orange' : 'red'}>
                {feedback.rating} stars
              </Tag>
              <Tag color={sentiment.color}>Sentiment: {sentiment.label}</Tag>
              <Tag color={followUp.color}>Follow-up: {followUp.label}</Tag>
            </Space>

            <div style={{ marginBottom: 8 }}>
              <Rate disabled value={feedback.rating} />
            </div>

            <Space wrap style={{ marginBottom: 8 }}>
              {feedback.speedRating && <Tag>Speed {feedback.speedRating}</Tag>}
              {feedback.qualityRating && <Tag>Quality {feedback.qualityRating}</Tag>}
              {feedback.attitudeRating && <Tag>Attitude {feedback.attitudeRating}</Tag>}
              {typeof feedback.sentimentScore === 'number' && <Tag>Score {Math.round(feedback.sentimentScore * 100)}%</Tag>}
              {keywords.map(keyword => <Tag key={keyword}>{keyword}</Tag>)}
            </Space>

            <Row gutter={[16, 8]} style={{ marginBottom: 8 }}>
              <Col xs={24} md={12}>
                <UserOutlined /> Student: {feedback.anonymous ? 'Anonymous' : (feedback.studentName || feedback.studentId || '-')}
              </Col>
              <Col xs={24} md={12}>
                <UserOutlined /> Staff: {feedback.repairmanName || feedback.repairmanId || '-'}
              </Col>
            </Row>

            {feedback.comment && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#f5f5f5', borderRadius: 4 }}>
                <strong><MessageOutlined /> Comment: </strong>{feedback.comment}
              </div>
            )}

            {feedback.sentimentSummary && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#fafafa', borderRadius: 4 }}>
                {feedback.sentimentSummary}
              </div>
            )}

            {feedback.followUpStatus && (
              <div style={{ marginBottom: 8, padding: '8px 12px', backgroundColor: '#fff7e6', borderRadius: 4 }}>
                <div><strong>Follow-up record:</strong> {feedback.followUpNote || '-'}</div>
                <div style={{ color: '#666', fontSize: 12 }}>
                  Handler: {feedback.followUpOperatorName || feedback.followUpOperatorId || '-'}
                  {' '}| Updated: {feedback.followUpUpdatedAt || '-'}
                </div>
              </div>
            )}

            <div style={{ color: '#666', fontSize: 12 }}>Created: {feedback.createdAt || '-'}</div>
          </div>

          <Button icon={<EditOutlined />} onClick={() => openFollowUp(feedback)}>
            Handle
          </Button>
        </div>
      </Card>
    );
  };

  return (
    <div style={{ padding: 16 }}>
      <h2>Feedback Management</h2>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="Total feedback" value={totalFeedbacks} prefix={<MessageOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="Average rating" value={averageRating} prefix={<StarOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="Needs attention" value={negativeCount} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic title="Follow-ups" value={followUpCount} />
          </Card>
        </Col>
      </Row>

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          <span>Sentiment</span>
          <Select
            value={sentimentFilter}
            style={{ width: 160 }}
            onChange={setSentimentFilter}
            options={[
              { value: 'ALL', label: 'All feedback' },
              { value: 'NEGATIVE', label: 'Negative only' },
            ]}
          />
          <span>Follow-up</span>
          <Select
            value={followUpFilter}
            style={{ width: 160 }}
            onChange={setFollowUpFilter}
            options={followUpOptions}
          />
          <Button onClick={loadFeedbacks} loading={loading}>Refresh</Button>
        </Space>
      </Card>

      {loading ? (
        <Card><div style={{ textAlign: 'center', padding: '40px 0' }}>Loading feedbacks...</div></Card>
      ) : feedbacks.length === 0 ? (
        <Card><div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>No feedback data</div></Card>
      ) : (
        feedbacks.map(renderFeedbackCard)
      )}

      <Modal
        title={editing ? `Handle feedback #${editing.id}` : 'Handle feedback'}
        open={Boolean(editing)}
        onCancel={() => setEditing(null)}
        onOk={saveFollowUp}
        confirmLoading={saving}
        okText="Save"
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
            placeholder="Processing note"
            maxLength={1000}
            showCount
          />
        </Space>
      </Modal>
    </div>
  );
};

export default FeedbackManagement;
