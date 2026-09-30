package com.affiliate.platform.cdp;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级 CDP 跨触点身份图谱与无向图连通分支引擎 (Identity Graph Resolution Engine)
 * <p>
 * 对标 Segment Personas / mParticle / Adobe AEP 核心身份打通技术：
 * 1. 采用加权并查集 (Disjoint Set Union, DSU) 求解跨触点多标识无向图连通分支，实现确定性与概率性传递闭包打通；
 * 2. 区分确定性标识 (Deterministic, 手机号/邮箱/CRM ID) 与概率性标识 (Probabilistic, 设备指纹/Cookie)；
 * 3. 具备工业级防身份桥接爆炸保护 (Anti-Bridging Guardrail)：
 *    当单节点度数超过设定的公共设备安全阈值 (MAX_CLUSTER_SIZE) 时，自动熔断强行断边，防止公共设备导致万人档案错误合并。
 */
@Component
public class IdentityGraphEngine {

    // 单个连通分支最大标识数量安全熔断阈值（防止网吧/公共设备桥接）
    public static final int DEFAULT_MAX_CLUSTER_SIZE = 30;

    // 默认最低可信打通置信度
    public static final double DEFAULT_MIN_CONFIDENCE = 0.80;

    // 并查集父指针映射：identifier -> parentIdentifier
    private final ConcurrentMap<String, String> parent = new ConcurrentHashMap<>();

    // 标识集群大小计数：rootIdentifier -> size
    private final ConcurrentMap<String, AtomicInteger> clusterSize = new ConcurrentHashMap<>();

    // 节点类型映射：identifier -> IdentifierType
    private final ConcurrentMap<String, IdentityGraphService.IdentifierType> nodeTypes = new ConcurrentHashMap<>();

    // 熔断孤立黑名单：针对已被标记为公共设备的标识
    private final Set<String> bridgedBlacklist = ConcurrentHashMap.newKeySet();

    private final int maxClusterSize;
    private final double minConfidenceAllowed;

    public IdentityGraphEngine() {
        this(DEFAULT_MAX_CLUSTER_SIZE, DEFAULT_MIN_CONFIDENCE);
    }

    public IdentityGraphEngine(int maxClusterSize, double minConfidenceAllowed) {
        this.maxClusterSize = maxClusterSize;
        this.minConfidenceAllowed = minConfidenceAllowed;
    }

    /**
     * 注册或打通两个身份标识之间的边
     *
     * @param idA        标识 A（如 Cookie ID）
     * @param typeA      标识 A 类型
     * @param idB        标识 B（如 Phone/CRM ID）
     * @param typeB      标识 B 类型
     * @param confidence 边连接置信度 (0.0 ~ 1.0)
     * @return true 代表成功合并归一，false 代表被防桥接熔断阻断或低于置信度
     */
    public synchronized boolean link(
            String idA, IdentityGraphService.IdentifierType typeA,
            String idB, IdentityGraphService.IdentifierType typeB,
            double confidence
    ) {
        if (idA == null || idA.isBlank() || idB == null || idB.isBlank()) {
            return false;
        }
        if (confidence < minConfidenceAllowed) {
            return false;
        }

        String normA = normalize(idA);
        String normB = normalize(idB);

        if (bridgedBlacklist.contains(normA) || bridgedBlacklist.contains(normB)) {
            return false; // 触碰防桥接黑名单，拒绝合并
        }

        initNode(normA, typeA);
        initNode(normB, typeB);

        String rootA = findRoot(normA);
        String rootB = findRoot(normB);

        if (rootA.equals(rootB)) {
            return true; // 已处于同一连通集群
        }

        int sizeA = clusterSize.get(rootA).get();
        int sizeB = clusterSize.get(rootB).get();

        // 防身份过度桥接爆炸检查 (Anti-Bridging Guardrail)
        if (sizeA + sizeB > maxClusterSize) {
            // 触碰公共设备异常，将较大的一方或两者拉入观察黑名单断边
            if (sizeA > maxClusterSize / 2) bridgedBlacklist.add(normA);
            if (sizeB > maxClusterSize / 2) bridgedBlacklist.add(normB);
            return false;
        }

        // 优先将更稳定的确定性标识或较大集群作为主根节点 (Canonical Root)
        if (shouldPrecede(rootA, rootB)) {
            parent.put(rootB, rootA);
            clusterSize.get(rootA).addAndGet(sizeB);
        } else {
            parent.put(rootA, rootB);
            clusterSize.get(rootB).addAndGet(sizeA);
        }

        return true;
    }

    /**
     * 获取指定标识在图谱中归一化后的主档案主键标识 (Canonical Identifier)
     */
    public String getCanonicalId(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String norm = normalize(identifier);
        if (!parent.containsKey(norm)) {
            return norm;
        }
        return findRoot(norm);
    }

    /**
     * 获取与当前标识属于同一连通闭包的全部关联身份集合
     */
    public Set<String> getCluster(String identifier) {
        if (identifier == null || identifier.isBlank()) return Set.of();
        String norm = normalize(identifier);
        String targetRoot = getCanonicalId(norm);

        Set<String> cluster = new HashSet<>();
        for (String node : parent.keySet()) {
            if (findRoot(node).equals(targetRoot)) {
                cluster.add(node);
            }
        }
        if (cluster.isEmpty()) {
            cluster.add(norm);
        }
        return Collections.unmodifiableSet(cluster);
    }

    /**
     * 并查集路径压缩查询根节点
     */
    private String findRoot(String node) {
        String p = parent.get(node);
        if (p == null || p.equals(node)) {
            return node;
        }
        String root = findRoot(p);
        parent.put(node, root); // 扁平化路径压缩
        return root;
    }

    private void initNode(String norm, IdentityGraphService.IdentifierType type) {
        parent.putIfAbsent(norm, norm);
        clusterSize.putIfAbsent(norm, new AtomicInteger(1));
        if (type != null) {
            nodeTypes.putIfAbsent(norm, type);
        }
    }

    private boolean shouldPrecede(String rootA, String rootB) {
        IdentityGraphService.IdentifierType typeA = nodeTypes.getOrDefault(rootA, IdentityGraphService.IdentifierType.PROBABILISTIC_FINGERPRINT);
        IdentityGraphService.IdentifierType typeB = nodeTypes.getOrDefault(rootB, IdentityGraphService.IdentifierType.PROBABILISTIC_FINGERPRINT);

        // 确定性标识 (CRM_ID / PHONE / EMAIL) 优先作为主根
        if (typeA.getDefaultConfidence() != typeB.getDefaultConfidence()) {
            return typeA.getDefaultConfidence() > typeB.getDefaultConfidence();
        }
        // 否则大集群优先
        int sizeA = clusterSize.getOrDefault(rootA, new AtomicInteger(1)).get();
        int sizeB = clusterSize.getOrDefault(rootB, new AtomicInteger(1)).get();
        return sizeA >= sizeB;
    }

    private String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public int totalTrackedNodes() {
        return parent.size();
    }

    public void clear() {
        parent.clear();
        clusterSize.clear();
        nodeTypes.clear();
        bridgedBlacklist.clear();
    }
}
