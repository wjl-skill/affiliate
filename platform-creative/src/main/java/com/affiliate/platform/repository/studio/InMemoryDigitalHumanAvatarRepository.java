package com.affiliate.platform.repository.studio;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;
import com.affiliate.platform.entity.CreativeDigitalHumanAvatarEntity;
import com.affiliate.platform.mapper.CreativeDigitalHumanAvatarMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数字人形象资产仓储实现 (支持 MyBatis-Plus PostgreSQL 持久化与内存沙箱兜底)
 */
@Repository
public class InMemoryDigitalHumanAvatarRepository implements DigitalHumanAvatarRepository {

    private final CreativeDigitalHumanAvatarMapper avatarMapper;
    private final Map<String, DigitalHumanAvatar> storage = new ConcurrentHashMap<>();

    public InMemoryDigitalHumanAvatarRepository() {
        this(null);
    }

    @Autowired
    public InMemoryDigitalHumanAvatarRepository(@Autowired(required = false) CreativeDigitalHumanAvatarMapper avatarMapper) {
        this.avatarMapper = avatarMapper;
        seedDefaults();
    }

    private void seedDefaults() {
        DigitalHumanAvatar f1 = DigitalHumanAvatar.create(
                "public",
                "甜美带货主播-小双",
                "2D_PHOTO",
                "https://oss.affiliate.com/avatars/female_shuang_origin.png",
                "https://oss.affiliate.com/avatars/female_shuang_mask.png",
                "zh_female_shuangkuai"
        );
        DigitalHumanAvatar m1 = DigitalHumanAvatar.create(
                "public",
                "专业测评主播-文峰",
                "2D_PHOTO",
                "https://oss.affiliate.com/avatars/male_feng_origin.png",
                "https://oss.affiliate.com/avatars/male_feng_mask.png",
                "zh_male_wenfeng"
        );
        save(f1);
        save(m1);
    }

    private String key(String tenantId, String id) {
        return (tenantId != null ? tenantId : "public") + ":" + id;
    }

    @Override
    public DigitalHumanAvatar save(DigitalHumanAvatar avatar) {
        if (avatar == null) {
            throw new IllegalArgumentException("avatar cannot be null");
        }
        if (avatarMapper != null) {
            try {
                CreativeDigitalHumanAvatarEntity entity = new CreativeDigitalHumanAvatarEntity(
                        avatar.id(),
                        avatar.tenantId(),
                        avatar.avatarName(),
                        avatar.avatarType() != null ? avatar.avatarType() : "2D_PHOTO",
                        avatar.originalImageUrl(),
                        avatar.maskImageUrl(),
                        avatar.defaultSpeaker(),
                        avatar.status() != null ? avatar.status() : "ACTIVE",
                        avatar.createdAt() != null ? avatar.createdAt() : Instant.now()
                );
                if (avatarMapper.selectById(entity.getId()) != null) {
                    avatarMapper.updateById(entity);
                } else {
                    avatarMapper.insert(entity);
                }
            } catch (Exception ignored) {}
        }
        storage.put(key(avatar.tenantId(), avatar.id()), avatar);
        return avatar;
    }

    @Override
    public Optional<DigitalHumanAvatar> findById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (avatarMapper != null) {
            try {
                QueryWrapper<CreativeDigitalHumanAvatarEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                CreativeDigitalHumanAvatarEntity entity = avatarMapper.selectOne(qw);
                if (entity != null) {
                    return Optional.of(toDomain(entity));
                }
            } catch (Exception ignored) {}
        }
        DigitalHumanAvatar found = storage.get(key(tenantId, id));
        if (found != null) {
            return Optional.of(found);
        }
        return storage.values().stream()
                .filter(a -> a.id().equals(id))
                .findFirst();
    }

    @Override
    public List<DigitalHumanAvatar> listActive(String tenantId) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        if (avatarMapper != null) {
            try {
                QueryWrapper<CreativeDigitalHumanAvatarEntity> qw = new QueryWrapper<>();
                qw.and(wrapper -> wrapper.eq("tenant_id", effectiveTenant).or().eq("tenant_id", "public"));
                qw.eq("status", "ACTIVE");
                List<CreativeDigitalHumanAvatarEntity> list = avatarMapper.selectList(qw);
                if (list != null && !list.isEmpty()) {
                    return list.stream().map(this::toDomain).toList();
                }
            } catch (Exception ignored) {}
        }
        return storage.values().stream()
                .filter(a -> (a.tenantId().equals(effectiveTenant) || a.tenantId().equals("public"))
                        && "ACTIVE".equalsIgnoreCase(a.status()))
                .sorted(Comparator.comparing(DigitalHumanAvatar::createdAt))
                .toList();
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        boolean deleted = false;
        if (avatarMapper != null) {
            try {
                QueryWrapper<CreativeDigitalHumanAvatarEntity> qw = new QueryWrapper<>();
                qw.eq("id", id);
                if (tenantId != null && !tenantId.isBlank()) {
                    qw.eq("tenant_id", effectiveTenant);
                }
                int rows = avatarMapper.delete(qw);
                if (rows > 0) {
                    deleted = true;
                }
            } catch (Exception ignored) {}
        }
        DigitalHumanAvatar removed = storage.remove(key(effectiveTenant, id));
        if (removed != null) {
            deleted = true;
        } else {
            Iterator<Map.Entry<String, DigitalHumanAvatar>> it = storage.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, DigitalHumanAvatar> entry = it.next();
                if (entry.getValue().id().equals(id)) {
                    it.remove();
                    deleted = true;
                }
            }
        }
        return deleted;
    }

    private DigitalHumanAvatar toDomain(CreativeDigitalHumanAvatarEntity entity) {
        return new DigitalHumanAvatar(
                entity.getId(),
                entity.getTenantId(),
                entity.getAvatarName(),
                entity.getAvatarType() != null ? entity.getAvatarType() : "2D_PHOTO",
                entity.getOriginalImageUrl(),
                entity.getMaskImageUrl(),
                entity.getDefaultSpeaker(),
                entity.getStatus() != null ? entity.getStatus() : "ACTIVE",
                entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now()
        );
    }

    public void clear() {
        storage.clear();
        seedDefaults();
    }
}
