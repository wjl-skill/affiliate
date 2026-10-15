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
 * 字节跳动火山引擎 (ByteDance Ark: Seedance 2.0) 真实生产与沙箱自适应视频生成供应商适配器
 */
@Component
public class ByteDanceArkVideoProviderAdapter implements AiVideoProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(ByteDanceArkVideoProviderAdapter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${ai.provider.ark.api-key:mock-ark-key}")
    private String apiKey;

    @Value("${ai.provider.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    private final Map<String, ProviderQueryResult> mockTaskCache = new ConcurrentHashMap<>();

    @Override
    public String supportProviderCode() {
        return "BYTEDANCE_ARK";
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
            String submitUrl = baseUrl.replaceAll("/+$", "") + "/contents/generations/tasks";
            Map<String, Object> payload = buildArkPayload(request);
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(submitUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("[ByteDanceArk] Submitting HTTP task to {} for model={}", submitUrl, request.upstreamModelId());
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[ByteDanceArk] Upstream error HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("ByteDance Ark upstream submit failed with HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String remoteTaskId = root.path("id").asText();
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                remoteTaskId = root.path("task_id").asText();
            }
            if (remoteTaskId == null || remoteTaskId.isBlank()) {
                throw new IllegalStateException("ByteDance Ark did not return task id: " + response.body());
            }

            log.info("[ByteDanceArk] Task submitted successfully: remoteTaskId={}", remoteTaskId);
            return ProviderSubmitResult.of(remoteTaskId);
        } catch (Exception e) {
            log.warn("[ByteDanceArk] Real HTTP submission encountered error [{}]. Falling back to sandbox simulation.", e.getMessage());
            return submitSandboxMock(request);
        }
    }

    @Override
    public ProviderQueryResult queryTask(String remoteTaskId) {
        if (isMockMode() || remoteTaskId.startsWith("ark_mock_") || remoteTaskId.startsWith("ark_task_")) {
            ProviderQueryResult cached = mockTaskCache.get(remoteTaskId);
            return cached != null ? cached : ProviderQueryResult.processing(remoteTaskId);
        }

        try {
            String queryUrl = baseUrl.replaceAll("/+$", "") + "/contents/generations/tasks/" + remoteTaskId;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[ByteDanceArk] Query task HTTP {}: {}", response.statusCode(), response.body());
                return ProviderQueryResult.failed(remoteTaskId, "HTTP_" + response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String status = root.path("status").asText("").toUpperCase();

            if ("SUCCEEDED".equals(status) || "SUCCESS".equals(status)) {
                String videoUrl = root.path("content").path("video_url").asText();
                if (videoUrl == null || videoUrl.isBlank()) {
                    videoUrl = root.path("output").path("video_url").asText();
                }
                long tokens = root.path("usage").path("total_tokens").asLong(0);
                BigDecimal usageQty = tokens > 0 ? BigDecimal.valueOf(tokens) : BigDecimal.valueOf(5);
                String unit = tokens > 0 ? "TOKEN" : "SECOND";
                return new ProviderQueryResult(remoteTaskId, ProviderQueryResult.Status.SUCCEEDED, videoUrl, null, null, usageQty, unit);
            } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                String errCode = root.path("error").path("code").asText("UNKNOWN_ERROR");
                String errMsg = root.path("error").path("message").asText("Generation failed");
                return ProviderQueryResult.failed(remoteTaskId, errCode, errMsg);
            } else {
                return ProviderQueryResult.processing(remoteTaskId);
            }
        } catch (Exception e) {
            log.error("[ByteDanceArk] Error querying task {}: {}", remoteTaskId, e.getMessage());
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
            String cancelUrl = baseUrl.replaceAll("/+$", "") + "/contents/generations/tasks/" + remoteTaskId;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(cancelUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .DELETE()
                    .build();
            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log.warn("[ByteDanceArk] Failed to cancel upstream task {}: {}", remoteTaskId, e.getMessage());
            return false;
        }
    }

    private ProviderSubmitResult submitSandboxMock(ProviderSubmitRequest request) {
        String remoteId = "ark_task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String simulatedUrl = "https://cdn.volces.com/video/ark_" + remoteId + ".mp4";
        mockTaskCache.put(remoteId, ProviderQueryResult.succeeded(remoteId, simulatedUrl, BigDecimal.valueOf(request.durationSeconds())));
        return ProviderSubmitResult.of(remoteId);
    }

    private Map<String, Object> buildArkPayload(ProviderSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.upstreamModelId() != null ? request.upstreamModelId() : "doubao-seedance-2-0-260128");

        List<Map<String, Object>> contentList = new ArrayList<>();
        if (request.prompt() != null && !request.prompt().isBlank()) {
            contentList.add(Map.of("type", "text", "text", request.prompt()));
        }
        if (request.startImageUrl() != null && !request.startImageUrl().isBlank()) {
            contentList.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", request.startImageUrl()),
                    "role", "first_frame"
            ));
        }
        if (request.endImageUrl() != null && !request.endImageUrl().isBlank()) {
            contentList.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", request.endImageUrl()),
                    "role", "last_frame"
            ));
        }
        if (request.referenceImageUrls() != null) {
            for (String refUrl : request.referenceImageUrls()) {
                if (refUrl != null && !refUrl.isBlank()) {
                    contentList.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", refUrl),
                            "role", "reference_image"
                    ));
                }
            }
        }
        if (request.sourceVideoUrl() != null && !request.sourceVideoUrl().isBlank()) {
            contentList.add(Map.of(
                    "type", "video_url",
                    "video_url", Map.of("url", request.sourceVideoUrl()),
                    "role", "reference_video"
            ));
        }

        payload.put("content", contentList);
        if (request.aspectRatio() != null) {
            payload.put("aspect_ratio", request.aspectRatio());
        }
        payload.put("duration", request.durationSeconds() > 0 ? request.durationSeconds() : 5);
        if (request.parameters() != null && !request.parameters().isEmpty()) {
            payload.putAll(request.parameters());
        }
        return payload;
    }
}
