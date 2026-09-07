package com.affiliate.platform.repository;

import java.util.List;
import java.util.Optional;

/**
 * 平台通用数据仓储顶级接口 (Generic Repository Interface)
 * <p>
 * 定义核心实体在持久化层的标准 CRUD 契约，屏蔽底层具体存储实现（内存 ConcurrentHashMap 或 PostgreSQL JDBC）。
 *
 * @param <T> 实体类型
 */
public interface Repository<T> {

    /**
     * 保存或更新实体数据
     *
     * @param entity 待保存的实体对象，不可为 null
     * @return 持久化后的实体对象
     */
    T save(T entity);

    /**
     * 根据主键唯一标识查找实体
     *
     * @param id 实体主键标识
     * @return 包含实体的 Optional 对象；若不存在则返回 Optional.empty()
     */
    Optional<T> find(String id);

    /**
     * 查询当前租户或全局可见的所有实体列表
     *
     * @return 实体列表（只读或防御性拷贝）
     */
    List<T> findAll();

    /**
     * 生成下一个全局或实体级唯一的业务主键 ID
     *
     * @param prefix 业务前缀（例如 "cr", "slot", "tenant"）
     * @return 带前缀和自增序号的唯一 ID 字符串
     */
    String nextId(String prefix);

    /**
     * 根据主键删除指定实体
     *
     * @param id 待删除实体的唯一标识
     */
    default void delete(String id) {}
}
