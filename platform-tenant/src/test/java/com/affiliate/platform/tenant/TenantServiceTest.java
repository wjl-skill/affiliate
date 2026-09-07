package com.affiliate.platform.tenant;

import com.affiliate.platform.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TenantServiceTest {

    private TenantService service;

    @BeforeEach
    void setUp() {
        service = new TenantService(new InMemoryRepository<>(Tenant::id));
    }

    @Test
    void createAndGetTenant() {
        Tenant created = service.create(new Tenant(null, "Acme Corp", Tenant.Status.ACTIVE, Instant.now()));
        assertNotNull(created.id());
        assertEquals("Acme Corp", created.name());
        assertEquals(Tenant.Status.ACTIVE, created.status());

        Tenant fetched = service.get(created.id());
        assertEquals("Acme Corp", fetched.name());

        Tenant deactivated = service.setActive(created.id(), false);
        assertEquals(Tenant.Status.SUSPENDED, deactivated.status());

        List<Tenant> list = service.list();
        assertEquals(1, list.size());
    }
}
