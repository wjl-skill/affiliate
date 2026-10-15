package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioScene;

import java.util.List;
import java.util.Optional;

/**
 * AI 长视频工作台分镜镜头仓储接口
 */
public interface StudioSceneRepository {

    StudioScene save(StudioScene scene);

    List<StudioScene> saveAll(List<StudioScene> scenes);

    Optional<StudioScene> findById(String tenantId, String id);

    List<StudioScene> findByProjectId(String tenantId, String projectId);

    void deleteByProjectId(String tenantId, String projectId);
}
