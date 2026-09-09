package com.affiliate.platform.mapper;

import com.affiliate.platform.entity.DmpSegmentMemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DmpSegmentMemberMapper extends BaseMapper<DmpSegmentMemberEntity> {
    @Insert("INSERT INTO dmp_segment_member (segment_id, anonymous_id) VALUES (#{segmentId}, #{anonymousId}) ON CONFLICT DO NOTHING")
    int insertIgnore(DmpSegmentMemberEntity entity);

    @Select("SELECT COUNT(*) FROM dmp_segment_member WHERE segment_id = #{segmentId} AND anonymous_id = #{anonymousId}")
    long countByMember(String segmentId, String anonymousId);
}
