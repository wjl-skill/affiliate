package com.affiliate.platform.service;

import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.AuditStatus;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.repository.Repository;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 广告素材业务服务 (Creative Service)
 * <p>
 * 负责广告图片、视频、原生及 HTML5 素材的生命周期管控：
 * 包含创建、安全合规审核流（通过/驳回）、第三方监测代码注入与 RTB 可竞价资格校验。
 */
@Service
public class CreativeService {

    // 依赖注入的数据仓储端口
    private final Repository<Creative> repo;

    // 领域事件发布器
    private final EventPublisher events;

    public CreativeService(Repository<Creative> repo) {
        this(repo, e -> {});
    }

    @Autowired
    public CreativeService(Repository<Creative> repo, EventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    /**
     * 创建并持久化新广告素材（初始状态为待审核 PENDING_REVIEW）
     */
    public Creative create(Creative input) {
        Creative creative = repo.save(new Creative(
                repo.nextId("cr"),
                input.name(),
                input.type(),
                input.assetUrl(),
                input.landingUrl(),
                input.width(),
                input.height(),
                input.categories(),
                input.active(),
                AuditStatus.PENDING_REVIEW,
                null,
                input.impressionTrackers(),
                input.clickTrackers(),
                Instant.now()
        ));
        events.publish(DomainEvent.create("creative.created.v1", tenant(), creative.id(), creative));
        return creative;
    }

    /**
     * 审核通过指定素材
     */
    public Creative approve(String id) {
        Creative approved = get(id).approve();
        repo.save(approved);
        events.publish(DomainEvent.create("creative.approved.v1", tenant(), id, approved));
        return approved;
    }

    /**
     * 审核驳回指定素材，附带违规原因说明
     */
    public Creative reject(String id, String reason) {
        Creative rejected = get(id).reject(reason);
        repo.save(rejected);
        events.publish(DomainEvent.create("creative.rejected.v1", tenant(), id, rejected));
        return rejected;
    }

    /**
     * 为素材绑定第三方曝光与点击监测代码
     */
    public Creative setTrackers(String id, List<String> impressionTrackers, List<String> clickTrackers) {
        Creative updated = get(id).withTrackers(impressionTrackers, clickTrackers);
        repo.save(updated);
        return updated;
    }

    /**
     * 获取全量素材列表
     */
    public List<Creative> list() {
        return repo.findAll();
    }

    /**
     * 获取允许参与 RTB 实时竞价撮合的合规素材（已激活且审核通过）
     */
    public List<Creative> listEligible() {
        return repo.findAll().stream()
                .filter(Creative::isEligibleForBidding)
                .toList();
    }

    /**
     * 根据 ID 检索素材
     */
    public Creative get(String id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("creative", id));
    }

    /**
     * 启用或暂停指定素材的投放状态
     */
    public Creative setActive(String id, boolean active) {
        Creative updated = get(id).activate(active);
        repo.save(updated);
        events.publish(DomainEvent.create("creative.status_changed.v1", tenant(), id, updated.active()));
        return updated;
    }

    private static String tenant() {
        return TenantContext.get() == null ? "public" : TenantContext.get();
    }
}
