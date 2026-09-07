package com.affiliate.platform.service;

/**
 * 资源未找到异常 (Resource Not Found Exception)
 * <p>
 * 当根据唯一标识 ID 检索广告素材、广告位、租户或分录记录不存在时抛出，
 * 映射为 HTTP 404 状态码。
 */
public class NotFoundException extends RuntimeException {

    /**
     * 构造资源未找到异常
     *
     * @param resource 业务资源名称（例如 "creative", "adSlot", "tenant"）
     * @param id       未检索到的主键标识 ID
     */
    public NotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }
}
