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
 * 1. 采用加权并查集 (Disjoint Set Union, DSU) + 倒排群组索引求解跨触点多标识无向图连通分支，实现确定性与概率性传递闭包打通；
 * 2. 具备 O(1) 常数时间群组成员检索（彻底消除全图线性扫描导致的 CPU 100% 雪崩）；
 * 3. 区分确定性标识 (Deterministic, 手机号/邮箱/CRM ID) 与概率性标识 (Probabilistic, 设备指纹/Cookie)；
 * 4. 具备工业级防身份桥接爆炸保护 (Anti-Bridging Guardrail)：
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

    // 倒排群组成员映射：rootIdentifier -> Set<String> members (用于 O(1) 极速查询群组成员)
    private final ConcurrentMap<String, Set<String>> clusterMembers = new ConcurrentHashMap<>();

    // 无向图边邻接表：identifier -> Set<String> neighborIdentifiers (用于解绑时图拓扑维护与局部 BFS 分裂)
    private final ConcurrentMap<String, Set<String>> adjacencyList = new ConcurrentHashMap<>();

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

        // 同步维护双向边邻接表（环路冗余边也必须持久化在拓扑中，确保后续解绑连通性判定精确）
        adjacencyList.computeIfAbsent(normA, k -> ConcurrentHashMap.newKeySet()).add(normB);
        adjacencyList.computeIfAbsent(normB, k -> ConcurrentHashMap.newKeySet()).add(normA);

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
        String mainRoot;
        String subRoot;
        int sizeSub;
        if (shouldPrecede(rootA, rootB)) {
            mainRoot = rootA;
            subRoot = rootB;
            sizeSub = sizeB;
        } else {
            mainRoot = rootB;
            subRoot = rootA;
            sizeSub = sizeA;
        }

        parent.put(subRoot, mainRoot);
        clusterSize.get(mainRoot).addAndGet(sizeSub);

        // O(1) 合并倒排群组成员集合
        Set<String> subMembers = clusterMembers.remove(subRoot);
        if (subMembers != null) {
            clusterMembers.computeIfAbsent(mainRoot, k -> ConcurrentHashMap.newKeySet()).addAll(subMembers);
        }

        return true;
    }

    /**
     * 解除两个身份标识之间的边关联，并执行局部 BFS 连通分量分裂 (Unlink & Split via local BFS)
     *
     * @param idA 待解绑标识 A
     * @param idB 待解绑标识 B
     * @return true 代表解绑成功或无需分裂
     */
    public synchronized boolean unlink(String idA, String idB) {
        if (idA == null || idA.isBlank() || idB == null || idB.isBlank()) {
            return false;
        }
        String normA = normalize(idA);
        String normB = normalize(idB);

        // 1. 从无向图边邻接表中移除双向边
        Set<String> adjA = adjacencyList.get(normA);
        if (adjA != null) adjA.remove(normB);
        Set<String> adjB = adjacencyList.get(normB);
        if (adjB != null) adjB.remove(normA);

        if (!parent.containsKey(normA) || !parent.containsKey(normB)) {
            return true;
        }

        String rootA = findRoot(normA);
        String rootB = findRoot(normB);
        if (!rootA.equals(rootB)) {
            return true; // 两者本就不在同一集群，无需分裂
        }

        // 2. 局部 BFS 探测：检查从 normA 出发是否仍能通过其他环路路径到达 normB
        Set<String> oldCluster = clusterMembers.get(rootA);
        if (oldCluster == null || oldCluster.isEmpty()) {
            return true;
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(normA);
        visited.add(normA);

        boolean stillConnected = false;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            if (curr.equals(normB)) {
                stillConnected = true;
                break;
            }
            Set<String> neighbors = adjacencyList.get(curr);
            if (neighbors != null) {
                for (String nb : neighbors) {
                    if (oldCluster.contains(nb) && visited.add(nb)) {
                        queue.add(nb);
                    }
                }
            }
        }

        if (stillConnected) {
            return true; // 存在备用路径环路，图未分裂
        }

        // 3. 连通分支分裂 (Connected Components Splitting via BFS)
        clusterMembers.remove(rootA);
        Set<String> unvisited = new HashSet<>(oldCluster);

        while (!unvisited.isEmpty()) {
            String startNode = unvisited.iterator().next();
            Set<String> newComponent = new HashSet<>();
            Queue<String> compQueue = new ArrayDeque<>();
            compQueue.add(startNode);
            newComponent.add(startNode);
            unvisited.remove(startNode);

            while (!compQueue.isEmpty()) {
                String node = compQueue.poll();
                Set<String> neighbors = adjacencyList.get(node);
                if (neighbors != null) {
                    for (String nb : neighbors) {
                        if (unvisited.remove(nb)) {
                            newComponent.add(nb);
                            compQueue.add(nb);
                        }
                    }
                }
            }

            // 选举该连通分支的最佳根节点
            String bestRoot = null;
            for (String member : newComponent) {
                if (bestRoot == null || shouldPrecede(member, bestRoot)) {
                    bestRoot = member;
                }
            }

            // 重建新连通分支的并查集父指针与倒排成员映射
            for (String member : newComponent) {
                parent.put(member, bestRoot);
            }
            clusterSize.put(bestRoot, new AtomicInteger(newComponent.size()));
            clusterMembers.computeIfAbsent(bestRoot, k -> ConcurrentHashMap.newKeySet()).addAll(newComponent);
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
     * 获取与当前标识属于同一连通闭包的全部关联身份集合（O(1) 极速倒排读取）
     */
    public Set<String> getCluster(String identifier) {
        if (identifier == null || identifier.isBlank()) return Set.of();
        String norm = normalize(identifier);
        String targetRoot = getCanonicalId(norm);

        Set<String> members = clusterMembers.get(targetRoot);
        if (members != null && !members.isEmpty()) {
            return Collections.unmodifiableSet(new HashSet<>(members));
        }
        return Set.of(norm);
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
        clusterMembers.computeIfAbsent(norm, k -> ConcurrentHashMap.newKeySet()).add(norm);
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
        clusterMembers.clear();
        adjacencyList.clear();
        nodeTypes.clear();
        bridgedBlacklist.clear();
    }
}
