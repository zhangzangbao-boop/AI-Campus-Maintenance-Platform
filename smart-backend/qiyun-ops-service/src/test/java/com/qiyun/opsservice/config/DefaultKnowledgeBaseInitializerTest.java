package com.qiyun.opsservice.config;

import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import com.qiyun.opsservice.repository.KnowledgeBaseRepository;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        assertEquals(defaultTitles().size(), items.size());
        assertTrue(items.size() >= 30);
        assertTrue(items.stream().allMatch(item -> Boolean.TRUE.equals(item.getEnabled())));
        assertTrue(items.stream().anyMatch(item ->
            "宿舍空调不制冷排查".equals(item.getTitle()) && "空调维修".equals(item.getCategoryKey())));
        assertTrue(items.stream().anyMatch(item ->
            "实验室通风柜和排风异常".equals(item.getTitle()) && "公共设施".equals(item.getCategoryKey())));
    }

    @Test
    @DisplayName("知识库已有部分数据时只补齐缺失默认知识")
    void addsOnlyMissingDefaults() {
        KnowledgeBase existing = new KnowledgeBase();
        existing.setTitle("宿舍空调不制冷排查");
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of(existing));
        DefaultKnowledgeBaseInitializer initializer = new DefaultKnowledgeBaseInitializer(knowledgeBaseRepository);

        initializer.run(null);

        ArgumentCaptor<Iterable<KnowledgeBase>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(knowledgeBaseRepository).saveAll(captor.capture());
        List<KnowledgeBase> items = toList(captor.getValue());

        assertEquals(defaultTitles().size() - 1, items.size());
        assertTrue(items.stream().noneMatch(item -> "宿舍空调不制冷排查".equals(item.getTitle())));
    }

    @Test
    @DisplayName("默认知识均存在时不覆盖管理员维护内容")
    void skipsWhenDefaultsAlreadyExist() {
        when(knowledgeBaseRepository.findAll()).thenReturn(defaultTitles().stream()
            .map(this::existing)
            .toList());
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

    private List<String> defaultTitles() {
        return List.of(
            "宿舍空调不制冷排查",
            "空调漏水和滴水处理",
            "空调异响异味处理",
            "宿舍水管漏水应急处理",
            "下水道堵塞处理",
            "水龙头和阀门损坏处理",
            "宿舍停电排查",
            "照明灯不亮或频闪处理",
            "插座发热和冒火花处理",
            "校园网 WiFi 无法连接",
            "宿舍有线网络断线处理",
            "网络速度慢和频繁掉线",
            "门锁失灵和钥匙卡滞处理",
            "窗户无法关闭和玻璃破损",
            "公共通道门和闭门器异常",
            "桌椅松动和断裂处理",
            "床架护栏和爬梯松动",
            "柜门抽屉合页损坏",
            "电梯困人应急处理",
            "电梯异响抖动或门异常",
            "电梯按钮和显示屏失灵",
            "消防栓和灭火器异常上报",
            "消防通道堵塞处理",
            "烟感报警和火警误报处理",
            "宿舍公共洗衣机故障",
            "饮水机和开水器异常",
            "公共卫生间设施损坏",
            "教室投影仪无画面",
            "教室音响和麦克风无声",
            "中控台和电子屏无法启动",
            "实验室设备异常断电",
            "实验室漏水和化学品附近渗水",
            "实验室通风柜和排风异常"
        );
    }
}
