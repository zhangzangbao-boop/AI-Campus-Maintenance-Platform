package com.qiyun.opsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiServiceClient;
import com.qiyun.feign.client.RepairServiceClient;
import com.qiyun.feign.dto.AnalyzeTicketRequest;
import com.qiyun.feign.dto.AnalyzeTicketResponse;
import com.qiyun.opsservice.domain.entity.FaultTrendAlert;
import com.qiyun.opsservice.domain.entity.Notification;
import com.qiyun.opsservice.domain.entity.UserReference;
import com.qiyun.opsservice.domain.enums.FaultTrendRiskLevel;
import com.qiyun.opsservice.domain.enums.UserRole;
import com.qiyun.opsservice.dto.FaultTrendAlertDto;
import com.qiyun.opsservice.dto.FaultTrendDashboardDto;
import com.qiyun.opsservice.dto.FaultTrendItemDto;
import com.qiyun.opsservice.dto.NotificationDto;
import com.qiyun.opsservice.repository.FaultTrendAlertRepository;
import com.qiyun.opsservice.repository.NotificationRepository;
import com.qiyun.opsservice.repository.UserReferenceRepository;
import com.qiyun.opsservice.websocket.WebSocketPushService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaultTrendAlertService {

    private static final int SEVEN_DAY_PERIOD = 7;
    private static final int THIRTY_DAY_PERIOD = 30;
    private static final int SEVEN_DAY_COUNT_THRESHOLD = 3;
    private static final int THIRTY_DAY_COUNT_THRESHOLD = 6;
    private static final double SEVEN_DAY_GROWTH_THRESHOLD = 50.0;
    private static final double THIRTY_DAY_GROWTH_THRESHOLD = 30.0;
    private static final String DEFAULT_FAULT_TREND_RULES = """
        {
          "sevenDays":{"countThreshold":3,"growthThreshold":50.0},
          "thirtyDays":{"countThreshold":6,"growthThreshold":30.0}
        }
        """;

    private final RepairServiceClient repairServiceClient;
    private final AiServiceClient aiServiceClient;
    private final FaultTrendAlertRepository faultTrendAlertRepository;
    private final UserReferenceRepository userReferenceRepository;
    private final NotificationRepository notificationRepository;
    private final WebSocketPushService webSocketPushService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FaultTrendDashboardDto dashboard(String authorization) {
        List<FaultTrendItemDto> trends = new ArrayList<>();
        trends.addAll(loadTrendItems(authorization, SEVEN_DAY_PERIOD));
        trends.addAll(loadTrendItems(authorization, THIRTY_DAY_PERIOD));
        return new FaultTrendDashboardDto(
            trends,
            faultTrendAlertRepository.findAllByOrderByRiskLevelDescLastDetectedAtDesc().stream()
                .map(this::toDto)
                .toList(),
            LocalDateTime.now(),
            faultTrendRules().sevenDayCountThreshold(),
            faultTrendRules().thirtyDayCountThreshold()
        );
    }

    @Transactional
    public FaultTrendDashboardDto refresh(String authorization) {
        List<FaultTrendItemDto> trends = new ArrayList<>();
        trends.addAll(processPeriod(authorization, SEVEN_DAY_PERIOD));
        trends.addAll(processPeriod(authorization, THIRTY_DAY_PERIOD));
        return new FaultTrendDashboardDto(
            trends,
            faultTrendAlertRepository.findAllByOrderByRiskLevelDescLastDetectedAtDesc().stream()
                .map(this::toDto)
                .toList(),
            LocalDateTime.now(),
            faultTrendRules().sevenDayCountThreshold(),
            faultTrendRules().thirtyDayCountThreshold()
        );
    }

    @Scheduled(cron = "${fault-trend.cron:0 0 * * * ?}")
    public void scheduledRefresh() {
        try {
            refresh(null);
        } catch (Exception e) {
            log.warn("高频故障趋势定时检测失败，不影响主流程: {}", e.getMessage());
        }
    }

    private List<FaultTrendItemDto> processPeriod(String authorization, int periodDays) {
        List<FaultTrendItemDto> result = new ArrayList<>();
        for (TrendRawItem item : loadRawItems(authorization, periodDays)) {
            FaultTrendRiskLevel riskLevel = calculateRisk(item.ticketCount(), item.growthRate(), periodDays);
            boolean triggered = shouldTrigger(item.ticketCount(), item.growthRate(), periodDays);
            if (triggered) {
                boolean created = upsertAlert(item, riskLevel);
                if (created) {
                    notifyAdmins(item, riskLevel);
                }
            }
            result.add(new FaultTrendItemDto(
                item.location(),
                item.category(),
                periodDays,
                item.ticketCount(),
                item.previousCount(),
                item.growthRate(),
                riskLevel,
                triggered
            ));
        }
        return result;
    }

    private List<FaultTrendItemDto> loadTrendItems(String authorization, int periodDays) {
        return loadRawItems(authorization, periodDays).stream()
            .map(item -> new FaultTrendItemDto(
                item.location(),
                item.category(),
                periodDays,
                item.ticketCount(),
                item.previousCount(),
                item.growthRate(),
                calculateRisk(item.ticketCount(), item.growthRate(), periodDays),
                shouldTrigger(item.ticketCount(), item.growthRate(), periodDays)
            ))
            .toList();
    }

    private List<TrendRawItem> loadRawItems(String authorization, int periodDays) {
        Map<String, Object> response = repairServiceClient.getFaultTrendStats(authorization, periodDays);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return Collections.emptyList();
        }
        Object items = dataMap.get("items");
        if (!(items instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(item -> toRawItem(item, periodDays))
            .toList();
    }

    private TrendRawItem toRawItem(Map<?, ?> item, int periodDays) {
        return new TrendRawItem(
            asText(item.get("location"), "未知位置"),
            asText(item.get("category"), "未分类"),
            periodDays,
            asLong(item.get("ticketCount")),
            asLong(item.get("previousCount")),
            asDouble(item.get("growthRate"))
        );
    }

    private boolean upsertAlert(TrendRawItem item, FaultTrendRiskLevel riskLevel) {
        FaultTrendAlert alert = faultTrendAlertRepository
            .findByLocationAndCategoryAndPeriodDays(item.location(), item.category(), item.periodDays())
            .orElse(null);
        boolean created = alert == null;
        if (created) {
            alert = new FaultTrendAlert();
            alert.setLocation(item.location());
            alert.setCategory(item.category());
            alert.setPeriodDays(item.periodDays());
        }
        AlertText alertText = buildAlertText(item, riskLevel);
        alert.setTicketCount(item.ticketCount());
        alert.setPreviousCount(item.previousCount());
        alert.setGrowthRate(item.growthRate());
        alert.setRiskLevel(riskLevel);
        alert.setAiReason(alertText.reason());
        alert.setSuggestion(alertText.suggestion());
        alert.setLastDetectedAt(LocalDateTime.now());
        faultTrendAlertRepository.save(alert);
        return created;
    }

    private AlertText buildAlertText(TrendRawItem item, FaultTrendRiskLevel riskLevel) {
        String ruleReason = "近" + item.periodDays() + "天内，" + item.location() + "的" + item.category()
            + "报修达到" + item.ticketCount() + "单，较上一周期增长" + item.growthRate() + "%。";
        String ruleSuggestion = switch (riskLevel) {
            case CRITICAL -> "建议立即安排专项排查，优先检查公共设备、管线和近期维修闭环质量。";
            case HIGH -> "建议管理员安排集中巡检，并提前准备常用备件和维修人员排班。";
            case MEDIUM -> "建议纳入本周巡检重点，持续观察同地点同分类新增工单。";
            default -> "建议保持常规巡检并关注后续工单变化。";
        };

        try {
            AnalyzeTicketResponse aiResponse = aiServiceClient.extractResponse(aiServiceClient.analyzeTicket(
                new AnalyzeTicketRequest(ruleReason, item.location())
            ));
            if (aiResponse != null) {
                String reason = aiResponse.category() == null || aiResponse.category().isBlank()
                    ? ruleReason
                    : ruleReason + " AI判断主要风险类型为：" + aiResponse.category() + "。";
                String suggestion = aiResponse.suggestion() == null || aiResponse.suggestion().isBlank()
                    ? ruleSuggestion
                    : aiResponse.suggestion();
                return new AlertText(reason, suggestion);
            }
        } catch (Exception e) {
            log.warn("AI趋势预警解释不可用，使用规则化说明: {}", e.getMessage());
        }
        return new AlertText(ruleReason, ruleSuggestion);
    }

    private void notifyAdmins(TrendRawItem item, FaultTrendRiskLevel riskLevel) {
        try {
            List<UserReference> admins = userReferenceRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN));
            if (admins.isEmpty()) {
                return;
            }
            String title = "高频故障趋势预警：" + riskLevel;
            String content = item.location() + " / " + item.category() + " 近" + item.periodDays()
                + "天新增" + item.ticketCount() + "单，增长率" + item.growthRate() + "%。";
            List<Notification> notifications = admins.stream()
                .map(admin -> {
                    Notification notification = new Notification();
                    notification.setReceiver(admin);
                    notification.setTitle(title);
                    notification.setContent(content);
                    notification.setReadFlag(false);
                    notification.setCreatedAt(LocalDateTime.now());
                    return notification;
                })
                .toList();
            notificationRepository.saveAll(notifications);
            webSocketPushService.pushNotificationToMany(
                admins.stream().map(UserReference::getUserId).toList(),
                new NotificationDto(null, null, title, content, null, false, LocalDateTime.now())
            );
        } catch (Exception e) {
            log.warn("趋势预警管理员通知失败，不影响预警生成: {}", e.getMessage());
        }
    }

    private boolean shouldTrigger(long ticketCount, double growthRate, int periodDays) {
        FaultTrendRules rules = faultTrendRules();
        if (periodDays == THIRTY_DAY_PERIOD) {
            return ticketCount >= rules.thirtyDayCountThreshold() && growthRate >= rules.thirtyDayGrowthThreshold();
        }
        return ticketCount >= rules.sevenDayCountThreshold() && growthRate >= rules.sevenDayGrowthThreshold();
    }

    private FaultTrendRiskLevel calculateRisk(long ticketCount, double growthRate, int periodDays) {
        if (ticketCount >= 10 || growthRate >= 200) {
            return FaultTrendRiskLevel.CRITICAL;
        }
        if ((periodDays == SEVEN_DAY_PERIOD && ticketCount >= 5) || ticketCount >= 8 || growthRate >= 100) {
            return FaultTrendRiskLevel.HIGH;
        }
        if (shouldTrigger(ticketCount, growthRate, periodDays)) {
            return FaultTrendRiskLevel.MEDIUM;
        }
        return FaultTrendRiskLevel.LOW;
    }

    private FaultTrendAlertDto toDto(FaultTrendAlert alert) {
        return new FaultTrendAlertDto(
            alert.getAlertId(),
            alert.getLocation(),
            alert.getCategory(),
            alert.getPeriodDays(),
            alert.getTicketCount(),
            alert.getPreviousCount(),
            alert.getGrowthRate(),
            alert.getRiskLevel(),
            alert.getAiReason(),
            alert.getSuggestion(),
            alert.getLastDetectedAt(),
            alert.getCreatedAt(),
            alert.getUpdatedAt()
        );
    }

    private String asText(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return Math.round(number.doubleValue() * 100.0) / 100.0;
        }
        if (value == null) {
            return 0.0;
        }
        return Math.round(Double.parseDouble(String.valueOf(value)) * 100.0) / 100.0;
    }

    private FaultTrendRules faultTrendRules() {
        try {
            String json = systemConfigService.getValue(SystemConfigService.FAULT_TREND_RULES, DEFAULT_FAULT_TREND_RULES);
            JsonNode root = objectMapper.readTree(json);
            JsonNode sevenDays = root.path("sevenDays");
            JsonNode thirtyDays = root.path("thirtyDays");
            return new FaultTrendRules(
                Math.max(1, sevenDays.path("countThreshold").asInt(SEVEN_DAY_COUNT_THRESHOLD)),
                Math.max(0.0, sevenDays.path("growthThreshold").asDouble(SEVEN_DAY_GROWTH_THRESHOLD)),
                Math.max(1, thirtyDays.path("countThreshold").asInt(THIRTY_DAY_COUNT_THRESHOLD)),
                Math.max(0.0, thirtyDays.path("growthThreshold").asDouble(THIRTY_DAY_GROWTH_THRESHOLD))
            );
        } catch (Exception e) {
            log.warn("读取高频故障阈值失败，使用默认规则: {}", e.getMessage());
            return new FaultTrendRules(
                SEVEN_DAY_COUNT_THRESHOLD,
                SEVEN_DAY_GROWTH_THRESHOLD,
                THIRTY_DAY_COUNT_THRESHOLD,
                THIRTY_DAY_GROWTH_THRESHOLD
            );
        }
    }

    private record TrendRawItem(
        String location,
        String category,
        Integer periodDays,
        Long ticketCount,
        Long previousCount,
        Double growthRate
    ) {}

    private record AlertText(String reason, String suggestion) {}

    private record FaultTrendRules(
        int sevenDayCountThreshold,
        double sevenDayGrowthThreshold,
        int thirtyDayCountThreshold,
        double thirtyDayGrowthThreshold
    ) {}
}
