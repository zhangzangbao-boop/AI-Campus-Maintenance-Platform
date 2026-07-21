import React, { useRef, useState } from 'react';
import {
  Alert,
  Card,
  Form,
  Input,
  Select,
  Button,
  Upload,
  message,
  List,
  Space,
  Tag,
  Modal,
  Descriptions
} from 'antd';
import {
  BulbOutlined,
  ThunderboltOutlined,
  UploadOutlined,
  CheckCircleOutlined,
  WarningOutlined
} from '@ant-design/icons';
import { repairService } from '../services/repairService';
import api from '../services/api';

const { Option } = Select;
const { TextArea } = Input;

const CATEGORY_OPTIONS = [
  { value: "waterAndElectricity", label: "水电维修", id: 1 },
  { value: "networkIssues", label: "网络故障", id: 2 },
  { value: "furnitureRepair", label: "家具维修", id: 3 },
  { value: "applianceIssues", label: "电器故障", id: 4 },
  { value: "publicFacilities", label: "公共设施", id: 5 },
  { value: "doorWindowRepair", label: "门窗维修", id: 6 },
  { value: "cleaning", label: "卫生清洁", id: 7 },
  { value: "fireSafety", label: "消防安全", id: 8 },
  { value: "airConditioning", label: "空调维修", id: 9 },
  { value: "other", label: "其他", id: 10 },
];

const categoryLabelToValue = CATEGORY_OPTIONS.reduce((map, item) => {
  map[item.label] = item.value;
  return map;
}, {});

// AI 返回的分类名到前端分类值的映射
const aiCategoryToValueMap = {
  '空调故障': 'airConditioning',
  '管道故障': 'waterAndElectricity',
  '电力故障': 'waterAndElectricity',
  '网络故障': 'networkIssues',
  '家具故障': 'furnitureRepair',
  '门窗故障': 'doorWindowRepair',
  '其他故障': 'other',
};

const normalizeText = (value) => (value || '').replace(/\s+/g, ' ').trim();

const extractLocationFallback = (text, currentLocation = '') => {
  const existingLocation = normalizeText(currentLocation);
  if (existingLocation) {
    return existingLocation;
  }

  const input = normalizeText(text);
  const patterns = [
    /([一二三四五六七八九十0-9]+号?(?:宿舍楼|宿舍|公寓|教学楼|实验楼|楼|栋|幢)\s*\d{1,2}[-—]\d{2,4})/,
    /((?:宿舍楼|宿舍|公寓|教学楼|实验楼|图书馆|食堂|体育馆)\s*\d+\s*楼\s*(?:卫生间|洗手间|浴室|走廊|大厅|教室|实验室|房间|宿舍)?)/,
    /([一二三四五六七八九十0-9]+号?(?:宿舍楼|公寓|教学楼|实验楼|楼|栋|幢)\s*\d{2,4}(?:室|房间|宿舍)?)/,
    /((?:图书馆|食堂|教学楼|实验楼|体育馆|操场|宿舍楼|宿舍|公寓)[\u4e00-\u9fa5A-Za-z0-9\s\-—]{0,16}(?:卫生间|洗手间|浴室|走廊|大厅|教室|实验室|门口|入口|房间|宿舍))/,
  ];

  for (const pattern of patterns) {
    const match = input.match(pattern);
    if (match?.[1]) {
      return normalizeText(match[1]).replace(/^[在于到至\s]+/, '').replace(/[的,，。；;：:\s]+$/, '');
    }
  }
  return '';
};

const inferIssueName = (text, category) => {
  const input = normalizeText(text).toLowerCase();
  if (input.includes('漏水') || input.includes('滴水') || input.includes('积水')) return '漏水故障';
  if (input.includes('堵塞') || input.includes('下水') || input.includes('地漏')) return '堵塞故障';
  if (input.includes('频闪') || input.includes('照明') || input.includes('灯')) return '照明故障';
  if (input.includes('插座')) return '插座故障';
  if (input.includes('断电') || input.includes('跳闸')) return '电力故障';
  if (input.includes('wifi') || input.includes('网络') || input.includes('网口')) return '网络故障';
  if (input.includes('空调')) return '空调故障';
  if (input.includes('门锁') || input.includes('门')) return '门锁故障';
  if (input.includes('窗') || input.includes('玻璃')) return '门窗故障';
  if (input.includes('桌') || input.includes('椅') || input.includes('床') || input.includes('柜')) return '家具故障';
  return category?.endsWith('故障') ? category : '维修问题';
};

const buildTitleFallback = (text, category, location) => {
  const issueName = inferIssueName(text, category);
  const title = location ? `${location}${issueName}` : `${issueName}报修`;
  return title.slice(0, 60);
};

const normalizeAiResult = (result = {}, text = '', currentLocation = '') => {
  const locationText = normalizeText(result.locationText || result.location) ||
                       extractLocationFallback(text, currentLocation);
  const title = normalizeText(result.title) ||
                buildTitleFallback(text, result.category, locationText);
  return {
    ...result,
    title,
    locationText,
    location: locationText,
  };
};

const CreateRepairPage = ({ currentUser, onSubmitSuccess }) => {
  const [form] = Form.useForm();
  const [fileList, setFileList] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [aiText, setAiText] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState(null);
  const [similarTickets, setSimilarTickets] = useState([]);
  const [knowledgeRecommendations, setKnowledgeRecommendations] = useState([]);
  const similarTicketTimerRef = useRef(null);

  // AI 分析结果弹窗状态
  const [aiModalVisible, setAiModalVisible] = useState(false);
  const [submittedAiResult, setSubmittedAiResult] = useState(null);

  // 处理文件上传
  const handleUploadChange = ({ fileList: newFileList }) => {
    setFileList(newFileList);
  };

  const handleAiAnalyze = async () => {
    if (!aiText.trim()) {
      message.warning('请先输入一段报修描述');
      return;
    }
    setAiLoading(true);
    try {
      // 调用 qiyun-ai-service 的 AI 分析接口
      const response = await api.ai.analyzeTicketV2({
        description: aiText,
        location: form.getFieldValue('location') || ''
      });
      const result = response?.data || response;

      // AI 分类名映射到前端分类值
      const categoryValue = aiCategoryToValueMap[result.category] ||
                            categoryLabelToValue[result.category] ||
                            'other';

      // AI 紧急程度映射到前端优先级
      const urgencyToPriorityMap = {
        '紧急': 'high',
        '普通': 'medium',
        '一般': 'low',
        high: 'high',
        medium: 'medium',
        low: 'low',
      };
      const priorityValue = urgencyToPriorityMap[result.urgency] || urgencyToPriorityMap[String(result.urgency || '').toLowerCase()] || 'low';
      const normalizedResult = normalizeAiResult(result, aiText, form.getFieldValue('location') || '');

      form.setFieldsValue({
        title: normalizedResult.title,
        location: normalizedResult.locationText,
        category: categoryValue,
        description: aiText,
        priority: priorityValue,
      });
      setAiResult(normalizedResult);
      message.success('智能识别完成，已自动填写报修表单');
    } catch (error) {
      console.error('智能识别失败:', error);
      message.error(`智能识别失败：${error.message}`);
    } finally {
      setAiLoading(false);
    }
  };

  const refreshSimilarTickets = async () => {
    const values = form.getFieldsValue();
    const categoryLabel = CATEGORY_OPTIONS.find(item => item.value === values.category)?.label;
    if (!values.description && !values.location && !categoryLabel) {
      return;
    }
    try {
      const response = await api.ai.findSimilarTickets({
        description: values.description,
        locationText: values.location,
        categoryKey: categoryLabel,
        limit: 5,
      });
      setSimilarTickets(Array.isArray(response?.data) ? response.data : []);
    } catch (error) {
      console.warn('相似工单检索失败:', error);
    }
  };

  // 处理文件删除
  const handleRemove = (file) => {
    const newFileList = fileList.filter(item => item.uid !== file.uid);
    setFileList(newFileList);
  };

  // 处理表单提交
  const handleFormSubmit = async (values) => {
    setSubmitting(true);
    try {
      // 创建一个包含所有字段的对象
      const orderData = {
        ...values,
        studentId: currentUser?.studentID,
        locationText: values.location,
        categoryId: getCategoryID(values.category)
      };

      console.log('提交的报修数据:', orderData);

      // 调用服务创建报修
      const newOrder = await repairService.createRepairOrder(orderData, fileList);
      console.log('创建报修成功:', newOrder);

      // 尝试调用 AI 分析接口获取分析结果
      try {
        const aiResponse = await api.ai.analyzeTicketV2({
          description: values.description,
          location: values.location
        });
        console.log('AI 分析结果:', aiResponse);

        if (aiResponse?.data) {
          setSubmittedAiResult(normalizeAiResult(aiResponse.data, values.description, values.location));
          setAiModalVisible(true);
        }
      } catch (aiError) {
        console.warn('AI 分析调用失败（不影响报单创建）:', aiError);
        // AI 分析失败不显示弹窗，直接显示成功消息
        message.success('报修申请提交成功！');
      }

      // 如果 AI 分析成功，弹窗中已显示结果
      // 如果 AI 分析失败，显示普通成功消息
      if (!submittedAiResult) {
        message.success('报修申请提交成功！');
      }

      // 重置表单
      form.resetFields();
      setFileList([]);
      setAiText('');
      setAiResult(null);
      setSimilarTickets([]);
      setKnowledgeRecommendations([]);

      // 通知父组件刷新数据
      if (onSubmitSuccess) {
        onSubmitSuccess();
      }
    } catch (error) {
      console.error('提交报修申请失败:', error);
      message.error('提交报修申请失败，请重试！');
    } finally {
      setSubmitting(false);
    }
  };

  // 获取分类ID
  const getCategoryID = (category) => {
    const match = CATEGORY_OPTIONS.find(item => item.value === category);
    return match?.id || 1;
  };

  // 上传组件配置
  const uploadProps = {
    beforeUpload: (file) => {
      if (fileList.length >= 5) {
        message.error('最多上传5张图片');
        return Upload.LIST_IGNORE;
      }
      const isImage = file.type.startsWith('image/');
      if (!isImage) {
        message.error('只能上传图片文件!');
        return Upload.LIST_IGNORE;
      }
      
      const isLt5M = file.size / 1024 / 1024 < 5;
      if (!isLt5M) {
        message.error('图片大小不能超过5MB!');
        return Upload.LIST_IGNORE;
      }
      
      return false;
    },
    fileList,
    onChange: handleUploadChange,
    onRemove: handleRemove,
    multiple: true,
    accept: 'image/*',
  };

  return (
    <div
      style={{
        padding: "20px 0",
      }}
    >
      <Card
        title={<span style={{ fontSize: "16px", fontWeight: "600", color: "#1f1f1f" }}>创建报修申请</span>}
        style={{
          width: "100%",
          maxWidth: "800px",
          margin: "0 auto",
          boxShadow: "0 2px 8px rgba(15, 82, 186, 0.06)",
          borderRadius: "8px",
          border: "none",
          background: "#FFFFFF",
        }}
        headStyle={{
          background: "#FFFFFF",
          borderBottom: "1px solid #e8e8e8",
          padding: "16px 20px",
        }}
        styles={{ body: {
          padding: "20px",
          background: "#F8FAFC",
        }}}
      >
        <Card
          size="small"
          title={
            <Space>
              <BulbOutlined />
              <span>AI 智能填写</span>
            </Space>
          }
          style={{ marginBottom: 16, borderRadius: 8 }}
        >
          <Space direction="vertical" style={{ width: "100%" }} size={12}>
            <TextArea
              rows={4}
              value={aiText}
              onChange={(event) => setAiText(event.target.value)}
              placeholder="例如：三号宿舍楼 6-612 的照明灯一直频闪，晚上学习很受影响，担心线路有问题。"
              maxLength={500}
              showCount
            />
            <Space wrap>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={aiLoading}
                onClick={handleAiAnalyze}
              >
                智能识别并填写
              </Button>
              <Button onClick={() => setAiText('宿舍 3 楼卫生间水一直漏，地面很滑。')}>
                快速示例
              </Button>
            </Space>
            {aiResult?.safetyTips && (
              <Alert
                type="warning"
                showIcon
                message="安全提醒"
                description={aiResult.safetyTips}
              />
            )}
            {aiResult?.source && (
              <div style={{ color: "#64748b", fontSize: 12 }}>
                识别来源：{aiResult.source}
              </div>
            )}
          </Space>
        </Card>

        {(similarTickets.length > 0 || knowledgeRecommendations.length > 0) && (
          <Card size="small" title="智能参考" style={{ marginBottom: 16, borderRadius: 8 }}>
            {similarTickets.length > 0 && (
              <div style={{ marginBottom: 12 }}>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>附近或同类相似工单</div>
                <List
                  size="small"
                  dataSource={similarTickets}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space wrap>
                            <span>#{item.ticketId} {item.categoryName}</span>
                            <Tag color={item.similarity >= 0.5 ? "red" : "blue"}>
                              相似度 {Math.round((item.similarity || 0) * 100)}%
                            </Tag>
                          </Space>
                        }
                        description={`${item.locationText || '未知位置'}｜${item.description || ''}`}
                      />
                    </List.Item>
                  )}
                />
              </div>
            )}
            {knowledgeRecommendations.length > 0 && (
              <div>
                <div style={{ fontWeight: 600, marginBottom: 8 }}>相关维修知识库建议</div>
                <List
                  size="small"
                  dataSource={knowledgeRecommendations}
                  renderItem={(item) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space wrap>
                            <span>{item.title}</span>
                            {item.estimatedMinutes && <Tag>{item.estimatedMinutes} 分钟</Tag>}
                          </Space>
                        }
                        description={item.safetyNotes || item.solutionSteps}
                      />
                    </List.Item>
                  )}
                />
              </div>
            )}
          </Card>
        )}

        <Form
          form={form}
          layout="vertical"
          onFinish={handleFormSubmit}
          onValuesChange={(_, allValues) => {
            const shouldSearch = allValues.description || allValues.location || allValues.category;
            if (shouldSearch) {
              window.clearTimeout(similarTicketTimerRef.current);
              similarTicketTimerRef.current = window.setTimeout(refreshSimilarTickets, 600);
            }
          }}
          autoComplete="off"
          style={{
            minHeight: "min-content",
          }}
        >
          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>报修标题</span>}
            name="title"
            rules={[
              { required: true, message: "请输入报修标题!" },
              { min: 4, message: "标题至少4个字符" },
              { max: 80, message: "标题不能超过80个字符" },
              { whitespace: true, message: "标题不能只包含空格" },
            ]}
          >
            <Input placeholder="请输入报修标题" />
          </Form.Item>

          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>报修分类</span>}
            name="category"
            rules={[{ required: true, message: "请选择报修分类!" }]}
          >
            <Select placeholder="请选择报修分类">
              {CATEGORY_OPTIONS.map(item => (
                <Option key={item.value} value={item.value}>{item.label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>具体位置</span>}
            name="location"
            rules={[
              { required: true, message: "请输入具体位置!" },
              { min: 4, message: "位置至少4个字符，建议包含楼栋和房间号" },
              { max: 100, message: "位置不能超过100个字符" },
              { whitespace: true, message: "位置不能只包含空格" },
            ]}
          >
            <Input placeholder="例如：3栋502寝室" />
          </Form.Item>

          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>问题描述</span>}
            name="description"
            rules={[
              { required: true, message: "请输入问题描述!" },
              { min: 10, message: "请至少填写10个字符，便于维修人员判断问题" },
              { max: 500, message: "问题描述不能超过500个字符" },
              { whitespace: true, message: "问题描述不能只包含空格" },
            ]}
          >
            <TextArea
              rows={6}
              placeholder="请详细描述您遇到的问题..."
              maxLength={500}
              showCount
            />
          </Form.Item>

          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>紧急程度</span>}
            name="priority"
            rules={[{ required: true, message: '请选择紧急程度!' }]}
            initialValue="low"
          >
            <Select placeholder="请选择紧急程度">
              <Option value="low">低</Option>
              <Option value="medium">中</Option>
              <Option value="high">高</Option>
            </Select>
          </Form.Item>

          <Form.Item
            label={<span style={{ fontSize: "15px", fontWeight: "500", color: "#1f1f1f" }}>上传相关图片</span>}
            extra={<span style={{ fontSize: "13px", color: "#8c8c8c" }}>支持上传多张图片，每张图片大小不超过5MB</span>}
          >
            <Upload
              {...uploadProps}
              listType="picture"
              showUploadList={{
                showPreviewIcon: true,
                showRemoveIcon: true,
              }}
            >
              <Button icon={<UploadOutlined />}>选择图片</Button>
            </Upload>
          </Form.Item>

          <Form.Item
            style={{
              textAlign: "center",
              marginTop: "24px",
              marginBottom: 0,
              flexShrink: 0,
            }}
          >
            <Button
              type="primary"
              htmlType="submit"
              loading={submitting}
              style={{
                width: "140px",
                marginRight: "16px",
              }}
            >
              提交报修申请
            </Button>
            <Button
              style={{
                width: "100px",
              }}
              onClick={() => {
                form.resetFields();
                setFileList([]);
              }}
            >
              重置
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* AI 分析结果弹窗 */}
      <Modal
        title={
          <Space>
            <CheckCircleOutlined style={{ color: '#52c41a' }} />
            <span>报修申请提交成功</span>
          </Space>
        }
        open={aiModalVisible}
        onCancel={() => {
          setAiModalVisible(false);
          setSubmittedAiResult(null);
        }}
        footer={[
          <Button key="close" type="primary" onClick={() => {
            setAiModalVisible(false);
            setSubmittedAiResult(null);
          }}>
            知道了
          </Button>
        ]}
        width={600}
      >
        <Alert
          message="您的报修申请已成功提交，以下是 AI 智能分析结果"
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
        />

        {submittedAiResult && (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={<strong>报修标题</strong>}>
              {submittedAiResult.title || '未识别'}
            </Descriptions.Item>
            <Descriptions.Item label={<strong>具体位置</strong>}>
              {submittedAiResult.locationText || submittedAiResult.location || '未识别'}
            </Descriptions.Item>
            <Descriptions.Item label={<strong>故障类型</strong>}>
              <Tag color="blue">{submittedAiResult.category || '未分类'}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label={<strong>紧急程度</strong>}>
              <Tag color={
                submittedAiResult.urgency === '紧急' ? 'red' :
                submittedAiResult.urgency === '普通' ? 'orange' : 'green'
              }>
                {submittedAiResult.urgency || '一般'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={<strong>维修建议</strong>}>
              {submittedAiResult.suggestion || '暂无建议'}
            </Descriptions.Item>
            {submittedAiResult.source && (
              <Descriptions.Item label={<strong>识别来源</strong>}>
                {submittedAiResult.source}
              </Descriptions.Item>
            )}
            {submittedAiResult.keywords && submittedAiResult.keywords.length > 0 && (
              <Descriptions.Item label={<strong>关键词</strong>}>
                <Space wrap>
                  {submittedAiResult.keywords.map((keyword, index) => (
                    <Tag key={index} color="geekblue">{keyword}</Tag>
                  ))}
                </Space>
              </Descriptions.Item>
            )}
          </Descriptions>
        )}

        <Alert
          message="维修人员将尽快处理您的报修请求，请保持联系方式畅通"
          type="info"
          showIcon
          style={{ marginTop: 16 }}
        />
      </Modal>
    </div>
  );
};

export default CreateRepairPage;
