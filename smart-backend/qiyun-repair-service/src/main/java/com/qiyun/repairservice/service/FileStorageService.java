package com.qiyun.repairservice.service;

import com.qiyun.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_IMAGE_COUNT = 5;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp"
    );

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    public List<String> storeImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> validFiles = files.stream()
            .filter(file -> file != null && !file.isEmpty())
            .toList();
        if (validFiles.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException("每条记录最多上传 " + MAX_IMAGE_COUNT + " 张图片");
        }
        validFiles.forEach(this::validateImage);
        List<String> storedUrls = new java.util.ArrayList<>();
        try {
            Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            for (MultipartFile file : validFiles) {
                storedUrls.add(storeValidatedImage(file, uploadDir));
            }
            return storedUrls;
        } catch (RuntimeException | IOException e) {
            deleteStoredFiles(storedUrls);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("图片保存失败: " + e.getMessage());
        }
    }

    public void deleteStoredFiles(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        for (String url : urls) {
            if (url == null || !url.startsWith("/uploads/")) {
                continue;
            }
            String filename = url.substring(url.lastIndexOf('/') + 1);
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                continue;
            }
            Path target = uploadDir.resolve(filename).normalize();
            if (!target.startsWith(uploadDir)) {
                continue;
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Best-effort cleanup after transaction failure.
            }
        }
    }

    private String storeValidatedImage(MultipartFile file, Path uploadDir) {
        validateImage(file);
        String contentType = normalizeContentType(file);
        String filename = UUID.randomUUID() + EXTENSION_BY_CONTENT_TYPE.get(contentType);
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
        if (file.getSize() <= 0) {
            throw new BusinessException("图片不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException("单张图片大小不能超过 5MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("图片文件名不能为空");
        }
        String filename = Paths.get(originalFilename).getFileName().toString();
        if (!filename.equals(originalFilename) || filename.contains("..")) {
            throw new BusinessException("非法文件名");
        }
        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 JPG、PNG、WebP 图片");
        }
        String contentType = normalizeContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 JPG、PNG、WebP 图片");
        }
        if (!extensionMatchesContentType(extension, contentType)) {
            throw new BusinessException("图片扩展名与文件类型不匹配");
        }
        if (!matchesContentType(file, contentType)) {
            throw new BusinessException("图片内容与文件类型不匹配");
        }
    }

    private String normalizeContentType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean extensionMatchesContentType(String extension, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/png" -> "png".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            default -> false;
        };
    }

    private boolean matchesContentType(MultipartFile file, String contentType) {
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4) {
                return false;
            }
            if ("image/jpeg".equals(contentType)) {
                return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            }
            if ("image/png".equals(contentType)) {
                return bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A;
            }
            if ("image/webp".equals(contentType)) {
                return bytes.length >= 12
                    && bytes[0] == 0x52
                    && bytes[1] == 0x49
                    && bytes[2] == 0x46
                    && bytes[3] == 0x46
                    && bytes[8] == 0x57
                    && bytes[9] == 0x45
                    && bytes[10] == 0x42
                    && bytes[11] == 0x50;
            }
            return false;
        } catch (IOException e) {
            throw new BusinessException("图片读取失败: " + e.getMessage());
        }
    }
}
