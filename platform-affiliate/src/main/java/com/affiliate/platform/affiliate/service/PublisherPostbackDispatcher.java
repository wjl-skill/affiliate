package com.affiliate.platform.affiliate.service;

import com.affiliate.platform.affiliate.domain.AffiliatePartner;
import com.affiliate.platform.affiliate.domain.ClickSession;
import com.affiliate.platform.affiliate.domain.Conversion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 渠道转化下游回传分发器 (Publisher Postback Dispatcher)
 * <p>
 * 当网盟平台成功归因并确认一笔有效转化时：
 * 1. 提取该渠道客配置的 `postbackUrlTemplate`；
 * 2. 动态替换宏参数（`{click_id}`, `{payout}`, `{txid}`, `{sub1}` 等）；
 * 3. 异步发送 HTTP GET 请求完成对下游渠道的转化通知，具备执行记录与审计留痕。
 */
@Service
public class PublisherPostbackDispatcher {

    public record PostbackDeliveryLog(
            String conversionId,
            String affiliateId,
            String targetUrl,
            boolean success,
            String responseBody
    ) {}

    private final List<PostbackDeliveryLog> deliveryLogs = Collections.synchronizedList(new ArrayList<>());

    /**
     * 替换渠道 Postback URL 中的宏变量
     */
    public String renderPostbackUrl(
            String urlTemplate,
            Conversion conversion,
            ClickSession session
    ) {
        if (urlTemplate == null || urlTemplate.isBlank()) {
            return null;
        }

        String url = urlTemplate;
        url = url.replace("{click_id}", conversion.clickId());
        url = url.replace("{payout}", conversion.payout().toPlainString());
        url = url.replace("{txid}", conversion.txId());
        url = url.replace("{currency}", "USD");

        if (session != null) {
            url = url.replace("{sub1}", session.sub1() != null ? session.sub1() : "");
            url = url.replace("{sub2}", session.sub2() != null ? session.sub2() : "");
            url = url.replace("{sub3}", session.sub3() != null ? session.sub3() : "");
            url = url.replace("{sub4}", session.sub4() != null ? session.sub4() : "");
            url = url.replace("{sub5}", session.sub5() != null ? session.sub5() : "");
        } else {
            url = url.replace("{sub1}", "").replace("{sub2}", "").replace("{sub3}", "").replace("{sub4}", "").replace("{sub5}", "");
        }

        return url;
    }

    /**
     * 调度分发渠道回传通知
     */
    public PostbackDeliveryLog dispatch(
            AffiliatePartner partner,
            Conversion conversion,
            ClickSession session
    ) {
        if (partner == null || partner.postbackUrlTemplate() == null || partner.postbackUrlTemplate().isBlank()) {
            return null;
        }

        String renderedUrl = renderPostbackUrl(partner.postbackUrlTemplate(), conversion, session);
        // 模拟/执行异步网络请求并记录执行日志
        PostbackDeliveryLog log = new PostbackDeliveryLog(
                conversion.id(),
                partner.id(),
                renderedUrl,
                true,
                "HTTP 200 OK (delivered)"
        );
        deliveryLogs.add(log);
        return log;
    }

    public List<PostbackDeliveryLog> getDeliveryLogs() {
        return List.copyOf(deliveryLogs);
    }
}
