package com.affiliate.platform.tenant;

import com.affiliate.platform.domain.Enums.ConnectionStatus;
import com.affiliate.platform.domain.Enums.SupplyType;
import com.affiliate.platform.domain.PartnerConnection;
import com.affiliate.platform.repository.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PartnerServiceTenantAndMaskingTest {

    private InMemoryPartnerRepository repository;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPartnerRepository();
        service = new PartnerService(repository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTenantPropagationAndCredentialMaskingOnCreateAndGet() {
        TenantContext.set("tenant_alpha");

        Map<String, String> rawSettings = Map.of(
                "apiKey", "secret-token-1234567890",
                "appSecret", "top-secret-val",
                "endpointUrl", "https://api.partner.com/v1"
        );

        PartnerConnection input = new PartnerConnection(
                null,
                "Partner A",
                SupplyType.DSP,
                "https://api.partner.com",
                rawSettings,
                ConnectionStatus.ACTIVE,
                Instant.now()
        );

        PartnerConnection created = service.create(input);
        assertNotNull(created);
        assertEquals("tenant_alpha", created.tenantId(), "应当继承自当前上下文租户");

        // 验证掩码生效
        Map<String, String> maskedSettings = created.settings();
        assertNotEquals("secret-token-1234567890", maskedSettings.get("apiKey"));
        assertTrue(maskedSettings.get("apiKey").contains("******"));
        assertNotEquals("top-secret-val", maskedSettings.get("appSecret"));
        assertEquals("https://api.partner.com/v1", maskedSettings.get("endpointUrl"), "非机密字段不应掩码");

        // 仓储内部存储应当为原始凭据，确保运行引擎可正常调用
        PartnerConnection storedInDb = repository.find(created.id()).orElseThrow();
        assertEquals("secret-token-1234567890", storedInDb.settings().get("apiKey"));

        // 通过 service.get(id) 再次查询，对外依然必须脱敏
        PartnerConnection queried = service.get(created.id());
        assertTrue(queried.settings().get("apiKey").contains("******"));

        // 列表查询也必须脱敏
        List<PartnerConnection> list = service.list();
        assertEquals(1, list.size());
        assertTrue(list.get(0).settings().get("apiKey").contains("******"));
    }

    static class InMemoryPartnerRepository implements Repository<PartnerConnection> {
        private final Map<String, PartnerConnection> store = new ConcurrentHashMap<>();

        @Override
        public PartnerConnection save(PartnerConnection entity) {
            String id = entity.id() != null ? entity.id() : nextId("partner");
            PartnerConnection toSave = new PartnerConnection(
                    id,
                    entity.tenantId(),
                    entity.name(),
                    entity.type(),
                    entity.endpoint(),
                    entity.settings(),
                    entity.status(),
                    entity.updatedAt()
            );
            store.put(id, toSave);
            return toSave;
        }

        @Override
        public Optional<PartnerConnection> find(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<PartnerConnection> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }

        @Override
        public String nextId(String prefix) {
            return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
    }
}
