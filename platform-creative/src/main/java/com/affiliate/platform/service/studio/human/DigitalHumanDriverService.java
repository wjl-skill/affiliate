package com.affiliate.platform.service.studio.human;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;
import com.affiliate.platform.domain.studio.DigitalHumanDriveResult;

import java.util.List;
import java.util.Optional;

/**
 * 豆包语音合成 TTS 与火山 OmniHuman 1.5 数字人驱动服务
 */
public interface DigitalHumanDriverService {

    /**
     * 语音合成：将口播旁白转换为高保真音频
     */
    String synthesizeVoiceoverAudio(String text, String speaker, String speed);

    /**
     * 驱动数字人主播生成唇形对齐视频切片
     */
    DigitalHumanDriveResult driveAvatar(String tenantId, String avatarId, String voiceoverText, String speaker);

    /**
     * 查询可用数字人主播列表
     */
    List<DigitalHumanAvatar> listAvatars(String tenantId);

    /**
     * 获取指定主播形象
     */
    Optional<DigitalHumanAvatar> getAvatar(String tenantId, String avatarId);

    /**
     * 注册/创建自定义数字人形象
     */
    DigitalHumanAvatar registerAvatar(String tenantId, String avatarName, String originalImageUrl, String maskImageUrl, String speakerCode);

    /**
     * 删除指定数字人形象
     */
    boolean deleteAvatar(String tenantId, String avatarId);
}
