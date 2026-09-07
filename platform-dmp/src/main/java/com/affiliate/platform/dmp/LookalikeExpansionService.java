package com.affiliate.platform.dmp;

import java.util.*;

/**
 * 相似人群算法扩展服务 (Lookalike Audience Expansion Service)
 * <p>
 * 基于种子客群特征标签向量计算 Jaccard 相似度系数：
 * J(A, B) = |A ∩ B| / |A ∪ B|
 * 提供高精准度种子人群（Seed Audience）向全网候选池的算法拓客（Lookalike Expansion）。
 */
public class LookalikeExpansionService {

    public record CandidateUser(String userId, Set<String> featureTags) {}

    public record ScoredUser(String userId, double similarityScore) {}

    /**
     * 根据种子特征标签在候选用户池中执行相似度扩展
     *
     * @param seedTags   种子客群核心标签集合
     * @param candidates 全网候选用户池
     * @param minScore   最低相似度截断门槛 (0.0 ~ 1.0)
     * @param limit      最大扩量人数限制
     * @return 排序后的高质量扩量人群列表
     */
    public List<ScoredUser> expand(
            Set<String> seedTags,
            List<CandidateUser> candidates,
            double minScore,
            int limit
    ) {
        if (seedTags == null || seedTags.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredUser> scoredList = new ArrayList<>();
        for (CandidateUser candidate : candidates) {
            double score = calculateJaccardSimilarity(seedTags, candidate.featureTags());
            if (score >= minScore) {
                scoredList.add(new ScoredUser(candidate.userId(), score));
            }
        }

        // 按相似度分值降序排列
        scoredList.sort(Comparator.comparingDouble(ScoredUser::similarityScore).reversed());

        if (limit > 0 && scoredList.size() > limit) {
            return scoredList.subList(0, limit);
        }
        return scoredList;
    }

    /**
     * 计算两组特征标签的 Jaccard 相似度
     */
    public double calculateJaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA == null || setB == null || setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}
