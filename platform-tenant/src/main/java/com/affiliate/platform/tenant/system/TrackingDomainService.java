package com.affiliate.platform.tenant.system;

import com.affiliate.platform.entity.TrackingDomainEntity;
import com.affiliate.platform.mapper.TrackingDomainMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 推广跟踪域名池管理服务 (Tracking Domain Service - MyBatis-Plus)
 * <p>
 * 基于 MyBatis-Plus 接入 PostgreSQL `sys_tracking_domain` 表管理域名池，并具备离线内存回退保护能力。
 */
@Service
public class TrackingDomainService {

    private static final Logger log = LoggerFactory.getLogger(TrackingDomainService.class);

    private final TrackingDomainMapper domainMapper;
    private final ConcurrentMap<String, TrackingDomain> fallbackStore = new ConcurrentHashMap<>();

    public TrackingDomainService() {
        this(null);
    }

    @Autowired
    public TrackingDomainService(@Autowired(required = false) TrackingDomainMapper domainMapper) {
        this.domainMapper = domainMapper;
        ensureDefaultDomains();
    }

    private void ensureDefaultDomains() {
        for (TrackingDomain seed : buildDefaultDomains()) {
            fallbackStore.put(seed.id(), seed);
        }
        if (domainMapper != null) {
            try {
                Long count = domainMapper.selectCount(null);
                if (count != null && count == 0) {
                    for (TrackingDomain seed : buildDefaultDomains()) {
                        saveDomain(seed);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check database default tracking domains, using memory store: {}", e.getMessage());
            }
        }
    }

    private List<TrackingDomain> buildDefaultDomains() {
        return List.of(
                new TrackingDomain(
                        "dom-01",
                        "tenant-1",
                        "trk.smartaff.com",
                        TrackingDomain.DomainType.TRACKING,
                        "lb-global.affnetwork.com",
                        TrackingDomain.DnsStatus.VERIFIED,
                        TrackingDomain.SslStatus.AUTO_SSL_ACTIVE,
                        null,
                        true,
                        TrackingDomain.Status.ACTIVE,
                        Instant.now()
                ),
                new TrackingDomain(
                        "dom-02",
                        "tenant-1",
                        "click.apexmedia.io",
                        TrackingDomain.DomainType.TRACKING,
                        "lb-global.affnetwork.com",
                        TrackingDomain.DnsStatus.PENDING_CNAME,
                        TrackingDomain.SslStatus.AUTO_SSL_ACTIVE,
                        "aff-vip-888",
                        false,
                        TrackingDomain.Status.ACTIVE,
                        Instant.now()
                )
        );
    }

    public TrackingDomain saveDomain(TrackingDomain domain) {
        if (domain.isDefault()) {
            fallbackStore.values().forEach(d -> {
                if (d.isDefault()) {
                    fallbackStore.put(d.id(), d.withDefault(false));
                }
            });
        }
        fallbackStore.put(domain.id(), domain);

        if (domainMapper != null) {
            try {
                if (domain.isDefault()) {
                    QueryWrapper<TrackingDomainEntity> defQw = new QueryWrapper<>();
                    defQw.eq("is_default", true);
                    List<TrackingDomainEntity> defs = domainMapper.selectList(defQw);
                    for (TrackingDomainEntity d : defs) {
                        d.setIsDefault(false);
                        domainMapper.updateById(d);
                    }
                }

                TrackingDomainEntity entity = new TrackingDomainEntity(
                        domain.id(),
                        domain.tenantId(),
                        domain.domain(),
                        domain.domainType().name(),
                        domain.cnameTarget(),
                        domain.dnsStatus().name(),
                        domain.sslStatus().name(),
                        domain.assignedAffiliateId(),
                        domain.isDefault(),
                        domain.status().name(),
                        domain.createdAt() != null ? domain.createdAt() : Instant.now()
                );

                if (domainMapper.selectById(domain.id()) != null) {
                    domainMapper.updateById(entity);
                } else {
                    domainMapper.insert(entity);
                }
            } catch (Exception e) {
                log.warn("Failed to persist tracking domain {} to database: {}, using memory store", domain.id(), e.getMessage());
            }
        }
        return domain;
    }

    public List<TrackingDomain> listDomains() {
        if (domainMapper != null) {
            try {
                QueryWrapper<TrackingDomainEntity> qw = new QueryWrapper<>();
                qw.orderByDesc("is_default").orderByAsc("created_at");
                List<TrackingDomainEntity> list = domainMapper.selectList(qw);
                return list.stream().map(this::toDomain).toList();
            } catch (Exception e) {
                log.warn("Failed to query tracking domains from database: {}, using memory store", e.getMessage());
            }
        }
        return new ArrayList<>(fallbackStore.values());
    }

    public Optional<TrackingDomain> find(String id) {
        if (domainMapper != null) {
            try {
                TrackingDomainEntity entity = domainMapper.selectById(id);
                return Optional.ofNullable(entity).map(this::toDomain);
            } catch (Exception e) {
                log.warn("Failed to find tracking domain {} in database: {}, using memory store", id, e.getMessage());
            }
        }
        return Optional.ofNullable(fallbackStore.get(id));
    }

    public boolean delete(String id) {
        if (domainMapper != null) {
            try {
                boolean deleted = domainMapper.deleteById(id) > 0;
                fallbackStore.remove(id);
                return deleted;
            } catch (Exception e) {
                log.warn("Failed to delete tracking domain {} from database: {}, using memory store", id, e.getMessage());
            }
        }
        return fallbackStore.remove(id) != null;
    }

    public TrackingDomain verifyDns(String id) {
        if (domainMapper != null) {
            try {
                TrackingDomainEntity entity = domainMapper.selectById(id);
                if (entity == null) throw new NoSuchElementException("域名不存在: " + id);
                entity.setDnsStatus(TrackingDomain.DnsStatus.VERIFIED.name());
                domainMapper.updateById(entity);
                return toDomain(entity);
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to verify DNS for domain {} in database: {}, using memory store", id, e.getMessage());
            }
        }

        TrackingDomain domain = fallbackStore.get(id);
        if (domain == null) throw new NoSuchElementException("域名不存在: " + id);
        TrackingDomain verified = domain.withDnsStatus(TrackingDomain.DnsStatus.VERIFIED);
        fallbackStore.put(id, verified);
        return verified;
    }

    public TrackingDomain setDefault(String id) {
        if (domainMapper != null) {
            try {
                TrackingDomainEntity target = domainMapper.selectById(id);
                if (target == null) throw new NoSuchElementException("域名不存在: " + id);

                QueryWrapper<TrackingDomainEntity> defQw = new QueryWrapper<>();
                defQw.eq("is_default", true);
                List<TrackingDomainEntity> defs = domainMapper.selectList(defQw);
                for (TrackingDomainEntity d : defs) {
                    d.setIsDefault(false);
                    domainMapper.updateById(d);
                }

                target.setIsDefault(true);
                domainMapper.updateById(target);
                return toDomain(target);
            } catch (NoSuchElementException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Failed to set default tracking domain {} in database: {}, using memory store", id, e.getMessage());
            }
        }

        TrackingDomain target = fallbackStore.get(id);
        if (target == null) throw new NoSuchElementException("域名不存在: " + id);
        fallbackStore.values().forEach(d -> fallbackStore.put(d.id(), d.withDefault(false)));
        TrackingDomain updated = target.withDefault(true);
        fallbackStore.put(id, updated);
        return updated;
    }

    private TrackingDomain toDomain(TrackingDomainEntity e) {
        return new TrackingDomain(
                e.getId(),
                e.getTenantId(),
                e.getDomain(),
                TrackingDomain.DomainType.valueOf(e.getDomainType()),
                e.getCnameTarget(),
                TrackingDomain.DnsStatus.valueOf(e.getDnsStatus()),
                TrackingDomain.SslStatus.valueOf(e.getSslStatus()),
                e.getAssignedAffiliateId(),
                Boolean.TRUE.equals(e.getIsDefault()),
                TrackingDomain.Status.valueOf(e.getStatus()),
                e.getCreatedAt()
        );
    }
}
