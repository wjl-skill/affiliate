package com.affiliate.platform.cdp;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class IdentityMappingServiceTest {
    @Test void mapsAndMergesFirstPartyIdentifiers() {
        IdentityMappingService service = new IdentityMappingService(event -> {});
        service.create(new CustomerProfile(null, "member-1", Set.of("phone:+8613800000000"), Map.of("level", "silver"), Set.of("buyer"), null, CustomerProfile.Status.ACTIVE, null));
        CustomerProfile merged = service.merge("member-1", Set.of("openid:wx-1"), Map.of("level", "gold"), Set.of("vip"));
        assertEquals("gold", service.resolve("openid:wx-1").attributes().get("level"));
        assertTrue(merged.traits().contains("vip"));
    }
}
