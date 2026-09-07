package com.affiliate.platform.dmp;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DmpServiceTest {
    @Test void expiresAnonymousAudience() {
        DmpService service = new DmpService(event -> {});
        AudienceSegment segment = service.create(new AudienceSegment(null, "retarget", AudienceSegment.Source.THIRD_PARTY, Set.of("sports"), Instant.now().plusSeconds(60), 0, AudienceSegment.Status.DRAFT, null));
        service.activate(segment.id(), true); service.addMembers(segment.id(), Set.of("cookie:abc"));
        assertTrue(service.contains(segment.id(), "cookie:abc"));
        assertEquals(1, service.get(segment.id()).memberCount());
    }
}
