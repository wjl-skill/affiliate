package com.affiliate.platform.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** DMP 分群匿名成员关系实体。 */
@TableName("dmp_segment_member")
public class DmpSegmentMemberEntity {
    private String segmentId;
    private String anonymousId;
    private Instant addedAt;
    public DmpSegmentMemberEntity() {}
    public DmpSegmentMemberEntity(String segmentId, String anonymousId) {
        this.segmentId = segmentId;
        this.anonymousId = anonymousId;
    }
    public String getSegmentId() { return segmentId; }
    public void setSegmentId(String segmentId) { this.segmentId = segmentId; }
    public String getAnonymousId() { return anonymousId; }
    public void setAnonymousId(String anonymousId) { this.anonymousId = anonymousId; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}
