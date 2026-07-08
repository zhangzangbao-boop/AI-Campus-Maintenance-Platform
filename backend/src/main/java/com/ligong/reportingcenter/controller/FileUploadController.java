package com.ligong.reportingcenter.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ligong.reportingcenter.service.FileStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
@Slf4j
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/images")
    public List<ImageUploadResponse> uploadImages(@RequestParam("files") MultipartFile[] files) {
        List<ImageUploadResponse> responses = new ArrayList<>();
        for (String fileUrl : fileStorageService.storeImages(Arrays.asList(files))) {
            String filename = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            responses.add(new ImageUploadResponse(fileUrl, filename));
        }
        return responses;
    }
    
    public static class ImageUploadResponse {
        private String url;
        private String name;
        
        public ImageUploadResponse(String url, String name) {
            this.url = url;
            this.name = name;
        }
        
        public String getUrl() {
            return url;
        }
        
        public String getName() {
            return name;
        }
    }
}
