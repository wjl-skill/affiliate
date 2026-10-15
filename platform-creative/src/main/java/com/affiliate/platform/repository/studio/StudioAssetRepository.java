package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioAsset;

import java.util.List;
import java.util.Optional;

/**
 * AI 长视频资产仓储接口
 */
public interface StudioAssetRepository {

    StudioAsset save(StudioAsset asset);

    List<StudioAsset> saveAll(List<StudioAsset> assets);

    Optional<StudioAsset> findById(String tenantId, String id);

    List<StudioAsset> findByProjectId(String tenantId, String projectId);
}
