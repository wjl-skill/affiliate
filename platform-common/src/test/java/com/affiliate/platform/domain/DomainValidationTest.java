package com.affiliate.platform.domain;

import com.affiliate.platform.domain.Enums.CreativeType;
import com.affiliate.platform.service.DomainValidationException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainValidationTest {

    @Test
    void testCreativeValid() {
        Creative creative = new Creative("c1", "Banner Ad", CreativeType.BANNER, "https://cdn.example.com/ad.png", "https://example.com", 300, 250, Set.of("tech"), true, Instant.now());
        assertEquals("c1", creative.id());
        assertEquals(300, creative.width());
        assertEquals(250, creative.height());
    }

    @Test
    void testCreativeInvalidDimensions() {
        assertThrows(DomainValidationException.class, () ->
            new Creative("c2", "Invalid Ad", CreativeType.BANNER, "https://cdn.example.com/ad.png", "https://example.com", -1, 250, Set.of(), true, Instant.now())
        );
    }

    @Test
    void testAdSlotNegativeFloorPrice() {
        assertThrows(DomainValidationException.class, () ->
            new AdSlot("s1", "Top Slot", 300, 250, -0.5, true, true, Instant.now())
        );
    }

    @Test
    void testPaginationCalculation() {
        Pagination.PageQuery query = new Pagination.PageQuery(2, 10, "id", true);
        assertEquals(20, query.offset());

        Pagination.PageResult<String> result = new Pagination.PageResult<>(java.util.List.of("a", "b"), 25, 2, 10);
        assertEquals(3, result.totalPages());
        assertFalse(result.hasNext());
        assertTrue(result.hasPrevious());
    }
}
