package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.StudioProject;

import java.util.List;
import java.util.Optional;

/**
 * AI 长视频项目仓储接口
 */
public interface StudioProjectRepository {

    StudioProject save(StudioProject project);

    Optional<StudioProject> findById(String tenantId, String id);

    List<StudioProject> listByTenant(String tenantId, int limit);
 
    boolean deleteById(String tenantId, String id);
}
