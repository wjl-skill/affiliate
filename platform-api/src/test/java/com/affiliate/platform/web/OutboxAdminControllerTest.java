package com.affiliate.platform.web;

import com.affiliate.platform.event.EventOutboxDeadEntity;
import com.affiliate.platform.event.OutboxDeadLetterService;
import com.affiliate.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxAdminControllerTest {

    private OutboxDeadLetterService deadLetterService;
    private OutboxAdminController controller;

    @BeforeEach
    void setUp() {
        deadLetterService = mock(OutboxDeadLetterService.class);
        controller = new OutboxAdminController(deadLetterService);
        TenantContext.set("tenant_admin_test");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("查询租户死信事件列表")
    void testListDeadLetters() {
        UUID eventId = UUID.randomUUID();
        EventOutboxDeadEntity dead = new EventOutboxDeadEntity(
                eventId, "tenant_admin_test", "order.paid.v1", "ord_101",
                "{}", Instant.now(), Instant.now(), 5, "Kafka timeout", null
        );

        when(deadLetterService.getDeadLetters("tenant_admin_test", 50))
                .thenReturn(List.of(dead));

        List<EventOutboxDeadEntity> list = controller.listDeadLetters(50);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(eventId);
        assertThat(list.get(0).getAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("重放死信事件成功")
    void testReplayDeadLetterSuccess() {
        UUID eventId = UUID.randomUUID();
        when(deadLetterService.replay(eventId)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.replayDeadLetter(eventId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("event_id")).isEqualTo(eventId);
    }

    @Test
    @DisplayName("重放不存在的死信事件返回失败状态")
    void testReplayDeadLetterNotFound() {
        UUID eventId = UUID.randomUUID();
        when(deadLetterService.replay(eventId)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.replayDeadLetter(eventId);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody().get("status")).isEqualTo("FAILED");
    }
}
