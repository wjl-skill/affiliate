package com.affiliate.platform.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 基于内存并发容器的通用仓储实现 (In-Memory Thread-Safe Repository)
 * <p>
 * 适用于本地开发、单测以及零外部依赖的原型验证，内部基于 ConcurrentHashMap 保证并发读写安全性。
 *
 * @param <T> 存储的领域实体对象类型
 */
public class InMemoryRepository<T> implements Repository<T> {

    // 内存数据存储容器：Key 为实体唯一标识 ID，Value 为实体对象
    private final ConcurrentMap<String, T> data = new ConcurrentHashMap<>();

    // 原子自增计数器：用于生成全局单调递增的序列号
    private final AtomicLong seq = new AtomicLong();

    // 实体 ID 提取函数：用于从实体对象中反射或函数式提取主键 Key
    private final Function<T, String> idExtractor;

    /**
     * 构造内存仓储实例
     *
     * @param idExtractor 从实体对象提取唯一标识 ID 的函数（例如 Creative::id）
     */
    public InMemoryRepository(Function<T, String> idExtractor) {
        this.idExtractor = idExtractor;
    }

    /**
     * 生成下一个带前缀的自增主键 ID
     *
     * @param prefix 业务前缀（例如 "cr" -> "cr_1", "cr_2"）
     * @return 格式化后的唯一主键字符串
     */
    @Override
    public String nextId(String prefix) {
        return prefix + "_" + seq.incrementAndGet();
    }

    /**
     * 保存或更新实体到内存 Map
     *
     * @param entity 实体对象
     * @return 刚保存的实体对象引用
     */
    @Override
    public T save(T entity) {
        data.put(idExtractor.apply(entity), entity);
        return entity;
    }

    /**
     * 根据主键 ID 检索实体
     *
     * @param id 实体唯一标识
     * @return 包含实体的 Optional 包装
     */
    @Override
    public Optional<T> find(String id) {
        return Optional.ofNullable(data.get(id));
    }

    /**
     * 获取当前容器内的全量不可变实体快照列表
     *
     * @return 不可变实体列表
     */
    @Override
    public List<T> findAll() {
        return List.copyOf(data.values());
    }

    /**
     * 根据主键 ID 从内存中移除实体
     *
     * @param id 待删除实体的唯一标识
     */
    @Override
    public void delete(String id) {
        data.remove(id);
    }
}
