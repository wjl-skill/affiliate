package com.affiliate.platform.rtb;

import com.affiliate.platform.cdp.CdpDomainEventSubscriber;
import com.affiliate.platform.cdp.RealtimeEventTraitEngine;
import com.affiliate.platform.domain.AdSlot;
import com.affiliate.platform.dsp.DeepCtrCvrInferenceEngine;
import com.affiliate.platform.dsp.DspOnlineFeedbackSubscriber;
import com.affiliate.platform.dsp.DynamicBidShadingEngine;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.InMemoryEventPublisher;
import com.affiliate.platform.event.InMemoryOutboxStore;
import com.affiliate.platform.nacos.MicroservicePlaneEndpointFilter;
import com.affiliate.platform.nacos.NacosConfigRefreshEvent;
import com.affiliate.platform.ssp.DynamicYieldManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Phase4SmartBiddingAndPlaneIsolationTest {

    @Test
    @DisplayName("1. 验证微服务四平面端点物理隔离守卫 (TRACKER / RTB / ADMIN / WORKER) 与 Nacos 热开关")
    void testMicroservicePlaneEndpointFilter() throws Exception {
        MicroservicePlaneEndpointFilter rtbFilter = new MicroservicePlaneEndpointFilter();
        rtbFilter.setApplicationName("platform-app-rtb");
        rtbFilter.setStrictIsolation(true);

        assertEquals(MicroservicePlaneEndpointFilter.Plane.RTB, rtbFilter.resolveActivePlane());

        // 场景 A: RTB 节点访问 /rtb/openrtb/2.5/bid -> 正常放行并注入 X-Microservice-Plane: RTB
        MockHttpServletRequest rtbBidReq = new MockHttpServletRequest("POST", "/rtb/openrtb/2.5/bid");
        MockHttpServletResponse rtbBidResp = new MockHttpServletResponse();
        rtbFilter.doFilter(rtbBidReq, rtbBidResp, new MockFilterChain());
        assertEquals(200, rtbBidResp.getStatus());
        assertEquals("RTB", rtbBidResp.getHeader("X-Microservice-Plane"));

        // 场景 B: RTB 节点被越权请求控制面路径 /api/v1/campaigns -> 拦截并返回 403 PLANE_ISOLATION_VIOLATION
        MockHttpServletRequest crossPlaneReq = new MockHttpServletRequest("GET", "/api/v1/campaigns");
        MockHttpServletResponse crossPlaneResp = new MockHttpServletResponse();
        rtbFilter.doFilter(crossPlaneReq, crossPlaneResp, new MockFilterChain());
        assertEquals(403, crossPlaneResp.getStatus());
        assertTrue(crossPlaneResp.getContentAsString().contains("PLANE_ISOLATION_VIOLATION"));

        // 场景 C: WORKER 节点仅开放 /api/v1/ops/outbox/** 等后台任务端点，拦截 /clk/123
        MicroservicePlaneEndpointFilter workerFilter = new MicroservicePlaneEndpointFilter();
        workerFilter.setApplicationName("platform-app-worker");

        MockHttpServletRequest workerOpsReq = new MockHttpServletRequest("GET", "/api/v1/ops/outbox/status");
        MockHttpServletResponse workerOpsResp = new MockHttpServletResponse();
        workerFilter.doFilter(workerOpsReq, workerOpsResp, new MockFilterChain());
        assertEquals(200, workerOpsResp.getStatus());

        MockHttpServletRequest workerClkReq = new MockHttpServletRequest("GET", "/clk/offer-1");
        MockHttpServletResponse workerClkResp = new MockHttpServletResponse();
        workerFilter.doFilter(workerClkReq, workerClkResp, new MockFilterChain());
        assertEquals(403, workerClkResp.getStatus());

        // 场景 D: Nacos 配置热刷新关闭隔离后放行跨平面请求
        rtbFilter.onNacosConfigRefresh(new NacosConfigRefreshEvent(
                this,
                "platform-app-rtb.yaml",
                "AFFILIATE_GROUP",
                Map.of("app.microservice.strict-isolation", "false")
        ));
        MockHttpServletResponse afterRefreshResp = new MockHttpServletResponse();
        rtbFilter.doFilter(crossPlaneReq, afterRefreshResp, new MockFilterChain());
        assertEquals(200, afterRefreshResp.getStatus());
    }

    @Test
    @DisplayName("2. 验证实时领域事件总线驱动 CDP 实时画像与 DSP 在线 FM 梯度更新 + Bid Shading 闭环")
    void testRealtimeEventBusToCdpAndDspFeedbackLoop() {
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher(new InMemoryOutboxStore());
        RealtimeEventTraitEngine traitEngine = new RealtimeEventTraitEngine();
        CdpDomainEventSubscriber cdpSubscriber = new CdpDomainEventSubscriber(traitEngine, eventPublisher);
        cdpSubscriber.initSubscription();

        DeepCtrCvrInferenceEngine inferenceEngine = new DeepCtrCvrInferenceEngine();
        DynamicBidShadingEngine bidShadingEngine = new DynamicBidShadingEngine();
        DspOnlineFeedbackSubscriber dspSubscriber = new DspOnlineFeedbackSubscriber(
                inferenceEngine, bidShadingEngine, eventPublisher
        );
        dspSubscriber.initSubscription();

        // 1. 发布曝光、点击、竞价胜出与大额转化四类领域事件
        eventPublisher.publish(DomainEvent.create(
                "impression.recorded.v1",
                "tenant_1",
                "imp_1",
                Map.of("userId", "device_9981", "slotId", "slot_us_banner", "campaignId", "camp_101", "country", "US")
        ));
        eventPublisher.publish(DomainEvent.create(
                "click.recorded.v1",
                "tenant_1",
                "clk_1",
                Map.of("userId", "device_9981", "slotId", "slot_us_banner", "campaignId", "camp_101", "country", "US")
        ));
        eventPublisher.publish(DomainEvent.create(
                "auction.win.v1",
                "tenant_1",
                "auc_1",
                Map.of("slotId", "slot_us_banner", "price", 3.20, "country", "US")
        ));
        eventPublisher.publish(DomainEvent.create(
                "conversion.approved.v1",
                "tenant_1",
                "conv_1",
                Map.of("userId", "device_9981", "slotId", "slot_us_banner", "campaignId", "camp_101", "revenue", 680.00)
        ));

        assertEquals(3L, cdpSubscriber.getProcessedTraitEvents());
        assertEquals(4L, dspSubscriber.getFeedbackCount());

        // 验证 CDP 用户实时画像已生成 whale_purchaser 高净值购买标签
        var snapshotOpt = traitEngine.getSnapshot("device_9981");
        assertTrue(snapshotOpt.isPresent());
        assertTrue(snapshotOpt.get().dynamicTraits().contains("whale_purchaser"));
    }

    @Test
    @DisplayName("3. 验证 RtbSmartBiddingBridge 串联 SSP 动态底价、CDP 实时人群溢价与 DSP FM + Bid Shading")
    void testRtbSmartBiddingBridgeEndToEnd() {
        RealtimeEventTraitEngine traitEngine = new RealtimeEventTraitEngine();
        // 预先注入大额转化用户行为，赋予 whale_purchaser 标签
        traitEngine.ingestEvent("vip_buyer_01", RealtimeEventTraitEngine.EventType.PURCHASE,
                new BigDecimal("650.00"), "FINTECH", "mobile", Instant.now());

        DynamicYieldManager yieldManager = new DynamicYieldManager();
        DeepCtrCvrInferenceEngine inferenceEngine = new DeepCtrCvrInferenceEngine();
        DynamicBidShadingEngine bidShadingEngine = new DynamicBidShadingEngine();

        RtbSmartBiddingBridge bridge = new RtbSmartBiddingBridge(
                yieldManager, traitEngine, inferenceEngine, bidShadingEngine
        );

        AdSlot baseSlot = new AdSlot("slot_home_top", "Home Top Banner", 300, 250, 0.80, true, true, Instant.now());
        OpenRtb.BidRequest request = new OpenRtb.BidRequest(
                "req_1001",
                List.of(new OpenRtb.Imp("imp_1", new OpenRtb.Banner(300, 250, List.of()), null, 0.80, "USD")),
                new OpenRtb.Site("publisher.app", "https://publisher.app/home", null),
                new OpenRtb.Device("Mozilla/5.0", "203.0.113.8", 1, "US"),
                new OpenRtb.User("vip_buyer_01", "buyer_01")
        );

        // Step 1: 验证 SSP 自适应底价增强
        AdSlot enrichedSlot = bridge.enrichSlotWithAdaptiveFloor(baseSlot, request);
        assertNotNull(enrichedSlot);
        assertTrue(enrichedSlot.floorPrice() >= 0.40);

        // Step 2 & 3: 验证 DSP + CDP 智能出价折算
        double smartBidPrice = bridge.calibrateSmartBidPrice(request, enrichedSlot, "adv_fintech", 2.60);
        assertTrue(smartBidPrice >= enrichedSlot.floorPrice(),
                "Calibrated smart bid price must be >= effective slot floor price");

        // Step 4: 验证拍卖结果回传 SSP 收益管理器及智能竞价可观测指标
        bridge.recordSlotOutcome(enrichedSlot.id(), true, smartBidPrice, 3);
        Map<String, Object> metrics = bridge.getMetricsSnapshot();
        assertEquals(1L, metrics.get("totalCalibrations"));
        assertEquals(1L, metrics.get("cdpBoostedBids"));
        assertEquals("desktop", RtbSmartBiddingBridge.resolveDeviceLabel(2));
        assertEquals("ctv", RtbSmartBiddingBridge.resolveDeviceLabel(3));
        assertEquals("mobile", RtbSmartBiddingBridge.resolveDeviceLabel(1));
    }
}
