package com.affiliate.platform.service;

import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.repository.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 媒体广告位业务服务 (Ad Slot Service)
 * <p>
 * 负责广告位的注册、规格尺寸定义、底价配置及启停状态管理。
 */
@Service
public class AdSlotService {

    // 广告位持久化仓储端口（支持内存或 PostgreSQL 实现）
    private final Repository<AdSlot> repo;

    @Autowired
    public AdSlotService(Repository<AdSlot> repo) {
        this.repo = repo;
    }

    /**
     * 注册并保存新广告位
     *
     * @param input 广告位信息
     * @return 已保存的广告位实体
     */
    public AdSlot create(AdSlot input) {
        return repo.save(new AdSlot(
                repo.nextId("slot"),
                input.name(),
                input.width(),
                input.height(),
                input.floorPrice(),
                input.secure(),
                input.active(),
                Instant.now()
        ));
    }

    /**
     * 获取全量广告位列表
     *
     * @return 广告位列表
     */
    public List<AdSlot> list() {
        return repo.findAll();
    }

    /**
     * 根据唯一标识查找广告位
     *
     * @param id 广告位标识
     * @return 广告位实体
     */
    public AdSlot get(String id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("adSlot", id));
    }

    /**
     * 切换广告位的激活状态
     *
     * @param id     广告位标识
     * @param active 是否启用
     * @return 更新后的实体
     */
    public AdSlot setActive(String id, boolean active) {
        return repo.save(get(id).activate(active));
    }
}
