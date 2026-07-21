package com.qiyun.opsservice.config;

import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import com.qiyun.opsservice.repository.KnowledgeBaseRepository;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DefaultKnowledgeBaseInitializerTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Test
    @DisplayName("知识库为空时初始化默认维修知识")
    void initializesDefaultsWhenEmpty() {
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of());
        DefaultKnowledgeBaseInitializer initializer = new DefaultKnowledgeBaseInitializer(knowledgeBaseRepository);

        initializer.run(null);

        ArgumentCaptor<Iterable<KnowledgeBase>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(knowledgeBaseRepository).saveAll(captor.capture());
        List<KnowledgeBase> items = toList(captor.getValue());

        assertEquals(6, items.size());
        assertEquals("校园网连接不上处理", items.get(3).getTitle());
        assertEquals("网络故障", items.get(3).getCategoryKey());
    }

    @Test
    @DisplayName("知识库已有部分数据时只补齐缺失默认知识")
    void addsOnlyMissingDefaults() {
        KnowledgeBase existing = new KnowledgeBase();
        existing.setTitle("校园网连接不上处理");
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(existing));
        DefaultKnowledgeBaseInitializer initializer = new DefaultKnowledgeBaseInitializer(knowledgeBaseRepository);

        initializer.run(null);

        ArgumentCaptor<Iterable<KnowledgeBase>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(knowledgeBaseRepository).saveAll(captor.capture());
        List<KnowledgeBase> items = toList(captor.getValue());

        assertEquals(5, items.size());
    }

    @Test
    @DisplayName("默认知识均存在时不覆盖管理员维护内容")
    void skipsWhenDefaultsAlreadyExist() {
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(
            existing("空调不制冷和异响排查"),
            existing("宿舍漏水应急处理"),
            existing("停电和照明异常报修流程"),
            existing("校园网连接不上处理"),
            existing("桌椅床柜松动和损坏处理"),
            existing("门窗和公共设施异常处理")
        ));
        DefaultKnowledgeBaseInitializer initializer = new DefaultKnowledgeBaseInitializer(knowledgeBaseRepository);

        initializer.run(null);

        verify(knowledgeBaseRepository, never()).saveAll(anyList());
    }

    private KnowledgeBase existing(String title) {
        KnowledgeBase item = new KnowledgeBase();
        item.setTitle(title);
        return item;
    }

    private List<KnowledgeBase> toList(Iterable<KnowledgeBase> items) {
        List<KnowledgeBase> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
