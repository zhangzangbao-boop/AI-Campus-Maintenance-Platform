import React, { useState, useEffect } from 'react';
import { Card, Rate, Button, Space, Tag, Popconfirm, Statistic, Row, Col } from 'antd';
import { DeleteOutlined, UserOutlined, StarOutlined, MessageOutlined } from '@ant-design/icons';
import { feedbackService } from './feedbackService';

const FeedbackManagement = () => {
  const [feedbacks, setFeedbacks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  // 加载评价数据
  const loadFeedbacks = async () => {
    setLoading(true);
    try {
      const data = await feedbackService.getAllFeedbacks();
      setFeedbacks(data);
    } catch (error) {
      console.error('加载评价数据失败:', error);
      // 错误信息已经在service中显示
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFeedbacks();
  }, []);

  // 删除评价
  const handleDeleteFeedback = async (feedbackId) => {
    setDeletingId(feedbackId);
    
    try {
      await feedbackService.deleteFeedback(feedbackId);
      
      // 从本地状态中移除已删除的评价
      setFeedbacks(prevFeedbacks => 
        prevFeedbacks.filter(feedback => feedback.id !== feedbackId)
      );
    } catch (error) {
      console.error('删除评价失败:', error);
      // 错误信息已经在service中显示
    } finally {
      setDeletingId(null);
    }
  };

  // 检查是否有不当内容
  const hasInappropriateContent = (comment) => {
    if (!comment) return false;
    const inappropriateWords = ['badword', '脏话', '投诉']; // 示例敏感词
    return inappropriateWords.some(word => comment.toLowerCase().includes(word.toLowerCase()));
  };

  // 渲染每条评价卡片
  const renderFeedbackCard = (feedback) => {
    // 直接使用API返回的维修人员姓名
    const repairmanName = feedback.repairmanName || '未知维修工';
    const studentName = feedback.studentName || feedback.studentId || '未知学生';

    // 根据评分设置标签颜色
    const getRatingTagColor = (rating) => {
      if (rating >= 4) return 'green';
      if (rating >= 3) return 'orange';
      return 'red';
    };

    const inappropriate = hasInappropriateContent(feedback.comment);
    const isDeleting = deletingId === feedback.id;

    return (
      <Card
        key={feedback.id}
        style={{
          marginBottom: 16,
          border: inappropriate ? '1px solid #ff4d4f' : '1px solid #d9d9d9'
        }}
        size="small"
        loading={isDeleting}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            {/* 评价头部信息 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <Space>
                <span style={{ fontWeight: 'bold' }}>评价 #{feedback.id}</span>
                <Tag color="blue">报修单: {feedback.repairOrderId || '未知'}</Tag>
                {feedback.anonymous && <Tag color="purple">匿名展示</Tag>}
                {feedback.resolved !== undefined && feedback.resolved !== null && (
                  <Tag color={feedback.resolved ? 'green' : 'red'}>
                    {feedback.resolved ? '已解决' : '未彻底解决'}
                  </Tag>
                )}
              </Space>
              <Tag color={getRatingTagColor(feedback.rating)}>
                {feedback.rating}星
              </Tag>
            </div>

            {/* 评分显示 */}
            <div style={{ marginBottom: 8 }}>
              <Rate disabled value={feedback.rating} />
            </div>

            {(feedback.speedRating || feedback.qualityRating || feedback.attitudeRating) && (
              <Space wrap style={{ marginBottom: 8 }}>
                {feedback.speedRating && <Tag>速度 {feedback.speedRating} 星</Tag>}
                {feedback.qualityRating && <Tag>质量 {feedback.qualityRating} 星</Tag>}
                {feedback.attitudeRating && <Tag>态度 {feedback.attitudeRating} 星</Tag>}
              </Space>
            )}

            {/* 参与方信息 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, flexWrap: 'wrap', gap: '8px' }}>
              <div>
                <UserOutlined style={{ marginRight: 4 }} />
                <strong>评价人:</strong> {feedback.anonymous ? '匿名学生' : studentName}
              </div>
              <div>
                <UserOutlined style={{ marginRight: 4 }} />
                <strong>维修人员:</strong> {repairmanName}
              </div>
            </div>

            {/* 评论内容 */}
            {feedback.comment && (
              <div style={{ 
                marginBottom: 8, 
                padding: '8px 12px', 
                backgroundColor: '#f5f5f5', 
                borderRadius: 4,
                border: inappropriate ? '1px solid #ffccc7' : 'none'
              }}>
                <div style={{ fontWeight: 'bold', marginBottom: 4, display: 'flex', alignItems: 'center' }}>
                  <MessageOutlined style={{ marginRight: 4 }} />
                  评论内容:
                </div>
                <div style={{ 
                  color: inappropriate ? '#ff4d4f' : 'inherit',
                  fontStyle: inappropriate ? 'italic' : 'normal'
                }}>
                  {feedback.comment}
                </div>
                {inappropriate && (
                  <Tag color="red" style={{ marginTop: 4 }}>可能包含不当内容</Tag>
                )}
              </div>
            )}

            {/* 时间信息 */}
            <div style={{ color: '#666', fontSize: '12px', display: 'flex', alignItems: 'center' }}>
              <span>评价时间: {feedback.createdAt || feedback.created_at}</span>
            </div>
          </div>

          {/* 删除按钮 */}
          <div style={{ marginLeft: 16, flexShrink: 0 }}>
            <Popconfirm
              title="确定删除这条评价吗？"
              description="此操作不可恢复"
              onConfirm={() => handleDeleteFeedback(feedback.id)}
              okText="确定"
              cancelText="取消"
              okType="danger"
            >
              <Button 
                type="primary" 
                danger 
                icon={<DeleteOutlined />}
                size="small"
                loading={isDeleting}
                disabled={isDeleting}
              >
                {isDeleting ? '删除中' : '删除'}
              </Button>
            </Popconfirm>
          </div>
        </div>
      </Card>
    );
  };

  // 统计数据
  const totalFeedbacks = feedbacks.length;
  const averageRating = totalFeedbacks > 0 
    ? (feedbacks.reduce((sum, item) => sum + item.rating, 0) / totalFeedbacks).toFixed(1)
    : 0;
  const feedbacksWithComments = feedbacks.filter(f => f.comment && f.comment.trim() !== '').length;
  const highRatings = feedbacks.filter(f => f.rating >= 4).length;

  return (
    <div className="analytics-page">
      <div className="analytics-header">
        <div>
          <div className="page-hero-eyebrow">平台管理</div>
          <h2>评价管理</h2>
          <p>查看和管理学生对维修服务的评价反馈，支持删除不当评价。</p>
        </div>
      </div>
      
      {/* 统计信息 */}
      <Row gutter={16}>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic
              title="总评价数"
              value={totalFeedbacks}
              prefix={<MessageOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic
              title="平均评分"
              value={averageRating}
              prefix={<StarOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic
              title="有文字评价"
              value={feedbacksWithComments}
              prefix={<MessageOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card size="small">
            <Statistic
              title="好评数(4星+)"
              value={highRatings}
              prefix={<StarOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 评价列表 */}
      <Card title="评价列表" style={{ marginTop: 16 }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <span>加载评价数据中...</span>
          </div>
        ) : feedbacks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
            暂无评价数据
          </div>
        ) : (
          <div>
            {feedbacks.map(renderFeedbackCard)}
          </div>
        )}
      </Card>
    </div>
  );
};

export default FeedbackManagement;
