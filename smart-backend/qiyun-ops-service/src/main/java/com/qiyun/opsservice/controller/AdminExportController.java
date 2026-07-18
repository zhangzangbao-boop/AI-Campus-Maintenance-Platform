package com.qiyun.opsservice.controller;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.opsservice.service.AdminExportService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/export")
public class AdminExportController {

    private final AdminExportService adminExportService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("type") String type,
            @RequestParam Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>(params);
        filters.remove("type");
        AdminExportService.ExportFile file = adminExportService.export(type, authorization, filters, currentUserId());
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(encoded, StandardCharsets.UTF_8).build().toString())
            .header("X-Export-Row-Count", String.valueOf(file.rowCount()))
            .body(file.content());
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录后再访问");
        }
        return authentication.getName();
    }
}
