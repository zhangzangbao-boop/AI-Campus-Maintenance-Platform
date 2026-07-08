package com.ligong.reportingcenter.controller;

import com.ligong.reportingcenter.dto.BackupDto;
import com.ligong.reportingcenter.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class BackupController {

    private final BackupService backupService;

    /**
     * 手动执行备份
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup() {
        try {
            BackupDto backup = backupService.performBackup();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "备份成功");
            result.put("data", backup);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "备份失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 获取所有备份列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listBackups() {
        try {
            List<BackupDto> backups = backupService.listBackups();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "获取备份列表成功");
            result.put("data", backups);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "获取备份列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> backupStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "获取备份状态成功");
        result.put("data", backupService.getBackupStatus());
        return ResponseEntity.ok(result);
    }

    /**
     * 恢复数据库
     */
    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreBackup(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            if (fileName == null || fileName.isBlank()) {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 400);
                result.put("message", "文件名不能为空");
                return ResponseEntity.status(400).body(result);
            }

            backupService.restoreBackup(fileName);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "恢复成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "恢复失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 删除备份文件
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable("fileName") String fileName) {
        try {
            if (fileName == null || fileName.trim().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("code", 400);
                result.put("message", "文件名不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            backupService.deleteBackup(fileName.trim());

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "备份已删除");
            result.put("fileName", fileName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("删除备份失败: {}", fileName, e);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("message", "删除备份失败: " + e.getMessage());
            result.put("fileName", fileName);
            return ResponseEntity.status(500).body(result);
        }
    }
}


