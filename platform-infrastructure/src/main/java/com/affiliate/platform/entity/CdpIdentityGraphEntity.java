package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.Instant;

/** CDP 确定性身份图谱索引实体。 */
@TableName("cdp_identity_graph")
public class CdpIdentityGraphEntity {
    private String tenantId;
    private String identifierType;
    @TableId
    private String identifierVal;
    private String profileId;
    private Instant linkedAt;
    public CdpIdentityGraphEntity() {}
    public CdpIdentityGraphEntity(String tenantId, String identifierType, String identifierVal, String profileId, Instant linkedAt) {
        this.tenantId = tenantId; this.identifierType = identifierType; this.identifierVal = identifierVal; this.profileId = profileId; this.linkedAt = linkedAt;
    }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getIdentifierType() { return identifierType; }
    public void setIdentifierType(String identifierType) { this.identifierType = identifierType; }
    public String getIdentifierVal() { return identifierVal; }
    public void setIdentifierVal(String identifierVal) { this.identifierVal = identifierVal; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }
}
