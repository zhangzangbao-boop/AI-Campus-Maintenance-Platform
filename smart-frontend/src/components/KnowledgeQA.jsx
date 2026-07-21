import React, { useState } from 'react';
import { Card, Input, Button, Select, Spin, Alert, Typography, Space, Tag, Empty, message } from 'antd';
import { SearchOutlined, BulbOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons';
import api from '../services/api';

const { TextArea } = Input;
const { Title, Text, Paragraph } = Typography;
const { Option } = Select;

/**
 * 维修知识问答页面
 * 供学生和维修工使用的 RAG 知识库问答功能
 */
const KnowledgeQA = () => {
  const [question, setQuestion] = useState('');
  const [categoryKey, setCategoryKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  // 故障分类选项
  const categories = [
    { value: '', label: '全部分类' },
    { value: 'ac', label: '空调故障' },
    { value: 'plumbing', label: '管道故障' },
    { value: 'electrical', label: '电力故障' },
    { value: 'network', label: '网络故障' },
    { value: 'furniture', label: '家具故障' },
    { value: 'door_window', label: '门窗故障' },
    { value: 'other', label: '其他故障' },
  ];

  // 常见问题快捷输入
  const quickQuestions = [
    '空调不制冷怎么办？',
    '宿舍漏水怎么处理？',
    '停电了怎么报修？',
    '网络连接不上怎么办？',
  ];

  const buildKnowledgeFallbackAnswer = (items) => {
    const primary = items[0];
    const lines = [
      `根据维修知识库，推荐先参考「${primary.title || '相关知识'}」：`,
    ];

    if (primary.solutionSteps) {
      lines.push('', primary.solutionSteps);
    }

    if (primary.safetyNotes) {
      lines.push('', `安全提示：${primary.safetyNotes}`);
    }

    if (primary.estimatedMinutes) {
      lines.push('', `预计处理时长：约 ${primary.estimatedMinutes} 分钟`);
    }

    if (items.length > 1) {
      lines.push('', '还可参考：');
      items.slice(1, 3).forEach((item) => {
        lines.push(`- ${item.title}`);
      });
    }

    return lines.join('\n');
  };

  const buildKnowledgeSources = (items) =>
    items.map((item) => ({
      id: String(item.knowledgeId),
      title: item.title,
      categoryKey: item.categoryKey,
      snippet: item.solutionSteps || item.symptomKeywords || item.safetyNotes || '',
      similarity: 0,
    }));

  const askKnowledgeBaseFallback = async (trimmedQuestion, fallbackMessage) => {
    const response = await api.knowledgeBase.recommend({
      categoryKey: categoryKey || undefined,
      text: trimmedQuestion,
      limit: 3,
    });

    if (response.code === 200 && Array.isArray(response.data) && response.data.length > 0) {
      setResult({
        success: true,
        answer: buildKnowledgeFallbackAnswer(response.data),
        sources: buildKnowledgeSources(response.data),
        similarity: 0,
        fallback: true,
        message: fallbackMessage || '已使用知识库基础检索生成回答',
      });
      return true;
    }

    setError(fallbackMessage || '未找到相关维修知识，请先在管理端维护维修知识库并重建索引');
    return false;
  };

  const handleAsk = async () => {
    if (!question.trim()) {
      message.warning('请输入您的问题');
      return;
    }

    const trimmedQuestion = question.trim();
    setLoading(true);
    setError('');
    setResult(null);

    try {
      const response = await api.ai.ragAsk(trimmedQuestion, categoryKey || null);

      if (response.code === 200 && response.data) {
        if (response.data.success === false) {
          const fallbackUsed = await askKnowledgeBaseFallback(
            trimmedQuestion,
            response.data.message || response.message
          );
          if (!fallbackUsed) {
            setError(response.data.message || response.message || '未找到相关维修知识');
          }
        } else {
          setResult({ success: true, ...response.data });
        }
      } else {
        const fallbackUsed = await askKnowledgeBaseFallback(
          trimmedQuestion,
          response.message
        );
        if (!fallbackUsed) {
          setError(response.message || '问答失败，请稍后重试');
        }
      }
    } catch (err) {
      try {
        const fallbackUsed = await askKnowledgeBaseFallback(
          trimmedQuestion,
          err.message
        );
        if (!fallbackUsed) {
          setError(err.message || '网络错误，请检查连接');
        }
      } catch (fallbackErr) {
        setError(fallbackErr.message || err.message || '网络错误，请检查连接');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleQuickQuestion = (q) => {
    setQuestion(q);
  };

  const getRetrievalTag = (mode, fallback) => {
    if (mode === 'chroma_ai') {
      return { color: 'green', text: 'Chroma 向量库 + AI 回答' };
    }
    if (mode === 'chroma_basic') {
      return { color: 'blue', text: 'Chroma 向量库检索' };
    }
    if (mode === 'local_index') {
      return { color: 'orange', text: '本地知识索引兜底' };
    }
    return { color: fallback ? 'orange' : 'green', text: fallback ? '基础回答' : 'AI 回答' };
  };

  return (
    <div style={{ padding: '24px', maxWidth: '900px', margin: '0 auto' }}>
      <Card>
        <Title level={3}>
          <BulbOutlined style={{ marginRight: '8px', color: '#1890ff' }} />
          维修知识问答
        </Title>
        <Paragraph type="secondary">
          输入您的问题，系统将基于维修知识库为您提供智能解答。
        </Paragraph>

        {/* 输入区域 */}
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* 故障分类选择 */}
          <div>
            <Text strong>故障分类（可选）</Text>
            <Select
              value={categoryKey}
              onChange={setCategoryKey}
              style={{ width: '100%', marginTop: '8px' }}
              placeholder="选择故障分类可提高检索准确性"
            >
              {categories.map(cat => (
                <Option key={cat.value} value={cat.value}>{cat.label}</Option>
              ))}
            </Select>
          </div>

          {/* 问题输入 */}
          <div>
            <Text strong>您的问题</Text>
            <TextArea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="例如：空调不制冷怎么办？"
              rows={3}
              style={{ marginTop: '8px' }}
              onPressEnter={(e) => {
                if (!e.shiftKey) {
                  e.preventDefault();
                  handleAsk();
                }
              }}
            />
          </div>

          {/* 快捷问题 */}
          <div>
            <Text type="secondary" style={{ fontSize: '12px' }}>快捷问题：</Text>
            <div style={{ marginTop: '8px' }}>
              {quickQuestions.map((q, idx) => (
                <Tag
                  key={idx}
                  style={{ cursor: 'pointer', marginBottom: '4px' }}
                  onClick={() => handleQuickQuestion(q)}
                >
                  {q}
                </Tag>
              ))}
            </div>
          </div>

          {/* 提交按钮 */}
          <Button
            type="primary"
            icon={<SearchOutlined />}
            onClick={handleAsk}
            loading={loading}
            size="large"
            block
          >
            提问
          </Button>
        </Space>
      </Card>

      {/* 加载状态 */}
      {loading && (
        <Card style={{ marginTop: '16px', textAlign: 'center' }}>
          <Spin tip="正在检索知识库..." />
        </Card>
      )}

      {/* 错误提示 */}
      {error && (
        <Alert
          message="提示"
          description={error}
          type="warning"
          showIcon
          style={{ marginTop: '16px' }}
        />
      )}

      {/* 回答结果 */}
      {result && !loading && (
        <Card style={{ marginTop: '16px' }}>
          <div style={{ marginBottom: '16px' }}>
            <Tag
              color={getRetrievalTag(result.retrievalMode, result.fallback).color}
              style={{ marginBottom: '8px' }}
            >
              {getRetrievalTag(result.retrievalMode, result.fallback).text}
            </Tag>
            {result.similarity > 0 && (
              <Tag color="blue" style={{ marginBottom: '8px' }}>
                相似度: {(result.similarity * 100).toFixed(0)}%
              </Tag>
            )}
          </div>

          <Title level={4}>回答</Title>
          <Paragraph style={{ whiteSpace: 'pre-wrap', lineHeight: '1.8' }}>
            {result.answer}
          </Paragraph>

          {/* 知识来源 */}
          {result.sources && result.sources.length > 0 && (
            <div style={{ marginTop: '24px' }}>
              <Title level={5}>知识来源</Title>
              {result.sources.map((source, idx) => (
                <Card
                  key={idx}
                  size="small"
                  style={{
                    marginTop: '8px',
                    backgroundColor: '#fafafa',
                    borderLeft: '3px solid #1890ff'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <Text strong>{source.title || `知识条目 ${idx + 1}`}</Text>
                    {source.similarity > 0 && (
                      <Tag>{(source.similarity * 100).toFixed(0)}%</Tag>
                    )}
                  </div>
                  <Text type="secondary" style={{ fontSize: '12px' }}>
                    {source.categoryKey && `分类: ${source.categoryKey}`}
                  </Text>
                  <Paragraph
                    style={{ marginTop: '8px', marginBottom: '0', fontSize: '13px' }}
                    ellipsis={{ rows: 2, expandable: true }}
                  >
                    {source.snippet}
                  </Paragraph>
                </Card>
              ))}
            </div>
          )}

          {/* 提示信息 */}
          {!result.fallback && (
            <Alert
              message="提示"
              description="此回答由 AI 基于维修知识库生成，仅供参考。如需进一步帮助，请联系维修人员。"
              type="info"
              showIcon
              style={{ marginTop: '16px' }}
            />
          )}
        </Card>
      )}

      {/* 无匹配结果 */}
      {result && !result.success && !loading && (
        <Empty
          description={result.message || '未找到相关知识'}
          style={{ marginTop: '24px' }}
        />
      )}
    </div>
  );
};

export default KnowledgeQA;
