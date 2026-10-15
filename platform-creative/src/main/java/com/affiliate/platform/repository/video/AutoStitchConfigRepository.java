package com.affiliate.platform.repository.video;

import com.affiliate.platform.domain.video.AutoStitchConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 批量混剪配置仓储接口
 */
public interface AutoStitchConfigRepository {

    AutoStitchConfig save(AutoStitchConfig config);

    Optional<AutoStitchConfig> findById(String tenantId, String id);

    List<AutoStitchConfig> listByTenant(String tenantId);

    List<AutoStitchConfig> findPendingSchedules(Instant beforeOrEqualTime);
 
    boolean deleteById(String tenantId, String id);
}
