package com.affiliate.platform.integration;

/**
 * 跨平台转化事件上报执行结果
 *
 * @param success     是否上报成功
 * @param platform    目标平台
 * @param orderId     业务订单号
 * @param message     执行响应信息或错误摘要
 * @param rawResponse 平台返回的原始响应载荷 (JSON/XML)
 */
public record ConversionUploadResult(
        boolean success,
        String platform,
        String orderId,
        String message,
        String rawResponse
) {
    public static ConversionUploadResult ok(String platform, String orderId, String message, String rawResponse) {
        return new ConversionUploadResult(true, platform, orderId, message, rawResponse);
    }

    public static ConversionUploadResult fail(String platform, String orderId, String message, String rawResponse) {
        return new ConversionUploadResult(false, platform, orderId, message, rawResponse);
    }
}
