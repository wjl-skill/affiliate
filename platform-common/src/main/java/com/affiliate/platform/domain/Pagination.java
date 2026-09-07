package com.affiliate.platform.domain;

import java.util.List;

/**
 * 通用分页查询契约与结果容器定义 (Pagination Domain Records)
 * <p>
 * 提供全平台统一的列表分页查询入参封装与标准分页响应包装。
 */
public final class Pagination {
    private Pagination() {}

    /**
     * 分页查询参数请求对象
     *
     * @param page      页码索引（从 0 开始计数，负数自动纠正为 0）
     * @param size      每页数据容量条数（最大限制 1000 条，默认 20 条）
     * @param sortBy    排序字段属性名（默认 "createdAt"）
     * @param ascending 是否按升序排序（true 为升序 ASC，false 为降序 DESC）
     */
    public record PageQuery(int page, int size, String sortBy, boolean ascending) {
        public PageQuery {
            // 页码防越界：小于 0 时重置为第 0 页
            page = Math.max(0, page);
            // 每页大小安全防护：介于 1 到 1000 之间，默认 20 条
            size = (size <= 0 || size > 1000) ? 20 : size;
            // 排序字段防空白：默认按创建时间降序
            sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy.trim();
        }

        /**
         * 快速构造默认按创建时间降序的分页查询对象
         */
        public static PageQuery of(int page, int size) {
            return new PageQuery(page, size, "createdAt", false);
        }

        /**
         * 计算当前页在数据库中的偏移量 (Offset)
         *
         * @return SQL 中的 OFFSET 数值
         */
        public int offset() {
            return page * size;
        }
    }

    /**
     * 分页结果封装包装对象
     *
     * @param items 当前页的数据元素实体列表
     * @param total 满足查询条件的总记录数
     * @param page  当前页码（从 0 开始）
     * @param size  每页容量大小
     * @param <T>   数据项实体泛型
     */
    public record PageResult<T>(List<T> items, long total, int page, int size) {
        public PageResult {
            // 结果集合防 null 防御性不可变拷贝
            items = items == null ? List.of() : List.copyOf(items);
        }

        /**
         * 计算总页数
         *
         * @return 向上取整后的总页数
         */
        public int totalPages() {
            return size == 0 ? 1 : (int) Math.ceil((double) total / (double) size);
        }

        /**
         * 是否存在下一页数据
         */
        public boolean hasNext() {
            return page + 1 < totalPages();
        }

        /**
         * 是否存在上一页数据
         */
        public boolean hasPrevious() {
            return page > 0;
        }
    }
}
