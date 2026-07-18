package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyun.feign.client.AiInternalClient;
import com.qiyun.feign.client.AiInternalClient.RepairCaseSyncRequest;
import com.qiyun.repairservice.controller.TicketController;
import com.qiyun.repairservice.domain.entity.AiTicketAnalysis;
import com.qiyun.repairservice.domain.entity.Category;
import com.qiyun.repairservice.domain.entity.RepairProcessRecord;
import com.qiyun.repairservice.domain.entity.RepairTicket;
import com.qiyun.repairservice.domain.entity.UserReference;
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.dto.HistoricalRepairCaseDto;
import com.qiyun.repairservice.repository.AiTicketAnalysisRepository;
import com.qiyun.repairservice.repository.RepairProcessRecordRepository;
import com.qiyun.repairservice.repository.TicketRepository;
import com.qiyun.repairservice.service.HistoricalRepairCaseService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HistoricalRepairCaseServiceTests {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private RepairProcessRecordRepository processRecordRepository;
    @Mock
    private AiTicketAnalysisRepository aiTicketAnalysisRepository;
    @Mock
    private AiInternalClient aiInternalClient;

    private HistoricalRepairCaseService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalRepairCaseService(
            ticketRepository,
            processRecordRepository,
            aiTicketAnalysisRepository,
            aiInternalClient,
            new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "internalSecret", "unit-secret");
    }

    @Test
    void syncConfirmedTicketIndexesRepairFactsWithoutStudentIdentity() {
        RepairTicket ticket = ticket(11L, "宿舍3栋402", TicketStatus.WAITING_FEEDBACK);
        ticket.setStudentConfirmedAt(LocalDateTime.now());
        ticket.setStudent(student("张同学", "13800138000"));
        when(ticketRepository.findById(11L)).thenReturn(Optional.of(ticket));
        when(processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket)).thenReturn(List.of(record()));
        when(aiTicketAnalysisRepository.findByTicketId(11L)).thenReturn(Optional.of(summary(11L)));

        service.syncTicketAsync(11L);

        ArgumentCaptor<RepairCaseSyncRequest> captor = ArgumentCaptor.forClass(RepairCaseSyncRequest.class);
        verify(aiInternalClient).syncRepairCase(eq("unit-secret"), eq(11L), captor.capture());
        RepairCaseSyncRequest request = captor.getValue();
        assertThat(request.document()).contains(request.repairMethod(), request.result());
        assertThat(request.document()).doesNotContain("张同学", "13800138000");
        assertThat(request.repairMethod()).isNotBlank();
        assertThat(request.materials()).isNotBlank();
    }

    @Test
    void deleteTicketRemovesVectorDocument() {
        service.deleteTicketAsync(9L);
        verify(aiInternalClient).deleteRepairCase("unit-secret", 9L);
    }

    @Test
    void rebuildIndexesConfirmedCompletedTickets() {
        RepairTicket ticket = ticket(21L, "教学楼", TicketStatus.CLOSED);
        ticket.setStudentConfirmedAt(LocalDateTime.now());
        when(ticketRepository.findConfirmedCompletedTickets()).thenReturn(List.of(ticket));
        when(processRecordRepository.findByTicketOrderByCreatedAtAsc(ticket)).thenReturn(List.of(record()));
        when(aiTicketAnalysisRepository.findByTicketId(21L)).thenReturn(Optional.empty());

        int count = service.rebuildIndex();

        assertThat(count).isEqualTo(1);
        verify(aiInternalClient).syncRepairCase(eq("unit-secret"), eq(21L), any(RepairCaseSyncRequest.class));
    }

    @Test
    void vectorSearchFiltersLowSimilarityAndKeepsSimilarityForGoodMatches() {
        RepairTicket current = ticket(30L, "实验楼", TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(30L)).thenReturn(Optional.of(current));
        when(aiInternalClient.searchRepairCases(eq("unit-secret"), any())).thenReturn(Map.of("data", List.of(
            Map.of("similarity", 0.82, "metadata", Map.of(
                "ticketId", "31", "categoryName", "水电维修", "failureCause", "阀门老化", "repairMethod", "更换阀门", "materials", "阀门", "result", "已修复")),
            Map.of("similarity", 0.2, "metadata", Map.of(
                "ticketId", "32", "categoryName", "水电维修", "failureCause", "无关", "repairMethod", "观察", "materials", "无", "result", "已关闭"))
        )));

        List<HistoricalRepairCaseDto> cases = service.recommendForTicket(30L, 5);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).ticketId()).isEqualTo(31L);
        assertThat(cases.get(0).similarity()).isEqualTo(0.82);
        assertThat(cases.get(0).fallback()).isFalse();
    }

    @Test
    void unavailableVectorSearchFallsBackToCategoryCasesWithoutSimilarity() {
        RepairTicket current = ticket(40L, "实验楼", TicketStatus.IN_PROGRESS);
        RepairTicket history = ticket(41L, "实验楼", TicketStatus.CLOSED);
        history.setStudentConfirmedAt(LocalDateTime.now());
        when(ticketRepository.findById(40L)).thenReturn(Optional.of(current));
        when(aiInternalClient.searchRepairCases(eq("unit-secret"), any())).thenThrow(new RuntimeException("chroma down"));
        when(ticketRepository.findConfirmedCompletedTicketsForFallback("水电维修", 40L)).thenReturn(List.of(history));
        when(processRecordRepository.findByTicketOrderByCreatedAtAsc(history)).thenReturn(List.of(record()));
        when(aiTicketAnalysisRepository.findByTicketId(41L)).thenReturn(Optional.empty());

        List<HistoricalRepairCaseDto> cases = service.recommendForTicket(40L, 5);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).similarity()).isNull();
        assertThat(cases.get(0).fallback()).isTrue();
    }

    @Test
    void rebuildEndpointIsAdminOnly() throws Exception {
        Method method = TicketController.class.getMethod("rebuildHistoricalCaseIndex");
        PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
        assertThat(auth).isNotNull();
        assertThat(auth.value()).contains("hasRole('ADMIN')");
    }

    private RepairTicket ticket(Long id, String location, TicketStatus status) {
        Category category = new Category();
        category.setCategoryName("水电维修");
        RepairTicket ticket = new RepairTicket();
        ticket.setTicketId(id);
        ticket.setCategory(category);
        ticket.setLocationText(location);
        ticket.setDescription("水龙头漏水，地面有积水");
        ticket.setRepairNotes("已更换阀门并测试无漏水");
        ticket.setProcessNotes("关闭水源，拆卸旧阀门，安装新阀门");
        ticket.setStatus(status);
        ticket.setCompletedAt(LocalDateTime.now());
        ticket.setCreatedAt(LocalDateTime.now().minusDays(2));
        return ticket;
    }

    private UserReference student(String name, String phone) {
        UserReference user = new UserReference();
        user.setUserId("student01");
        user.setNickname(name);
        user.setContactPhone(phone);
        return user;
    }

    private RepairProcessRecord record() {
        RepairProcessRecord record = new RepairProcessRecord();
        record.setActionType(RepairProcessActionType.FINISHED);
        record.setContent("现场处理完成");
        record.setRepairDescription("更换阀门并做通水测试");
        record.setMaterialsUsed("阀门、密封胶带");
        return record;
    }

    private AiTicketAnalysis summary(Long ticketId) {
        AiTicketAnalysis analysis = new AiTicketAnalysis();
        analysis.setTicketId(ticketId);
        analysis.setSummary("故障原因为阀门老化，维修过程为更换阀门，处理结果为已修复。");
        analysis.setRawResponse("{\"failureCause\":\"阀门老化\",\"repairProcess\":\"更换阀门\",\"materialsUsed\":\"阀门、密封胶带\",\"result\":\"已修复\"}");
        return analysis;
    }
}

