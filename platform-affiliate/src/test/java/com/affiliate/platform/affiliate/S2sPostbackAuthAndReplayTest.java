package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.domain.Offer;
import com.affiliate.platform.affiliate.service.*;
import com.affiliate.platform.affiliate.web.S2sPostbackController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class S2sPostbackAuthAndReplayTest {

    private OfferService offerService;
    private ClickTrackerService clickTracker;
    private S2sPostbackService postbackService;
    private SubIdAnalyticsService analyticsService;
    private S2sPostbackController controller;

    private static final String HMAC_SECRET = "affiliate-postback-hmac-secret-default";

    @BeforeEach
    void setUp() {
        offerService = new OfferService();
        clickTracker = new ClickTrackerService();
        AffiliateAntiFraudEngine antiFraudEngine = new AffiliateAntiFraudEngine();
        PublisherPostbackDispatcher postbackDispatcher = new PublisherPostbackDispatcher();
        postbackService = new S2sPostbackService(clickTracker, offerService, antiFraudEngine, postbackDispatcher);
        analyticsService = new SubIdAnalyticsService();
        controller = new S2sPostbackController(postbackService, analyticsService);

        // 显式开启强制鉴权模式
        ReflectionTestUtils.setField(controller, "authRequired", true);
        ReflectionTestUtils.setField(controller, "postbackSecret", HMAC_SECRET);
    }

    private String calculateHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void postbackRejectsUnauthorizedOrFakeApiKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 伪造/非法的 API Key
        request.addHeader("X-API-Key", "invalid-hacker-key");

        ResponseEntity<Map<String, Object>> response = controller.handleGetPostback(
                "c_fake123", "tx_001", new BigDecimal("100.00"), request
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHORIZED", response.getBody().get("status"));
    }

    @Test
    void postbackAcceptsValidApiKeyAndIdempotentTxId() {
        // 1. 创建有效 Offer 与点击
        Offer offer = new Offer(
                "off-auth-1", "tenant-1", "adv-nike", "Nike CPA",
                "https://nike.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("10.00"), new BigDecimal("15.00"),
                Offer.Status.ACTIVE, 100, new BigDecimal("1000.00"),
                null, Set.of("US"), Set.of(1), null, Instant.now()
        );
        offerService.save(offer);

        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(
                offer, "aff-1", "sub1", null, null, null, null,
                "192.168.1.100", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "US", 1
        );
        // 模拟点击发生于 10 秒前以满足反作弊 CTIT >= 3s 要求
        com.affiliate.platform.affiliate.domain.ClickSession validSession = new com.affiliate.platform.affiliate.domain.ClickSession(
                click.clickId(), "tenant-1", offer.id(), "aff-1",
                "sub1", null, null, null, null,
                "192.168.1.100", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "US", 1,
                Instant.now().minusSeconds(10), Instant.now().plus(java.time.Duration.ofDays(30))
        );
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, com.affiliate.platform.affiliate.domain.ClickSession> store =
                (java.util.concurrent.ConcurrentMap<String, com.affiliate.platform.affiliate.domain.ClickSession>) ReflectionTestUtils.getField(clickTracker, "sessionStore");
        store.put(click.clickId(), validSession);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "adv-key-default");

        // 2. 首次回传应成功 (鉴权通过且状态为 200 OK，待结算 PENDING)
        ResponseEntity<Map<String, Object>> res1 = controller.handleGetPostback(
                click.clickId(), "order_tx_999", new BigDecimal("100.00"), request
        );
        assertEquals(HttpStatus.OK, res1.getStatusCode());
        assertEquals("PENDING", res1.getBody().get("status"));
        String convId1 = (String) res1.getBody().get("conversion_id");
        assertNotNull(convId1);

        // 3. 相同 txid 重复回传，应直接幂等返回，不重复创建
        ResponseEntity<Map<String, Object>> res2 = controller.handleGetPostback(
                click.clickId(), "order_tx_999", new BigDecimal("100.00"), request
        );
        assertEquals(HttpStatus.OK, res2.getStatusCode());
        assertEquals("PENDING", res2.getBody().get("status"));
        assertEquals(convId1, res2.getBody().get("conversion_id"), "重复交易号应幂等返回同一转化");
    }

    @Test
    void postbackHmacSignatureAndReplayAttackProtection() throws Exception {
        Offer offer = new Offer(
                "off-auth-2", "tenant-1", "adv-apple", "Apple CPA",
                "https://apple.com?click_id={click_id}", Offer.PayoutType.CPA,
                new BigDecimal("20.00"), new BigDecimal("30.00"),
                Offer.Status.ACTIVE, 100, new BigDecimal("1000.00"),
                null, Set.of("US"), Set.of(1), null, Instant.now()
        );
        offerService.save(offer);

        ClickTrackerService.ClickTrackingResult click = clickTracker.trackClick(
                offer, "aff-2", "sub1", null, null, null, null,
                "192.168.1.101", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "US", 1
        );
        com.affiliate.platform.affiliate.domain.ClickSession validSession = new com.affiliate.platform.affiliate.domain.ClickSession(
                click.clickId(), "tenant-1", offer.id(), "aff-2",
                "sub1", null, null, null, null,
                "192.168.1.101", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "US", 1,
                Instant.now().minusSeconds(10), Instant.now().plus(java.time.Duration.ofDays(30))
        );
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, com.affiliate.platform.affiliate.domain.ClickSession> store =
                (java.util.concurrent.ConcurrentMap<String, com.affiliate.platform.affiliate.domain.ClickSession>) ReflectionTestUtils.getField(clickTracker, "sessionStore");
        store.put(click.clickId(), validSession);

        String clickId = click.clickId();
        String txId = "order_hmac_101";

        // 1. 测试重放攻击：时间戳超过 300 秒前 (如 600 秒前)
        long expiredTs = Instant.now().getEpochSecond() - 600;
        String expiredPayload = clickId + ":" + txId + ":" + expiredTs;
        String expiredSign = calculateHmac(expiredPayload, HMAC_SECRET);

        MockHttpServletRequest replayReq = new MockHttpServletRequest();
        replayReq.addHeader("X-Signature", expiredSign);
        replayReq.addHeader("X-Timestamp", String.valueOf(expiredTs));

        ResponseEntity<Map<String, Object>> replayRes = controller.handleGetPostback(
                clickId, txId, new BigDecimal("50.00"), replayReq
        );
        assertEquals(HttpStatus.UNAUTHORIZED, replayRes.getStatusCode());

        // 2. 测试合法签名与正常时间戳
        long validTs = Instant.now().getEpochSecond();
        String validPayload = clickId + ":" + txId + ":" + validTs;
        String validSign = calculateHmac(validPayload, HMAC_SECRET);

        MockHttpServletRequest validReq = new MockHttpServletRequest();
        validReq.addHeader("X-Signature", validSign);
        validReq.addHeader("X-Timestamp", String.valueOf(validTs));

        ResponseEntity<Map<String, Object>> validRes = controller.handleGetPostback(
                clickId, txId, new BigDecimal("50.00"), validReq
        );
        assertEquals(HttpStatus.OK, validRes.getStatusCode());
        assertEquals("PENDING", validRes.getBody().get("status"));
    }
}
