package com.affiliate.platform.rtb;

import java.util.Map;

/**
 * OpenRTB 宏代码动态替换引擎 (OpenRTB Macro Replacer)
 * <p>
 * 遵循 IAB OpenRTB 规范，替换胜出通知 URL (NURL)、计费曝光 URL (BURL) 或广告渲染代码 (ADM) 中的动态宏标记：
 * - ${AUCTION_PRICE}：胜出成交结算价格；
 * - ${AUCTION_ID}：实时竞价请求唯一标识；
 * - ${AUCTION_BID_ID}：胜出出价唯一标识；
 * - ${AUCTION_IMP_ID}：曝光位唯一标识；
 * - ${AUCTION_CURRENCY}：清算货币代码。
 */
public final class MacroReplacer {

    private MacroReplacer() {}

    /**
     * 替换模板字符串中的所有 OpenRTB 标准宏
     *
     * @param template 待替换的 URL 或 HTML 模板
     * @param context  宏键值映射上下文
     * @return 替换后的最终文本
     */
    public static String replace(String template, Map<String, String> context) {
        if (template == null || template.isBlank() || context == null || context.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String macroKey = "${" + entry.getKey() + "}";
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace(macroKey, value);
        }
        return result;
    }

    /**
     * 针对标准 NURL 场景的快捷替换
     */
    public static String replaceNurl(
            String nurlTemplate,
            String auctionId,
            String bidId,
            String impId,
            double price,
            String currency
    ) {
        return replace(nurlTemplate, Map.of(
                "AUCTION_ID", auctionId,
                "AUCTION_BID_ID", bidId,
                "AUCTION_IMP_ID", impId,
                "AUCTION_PRICE", String.valueOf(price),
                "AUCTION_CURRENCY", currency != null ? currency : "USD"
        ));
    }
}
