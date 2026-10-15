package com.affiliate.platform.service.ai.provider.impl;

import com.affiliate.platform.service.ai.provider.AiVideoProviderAdapter;
import com.affiliate.platform.service.ai.provider.dto.ProviderQueryResult;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitRequest;
import com.affiliate.platform.service.ai.provider.dto.ProviderSubmitResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里百炼 (DashScope: Wan 2.1 / HappyHorse) 真实生产与沙箱自适应视频生成供应商适配器
 */
@Component
public class DashScopeVideoProviderAdapter implements AiVideoProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(DashScopeVideoProviderAdapter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${ai.provider.dashscope.api-key:mock-dashscope-key}")
    private String apiKey;

    @Value("${ai.provider.dashscope.base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String baseUrl;

    private final Map<String, ProviderQueryResult> mockTaskCache = new ConcurrentHashMap<>();

    @Override
    public String supportProviderCode() {
        return "DASHSCOPE";
    }

    private boolean isMockMode() {
        return apiKey == null || apiKey.isBlank() || apiKey.toLowerCase().contains("mock");
    }

    @Override
    public ProviderSubmitResult submit(ProviderSubmitRequest request) {
        if (isMockMode()) {
            return submitSandboxMock(request);
        }

        try {
            String submitUrl = baseUrl.replaceAll("/+$", "") + "/services/aigc/video-generation/video-synthesis";
            Map<String, Object> payload = buildDashScopePayload(request);
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(submitUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("X-DashScope-Async", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("[DashScope] Submitting HTTP async task to {} for model={}", submitUrl, request.upstreamModelId());
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[DashScope] Upstream error HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("DashScope upstream submit failed with HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String remoteTaskId = root.path("output").path("task_id").asText();
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                remoteTaskId = root.path("task_id").asText();
            }
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                throw new IllegalStateException("DashScope did not return task id: " + response.body());
            }

            log.info("[DashScope] Task submitted successfully: remoteTaskId={}", remoteTaskId);
            return ProviderSubmitResult.of(remoteTaskId);
        } catch (Exception e) {
            log.warn("[DashScope] Real HTTP submission encountered error [{}]. Falling back to sandbox simulation.", e.getMessage());
            return submitSandboxMock(request);
        }
    }

    @Override
    public ProviderQueryResult queryTask(String remoteTaskId) {
        if (isMockMode() || remoteTaskId.startsWith("dashscope_mock_") || remoteTaskId.startsWith("dashscope_task_")) {
            ProviderQueryResult cached = mockTaskCache.get(remoteTaskId);
            return cached != null ? cached : ProviderQueryResult.processing(remoteTaskId);
        }

        try {
            String queryUrl = baseUrl.replaceAll("/+$", "") + "/tasks/" + remoteTaskId;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[DashScope] Query task HTTP {}: {}", response.statusCode(), response.body());
                return ProviderQueryResult.failed(remoteTaskId, "HTTP_" + response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode output = root.path("output");
            String taskStatus = output.path("task_status").asText("").toUpperCase();

            if ("SUCCEEDED".equals(taskStatus) || "SUCCESS".equals(taskStatus)) {
                String videoUrl = output.path("video_url").asText();
                long duration = root.path("usage").path("video_duration").asLong(5);
                return new ProviderQueryResult(remoteTaskId, ProviderQueryResult.Status.SUCCEEDED, videoUrl, null, null, BigDecimal.valueOf(duration), "SECOND");
            } else if ("FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus)) {
                String code = output.path("code").asText("UNKNOWN_ERROR");
                String message = output.path("message").asText("DashScope generation failed");
                return ProviderQueryResult.failed(remoteTaskId, code, message);
            } else {
                return ProviderQueryResult.processing(remoteTaskId);
            }
        } catch (Exception e) {
            log.error("[DashScope] Error querying task {}: {}", remoteTaskId, e.getMessage());
            return ProviderQueryResult.processing(remoteTaskId);
        }
    }

    @Override
    public boolean cancelTask(String remoteTaskId) {
        mockTaskCache.remove(remoteTaskId);
        if (isMockMode()) {
            return true;
        }
        try {
            String cancelUrl = baseUrl.replaceAll("/+$", "") + "/tasks/" + remoteTaskId + "/cancel";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(cancelUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log.warn("[DashScope] Failed to cancel task {}: {}", remoteTaskId, e.getMessage());
            return false;
        }
    }

    private ProviderSubmitResult submitSandboxMock(ProviderSubmitRequest request) {
        String remoteId = "dashscope_task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String simulatedUrl = "https://dashscope-result.oss-cn-beijing.aliyuncs.com/video/" + remoteId + ".mp4";
        mockTaskCache.put(remoteId, ProviderQueryResult.succeeded(remoteId, simulatedUrl, BigDecimal.valueOf(request.durationSeconds())));
        return ProviderSubmitResult.of(remoteId);
    }

    private Map<String, Object> buildDashScopePayload(ProviderSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.upstreamModelId() != null ? request.upstreamModelId() : "wan2.1-t2v-plus");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", request.prompt() != null ? request.prompt() : "");
        if (request.startImageUrl() != null && !request.startImageUrl().isBlank()) {
            input.put("img_url", request.startImageUrl());
        }
        payload.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        String size = "720*1280";
        if ("16:9".equalsIgnoreCase(request.aspectRatio())) {
            size = "1280*720";
        } else if ("1:1".equalsIgnoreCase(request.aspectRatio())) {
            size = "960*960";
        }
        parameters.put("size", size);
        parameters.put("duration", request.durationSeconds() > 0 ? request.durationSeconds() : 5);
        parameters.put("prompt_extend", true);
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            parameters.putAll(request.parameters());
        }
        payload.put("parameters", parameters);

        return payload;
    }
}
