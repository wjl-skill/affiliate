package com.affiliate.platform.dsp;

import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.Repository;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 需求方广告活动业务管理服务 (DSP Campaign Service)
 * <p>
 * 提供广告活动的创建、定向匹配、按 ID 查询、启停切换以及领域事件通知。
 * 底层基于 PostgreSQL 关系数据库与 Guava + Redis 两级缓存，保证高并发撮合下毫秒级定向召回。
 */
@Service
public class CampaignService {

    // 广告活动持久化仓储（结合两级缓存）
    private final Repository<Campaign> repository;

    // 领域事件发布器
    private final EventPublisher events;

    public CampaignService(Repository<Campaign> repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    /**
     * 创建并持久化新广告活动，并发布 campaign.created.v1 领域事件
     *
     * @param input 活动输入数据
     * @return 初始状态为 DRAFT 的 Campaign 实体
     */
    public Campaign create(Campaign input) {
        String id = repository.nextId("campaign");
        Campaign campaign = new Campaign(
                id,
                input.advertiserId(),
                input.name(),
                input.startDate(),
                input.endDate(),
                input.dailyBudget(),
                input.maxBid(),
                input.targetDomains(),
                input.targetDeviceTypes(),
                Campaign.Status.DRAFT,
                Instant.now()
        );
        repository.save(campaign);
        events.publish(DomainEvent.create("campaign.created.v1", tenant(), id, campaign));
        return campaign;
    }

    /**
     * 查询全量活动列表（命中两级缓存）
     *
     * @return 活动列表快照
     */
    public List<Campaign> list() {
        return repository.findAll();
    }

    /**
     * 根据活动 ID 查询详情（优先读取 L1 Guava 与 L2 Redis，未命中回源 PostgreSQL）
     *
     * @param id 活动标识
     * @return Campaign 实体
     * @throws NotFoundException 活动不存在时抛出
     */
    public Campaign get(String id) {
        return repository.find(id)
                .orElseThrow(() -> new NotFoundException("campaign", id));
    }

    /**
     * 切换活动的投放状态（激活或暂停），并发布 campaign.status_changed.v1 事件
     *
     * @param id     活动标识
     * @param active 是否激活
     * @return 状态更新后的 Campaign 对象
     */
    public Campaign setActive(String id, boolean active) {
        Campaign next = get(id).activate(active);
        repository.save(next);
        events.publish(DomainEvent.create("campaign.status_changed.v1", tenant(), id, next.status()));
        return next;
    }

    /**
     * 基础定向匹配：根据媒体域名、设备类型和日期筛选可用活动候选集
     *
     * @param domain     媒体域名
     * @param deviceType 设备类型
     * @param date       日期
     * @return 符合全部定向条件的可用活动列表
     */
    public List<Campaign> match(String domain, int deviceType, LocalDate date) {
        return match(TrafficContext.of(domain, deviceType, date));
    }

    /**
     * 全维度定向匹配引擎：根据综合流量上下文（含地理、设备、时段、域名等）筛选可用活动
     *
     * @param ctx 综合流量环境上下文
     * @return 符合全部定向条件的可用活动列表
     */
    public List<Campaign> match(TrafficContext ctx) {
        return repository.findAll().stream()
                .filter(c -> c.matches(ctx))
                .toList();
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.required();
    }
}
