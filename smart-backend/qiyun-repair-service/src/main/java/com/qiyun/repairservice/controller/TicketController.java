package com.qiyun.repairservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.domain.enums.RepairProcessActionType;
import com.qiyun.repairservice.domain.enums.TicketStatus;
import com.qiyun.repairservice.dto.AiTicketAnalysisViewDto;
import com.qiyun.repairservice.dto.CompletionSummaryDto;
import com.qiyun.repairservice.dto.HistoricalRepairCaseDto;
import com.qiyun.repairservice.dto.RepairProcessRecordDto;
import com.qiyun.repairservice.dto.StaffDashboardDto;
import com.qiyun.repairservice.dto.StaffRecommendationDto;
import com.qiyun.repairservice.dto.TicketDetailDto;
import com.qiyun.repairservice.dto.TicketSummaryDto;
import com.qiyun.repairservice.dto.request.AiTicketAnalysisCorrectionRequest;
import com.qiyun.repairservice.dto.request.RepairProcessRecordRequest;
import com.qiyun.repairservice.dto.request.StudentCompletionRejectRequest;
import com.qiyun.repairservice.dto.request.TicketAssignRequest;
import com.qiyun.repairservice.dto.request.TicketCreateRequest;
import com.qiyun.repairservice.dto.request.TicketRatingRequest;
import com.qiyun.repairservice.dto.request.TicketStatusUpdateRequest;
import com.qiyun.repairservice.service.RepairProcessRecordService;
import com.qiyun.repairservice.service.TicketService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;
    private final RepairProcessRecordService repairProcessRecordService;

    // 婵犮垼娉涚€氼噣骞冩繝鍥х鐎广儱娴傛导鍌炴煕濞嗘劕鐏撮柍褜鍏涢懗璺衡枔?multipart/form-data 闂佺缈伴崕浼村箞閵娾晛绀嗘繛鎴烆焽缁憋箓鎮归崶顒佹暠闁?
    // 婵炶揪缍€濞夋洟寮?@ModelAttribute 闂佸搫娲ら妵姗€宕?@RequestPart闂佹寧绋戦張顒€煤鐠恒劉鍋撶涵鍛撴繝銏☆殜瀹曠兘鎮ч崼婵嗩槻闂?multipart 闁荤姴娲弨閬嶆儑?
    @PostMapping(
            value = "/repair-orders",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, "multipart/form-data"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createWithFiles(
            // 闂佸憡鎸哥粔鍫曨敂椤掑嫭鎯為柣锝呰嫰婵兠归崗鑲╃叝缂?studentId闂佹寧绋戦惌浣烘崲閺嶎厽鐓傜€光偓閳ь剛鍒掗悜妯尖枖闁逞屽墲缁犳盯鎮剧仦鐐吅闂佹寧绋戦懟顖烇綖閹邦喚纾奸柛顐ｇ矊閳诲繘鏌ｉ～顒€濡肩紓宥咁儔瀹曟粌顓奸崶顭戜划閻熸粎澧楀ú婊堝极閵堝绠?
            @RequestPart(value = "studentId", required = false) String ignoredStudentId,
            @RequestPart(value = "title", required = false) String title,
            @RequestPart(value = "categoryId", required = false) String categoryIdStr,
            @RequestPart(value = "locationText", required = false) String locationText,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "priority", required = false) String priority,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        // 婵犳鍠栭鍥╁垝閹惧顩?SecurityContext 闂佸吋鍎抽崲鑼躲亹閸ヮ亗浜归柟鎯у暱椤ゅ懘鏌ｈ椤曆呯礊瀹ュ鍋ㄩ柕濠忕畱閻撴洟鏌ㄥ☉妯肩劯濞村皷鏅犲畷妤€顓奸崨顓ф瀫缂備焦妫忛崹顖炲触婢舵劖鐒?studentId
        String studentId;
        try {
            studentId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (studentId == null || studentId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        // 闁哄鍎愰崜姘暦?categoryId
        Long categoryId;
        try {
            categoryId = Long.valueOf(categoryIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException("闂佸憡甯掑Λ娑氭偖閻涚眹闂佸搫绉堕崢褏妲愰敓鐘崇叆婵炲棙甯╅崵? " + categoryIdStr);
        }

        TicketCreateRequest request = new TicketCreateRequest(
            studentId,
            categoryId,
            locationText,
            description,
            priority,
            null
        );

        TicketDetailDto detail = ticketService.createTicket(request, images);

        // 婵炴垶鎹佸銊ц姳閿熺姴绀傞柣鎾冲瘨閸熷洭鏌涢幘宕囆ゆい蹇ｅ墰缁辨帡鎮㈤崜渚囦紘闂?{ code, data, message } 闂佸憡绻傜粔瀵歌姳閼碱剛纾奸柟鎯ь嚟閳ь剦鍨堕弫宥呯暆閸愵亞顔愰梻浣瑰絻閼活垱绂掑☉娆戔枖闁逞屽墰娴狅箓宕掑顒傛▉闁?
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "创建成功");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/repair-orders/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> listMyTickets(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        String studentId = currentUserId();
        TicketStatus ticketStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            ticketStatus = mapStatusFromFrontend(status);
        }

        Map<String, Object> data = ticketService.listStudentTicketsPage(
            studentId,
            ticketStatus,
            scope,
            category,
            priority,
            keyword,
            page,
            size
        );

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟閭﹀墮椤?");
        result.put("data", data);
        return result;
    }
    @GetMapping("/repair-orders/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> detail(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id, hasRole("ADMIN"));
        assertCanReadTicket(detail);

        // 闁哄鏅滈弻銊ッ洪弽顐ょ＜闁绘柨澧庨閬嶆煛瀹ュ洤甯剁紒鎲嬬節閹啴宕熼锝嗗劌闁?
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟閭﹀墮椤?");
        result.put("data", detail);
        return result;
    }

    @PostMapping("/repair-orders/{id}/completion-summary/regenerate")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> regenerateCompletionSummary(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(detail);
        }
        CompletionSummaryDto summary = ticketService.regenerateCompletionSummary(id);
        return success("鐎瑰本鍨氶幀鑽ょ波瀹告煡鍣搁弬鎵晸閹?", summary);
    }

    @GetMapping("/repair-orders/{id}/historical-cases")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> recommendHistoricalCases(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(detail);
        }
        List<HistoricalRepairCaseDto> cases = ticketService.recommendHistoricalCases(id);
        return success("闂佺儵鏅犻弲鏌ュ箮閳ь剛绱撴担铏圭瘈闁逛究鍔岄々濂稿醇濠婂懍绱ｉ梺鍏煎劤閸㈣尪銇愰崶顒€绠ｉ柟閭﹀墮椤?", cases);
    }

    @DeleteMapping("/repair-orders/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        // 婵?SecurityContext 闂佸吋鍎抽崲鑼躲亹閸ヮ亗浜归柟鎯у暱椤ゅ懘鏌ｈ椤曆呯礊瀹ュ洠鍋撳☉鍐差洭闁轰焦褰侱
        String studentId;
        try {
            studentId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (studentId == null || studentId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        ticketService.deleteTicket(id, studentId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "閸掔娀娅庨幎銉ゆ叏閸楁洘鍨氶崝?");
        result.put("data", null);
        return result;
    }

    @PutMapping("/repair-orders/{id}/confirm-completion")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> confirmCompletion(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.confirmCompletion(id, currentUserId());
        return success("Repair completion confirmed", detail);
    }

    @PutMapping("/repair-orders/{id}/reject-completion")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> rejectCompletion(@PathVariable("id") Long id,
                                                @Valid @RequestBody StudentCompletionRejectRequest request) {
        TicketDetailDto detail = ticketService.rejectCompletion(id, currentUserId(), request.reason());
        return success("Repair returned to processing", detail);
    }

    @PostMapping("/repair-orders/{id}/evaluate")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String, Object> evaluate(@PathVariable("id") Long id,
                                        @Valid @RequestBody TicketRatingRequest request) {
        TicketRatingRequest checkedRequest = new TicketRatingRequest(
            currentUserId(),
            request.score(),
            request.comment(),
            request.speedRating(),
            request.qualityRating(),
            request.attitudeRating(),
            request.resolved(),
            request.anonymous()
        );
        TicketDetailDto detail = ticketService.rateTicket(id, checkedRequest);

        // 缂傚倷鑳堕崰宥囩博鐎涙ɑ浜ら柡鍌涘缁€鈧紓鍌欑劍閹稿鎮?{ code, data, message }
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "闁荤姴娲ょ€氼亪鎮抽鐐茬闁归偊鍓欓～?");
        result.put("data", detail);
        return result;
    }

    @PostMapping("/tasks/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto assign(@PathVariable("id") Long id,
                                  @RequestBody TicketAssignRequest request) {
        if (request == null || request.staffId() == null || request.staffId().isBlank()) {
            throw new BusinessException("缂傚倷鑳剁换婵嬪箞閵娿儺鍟呮い褍绮氭繛鎴炴尭缁夌兘宕楀Ο鑽も枖闁惧繐鍘滈弫?");
        }
        TicketAssignRequest checkedRequest = new TicketAssignRequest(currentUserId(), request.staffId());
        return ticketService.assignTicket(id, checkedRequest);
    }

    @PutMapping("/tasks/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public Map<String, Object> changeTaskStatus(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> requestBody) {
        // 婵?SecurityContext 闂佸吋鍎抽崲鑼躲亹閸ヮ亗浜归柟鎯у暱椤ゅ懘鏌熼崹顔拘＄紓宥嗘瀹曘劌螖閻?
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        // 婵炲濮村锕傤敋椤掆偓鏁堥柛灞剧懅缁夌厧鈽夐幙鍐ㄥ绩妤犵偛绻樺畷锝夊冀椤愨懣锕傛煙?
        if (hasRole("STAFF") && !hasRole("ADMIN")) {
            assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        }

        String newStatusStr = (String) requestBody.get("newStatus");
        if (newStatusStr == null || newStatusStr.isBlank()) {
            throw new BusinessException("閺傛壆濮搁幀浣风瑝閼虫垝璐熺粚?");
        }

        // 闁诲繐绻愬Λ妤呮偤瑜忕划顓㈡晜閼愁垼娲梺缁橆焾閸╂牠鍩€椤戞寧绁版繛鏉戞楠炴垿锝為锛勵槹闂佸搫顑嗛惌顔戒繆?
        TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            // 闁诲繐绻戠换鍡涙儊椤栨稓顩烽幖绮光偓鎰佹瀫缂備焦妫忛崹铏叏閹间礁绠戝ù锝囩《閸嬫捇宕楅崗澶逛線鎮?
            newStatus = mapStatusFromFrontend(newStatusStr);
        }

        String rejectionReason = (String) requestBody.get("rejectionReason");

        TicketStatusUpdateRequest request = new TicketStatusUpdateRequest(
            operatorId,
            newStatus,
            rejectionReason
        );

        TicketDetailDto detail = ticketService.updateStatus(id, request);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "娴犺濮熼悩鑸碘偓浣规纯閺傜増鍨氶崝?");
        result.put("data", detail);
        return result;
    }

    @PutMapping("/tasks/{id}/complete")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> completeTask(@PathVariable("id") Long id,
                                      @RequestBody Map<String, Object> requestBody) {
        // 婵?SecurityContext 闂佸吋鍎抽崲鑼躲亹閸ヮ亗浜归柟鎯у暱椤ゅ懘鏌熼崹顔拘＄紓宥嗘瀹曘劌螖閻?
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        // 闁诲海鎳撻張顒勫垂濮橆厾顩烽悹鍥ㄥ絻椤倝鏌￠崘顓у晣缂佽鲸绻堥幃鈺呮嚋绾版ê浜惧ù锝呮憸鐎瑰鎮归崶銉ュ姕婵?RESOLVED
        String rejectionReason = (String) requestBody.get("rejectionReason");
        String notes = (String) requestBody.get("notes");

        assertStaffOwnsTicket(ticketService.getTicketDetail(id));

        if (notes != null && !notes.isBlank()) {
            ticketService.updateProcessNotes(id, notes, operatorId);
        }

        TicketStatusUpdateRequest request = new TicketStatusUpdateRequest(
            operatorId,
            TicketStatus.RESOLVED,
            rejectionReason
        );

        TicketDetailDto detail = ticketService.updateStatus(id, request);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "娴犺濮熼悩鑸碘偓浣规纯閺傜増鍨氶崝?");
        result.put("data", detail);
        return result;
    }

    @PutMapping("/tasks/{id}/arrive")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> arriveTask(@PathVariable("id") Long id,
                                          @RequestBody(required = false) Map<String, Object> requestBody) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        String content = textValue(requestBody, "content", "缂佺繝鎱ㄦ禍鍝勬喅瀹告彃鍩屾潏鍓у箛閸﹀搫鑻熷鈧慨瀣壋閺屻儲鏅犻梾婧库偓?");
        String imageUrl = textValue(requestBody, "imageUrl", null);
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(
            id,
            currentUserId(),
            new RepairProcessRecordRequest(RepairProcessActionType.ARRIVED, content, imageUrl)
        );
        return success("閸掓澘婧€绾喛顓诲鍙夊絹娴?", record);
    }

    @PostMapping("/tasks/{id}/process-records")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> addTaskProcessRecord(@PathVariable("id") Long id,
                                                    @Valid @RequestBody RepairProcessRecordRequest request) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(id, currentUserId(), request);
        return success("缂佺繝鎱ㄦ潻鍥┾柤鐠佹澘缍嶅鍙夊絹娴?", record);
    }

    @PostMapping(value = "/tasks/{id}/process-records/with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> addTaskProcessRecordWithImages(@PathVariable("id") Long id,
                                                              @Valid @RequestPart("record") RepairProcessRecordRequest request,
                                                              @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        RepairProcessRecordDto record = repairProcessRecordService.addRecordWithImages(id, currentUserId(), request, images);
        return success("缂佺繝鎱ㄦ潻鍥┾柤鐠佹澘缍嶅鍙夊絹娴?", record);
    }

    @PostMapping("/tasks/{id}/transfer-request")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> requestTransfer(@PathVariable("id") Long id,
                                               @RequestBody Map<String, Object> requestBody) {
        assertStaffOwnsTicket(ticketService.getTicketDetail(id));
        String reason = textValue(requestBody, "reason", "");
        if (reason.isBlank()) {
            reason = textValue(requestBody, "content", "");
        }
        if (reason.isBlank()) {
            throw new BusinessException("鐠囧嘲锝為崘娆掓祮濞叉儳甯崶?");
        }
        RepairProcessRecordDto record = repairProcessRecordService.addRecord(
            id,
            currentUserId(),
            new RepairProcessRecordRequest(RepairProcessActionType.TRANSFER_REQUEST, reason, textValue(requestBody, "imageUrl", null))
        );
        return success("鏉烆剚娣抽悽瀹狀嚞瀹稿弶褰佹禍?", record);
    }

    @GetMapping("/tasks/my")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> listMyTasks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        // 婵炲濮村ú鐎峜urity Context婵炴垶鎼╅崣鈧鐐茬箻瀹曪綁寮借缁夊ジ鏌涢幘宕囆ゆ繛锝庡枟缁岄亶顢欓梻瀵哥煑ID
        String staffId;
        try {
            staffId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (staffId == null || staffId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        // 闁哄鍎愰崜姘暦閺屻儲鍋愰柤鍝ヮ暯閸嬫挻鎷呯粙鎸庮棟闂?
        TicketStatus ticketStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            ticketStatus = mapStatusFromFrontend(status);
        }

        // 婵炶揪缍€濞夋洟寮妶澶婄闁糕剝绋忛埀顒€顦靛濠氬Ψ椤垵娈?
        return ticketService.listStaffTasksPage(staffId, ticketStatus, scope, category, priority, keyword, page, size);
    }

    /**
     * 闂佸吋鍎抽崲鑼躲亹閸モ晝纾肩紓鍫㈠У閸欏繒鈧鎮堕崕杈ㄥ閹邦厽濯存繝濠傚暙闁拌京绱撴担鍝勬灆妞ゆ挻鎮傚顐︽偋閸繄銈?
     */
    @GetMapping("/staff/dashboard")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> getStaffDashboard() {
        String staffId = currentUserId();
        StaffDashboardDto dashboard = ticketService.getStaffDashboard(staffId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "閼惧嘲褰囩紒缈犳叏瀹搞儳绮虹拋鈩冩殶閹诡喗鍨氶崝?");
        result.put("data", dashboard);
        return result;
    }

    /**
     * 闂佸吋鍎抽崲鑼躲亹閸モ晝纾肩紓鍫㈠У閸欏繒鈧鎮堕崕浼村箲閵忋倕绀夐梽鍥敋濞戙垹绠氶柛婊冨暟缁€鍕熆瑜忛崑娑橆啅闁秵鍋嬮柛顐墰缁€澶愭煕閺嵮勫櫣闁诡垰鐗撳鎾级閹搭厽鈻奸梺绋跨箰閻ゅ洦绂掗崼銉ユ瀬闁绘鐗嗙粊锕傛煥?
     */
    @GetMapping("/tasks/{id}/detail")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> getTaskDetailWithRecords(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        assertStaffOwnsTicket(detail);

        // 闁哄鏅滈弻銊ッ洪弽顐ょ＜闁绘柨澧庨閬嶆煛瀹ュ洤甯剁紒鎲嬬節閹啴宕熼锝嗗劌闁?
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟閭﹀墮椤?");
        result.put("data", detail);
        return result;
    }

    // ==================== 閻庤鎮堕崕鍗炵暦閻旂绶為柛鏇ㄥ幗閸婄偞绻涚紙鐘哄厡闁宠銈搁獮鎺楀Ψ閵夈儳绋?====================

    /**
     * 缂傚倷鑳剁换婵嬪箞閵娿儺鍟呴柕澶堝€曢惁婊堟煕閺傝濡介悽顖涙尦瀹?
     * POST /api/tasks/{id}/accept
     *
     * 闂佺粯顭堥崺鏍焵椤戣法鍔嶇紒渚婇檮濞? WAITING_ACCEPT -> IN_PROGRESS
     */
    @PostMapping("/tasks/{id}/accept")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> acceptTask(@PathVariable("id") Long id) {
        String staffId = currentUserId();
        TicketDetailDto detail = ticketService.acceptTicket(id, staffId);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "閼惧嘲褰囬幋鎰");
        result.put("data", detail);
        return result;
    }

    /**
     * 缂傚倷鑳剁换婵嬪箞閵娿儺鍟呴柕澶堝劤閺嗘岸鏌熺€涙ê濮囧ù鍏煎姍瀹?
     * PUT /api/tasks/{id}/resolve
     *
     * 闂佺粯顭堥崺鏍焵椤戣法鍔嶇紒渚婇檮濞? IN_PROGRESS -> RESOLVED
     */
    @PutMapping("/tasks/{id}/resolve")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> resolveTask(@PathVariable("id") Long id,
                                           @RequestBody(required = false) Map<String, Object> requestBody) {
        String staffId = currentUserId();
        String repairNotes = requestBody != null ? (String) requestBody.get("repairNotes") : null;

        TicketDetailDto detail = ticketService.resolveTicket(id, staffId, repairNotes);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "閻庤鎮堕崕鍗炵暦閻斿鍟呴柟缁樺笧閺嗘岸鏌熺€涙ê濮х紒杈ㄧ箘缁灚寰勬繝鍕€€闁诲孩鍐绘俊鍥极閹捐埖鍏滄い鏃€顑欓崥?");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public Map<String, Object> taskDetail(@PathVariable("id") Long id) {
        TicketDetailDto detail = ticketService.getTicketDetail(id);
        assertStaffOwnsTicket(detail);

        // 闁哄鏅滈弻銊ッ洪弽顐ょ＜闁绘柨澧庨閬嶆煛瀹ュ洤甯剁紒鎲嬬節閹啴宕熼锝嗗劌闁?
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟閭﹀墮椤?");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/admin/repair-orders")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listAllTickets(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        TicketStatus targetStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            targetStatus = mapStatusFromFrontend(status);
        }

        Map<String, Object> data = ticketService.listAdminTicketsPage(
            targetStatus,
            category,
            keyword,
            includeDeleted,
            page,
            size
        );

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取成功");
        result.put("data", data);
        return result;
    }

    @PutMapping("/admin/repair-orders/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminAssign(@PathVariable("id") Long id,
                                     @Valid @RequestBody Map<String, String> requestBody) {
        // 婵?SecurityContext 闂佸吋鍎抽崲鑼躲亹閸ヮ亗浜归柟鎯у暱椤ゅ懘鏌熼崹顔拘＄紓宥嗘瀹曘劌螖閻?
        String operatorId;
        try {
            operatorId = SecurityContextHolder.getContext().getAuthentication().getName();
            if (operatorId == null || operatorId.isBlank()) {
                throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
            }
        } catch (Exception e) {
            throw new BusinessException("閺冪姵纭堕懢宄板絿瑜版挸澧犻悽銊﹀煕娣団剝浼呴敍宀冾嚞閸忓牏娅ヨぐ?");
        }

        // 婵炲濮村锕傤敋椤掆偓鏁堥柛灞剧懅缁夌厧鈽夐幙鍐ㄥ绩妤犵偛绻樺畷锝夊冀椤愶絾鈻曟繛锝呮祩閸犳牗瀵奸幀娆?
        String staffId = requestBody.get("repairmanId") != null ? requestBody.get("repairmanId") : requestBody.get("staffId");
        if (staffId == null || staffId.isBlank()) {
            throw new BusinessException("缂傚倷鑳剁换婵嬪箞閵娿儺鍟呮い褍绮氭繛鎴炴尭缁夌兘宕楀Ο鑽も枖闁惧繐鍘滈弫?");
        }

        // 闂佸搫顑呯€氫即鍩€椤掑倸孝闁搞劌閰ｉ弻濠傤吋閸偄娈插┑?
        TicketAssignRequest request = new TicketAssignRequest(operatorId, staffId);
        TicketDetailDto detail = ticketService.assignTicket(id, request);

        // 闁哄鏅滈弻銊ッ洪弽顐ょ＜闁绘柨澧庨閬嶆煛瀹ュ洤甯剁紒?
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "分配成功");
        result.put("data", detail);
        return result;
    }

    @GetMapping("/admin/repair-orders/{id}/recommend-staff")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> recommendStaff(@PathVariable("id") Long id) {
        List<StaffRecommendationDto> recommendations = ticketService.recommendStaffForTicket(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "缂傚倷鑳剁换婵嬪箞閵婏妇顩查柛婵嗗閸犲懘鏌熼幁鎺戝鐎规洘锕㈤獮瀣箛椤掆偓椤?");
        result.put("data", recommendations);
        return result;
    }

    @PutMapping("/admin/repair-orders/{id}/ai-analysis/correction")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> correctAiAnalysis(@PathVariable("id") Long id,
                                                 @Valid @RequestBody AiTicketAnalysisCorrectionRequest request) {
        AiTicketAnalysisViewDto analysis = ticketService.correctAiAnalysis(id, request, currentUserId());
        return success("AI analysis correction saved", analysis);
    }

    @PostMapping("/admin/repair-orders/history-cases/rebuild-index")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> rebuildHistoricalCaseIndex() {
        int count = ticketService.rebuildHistoricalCaseIndex(currentUserId());
        return success("历史维修案例索引重建完成", Map.of("syncedCount", count));
    }

    @PutMapping("/admin/repair-orders/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto adminChangeStatus(@PathVariable("id") Long id,
                                           @RequestBody TicketStatusUpdateRequest request) {
        if (request == null || request.newStatus() == null) {
            throw new BusinessException("鐠囩兘鈧瀚ㄩ弬鎵畱瀹搞儱宕熼悩鑸碘偓?");
        }
        TicketStatusUpdateRequest checkedRequest = new TicketStatusUpdateRequest(
            currentUserId(),
            request.newStatus(),
            request.rejectionReason()
        );
        return ticketService.updateStatus(id, checkedRequest);
    }

    @PutMapping("/admin/repair-orders/{id}/repair-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto updateRepairNotes(@PathVariable("id") Long id,
                                           @RequestBody UpdateNotesRequest request) {
        String operatorId = currentUserId();
        return ticketService.updateRepairNotes(id, request.notes(), operatorId);
    }

    @PutMapping("/admin/repair-orders/{id}/process-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto updateProcessNotes(@PathVariable("id") Long id,
                                             @RequestBody UpdateNotesRequest request) {
        String operatorId = currentUserId();
        return ticketService.updateProcessNotes(id, request.notes(), operatorId);
    }

    @PutMapping("/admin/repair-orders/{id}/estimated-completion-time")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketDetailDto setEstimatedCompletionTime(@PathVariable("id") Long id,
                                                    @RequestBody SetEstimatedTimeRequest request) {
        String operatorId = currentUserId();
        return ticketService.setEstimatedCompletionTime(id, request.estimatedTime(), operatorId);
    }

    // 闁诲繐绻愬Λ妤佹櫠閻樼數鍗氭い鏍ㄧ矊绗戦梺璇″厸缁€渚€鍩€椤掆偓閸氬危瑜忔禍鎼佸礋椤愩垻鍘掗梺鍛婅壘濞寸兘顢旈鍕嚑婵﹩鍋勯々顒勬煕?
    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }

    private boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private void assertCanReadTicket(TicketDetailDto detail) {
        if (hasRole("ADMIN")) {
            return;
        }

        String userId = currentUserId();
        if (hasRole("STUDENT") && userId.equals(detail.studentId())) {
            return;
        }
        if (hasRole("STAFF") && userId.equals(detail.staffId())) {
            return;
        }

        throw new BusinessException(HttpStatus.FORBIDDEN, "瑜版挸澧犵拹锕€褰块弮鐘虫綀閺屻儳婀呯拠銉ヤ紣閸?");
    }

    private void assertStaffOwnsTicket(TicketDetailDto detail) {
        if (!currentUserId().equals(detail.staffId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "瑜版挸澧犵拹锕€褰块弮鐘虫綀閺屻儳婀呯拠銉ф樊娣囶喕鎹㈤崝?");
        }
    }

    private TicketStatus mapStatusFromFrontend(String frontendStatus) {
        String status = frontendStatus.toLowerCase();
        return switch (status) {
            case "pending" -> TicketStatus.WAITING_ACCEPT;
            case "processing" -> TicketStatus.IN_PROGRESS;
            case "awaiting_confirmation", "completed", "resolved" -> TicketStatus.RESOLVED;
            case "to_be_evaluated", "waiting_feedback" -> TicketStatus.WAITING_FEEDBACK;
            case "feedbacked" -> TicketStatus.FEEDBACKED;
            case "closed" -> TicketStatus.CLOSED;
            case "rejected" -> TicketStatus.REJECTED;
            default -> {
                // 闁诲繐绻戠换鍡涙儊椤栫偞鍎庨悗娑櫭径宥夊级閻戝棗澧€规洖寮剁粙澶愭倻濡棿娴锋繛鎴炴尰閸庢娊鍩€?
                try {
                    yield TicketStatus.valueOf(frontendStatus.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("闂佸搫鍟版慨鐢稿疾閵夆晜鍎嶉柛鏇ㄤ簼瀹曟煡鏌涢弮鎾剁？濠殿喗鎮傞獮鈧? " + frontendStatus);
                }
            }
        };
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private String textValue(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(body.get(key)).trim();
    }

    // 闂佸憡鍔曢幊姗€宕曠€靛憡瀚氶梺鍨儑濠€瀵哥磼?
    public record UpdateNotesRequest(String notes) {}

    public record SetEstimatedTimeRequest(java.time.LocalDateTime estimatedTime) {}
}

