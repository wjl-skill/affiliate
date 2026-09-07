package com.affiliate.platform.integration;

import com.affiliate.platform.domain.PartnerConnection;
import java.util.concurrent.CompletableFuture;

/**
 * 第三方广告平台连接适配器通用接口 (Ad Platform Connector Interface)
 * <p>
 * 定义对 Google Ad Manager (GAM)、Google Ads、The Trade Desk 等外部平台的物料与投放数据同步契约。
 */
public interface AdPlatformConnector {

    /**
     * 连接器对应的服务提供商唯一标识（如 "GAM", "GOOGLE_ADS"）
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
     * 数据同步执行结果明细
     *
     * @param provider 提供商标识
     * @param imported 成功导入/同步的记录条数
     * @param rejected 校验失败或被远端拒绝的条数
     * @param message  执行状态概要说明信息
     */
    record SyncResult(String provider, int imported, int rejected, String message) {}
}
