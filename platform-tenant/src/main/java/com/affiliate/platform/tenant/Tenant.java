package com.affiliate.platform.tenant;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * 租户领域实体 (Tenant Domain Record)
 * <p>
 * 代表平台中的一个独立组织、客户或广告代理商，作为全平台数据与预算隔离的顶级命名空间。
 *
 * @param id        租户全局唯一主键 ID
 * @param name      租户企业/组织名称
 * @param status    租户状态（ACTIVE 正常激活、SUSPENDED 冻结封禁）
 * @param createdAt 租户注册创建时间戳
 */
public record Tenant(
        String id,
        @NotBlank String name,
        Status status,
        Instant createdAt
) {
    /**
     * 租户运行生命周期状态
     */
    public enum Status {
        /** 正常激活：可正常投放广告并结算 */
        ACTIVE,
        /** 冻结挂起：禁止创建活动与实时竞价 */
        SUSPENDED
    }

    /**
     * 切换租户激活状态并返回新的不可变实体
     *
     * @param active true 为激活，false 为挂起
     * @return 更新状态后的 Tenant 实例
     */
    public Tenant activate(boolean active) {
        return new Tenant(id, name, active ? Status.ACTIVE : Status.SUSPENDED, createdAt);
    }
}
