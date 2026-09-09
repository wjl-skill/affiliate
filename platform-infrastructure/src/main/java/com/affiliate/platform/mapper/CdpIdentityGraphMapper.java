package com.affiliate.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import com.affiliate.platform.entity.CdpIdentityGraphEntity;

@Mapper
public interface CdpIdentityGraphMapper extends BaseMapper<CdpIdentityGraphEntity> {
    @Insert("INSERT INTO cdp_identity_graph (tenant_id, identifier_type, identifier_val, profile_id, linked_at) VALUES (#{tenantId}, #{identifierType}, #{identifierVal}, #{profileId}, #{linkedAt}) ON CONFLICT (tenant_id, identifier_type, identifier_val) DO UPDATE SET profile_id = EXCLUDED.profile_id, linked_at = EXCLUDED.linked_at")
    int upsert(CdpIdentityGraphEntity entity);

    @Select("SELECT profile_id FROM cdp_identity_graph WHERE tenant_id = #{tenantId} AND identifier_val = #{identifierVal} ORDER BY linked_at DESC LIMIT 1")
    String findProfileId(String tenantId, String identifierVal);

    @Select("SELECT tenant_id, identifier_type, identifier_val, profile_id, linked_at FROM cdp_identity_graph WHERE tenant_id = #{tenantId} AND profile_id = #{profileId} ORDER BY linked_at DESC")
    List<CdpIdentityGraphEntity> findByProfile(String tenantId, String profileId);

    @Delete("DELETE FROM cdp_identity_graph WHERE tenant_id = #{tenantId} AND profile_id = #{profileId}")
    int deleteByProfile(String tenantId, String profileId);
}
