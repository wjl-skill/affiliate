package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;

import java.util.List;
import java.util.Optional;

/**
 * 数字人主播形象仓储接口
 */
public interface DigitalHumanAvatarRepository {

    DigitalHumanAvatar save(DigitalHumanAvatar avatar);

    Optional<DigitalHumanAvatar> findById(String tenantId, String id);

    List<DigitalHumanAvatar> listActive(String tenantId);

    boolean deleteById(String tenantId, String id);
}
