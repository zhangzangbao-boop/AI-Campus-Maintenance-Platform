package com.qiyun.opsservice.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库向量索引自动补偿同步。
 *
 * <p>Chroma 通常由 Docker 单独启动，可能晚于业务服务启动。该任务会定期把数据库中
 * 已启用的维修知识补写到 AI 服务索引，避免“Chroma 已启动但集合为空”的情况。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseVectorSyncScheduler {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${knowledge-base.vector-sync.enabled:true}")
    private boolean enabled;

    @Scheduled(
        initialDelayString = "${knowledge-base.vector-sync.initial-delay-ms:30000}",
        fixedDelayString = "${knowledge-base.vector-sync.fixed-delay-ms:300000}"
    )
    public void syncEnabledKnowledgeToVectorIndex() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            int synced = knowledgeBaseService.syncEnabledKnowledgeIndex();
            if (synced > 0) {
                log.info("知识库向量索引自动同步完成: synced={}", synced);
            }
        } catch (Exception e) {
            log.warn("知识库向量索引自动同步失败，将在下个周期重试: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
}
