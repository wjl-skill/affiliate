package com.affiliate.platform.rtb;

import com.affiliate.platform.domain.Creative;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 广告素材内存多维倒排索引 (In-Memory Creative Inverted Index)
 * <p>
 * 将素材按宽高的复合规格（例如 "300x250", "728x90"）进行多维空间分桶建立倒排索引，
 * 将素材召回耗时从 O(N) 线性扫描降低至 O(1) 纳秒级字典查表，保障竞价在 20ms 内完成。
 */
@Component
public class CreativeInvertedIndex {

    // 尺寸维度倒排索引：Key 为 "宽x高" 格式（如 "300x250"），Value 为该尺寸下所有处于激活状态的素材列表
    private final ConcurrentMap<String, CopyOnWriteArrayList<Creative>> dimensionIndex = new ConcurrentHashMap<>();

    // 主键反查索引：Key 为素材 ID，Value 为素材对象引用
    private final ConcurrentMap<String, Creative> idIndex = new ConcurrentHashMap<>();

    // 全量激活素材列表：用于无特定尺寸限制或尺寸无关物料检索
    private final CopyOnWriteArrayList<Creative> allActive = new CopyOnWriteArrayList<>();

    /**
     * 将单个素材增量建立或刷新索引
     *
     * @param creative 素材对象
     */
    public void index(Creative creative) {
        if (creative == null) return;
        // 1. 如果此前已存在该素材历史版本，先清理旧索引引用
        remove(creative.id());

        // 2. 仅对当前处于激活投放状态的素材建立倒排索引
        if (creative.active()) {
            idIndex.put(creative.id(), creative);
            allActive.add(creative);
            String dimKey = key(creative.width(), creative.height());
            // 写入尺寸倒排桶
            dimensionIndex.computeIfAbsent(dimKey, ignored -> new CopyOnWriteArrayList<>()).add(creative);
        }
    }

    /**
     * 批量重建全量素材倒排索引
     *
     * @param creatives 全量素材迭代集合
     */
    public void loadAll(Iterable<Creative> creatives) {
        dimensionIndex.clear();
        idIndex.clear();
        allActive.clear();
        if (creatives != null) {
            for (Creative creative : creatives) {
                index(creative);
            }
        }
    }

    /**
     * 根据素材 ID 从倒排索引中移除
     *
     * @param creativeId 待下线或删除的素材标识
     */
    public void remove(String creativeId) {
        Creative existing = idIndex.remove(creativeId);
        if (existing != null) {
            allActive.remove(existing);
            String dimKey = key(existing.width(), existing.height());
            CopyOnWriteArrayList<Creative> list = dimensionIndex.get(dimKey);
            if (list != null) {
                list.remove(existing);
            }
        }
    }

    /**
     * 根据请求指定的宽高尺寸，O(1) 瞬时召回候选素材集合
     *
     * @param requestedWidth  请求的广告位宽度像素（0 代表不限）
     * @param requestedHeight 请求的广告位高度像素（0 代表不限）
     * @return 匹配尺寸的可用素材候选列表（保证不可为 null）
     */
    public List<Creative> findCandidates(int requestedWidth, int requestedHeight) {
        if (requestedWidth <= 0 && requestedHeight <= 0) {
            return allActive;
        }
        String dimKey = key(requestedWidth, requestedHeight);
        List<Creative> matched = dimensionIndex.get(dimKey);
        // 无匹配候选时返回不可变空列表，避免额外内存对象分配
        return matched == null ? Collections.emptyList() : matched;
    }

    /**
     * 获取当前处于活跃索引状态的素材总数
     */
    public int totalActiveCount() {
        return allActive.size();
    }

    /**
     * 生成尺寸维度标准 Key
     */
    private static String key(int w, int h) {
        return w + "x" + h;
    }
}
