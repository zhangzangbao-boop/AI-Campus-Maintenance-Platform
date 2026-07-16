package com.qiyun.repairservice;

import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.domain.enums.UserRole;
import com.qiyun.repairservice.repository.SlaAlertRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.repository.UserReferenceRepository;
import com.qiyun.repairservice.service.SlaAlertService;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SLA告警服务测试
 */
@ExtendWith(MockitoExtension.class)
class SlaAlertServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SlaAlertRepository slaAlertRepository;

    @Mock
    private UserReferenceRepository userReferenceRepository;

    @Mock
    private com.qiyun.repairservice.service.NotificationPushService notificationPushService;

    @InjectMocks
    private SlaAlertService slaAlertService;

    private RepairTicket createTicket(Long id, TicketStatus status, String priority, LocalDateTime createdAt, LocalDateTime assignedAt) {
        RepairTicket ticket = new RepairTicket();
        ticket.setTicketId(id);
        ticket.setStatus(status);
        ticket.setPriority(priority);
        ticket.setCreatedAt(createdAt);
        ticket.setAssignedAt(assignedAt);
        ticket.setDeleted(false);

        Category category = new Category();
        category.setCategoryName("电力故障");
        ticket.setCategory(category);

        ticket.setLocationText("测试楼栋A101");

        return ticket;
    }

    @Test
    @DisplayName("正常工单不触发告警")
    void normalTicketNoAlert() {
        // 工单刚创建，距离截止时间还远
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(1), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        // 活跃工单正常，无告警
        assertEquals(0, result.get("warningCount"));
        assertEquals(0, result.get("overdueCount"));
    }

    @Test
    @DisplayName("即将超时工单触发WARNING告警")
    void warningTicketTriggersAlert() {
        // 中优先级受理时限：8小时，预警阈值：25% = 2小时
        // 创建7小时前，距离截止只剩1小时，触发WARNING
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(7), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(anyLong(), anyString())).thenReturn(false);
        when(slaAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN))
            .thenReturn(java.util.List.of(new UserReference()));
        doNothing().when(notificationPushService).notifyAndPushBatch(any(), anyString(), anyString(), any());

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        assertEquals(1, result.get("warningCount"));
        assertEquals(0, result.get("overdueCount"));
    }

    @Test
    @DisplayName("已超时工单触发OVERDUE告警")
    void overdueTicketTriggersAlert() {
        // 中优先级受理时限：8小时
        // 创建10小时前，已超时2小时，触发OVERDUE
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(10), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(anyLong(), anyString())).thenReturn(false);
        when(slaAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN)).thenReturn(java.util.List.of(new UserReference()));
        doNothing().when(notificationPushService).notifyAndPushBatch(any(), anyString(), anyString(), any());
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        assertEquals(0, result.get("warningCount"));
        assertEquals(1, result.get("overdueCount"));
    }

    @Test
    @DisplayName("已完成的工单不检查SLA")
    void completedTicketNotChecked() {
        LocalDateTime now = LocalDateTime.now();
        // 创建10小时前，已超时，但状态是RESOLVED
        RepairTicket ticket = createTicket(1L, TicketStatus.RESOLVED, "medium", now.minusHours(10), now.minusHours(9));

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        // 已完成工单不计入告警
        assertEquals(0, result.get("warningCount"));
        assertEquals(0, result.get("overdueCount"));
    }

    @Test
    @DisplayName("重复告警被去重")
    void duplicateAlertDeduplicated() {
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(10), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));
        // 已存在OVERDUE告警
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(1L, "OVERDUE")).thenReturn(true);

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        // 去重后不产生新告警
        assertEquals(0, result.get("overdueCount"));
    }

    @Test
    @DisplayName("低优先级升级为中优先级")
    void lowPriorityUpgradedToMedium() {
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "low", now.minusHours(30), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(anyLong(), anyString())).thenReturn(false);
        when(slaAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN)).thenReturn(java.util.List.of(new UserReference()));
        doNothing().when(notificationPushService).notifyAndPushBatch(any(), anyString(), anyString(), any());
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            RepairTicket saved = inv.getArgument(0);
            assertEquals("medium", saved.getPriority());
            return saved;
        });

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        assertEquals(1, result.get("priorityUpgradedCount"));
    }

    @Test
    @DisplayName("高优先级不再升级")
    void highPriorityNotUpgraded() {
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "high", now.minusHours(5), null);

        // 手动调用升级方法
        boolean upgraded = slaAlertService.upgradePriority(ticket);

        assertFalse(upgraded);
        assertEquals("high", ticket.getPriority());
    }

    @Test
    @DisplayName("通知失败不影响其他工单")
    void notificationFailureDoesNotBlockOther() {
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket1 = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(10), null);
        RepairTicket ticket2 = createTicket(2L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(10), null);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket1, ticket2));
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(anyLong(), anyString())).thenReturn(false);
        when(slaAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN))
            .thenThrow(new RuntimeException("通知服务异常")) // 第一次抛异常
            .thenReturn(java.util.List.of(new UserReference())); // 第二次成功
        doNothing().when(notificationPushService).notifyAndPushBatch(any(), anyString(), anyString(), any());
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        // 即使通知失败，也会处理第二个工单
        assertEquals(2, result.get("overdueCount"));
        assertEquals(1, result.get("notificationFailedCount"));
    }

    @Test
    @DisplayName("已删除工单不检查SLA")
    void deletedTicketNotChecked() {
        LocalDateTime now = LocalDateTime.now();
        RepairTicket ticket = createTicket(1L, TicketStatus.WAITING_ACCEPT, "medium", now.minusHours(10), null);
        ticket.setDeleted(true);

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        assertEquals(0, result.get("overdueCount"));
    }

    @Test
    @DisplayName("IN_PROGRESS状态检查完成时限")
    void inProgressChecksCompletionTime() {
        LocalDateTime now = LocalDateTime.now();
        // 中优先级完成时限：72小时，预警阈值：18小时
        // 分配70小时前，距离截止只剩2小时，触发WARNING
        RepairTicket ticket = createTicket(1L, TicketStatus.IN_PROGRESS, "medium", now.minusHours(75), now.minusHours(70));

        when(ticketRepository.findAll()).thenReturn(java.util.List.of(ticket));
        when(slaAlertRepository.existsByTicketIdAndAlertLevel(anyLong(), anyString())).thenReturn(false);
        when(slaAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userReferenceRepository.findByRoleAndIsActiveTrue(UserRole.ADMIN)).thenReturn(java.util.List.of(new UserReference()));
        doNothing().when(notificationPushService).notifyAndPushBatch(any(), anyString(), anyString(), any());

        Map<String, Integer> result = slaAlertService.checkAndAlert();

        assertEquals(1, result.get("warningCount"));
        assertEquals(0, result.get("overdueCount"));
    }
}