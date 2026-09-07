package com.affiliate.platform.tenant;

import com.affiliate.platform.repository.Repository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 多租户管理核心业务服务 (Tenant Service)
 * <p>
 * 提供租户生命周期的创建、按 ID 检索、全量列表查看及启用/冻结状态切换。
 * 底层对接带 Guava + Redis 两级缓存的 PostgreSQL 持久化仓储。
 */
@Service
public class TenantService {

    // 租户数据仓储（支持内存回退与 PostgreSQL 自动装配）
    private final Repository<Tenant> repository;

    public TenantService(Repository<Tenant> repository) {
        this.repository = repository;
    }

    /**
     * 注册创建新租户
     *
     * @param input 租户创建请求信息
     * @return 初始状态为 ACTIVE 的新租户实体
     */
    public Tenant create(Tenant input) {
        return repository.save(new Tenant(
                repository.nextId("tenant"),
                input.name(),
                Tenant.Status.ACTIVE,
                Instant.now()
        ));
    }

    /**
     * 获取系统中注册的全量租户列表
     *
     * @return 租户列表
     */
    public List<Tenant> list() {
        return repository.findAll();
    }

    /**
     * 根据主键 ID 检索指定租户
     *
     * @param id 租户唯一标识
     * @return 租户实体
     * @throws IllegalArgumentException 租户不存在时抛出
     */
    public Tenant get(String id) {
        return repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("tenant not found: " + id));
    }

    /**
     * 切换租户的激活/挂起状态
     *
     * @param id     租户标识
     * @param active 是否激活
     * @return 状态更新后的租户实体
     */
    public Tenant setActive(String id, boolean active) {
        return repository.save(get(id).activate(active));
    }
}
