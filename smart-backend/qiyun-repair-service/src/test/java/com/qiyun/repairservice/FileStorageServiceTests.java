package com.qiyun.repairservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qiyun.common.exception.BusinessException;
import com.qiyun.repairservice.service.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTests {

    private FileStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new FileStorageService();
        Path uploadDir = Path.of("target", "test-uploads", "f10-file-storage").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());
    }

    @Test
    void storesValidPngWithRandomUploadUrl() {
        List<String> urls = service.storeImages(List.of(file("site.png", "image/png", pngBytes())));

        assertThat(urls).hasSize(1);
        assertThat(urls.get(0)).startsWith("/uploads/").endsWith(".png");
        assertThat(urls.get(0)).doesNotContain("site.png");
    }

    @Test
    void rejectsTooManyImages() {
        List<MultipartFile> files = List.of(
            file("a.png", "image/png", pngBytes()),
            file("b.png", "image/png", pngBytes()),
            file("c.png", "image/png", pngBytes()),
            file("d.png", "image/png", pngBytes()),
            file("e.png", "image/png", pngBytes()),
            file("f.png", "image/png", pngBytes())
        );

        assertThrows(BusinessException.class, () -> service.storeImages(files));
    }

    @Test
    void rejectsPathTraversalFilename() {
        MockMultipartFile file = file("../bad.png", "image/png", pngBytes());

        assertThrows(BusinessException.class, () -> service.storeImages(List.of(file)));
    }

    @Test
    void rejectsMismatchedExtensionAndContentType() {
        MockMultipartFile file = file("fake.jpg", "image/png", pngBytes());

        assertThrows(BusinessException.class, () -> service.storeImages(List.of(file)));
    }
    @Test
    void rejectsFakeImageContent() {
        MockMultipartFile file = file("fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> service.storeImages(List.of(file)));
    }

    @Test
    void rejectsUnsupportedType() {
        MockMultipartFile file = file("note.gif", "image/gif", new byte[] { 'G', 'I', 'F', '8' });

        assertThrows(BusinessException.class, () -> service.storeImages(List.of(file)));
    }

    @Test
    void rejectsOversizedImage() {
        byte[] bytes = new byte[(int) FileStorageService.MAX_IMAGE_SIZE_BYTES + 1];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;

        assertThrows(BusinessException.class, () -> service.storeImages(List.of(file("big.png", "image/png", bytes))));
    }


    @Test
    void readFailureReturnsSafeMessage() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(12L);
        when(file.getOriginalFilename()).thenReturn("broken.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getBytes()).thenThrow(new java.io.IOException("C:\\secret\\raw-stack"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.storeImages(List.of(file)));

        assertThat(exception.getMessage()).isEqualTo("图片读取失败，请重新选择图片后再试");
        assertThat(exception.getMessage()).doesNotContain("C:\\secret");
    }
    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("images", name, contentType, bytes);
    }

    private byte[] pngBytes() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
        };
    }
}
