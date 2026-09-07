package com.affiliate.platform.cdp;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 确定性与概率性客户身份图谱服务 (CDP Identity Graph Service)
 * <p>
 * 支持多触点跨端用户身份关联打通：
 * 1. 确定性打通（Deterministic）：基于手机号、邮箱、会员 ID（置信度 1.0）；
 * 2. 概率性打通（Probabilistic）：基于 IP + 设备指纹环境相似度（置信度 0.85）；
 * 3. 支持置信度门槛截断（Confidence Threshold），防止低置信度错误混淆。
 */
@Service
public class IdentityGraphService {

    public enum IdentifierType {
        /** 确定性手机号 */
        PHONE(1.0),
        /** 确定性邮箱 */
        EMAIL(1.0),
        /** 确定性一方 CRM 会员 ID */
        CRM_ID(1.0),
        /** 确定性设备物理 ID (IDFA/GAID) */
        DEVICE_ID(0.95),
        /** 概率性网络与指纹组合标识 */
        PROBABILISTIC_FINGERPRINT(0.85);

        private final double defaultConfidence;

        IdentifierType(double defaultConfidence) {
            this.defaultConfidence = defaultConfidence;
        }

        public double getDefaultConfidence() {
            return defaultConfidence;
        }
    }

    public record IdentityNode(String identifier, IdentifierType type, double confidence) {}

    // 主身份到关联标识集合图谱：Key 为 primaryId, Value 为关联节点列表
    private final ConcurrentMap<String, Set<IdentityNode>> graph = new ConcurrentHashMap<>();

    /**
     * 关联身份节点至主档案 (带置信度门槛检查)
     *
     * @param primaryId            主档案标识 (如 "cust_1001")
     * @param node                 待关联的身份节点
     * @param minConfidenceAllowed 允许打通的最低置信度门槛 (如 0.80)
     * @return true 代表关联成功，false 代表置信度不足未关联
     */
    public boolean linkIdentifier(String primaryId, IdentityNode node, double minConfidenceAllowed) {
        if (primaryId == null || primaryId.isBlank() || node == null) {
            return false;
        }

        if (node.confidence() < minConfidenceAllowed) {
            return false; // 置信度不足，拒绝打通
        }

        graph.computeIfAbsent(primaryId, k -> ConcurrentHashMap.newKeySet()).add(node);
        return true;
    }

    /**
     * 获取客户名下所有关联身份图谱节点
     */
    public Set<IdentityNode> getLinkedIdentities(String primaryId) {
        return graph.getOrDefault(primaryId, Set.of());
    }

    /**
     * 判断指定标识是否归属于该客户 (带置信度)
     */
    public boolean contains(String primaryId, String identifier) {
        Set<IdentityNode> nodes = graph.get(primaryId);
        if (nodes == null) return false;
        return nodes.stream().anyMatch(n -> n.identifier().equalsIgnoreCase(identifier));
    }
}
