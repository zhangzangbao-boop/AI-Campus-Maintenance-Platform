package com.qiyun.aiservice.controller;

import com.qiyun.aiservice.service.ChromaClientService;
import com.qiyun.aiservice.service.RagKnowledgeService;
import com.qiyun.aiservice.service.RagKnowledgeService.RagAnswer;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 知识库问答接口
 */
@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagKnowledgeService ragKnowledgeService;
    private final ChromaClientService chromaClientService;

    /**
     * 知识问答接口
     *
     * @param question    用户问题
     * @param categoryKey 故障分类（可选）
     * @return 问答结果
     */
    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestParam("question") String question,
            @RequestParam(value = "categoryKey", required = false) String categoryKey) {

        RagAnswer answer = ragKnowledgeService.ask(question, categoryKey);

        Map<String, Object> response = new HashMap<>();
        response.put("code", answer.success() ? 200 : 404);

        if (answer.success()) {
            Map<String, Object> data = new HashMap<>();
            data.put("answer", answer.answer());
            data.put("sources", answer.sources());
            data.put("similarity", answer.maxSimilarity());
            data.put("fallback", answer.fallback());
            data.put("retrievalMode", answer.retrievalMode());
            response.put("data", data);
            response.put("message", "回答生成成功");
        } else {
            response.put("message", answer.message());
            response.put("data", null);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "RAG status");
        response.put("data", chromaClientService.diagnosticStatus());
        return ResponseEntity.ok(response);
    }


}
