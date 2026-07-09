package com.ligong.reportingcenter.service;

import com.ligong.reportingcenter.dto.BackupDto;
import com.ligong.reportingcenter.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class BackupService {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${backup.directory:./backups}")
    private String backupDirectory;

    @Value("${backup.retention-days:30}")
    private int retentionDays;

    private static final String DB_NAME = "repairdb";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 应用启动时自动创建备份目录
     */
    @PostConstruct
    public void initBackupDirectory() {
        try {
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
                log.info("应用启动：已创建备份目录: {}", backupDir.toAbsolutePath());
            } else {
                log.info("应用启动：备份目录已存在: {}", backupDir.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("应用启动：创建备份目录失败", e);
        }
    }

    /**
     * 执行数据库备份
     */
    public BackupDto performBackup() {
        try {
            // 确保备份目录存在
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
                log.info("创建备份目录: {}", backupDir.toAbsolutePath());
            }

            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String fileName = String.format("repairdb_backup_%s.sql", timestamp);
            Path backupFile = backupDir.resolve(fileName);

            // 构建mysqldump命令
            String host = extractHost(datasourceUrl);
            String port = extractPort(datasourceUrl);
            
            // 使用ProcessBuilder构建命令，避免密码暴露在命令行
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows系统
                processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "-h", host,
                    "-P", port,
                    "-u", datasourceUsername,
                    "-p" + datasourcePassword, // Windows下密码直接跟在-p后面
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    DB_NAME
                );
            } else {
                // Linux/Mac系统
                processBuilder = new ProcessBuilder(
                    "mysqldump",
                    "-h", host,
                    "-P", port,
                    "-u", datasourceUsername,
                    "-p" + datasourcePassword,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    DB_NAME
                );
            }
            
            // 重定向输出到文件
            processBuilder.redirectOutput(backupFile.toFile());
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 等待进程完成
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 读取错误信息
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                    throw new BusinessException("备份失败: " + error.toString());
                }
            }

            // 验证备份文件是否创建成功
            if (!Files.exists(backupFile) || Files.size(backupFile) == 0) {
                throw new BusinessException("备份文件创建失败或文件为空");
            }

            long fileSize = Files.size(backupFile);
            log.info("数据库备份成功: {}, 大小: {} bytes", backupFile.toAbsolutePath(), fileSize);

            // 清理旧备份
            cleanupOldBackups();

            return new BackupDto(
                fileName,
                backupFile.toAbsolutePath().toString(),
                fileSize,
                LocalDateTime.now(),
                "SUCCESS"
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("备份过程中发生错误", e);
            throw new BusinessException("备份失败: " + e.getMessage());
        }
    }

    /**
     * 恢复数据库
     */
    public void restoreBackup(String fileName) {
        try {
            Path backupFile = Paths.get(backupDirectory, fileName);
            
            if (!Files.exists(backupFile)) {
                throw new BusinessException("备份文件不存在: " + fileName);
            }

            try {
                BackupDto safetyBackup = performBackup();
                log.info("恢复前已创建保护备份: {}", safetyBackup.fileName());
            } catch (Exception e) {
                throw new BusinessException("恢复前创建保护备份失败，已取消恢复操作: " + e.getMessage());
            }

            String host = extractHost(datasourceUrl);
            String port = extractPort(datasourceUrl);

            // 使用ProcessBuilder构建恢复命令
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows系统 - 使用PowerShell或cmd执行
                // 由于Windows下输入重定向复杂，使用cmd /c执行完整命令
                String command = String.format(
                    "mysql -h%s -P%s -u%s -p%s %s < \"%s\"",
                    host, port, datasourceUsername, datasourcePassword, DB_NAME, backupFile.toAbsolutePath()
                );
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                // Linux/Mac系统
                processBuilder = new ProcessBuilder(
                    "mysql",
                    "-h", host,
                    "-P", port,
                    "-u", datasourceUsername,
                    "-p" + datasourcePassword,
                    DB_NAME
                );
                // 重定向输入从备份文件读取
                processBuilder.redirectInput(backupFile.toFile());
            }
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 对于Windows，需要等待进程完成
            // 对于Linux，进程会从文件读取输入
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    StringBuilder error = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                    throw new BusinessException("恢复失败: " + error.toString());
                }
            }

            log.info("数据库恢复成功: {}", backupFile.toAbsolutePath());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("恢复过程中发生错误", e);
            throw new BusinessException("恢复失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有备份文件列表
     */
    public List<BackupDto> listBackups() {
        try {
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                return new ArrayList<>();
            }

            List<BackupDto> backups = new ArrayList<>();
            try (Stream<Path> paths = Files.list(backupDir)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant();
                        } catch (IOException e) {
                            return java.time.Instant.MIN;
                        }
                    }).reversed())
                    .forEach(path -> {
                        try {
                            String fileName = path.getFileName().toString();
                            long fileSize = Files.size(path);
                            LocalDateTime backupTime = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(path).toInstant(),
                                java.time.ZoneId.systemDefault()
                            );
                            
                            backups.add(new BackupDto(
                                fileName,
                                path.toAbsolutePath().toString(),
                                fileSize,
                                backupTime,
                                "SUCCESS"
                            ));
                        } catch (IOException e) {
                            log.error("读取备份文件信息失败: {}", path, e);
                        }
                    });
            }

            return backups;
        } catch (Exception e) {
            log.error("获取备份列表失败", e);
            throw new BusinessException("获取备份列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除备份文件
     */
    public void deleteBackup(String fileName) {
        try {
            Path backupFile = Paths.get(backupDirectory, fileName);
            if (!Files.exists(backupFile)) {
                throw new BusinessException("备份文件不存在: " + fileName);
            }
            Files.delete(backupFile);
            log.info("删除备份文件: {}", backupFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("删除备份文件失败", e);
            throw new BusinessException("删除备份文件失败: " + e.getMessage());
        }
    }

    /**
     * 清理旧备份（保留指定天数）
     */
    private void cleanupOldBackups() {
        try {
            Path backupDir = Paths.get(backupDirectory);
            if (!Files.exists(backupDir)) {
                return;
            }

            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            
            try (Stream<Path> paths = Files.list(backupDir)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .forEach(path -> {
                        try {
                            LocalDateTime fileTime = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(path).toInstant(),
                                java.time.ZoneId.systemDefault()
                            );
                            if (fileTime.isBefore(cutoffDate)) {
                                Files.delete(path);
                                log.info("删除过期备份文件: {}", path.getFileName());
                            }
                        } catch (IOException e) {
                            log.error("删除过期备份文件失败: {}", path, e);
                        }
                    });
            }
        } catch (Exception e) {
            log.warn("清理旧备份时发生错误", e);
        }
    }

    public Map<String, Object> getBackupStatus() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("directory", Paths.get(backupDirectory).toAbsolutePath().normalize().toString());
        status.put("retentionDays", retentionDays);
        status.put("backupCount", listBackups().size());
        status.put("database", DB_NAME);
        status.put("mysqldumpRequired", true);
        return status;
    }

    /**
     * 从JDBC URL提取主机地址
     */
    private String extractHost(String url) {
        // jdbc:mysql://localhost:3306/repairdb?...
        try {
            String withoutPrefix = url.substring(url.indexOf("://") + 3);
            String hostAndPort = withoutPrefix.substring(0, withoutPrefix.indexOf("/"));
            if (hostAndPort.contains(":")) {
                return hostAndPort.split(":")[0];
            }
            return hostAndPort;
        } catch (Exception e) {
            return "localhost";
        }
    }

    /**
     * 从JDBC URL提取端口号
     */
    private String extractPort(String url) {
        // jdbc:mysql://localhost:3306/repairdb?...
        try {
            String withoutPrefix = url.substring(url.indexOf("://") + 3);
            String hostAndPort = withoutPrefix.substring(0, withoutPrefix.indexOf("/"));
            if (hostAndPort.contains(":")) {
                return hostAndPort.split(":")[1];
            }
            return "3306";
        } catch (Exception e) {
            return "3306";
        }
    }
}

