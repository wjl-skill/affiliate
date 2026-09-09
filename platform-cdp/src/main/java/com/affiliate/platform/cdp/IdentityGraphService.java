package com.affiliate.platform.cdp;

import com.affiliate.platform.entity.CdpIdentityGraphEntity;
import com.affiliate.platform.mapper.CdpIdentityGraphMapper;
import com.affiliate.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** CDP 身份图谱服务；数据库启用时通过 MyBatis-Plus Mapper 持久化。 */
@Service
public class IdentityGraphService {
    public enum IdentifierType {
        PHONE(1.0), EMAIL(1.0), CRM_ID(1.0), DEVICE_ID(0.95), PROBABILISTIC_FINGERPRINT(0.85);
        private final double defaultConfidence;
        IdentifierType(double defaultConfidence) { this.defaultConfidence = defaultConfidence; }
        public double getDefaultConfidence() { return defaultConfidence; }
    }
    public record IdentityNode(String identifier, IdentifierType type, double confidence) {}

    private final CdpIdentityGraphMapper graphMapper;
    private final ConcurrentMap<String, Set<IdentityNode>> graph = new ConcurrentHashMap<>();

    public IdentityGraphService(@Autowired(required = false) CdpIdentityGraphMapper graphMapper) { this.graphMapper = graphMapper; }
    public IdentityGraphService() { this(null); }

    public boolean linkIdentifier(String primaryId, IdentityNode node, double minConfidenceAllowed) {
        if (primaryId == null || primaryId.isBlank() || node == null || node.identifier() == null || node.identifier().isBlank()
                || node.confidence() < minConfidenceAllowed) return false;
        String tenant = tenant();
        if (graphMapper != null) {
            graphMapper.upsert(new CdpIdentityGraphEntity(tenant, node.type().name(), node.identifier(), primaryId, Instant.now()));
            return true;
        }
        graph.computeIfAbsent(tenant + ":" + primaryId, ignored -> ConcurrentHashMap.newKeySet()).add(node);
        return true;
    }

    public Set<IdentityNode> getLinkedIdentities(String primaryId) {
        if (primaryId == null) return Set.of();
        String tenant = tenant();
        if (graphMapper != null) {
            return graphMapper.findByProfile(tenant, primaryId).stream().map(this::toNode)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Set.copyOf(graph.getOrDefault(tenant + ":" + primaryId, Set.of()));
    }

    public boolean contains(String primaryId, String identifier) {
        if (primaryId == null || identifier == null) return false;
        if (graphMapper != null) {
            return graphMapper.findByProfile(tenant(), primaryId).stream().anyMatch(e -> e.getIdentifierVal().equalsIgnoreCase(identifier));
        }
        return getLinkedIdentities(primaryId).stream().anyMatch(n -> n.identifier().equalsIgnoreCase(identifier));
    }

    private IdentityNode toNode(CdpIdentityGraphEntity entity) {
        IdentifierType type;
        try { type = IdentifierType.valueOf(entity.getIdentifierType()); }
        catch (IllegalArgumentException ex) { type = IdentifierType.PROBABILISTIC_FINGERPRINT; }
        return new IdentityNode(entity.getIdentifierVal(), type, type.getDefaultConfidence());
    }
    private static String tenant() { return TenantContext.get() == null ? "public" : TenantContext.required(); }
}
