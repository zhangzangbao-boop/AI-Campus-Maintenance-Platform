package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.dto.AnnouncementDto;
import com.qiyun.opsservice.dto.request.AnnouncementRequest;
import com.qiyun.opsservice.service.AnnouncementService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/api/announcements")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> listVisible() {
        return success("公告获取成功", announcementService.listVisible());
    }

    @GetMapping("/api/announcements/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> getVisible(@PathVariable("id") Long id) {
        return success("公告获取成功", announcementService.getVisible(id));
    }

    @GetMapping("/api/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminList() {
        List<AnnouncementDto> announcements = announcementService.adminList();
        return success("公告获取成功", announcements);
    }

    @GetMapping("/api/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminGet(@PathVariable("id") Long id) {
        return success("公告获取成功", announcementService.adminGet(id));
    }

    @PostMapping("/api/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody AnnouncementRequest request) {
        return success("公告已创建", announcementService.create(request));
    }

    @PutMapping("/api/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody AnnouncementRequest request) {
        return success("公告已更新", announcementService.update(id, request));
    }

    @PostMapping("/api/admin/announcements/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> publish(@PathVariable("id") Long id) {
        return success("公告已发布", announcementService.publish(id));
    }

    @PostMapping("/api/admin/announcements/{id}/withdraw")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> withdraw(@PathVariable("id") Long id) {
        return success("公告已撤回", announcementService.withdraw(id));
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        announcementService.delete(id);
    }

    private Map<String, Object> success(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
