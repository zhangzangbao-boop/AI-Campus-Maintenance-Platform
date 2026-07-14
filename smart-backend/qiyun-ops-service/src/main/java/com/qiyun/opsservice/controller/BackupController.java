package com.qiyun.opsservice.controller;

import com.qiyun.opsservice.dto.BackupDto;
import com.qiyun.opsservice.service.BackupService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/backup")
public class BackupController {

    private final BackupService backupService;

    /**
     * 创建数据库备份
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createBackup() {
        log.info("管理员请求创建数据库备份");
        try {
            BackupDto backup = backupService.performBackup();
            return ResponseEntity.ok(success(backup, "备份创建成功"));
        } catch (Exception e) {
            log.error("创建备份失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("备份失败: " + e.getMessage()));
        }
    }

    /**
     * 获取备份列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> listBackups() {
        log.info("管理员请求获取备份列表");
        try {
            List<BackupDto> backups = backupService.listBackups();
            return ResponseEntity.ok(success(backups, "获取成功"));
        } catch (Exception e) {
            log.error("获取备份列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("获取备份列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取备份状态
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBackupStatus() {
        log.info("管理员请求获取备份状态");
        try {
            Map<String, Object> status = backupService.getBackupStatus();
            return ResponseEntity.ok(success(status, "获取成功"));
        } catch (Exception e) {
            log.error("获取备份状态失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("获取备份状态失败: " + e.getMessage()));
        }
    }

    /**
     * 恢复数据库
     */
    @PostMapping("/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> restoreBackup(
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> request) {
        String fileName = request.get("fileName");
        log.info("管理员请求恢复数据库: fileName={}", fileName);
        try {
            backupService.restoreBackup(fileName);
            return ResponseEntity.ok(success(null, "数据库恢复成功"));
        } catch (Exception e) {
            log.error("恢复数据库失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("恢复失败: " + e.getMessage()));
        }
    }

    /**
     * 删除备份文件
     */
    @DeleteMapping("/{fileName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteBackup(@PathVariable("fileName") String fileName) {
        log.info("管理员请求删除备份文件: fileName={}", fileName);
        try {
            backupService.deleteBackup(fileName);
            return ResponseEntity.ok(success(null, "删除成功"));
        } catch (Exception e) {
            log.error("删除备份文件失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("删除失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }
}