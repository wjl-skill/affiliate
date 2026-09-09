package com.affiliate.platform.cdp;

import com.affiliate.platform.cache.TwoTierCache;
import com.affiliate.platform.cache.TwoTierCacheManager;
import com.affiliate.platform.entity.CdpIdentityGraphEntity;
import com.affiliate.platform.entity.CdpProfileEntity;
import com.affiliate.platform.event.DomainEvent;
import com.affiliate.platform.event.EventPublisher;
import com.affiliate.platform.mapper.CdpIdentityGraphMapper;
import com.affiliate.platform.mapper.CdpProfileMapper;
import com.affiliate.platform.service.NotFoundException;
import com.affiliate.platform.tenant.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** CDP 确定性身份映射服务，生产环境通过 MyBatis-Plus Mapper 持久化。 */
@Service
public class IdentityMappingService {
    private final EventPublisher events;
    private final CdpProfileMapper profileMapper;
    private final CdpIdentityGraphMapper graphMapper;
    private final ObjectMapper mapper;
    private final TwoTierCache<String, CustomerProfile> profileCache;
    private final TwoTierCache<String, String> idIndexCache;
    private final ConcurrentMap<String, CustomerProfile> localProfiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> localIdentifierIndex = new ConcurrentHashMap<>();

    @Autowired
    public IdentityMappingService(EventPublisher events,
                                  @Autowired(required = false) CdpProfileMapper profileMapper,
                                  @Autowired(required = false) CdpIdentityGraphMapper graphMapper,
                                  @Autowired(required = false) ObjectMapper mapper,
                                  @Autowired(required = false) TwoTierCacheManager cacheManager) {
        this.events = events; this.profileMapper = profileMapper; this.graphMapper = graphMapper;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        this.profileCache = cacheManager == null ? null : cacheManager.getOrCreate("cdp_profile", CustomerProfile.class);
        this.idIndexCache = cacheManager == null ? null : cacheManager.getOrCreate("cdp_id_map", String.class);
    }

    public IdentityMappingService(EventPublisher events) { this(events, null, null, null, null); }

    public CustomerProfile create(CustomerProfile input) {
        Instant now = Instant.now();
        CustomerProfile profile = new CustomerProfile("cdp_profile_" + UUID.randomUUID(), input.primaryId(), input.identifiers(), input.attributes(), input.traits(), now, CustomerProfile.Status.ACTIVE, now);
        save(profile); events.publish(DomainEvent.create("cdp.profile.created.v1", tenant(), profile.id(), profile)); return profile;
    }

    public CustomerProfile resolve(String identifier) {
        String profileId = idIndexCache == null ? (graphMapper == null ? localIdentifierIndex.get(key(identifier)) : graphMapper.findProfileId(tenant(), identifier))
                : idIndexCache.get(key(identifier), ignored -> findProfileIdByIdentifier(identifier));
        if (profileId == null) throw new NotFoundException("customerProfile", identifier);
        CustomerProfile profile = profileCache == null ? (profileMapper == null ? localProfiles.get(key(profileId)) : findProfileById(profileId))
                : profileCache.get(key(profileId), ignored -> findProfileById(profileId));
        if (profile == null) throw new NotFoundException("customerProfile", identifier); return profile;
    }

    public CustomerProfile merge(String primaryId, Set<String> identifiers, Map<String, String> attributes, Set<String> traits) {
        CustomerProfile current = current(primaryId);
        if (current.status() != CustomerProfile.Status.ACTIVE) throw new IllegalStateException("profile is not active");
        CustomerProfile updated = current.merge(identifiers, attributes, traits, Instant.now());
        save(updated); events.publish(DomainEvent.create("cdp.profile.updated.v1", tenant(), updated.id(), updated)); return updated;
    }

    public List<CustomerProfile> list() {
        if (profileMapper != null) return profileMapper.selectList(new LambdaQueryWrapper<CdpProfileEntity>().eq(CdpProfileEntity::getTenantId, tenant()).orderByDesc(CdpProfileEntity::getCreatedAt).last("LIMIT 1000")).stream().map(this::fromEntity).toList();
        return localProfiles.entrySet().stream().filter(e -> e.getKey().startsWith(tenant() + ":")).map(Map.Entry::getValue).toList();
    }

    public CustomerProfile optOut(String primaryId) {
        CustomerProfile p = current(primaryId);
        CustomerProfile next = new CustomerProfile(p.id(), p.primaryId(), p.identifiers(), p.attributes(), p.traits(), p.lastSeenAt(), CustomerProfile.Status.OPTED_OUT, p.createdAt());
        save(next); events.publish(DomainEvent.create("cdp.profile.opted_out.v1", tenant(), next.id(), next)); return next;
    }

    public void erase(String primaryId) {
        CustomerProfile profile = current(primaryId);
        if (profileMapper != null && graphMapper != null) { graphMapper.deleteByProfile(tenant(), profile.id()); profileMapper.delete(new LambdaQueryWrapper<CdpProfileEntity>().eq(CdpProfileEntity::getTenantId, tenant()).eq(CdpProfileEntity::getId, profile.id())); }
        else { localProfiles.remove(key(profile.id())); profile.identifiers().forEach(id -> localIdentifierIndex.remove(key(id))); localIdentifierIndex.remove(key(profile.primaryId())); }
        if (profileCache != null) profileCache.evict(key(profile.id()));
        if (idIndexCache != null) { profile.identifiers().forEach(id -> idIndexCache.evict(key(id))); idIndexCache.evict(key(profile.primaryId())); }
        events.publish(DomainEvent.create("cdp.profile.erased.v1", tenant(), profile.id(), Map.of("primaryId", primaryId)));
    }

    private void save(CustomerProfile profile) {
        if (profileMapper != null && graphMapper != null) {
            CdpProfileEntity entity = toEntity(profile);
            CdpProfileEntity existing = profileMapper.selectOne(new LambdaQueryWrapper<CdpProfileEntity>().eq(CdpProfileEntity::getTenantId, tenant()).eq(CdpProfileEntity::getId, profile.id()));
            if (existing == null) profileMapper.insert(entity); else profileMapper.updateById(entity);
            saveGraphEntry(profile.primaryId(), profile.id()); profile.identifiers().forEach(id -> saveGraphEntry(id, profile.id()));
        } else {
            profile.identifiers().forEach(id -> { String existing = localIdentifierIndex.get(key(id)); if (existing != null && !existing.equals(profile.id())) throw new IllegalArgumentException("identifier already mapped"); });
            String existing = localIdentifierIndex.get(key(profile.primaryId())); if (existing != null && !existing.equals(profile.id())) throw new IllegalArgumentException("primaryId already mapped");
            localProfiles.put(key(profile.id()), profile); profile.identifiers().forEach(id -> localIdentifierIndex.put(key(id), profile.id())); localIdentifierIndex.put(key(profile.primaryId()), profile.id());
        }
        if (profileCache != null) profileCache.put(key(profile.id()), profile);
        if (idIndexCache != null) { idIndexCache.put(key(profile.primaryId()), profile.id()); profile.identifiers().forEach(id -> idIndexCache.put(key(id), profile.id())); }
    }

    private void saveGraphEntry(String identifier, String profileId) {
        String type = identifier.contains(":") ? identifier.substring(0, identifier.indexOf(':')).toUpperCase() : "GENERIC";
        graphMapper.upsert(new CdpIdentityGraphEntity(tenant(), type, identifier, profileId, Instant.now()));
    }
    private String findProfileIdByIdentifier(String identifier) { return graphMapper == null ? localIdentifierIndex.get(key(identifier)) : graphMapper.findProfileId(tenant(), identifier); }
    private CustomerProfile findProfileById(String id) { if (profileMapper == null) return localProfiles.get(key(id)); CdpProfileEntity e = profileMapper.selectOne(new LambdaQueryWrapper<CdpProfileEntity>().eq(CdpProfileEntity::getTenantId, tenant()).eq(CdpProfileEntity::getId, id)); return e == null ? null : fromEntity(e); }
    private CustomerProfile current(String primaryId) { if (profileMapper != null) { CdpProfileEntity e = profileMapper.selectOne(new LambdaQueryWrapper<CdpProfileEntity>().eq(CdpProfileEntity::getTenantId, tenant()).eq(CdpProfileEntity::getPrimaryId, primaryId)); if (e != null) return fromEntity(e); } else { for (CustomerProfile p : localProfiles.values()) if (p.primaryId().equals(primaryId) && localProfiles.containsKey(key(p.id()))) return p; } throw new NotFoundException("customerProfile", primaryId); }
    private CdpProfileEntity toEntity(CustomerProfile p) { CdpProfileEntity e = new CdpProfileEntity(); e.setId(p.id()); e.setTenantId(tenant()); e.setPrimaryId(p.primaryId()); e.setStatus(p.status().name()); try { e.setIdentifiers(mapper.writeValueAsString(p.identifiers())); e.setAttributes(mapper.writeValueAsString(p.attributes())); e.setTraits(mapper.writeValueAsString(p.traits())); } catch (Exception ex) { throw new IllegalStateException("cannot serialize customer profile", ex); } e.setLastSeenAt(p.lastSeenAt()); e.setCreatedAt(p.createdAt()); return e; }
    private CustomerProfile fromEntity(CdpProfileEntity e) { try { Set<String> ids = mapper.readValue(e.getIdentifiers(), new TypeReference<>() {}); Map<String,String> attrs = mapper.readValue(e.getAttributes(), new TypeReference<>() {}); Set<String> traits = mapper.readValue(e.getTraits(), new TypeReference<>() {}); return new CustomerProfile(e.getId(), e.getPrimaryId(), ids, attrs, traits, e.getLastSeenAt(), CustomerProfile.Status.valueOf(e.getStatus()), e.getCreatedAt()); } catch (Exception ex) { throw new IllegalStateException("cannot deserialize customer profile", ex); } }
    private static String key(String value) { return tenant() + ":" + value; }
    private static String tenant() { return TenantContext.get() == null ? "public" : TenantContext.required(); }
}
