package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final long DEFAULT_MAX_IMAGE_SIZE_MB = 5L;
    private static final int DEFAULT_MAX_IMAGE_COUNT = 5;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    public List<String> storeImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> validFiles = files.stream()
            .filter(file -> file != null && !file.isEmpty())
            .toList();
        if (validFiles.size() > DEFAULT_MAX_IMAGE_COUNT) {
            throw new BusinessException("最多上传 " + DEFAULT_MAX_IMAGE_COUNT + " 张图片");
        }
        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            return validFiles.stream()
                .map(file -> storeImage(file, uploadDir))
                .toList();
        } catch (IOException e) {
            throw new BusinessException("图片保存失败: " + e.getMessage());
        }
    }

    private String storeImage(MultipartFile file, Path uploadDir) {
        validateImage(file);
        String extension = extensionFor(file);
        String filename = UUID.randomUUID() + extension;
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new BusinessException("非法文件路径");
        }
        try {
            Files.write(target, file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("图片保存失败: " + e.getMessage());
        }
        return "/uploads/" + filename;
    }

    private void validateImage(MultipartFile file) {
        if (file.getSize() > DEFAULT_MAX_IMAGE_SIZE_MB * 1024 * 1024) {
            throw new BusinessException("单张图片大小不能超过 " + DEFAULT_MAX_IMAGE_SIZE_MB + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支持 JPG、PNG、WebP 图片");
        }
    }

    private String extensionFor(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}