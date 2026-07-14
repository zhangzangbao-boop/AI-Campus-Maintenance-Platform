package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.dto.KnowledgeBaseDto;
import com.qiyun.opsservice.dto.request.KnowledgeBaseRequest;
import com.qiyun.opsservice.service.KnowledgeBaseService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/api/knowledge-base/search")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> search(
            @RequestParam(value = "categoryKey", required = false) String categoryKey,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return success(knowledgeBaseService.list(categoryKey, keyword, false), "获取成功");
    }

    @GetMapping("/api/knowledge-base/recommend")
    @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
    public Map<String, Object> recommend(
            @RequestParam(value = "categoryKey", required = false) String categoryKey,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return success(knowledgeBaseService.recommend(categoryKey, text, limit), "推荐成功");
    }

    @GetMapping("/api/admin/knowledge-base")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminList(
            @RequestParam(value = "categoryKey", required = false) String categoryKey,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDisabled", defaultValue = "true") boolean includeDisabled) {
        List<KnowledgeBaseDto> list = knowledgeBaseService.list(categoryKey, keyword, includeDisabled);
        return success(list, "获取成功");
    }

    @PostMapping("/api/admin/knowledge-base")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody KnowledgeBaseRequest request) {
        return success(knowledgeBaseService.create(request), "新增成功");
    }

    @PutMapping("/api/admin/knowledge-base/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> update(@PathVariable("id") Long id,
                                      @RequestBody KnowledgeBaseRequest request) {
        return success(knowledgeBaseService.update(id, request), "更新成功");
    }

    @DeleteMapping("/api/admin/knowledge-base/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        knowledgeBaseService.delete(id);
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}