package com.affiliate.platform.ssp;

import com.affiliate.platform.domain.AdSlot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 供给方动态收益与底价决策引擎 (SSP Dynamic Yield & Floor Price Manager)
 * <p>
 * 解决媒体端收益最大化（Yield Optimization）核心诉求：
 * 在流量请求进入时，根据地理位置、终端设备类型与时段动态计算该流量的最优软硬底价。
 */
@Service
public class DynamicYieldManager {

    // 广告位专属底价规则列表：Key 为 slotId
    private final ConcurrentMap<String, List<FloorPriceRule>> rules = new ConcurrentHashMap<>();

    /**
     * 注册或更新广告位的动态底价规则
     */
    public void addRule(FloorPriceRule rule) {
        rules.computeIfAbsent(rule.slotId(), k -> new ArrayList<>()).add(rule);
    }

    /**
     * 清空广告位规则
     */
    public void clearRules(String slotId) {
        rules.remove(slotId);
    }

    /**
     * 计算该次曝光请求的实际生效底价
     *
     * @param slot       广告位基础配置
     * @param country    请求来源国家 (如 "US", "CN")
     * @param deviceType 请求设备类型 (如 1 手机, 4 桌面PC)
     * @param hour       请求时刻小时 (0..23)
     * @return 计算后的底价结果对象（包含有效硬底价与软底价）
     */
    public FloorPriceResult resolveFloor(AdSlot slot, String country, int deviceType, int hour) {
        BigDecimal defaultFloor = BigDecimal.valueOf(slot.floorPrice());
        List<FloorPriceRule> slotRules = rules.get(slot.id());

        if (slotRules == null || slotRules.isEmpty()) {
            return new FloorPriceResult(defaultFloor, defaultFloor);
        }

        // 匹配最具体的规则（若多条匹配则取最高底价以保障媒体收益）
        BigDecimal maxHard = defaultFloor;
        BigDecimal maxSoft = defaultFloor;

        for (FloorPriceRule rule : slotRules) {
            if (rule.matches(country, deviceType, hour)) {
                if (rule.hardFloor().compareTo(maxHard) > 0) {
                    maxHard = rule.hardFloor();
                }
                if (rule.softFloor().compareTo(maxSoft) > 0) {
                    maxSoft = rule.softFloor();
                }
            }
        }

        return new FloorPriceResult(maxHard, maxSoft);
    }

    /**
     * 底价决策结果
     *
     * @param hardFloor 硬底价：低于此价格直接流拍
     * @param softFloor 软底价：高于此价格走第一价结算，介于两者之间走次高价竞价
     */
    public record FloorPriceResult(BigDecimal hardFloor, BigDecimal softFloor) {}
}
