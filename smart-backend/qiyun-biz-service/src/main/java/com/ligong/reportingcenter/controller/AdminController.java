package com.ligong.reportingcenter.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员控制器 - 工单、评价、统计等业务管理
 * 注意：
 * - 审计日志已迁移至 ops-service
 * - 系统配置已迁移至 ops-service
 * - 备份管理已迁移至 ops-service
 * - 转派审批已迁移至 repair-service AdminTransferController
 * - 设施健康已迁移至 ops-service StatisticsController
 * - 通知管理已迁移至 ops-service NotificationController
 * - 知识库已迁移至 ops-service KnowledgeBaseController
 * - 统计接口已迁移至 ops-service StatisticsController
 * - 评价管理已迁移至 ops-service FeedbackController
 *
 * 本Controller保留仅为过渡，后续可能删除
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    // 所有方法已迁移到其他服务
    // 本类保留仅为过渡
}