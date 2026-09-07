package com.affiliate.platform.service;

import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.AuditStatus;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CreativeServiceTest {

    private CreativeService service;

    @BeforeEach
    void setUp() {
        service = new CreativeService(new InMemoryRepository<>(Creative::id), event -> {});
    }

    @Test
    void auditWorkflowApproveAndReject() {
        // 创建素材 -> 默认状态为 PENDING_REVIEW
        Creative creative = service.create(new Creative(
                null,
                "Summer Banner",
                CreativeType.BANNER,
                "https://cdn.example.com/banner.png",
                "https://example.com/landing",
                300,
                250,
                Set.of("IAB1"),
                true,
                Instant.now()
        ));

        assertEquals(AuditStatus.PENDING_REVIEW, creative.auditStatus());
        assertFalse(creative.isEligibleForBidding());
        assertTrue(service.listEligible().isEmpty());

        // 审核通过
        Creative approved = service.approve(creative.id());
        assertEquals(AuditStatus.APPROVED, approved.auditStatus());
        assertTrue(approved.isEligibleForBidding());
        assertEquals(1, service.listEligible().size());

        // 注入监测代码
        Creative tracked = service.setTrackers(approved.id(),
                List.of("https://tracker.com/imp?id=123"),
                List.of("https://tracker.com/clk?id=123")
        );
        assertEquals(1, tracked.impressionTrackers().size());
        assertEquals(1, tracked.clickTrackers().size());

        // 审核驳回
        Creative rejected = service.reject(creative.id(), "ADULT_CONTENT");
        assertEquals(AuditStatus.REJECTED, rejected.auditStatus());
        assertEquals("ADULT_CONTENT", rejected.rejectionReason());
        assertFalse(rejected.isEligibleForBidding());
        assertTrue(service.listEligible().isEmpty());
    }
}
