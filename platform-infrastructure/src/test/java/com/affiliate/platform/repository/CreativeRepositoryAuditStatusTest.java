package com.affiliate.platform.repository;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.domain.Creative;
import com.affiliate.platform.domain.Enums.AuditStatus;
import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.entity.CreativeEntity;
import com.affiliate.platform.mapper.CreativeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreativeRepositoryAuditStatusTest {

    private CreativeMapper mapper;
    private TwoTierCacheManager cacheManager;
    private TwoTierCache<String, Creative> cache;
    private PostgresCreativeRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(CreativeMapper.class);
        cacheManager = mock(TwoTierCacheManager.class);
        cache = mock(TwoTierCache.class);
        when(cacheManager.getOrCreate(eq("creative"), eq(Creative.class))).thenReturn(cache);

        repository = new PostgresCreativeRepository(mapper, new ObjectMapper(), cacheManager);
    }

    @Test
    void defaultCreativeStatusMustBePendingReview() {
        // 使用兼容构造器，未指定审核状态
        Creative creative = new Creative(
                "cr-100", "Banner Ad", CreativeType.BANNER,
                "https://cdn.example.com/ad.jpg", "https://example.com/landing",
                300, 250, Set.of("tech"), true, Instant.now()
        );

        // 断言默认安全状态为待审核，杜绝未审先播
        assertEquals(AuditStatus.PENDING_REVIEW, creative.auditStatus());
        assertNull(creative.rejectionReason());
    }

    @Test
    void persistAndReloadPreservesPendingAndRejectedStatus() {
        // 1. 测试保存待审素材
        Creative pending = new Creative(
                "cr-pending", "Pending Ad", CreativeType.BANNER,
                "https://cdn.example.com/p.jpg", "https://example.com/p",
                300, 250, Set.of("news"), true, Instant.now()
        );

        repository.save(pending);

        ArgumentCaptor<CreativeEntity> captor = ArgumentCaptor.forClass(CreativeEntity.class);
        verify(mapper).insert(captor.capture());
        CreativeEntity savedEntity = captor.getValue();
        assertEquals("PENDING_REVIEW", savedEntity.getAuditStatus());

        // 2. 模拟从数据库回读 (缓存失效场景下)
        when(cache.get(eq("cr-pending"), any())).thenAnswer(invocation -> {
            Function<String, Creative> loader = invocation.getArgument(1);
            return loader.apply("cr-pending");
        });
        when(mapper.selectById("cr-pending")).thenReturn(savedEntity);

        Optional<Creative> reloaded = repository.find("cr-pending");
        assertTrue(reloaded.isPresent());
        assertEquals(AuditStatus.PENDING_REVIEW, reloaded.get().auditStatus());
        assertNotEquals(AuditStatus.APPROVED, reloaded.get().auditStatus());

        // 3. 测试保存被驳回素材
        Creative rejected = new Creative(
                "cr-rejected", "Rejected Ad", CreativeType.BANNER,
                "https://cdn.example.com/r.jpg", "https://example.com/r",
                300, 250, Set.of("news"), false,
                AuditStatus.REJECTED, "落地页包含违规诱导内容",
                null, null, Instant.now()
        );

        repository.save(rejected);
        verify(mapper, times(2)).insert(captor.capture());
        CreativeEntity rejectedEntity = captor.getValue();
        assertEquals("REJECTED", rejectedEntity.getAuditStatus());
        assertEquals("落地页包含违规诱导内容", rejectedEntity.getRejectionReason());

        when(mapper.selectById("cr-rejected")).thenReturn(rejectedEntity);
        when(cache.get(eq("cr-rejected"), any())).thenAnswer(invocation -> {
            Function<String, Creative> loader = invocation.getArgument(1);
            return loader.apply("cr-rejected");
        });

        Optional<Creative> reloadedRejected = repository.find("cr-rejected");
        assertTrue(reloadedRejected.isPresent());
        assertEquals(AuditStatus.REJECTED, reloadedRejected.get().auditStatus());
        assertEquals("落地页包含违规诱导内容", reloadedRejected.get().rejectionReason());
    }
}
