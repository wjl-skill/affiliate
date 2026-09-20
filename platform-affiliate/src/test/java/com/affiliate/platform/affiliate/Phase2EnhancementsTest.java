package com.affiliate.platform.affiliate;

import com.affiliate.platform.affiliate.cache.CacheKeyGenerator;
import com.affiliate.platform.affiliate.cache.MultiLevelCacheManager;
import com.affiliate.platform.affiliate.domain.IpGeolocationCacheEntity;
import com.affiliate.platform.affiliate.domain.PaymentMethodEntity;
import com.affiliate.platform.affiliate.domain.PaymentTransactionEntity;
import com.affiliate.platform.affiliate.repository.*;
import com.affiliate.platform.affiliate.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 二阶段强化服务集成测试（地理定位CIDR算法、多币种与支付网关、邮件与Webhook通知）
 */
class Phase2EnhancementsTest {

    private GeolocationService geolocationService;
    private PaymentGatewayService paymentGatewayService;
    private AffiliateNotificationService notificationService;

    private IpGeolocationRepository geoRepository;
    private PaymentTransactionRepository transactionRepository;
    private PaymentMethodRepository paymentMethodRepository;
    private NotificationRepository notificationRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private WebhookEndpointRepository webhookRepository;
    private MultiLevelCacheManager cacheManager;
    private CacheKeyGenerator keyGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        geoRepository = Mockito.mock(IpGeolocationRepository.class);
        transactionRepository = Mockito.mock(PaymentTransactionRepository.class);
        paymentMethodRepository = Mockito.mock(PaymentMethodRepository.class);
        notificationRepository = Mockito.mock(NotificationRepository.class);
        preferenceRepository = Mockito.mock(NotificationPreferenceRepository.class);
        webhookRepository = Mockito.mock(WebhookEndpointRepository.class);
        cacheManager = Mockito.mock(MultiLevelCacheManager.class);
        keyGenerator = new CacheKeyGenerator();
        objectMapper = new ObjectMapper();

        // 默认 cacheManager 直接透传 fallback
        when(cacheManager.get(any(), any(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(3);
            return Optional.ofNullable(supplier.get());
        });

        geolocationService = new GeolocationService(geoRepository, cacheManager, keyGenerator);
        paymentGatewayService = new PaymentGatewayService(transactionRepository, paymentMethodRepository, cacheManager, keyGenerator, objectMapper);
        notificationService = new AffiliateNotificationService(notificationRepository, preferenceRepository, webhookRepository, cacheManager, keyGenerator, objectMapper);
    }

    @Test
    @DisplayName("测试 IPv4 与 IPv6 掩码 CIDR 判定算法准确度")
    void testIpv4AndIpv6CidrAlgorithm() throws Exception {
        Method ipInRangeMethod = GeolocationService.class.getDeclaredMethod("ipInRange", String.class, String.class);
        ipInRangeMethod.setAccessible(true);

        // 1. IPv4 /24 子网测试
        assertTrue((Boolean) ipInRangeMethod.invoke(geolocationService, "192.0.2.15", "192.0.2.0/24"));
        assertTrue((Boolean) ipInRangeMethod.invoke(geolocationService, "192.0.2.254", "192.0.2.0/24"));
        assertFalse((Boolean) ipInRangeMethod.invoke(geolocationService, "192.0.3.1", "192.0.2.0/24"));

        // 2. IPv4 /9 大网段掩码测试
        assertTrue((Boolean) ipInRangeMethod.invoke(geolocationService, "3.10.20.30", "3.0.0.0/9"));
        assertFalse((Boolean) ipInRangeMethod.invoke(geolocationService, "3.128.0.1", "3.0.0.0/9"));

        // 3. IPv6 /32 子网测试
        assertTrue((Boolean) ipInRangeMethod.invoke(geolocationService, "2001:db8:0:0:0:0:0:1", "2001:db8::/32"));
        assertFalse((Boolean) ipInRangeMethod.invoke(geolocationService, "2001:db9:0:0:0:0:0:1", "2001:db8::/32"));
    }

    @Test
    @DisplayName("测试多国家主流 IP 解析及内网私有 IP 识别")
    void testGeolocationLookupMultiCountry() {
        // 1. 本地内网 IP
        GeolocationService.IpGeolocation privateGeo = geolocationService.lookup("192.168.1.100");
        assertNotNull(privateGeo);
        assertEquals("ZZ", privateGeo.countryCode());

        // 2. 中国 IP
        GeolocationService.IpGeolocation cnGeo = geolocationService.lookup("114.114.114.114");
        assertNotNull(cnGeo);
        assertEquals("CN", cnGeo.countryCode());
        assertEquals("China", cnGeo.countryName());

        // 3. 英国 IP
        GeolocationService.IpGeolocation gbGeo = geolocationService.lookup("51.100.20.30");
        assertNotNull(gbGeo);
        assertEquals("GB", gbGeo.countryCode());

        // 4. 日本 IP
        GeolocationService.IpGeolocation jpGeo = geolocationService.lookup("133.20.10.5");
        assertNotNull(jpGeo);
        assertEquals("JP", jpGeo.countryCode());

        // 5. 德国 IP
        GeolocationService.IpGeolocation deGeo = geolocationService.lookup("141.50.20.10");
        assertNotNull(deGeo);
        assertEquals("DE", deGeo.countryCode());
    }

    @Test
    @DisplayName("测试支付网关流水凭据生成与多币种批付处理")
    void testPaymentGatewayProcessViaGateway() throws Exception {
        Method processMethod = PaymentGatewayService.class.getDeclaredMethod("processPaymentViaGateway",
                PaymentGatewayService.PaymentTransaction.class, PaymentGatewayService.PaymentMethod.class);
        processMethod.setAccessible(true);

        PaymentGatewayService.PaymentTransaction tx = new PaymentGatewayService.PaymentTransaction(
                "tx_001", "aff_100", "inv_200", "pm_300",
                new BigDecimal("500.00"), "USD", BigDecimal.ZERO, new BigDecimal("500.00"), "USD",
                BigDecimal.ONE, new BigDecimal("500.00"),
                PaymentGatewayService.PaymentStatus.PENDING, null, null, 0,
                Instant.now(), null, null
        );

        // 1. 测试 PayPal 批付凭证生成
        PaymentGatewayService.PaymentMethod paypalMethod = new PaymentGatewayService.PaymentMethod(
                "pm_paypal", "aff_100", PaymentGatewayService.PaymentMethodType.PAYPAL,
                Map.of("email", "partner@affiliate.com"), "USD", true,
                PaymentGatewayService.PaymentMethodStatus.VERIFIED, null, Instant.now(), Instant.now()
        );
        String paypalRef = (String) processMethod.invoke(paymentGatewayService, tx, paypalMethod);
        assertNotNull(paypalRef);
        assertTrue(paypalRef.startsWith("pp_payout_"));

        // 2. 测试 Stripe 批付凭证生成
        PaymentGatewayService.PaymentMethod stripeMethod = new PaymentGatewayService.PaymentMethod(
                "pm_stripe", "aff_100", PaymentGatewayService.PaymentMethodType.STRIPE,
                Map.of("stripe_account_id", "acct_1H4x2..."), "USD", false,
                PaymentGatewayService.PaymentMethodStatus.VERIFIED, null, Instant.now(), Instant.now()
        );
        String stripeRef = (String) processMethod.invoke(paymentGatewayService, tx, stripeMethod);
        assertNotNull(stripeRef);
        assertTrue(stripeRef.startsWith("str_tr_"));
    }

    @Test
    @DisplayName("测试渠道通知服务之转化与支付通知流程")
    void testNotificationServiceConversionAndPayment() {
        assertDoesNotThrow(() -> {
            notificationService.notifyConversion(
                    "aff_999", "offer_101", "clk_abc123", "tx_9988",
                    AffiliateNotificationService.ConversionStatus.APPROVED, "45.50"
            );

            notificationService.notifyPayment(
                    "aff_999", "inv_2026_09",
                    AffiliateNotificationService.PaymentStatus.PAYMENT_COMPLETED,
                    "1250.00", "PAYPAL", null
            );
        });
    }
}
