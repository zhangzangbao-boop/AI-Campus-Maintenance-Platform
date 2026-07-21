package com.qiyun.opsservice.config;

import com.qiyun.opsservice.domain.entity.KnowledgeBase;
import com.qiyun.opsservice.repository.KnowledgeBaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes a small usable maintenance knowledge base for fresh demo databases.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultKnowledgeBaseInitializer implements ApplicationRunner {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<KnowledgeBase> defaults = List.of(
            item(
                "电器故障",
                "空调不制冷和异响排查",
                "空调,不制冷,制冷差,异响,滤网,遥控器,室外机,制冷剂",
                "1. 确认空调已通电，遥控器模式为制冷，温度设置低于室温；2. 清洗过滤网，检查出风口是否被遮挡；3. 查看室外机是否正常运转、散热是否受阻；4. 仍不制冷时提交报修，说明宿舍号、空调编号和异常现象。",
                "不要自行拆机或攀爬检查室外机，发现焦味、冒烟或漏电时立即断电并远离设备。",
                90
            ),
            item(
                "水电维修",
                "宿舍漏水应急处理",
                "漏水,渗水,滴水,水管,水龙头,卫生间,地面,积水,下水道",
                "1. 先关闭附近水阀或停止使用相关水龙头；2. 用拖把、毛巾简单清理积水，避免滑倒；3. 拍摄漏水位置和影响范围；4. 在报修单中填写宿舍楼栋、房间号、漏水位置和是否持续漏水。",
                "如果漏水靠近插座、电器或配电箱，先切断相关电源，不要触碰潮湿电器。",
                45
            ),
            item(
                "电器故障",
                "停电和照明异常报修流程",
                "停电,没电,跳闸,照明,灯不亮,频闪,插座,电闸,配电箱",
                "1. 先确认是单个插座、单间宿舍还是整层停电；2. 检查是否有大功率电器导致跳闸；3. 不要自行打开配电箱内部接线；4. 提交报修时说明停电范围、发生时间和是否伴随焦味或火花。",
                "发现焦味、冒烟、火花或插座发热时立即停止用电，并通知宿管或维修人员。",
                60
            ),
            item(
                "网络故障",
                "校园网连接不上处理",
                "网络,校园网,无线,wifi,WiFi,有线,网口,断线,无法连接,认证失败,IP,水晶头,路由器,AP",
                "1. 确认账号密码正确，并查看是否欠费或账号被占用；2. 重启电脑或手机网络，重新连接校园网；3. 有线网络请检查网线和墙面网口是否松动；4. 无线网络请尝试靠近 AP 或切换网络；5. 仍无法连接时提交报修，附上错误提示截图、宿舍号和设备类型。",
                "不要私自拆弱电箱、交换机或 AP 设备；多人同时无法上网时请注明影响范围。",
                40
            ),
            item(
                "家具维修",
                "桌椅床柜松动和损坏处理",
                "桌子,椅子,床,柜子,门板,抽屉,松动,断裂,螺丝,合页",
                "1. 停止继续使用明显断裂或晃动的家具；2. 拍摄损坏位置；3. 报修时说明家具类型、损坏部位和是否影响正常使用；4. 维修人员到场后根据情况加固或更换配件。",
                "断裂木板、金属边角可能划伤，请先移开尖锐部件并避免承重使用。",
                35
            ),
            item(
                "公共设施",
                "门窗和公共设施异常处理",
                "门,窗,门锁,门禁,玻璃门,闭门器,公共门,无法关闭,异响",
                "1. 判断是否影响通行或安全；2. 拍摄门窗、门禁或闭门器异常位置；3. 报修时说明具体楼栋楼层和公共区域位置；4. 紧急通道门异常请同步通知宿管。",
                "玻璃破损、门体脱落或闭门器失控时，不要强行使用，并在现场放置提醒。",
                50
            )
        );

        Set<String> existingTitles = knowledgeBaseRepository.findAll().stream()
            .map(KnowledgeBase::getTitle)
            .collect(Collectors.toSet());
        List<KnowledgeBase> missingDefaults = defaults.stream()
            .filter(item -> !existingTitles.contains(item.getTitle()))
            .toList();

        if (missingDefaults.isEmpty()) {
            return;
        }

        knowledgeBaseRepository.saveAll(missingDefaults);
        log.info("已补齐默认维修知识库: count={}", missingDefaults.size());
    }

    private KnowledgeBase item(
        String categoryKey,
        String title,
        String symptomKeywords,
        String solutionSteps,
        String safetyNotes,
        Integer estimatedMinutes
    ) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase item = new KnowledgeBase();
        item.setCategoryKey(categoryKey);
        item.setTitle(title);
        item.setSymptomKeywords(symptomKeywords);
        item.setSolutionSteps(solutionSteps);
        item.setSafetyNotes(safetyNotes);
        item.setEstimatedMinutes(estimatedMinutes);
        item.setEnabled(true);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }
}
