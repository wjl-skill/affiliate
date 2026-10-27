package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;

import java.util.concurrent.CompletableFuture;

/**
 * 通用第三方广告/买量平台连接器核心 SPI (Unified Ad Platform Connector SPI)
 * <p>
 * 定义对 Google Ads、TikTok Ads、Facebook Ads、Google Ad Manager (GAM) 等外部平台的物料同步、效果报表与转化回传契约。
 */
public interface AdPlatformConnector {

    /**
     * 连接器对应的服务提供商唯一标识（如 "GOOGLE_ADS", "TIKTOK", "FACEBOOK", "GAM"）
     *
     * @return 提供商标识
     */
    String provider();

    /**
     * 触发与第三方广告平台的异步数据同步任务
     *
     * @param connection 合作方连接凭据与端点配置
     * @return 包含同步统计结果的 CompletableFuture
     */
    CompletableFuture<SyncResult> sync(PartnerConnection connection);

    /**
     * 检索指定账号在特定时间段的广告投放效果报表（曝光、点击、转化、消耗）
     *
     * @param query 报表查询参数载荷
     * @return 报表结果
     */
    default CompletableFuture<AdPerformanceReport> queryPerformance(AdPerformanceQuery query) {
        return CompletableFuture.completedFuture(new AdPerformanceReport(
                provider(),
                query != null ? query.accountId() : "",
                java.util.List.of(),
                java.math.BigDecimal.ZERO,
                0L,
                0L,
                0L
        ));
    }

    /**
     * S2S 增强型/回传转化事件上报 (Google Enhanced Conversions / Meta CAPI / TikTok Events API)
     *
     * @param payload 统一转化载荷
     * @return 上报执行结果
     */
    default CompletableFuture<ConversionUploadResult> uploadConversion(UnifiedConversionPayload payload) {
        return CompletableFuture.completedFuture(new ConversionUploadResult(
                false,
                provider(),
                payload != null ? payload.orderId() : "",
                "Conversion upload not supported by provider: " + provider(),
                null
        ));
    }

    /**
     * 数据同步执行结果明细
     *
     * @param provider 提供商标识
     * @param imported 成功导入/同步的记录条数
     * @param rejected 校验失败或被远端拒绝的条数
     * @param message  执行状态概要说明信息
     */
    record SyncResult(String provider, int imported, int rejected, String message) {}
}
