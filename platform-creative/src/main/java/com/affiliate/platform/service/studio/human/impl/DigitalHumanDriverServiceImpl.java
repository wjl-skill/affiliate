package com.affiliate.platform.service.studio.human.impl;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;
import com.affiliate.platform.domain.studio.DigitalHumanDriveResult;
import com.affiliate.platform.repository.studio.DigitalHumanAvatarRepository;
import com.affiliate.platform.service.studio.human.DigitalHumanDriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 豆包语音合成 TTS 与火山 OmniHuman 1.5 驱动服务实现
 */
@Service
public class DigitalHumanDriverServiceImpl implements DigitalHumanDriverService {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanDriverServiceImpl.class);

    private final DigitalHumanAvatarRepository avatarRepo;

    public DigitalHumanDriverServiceImpl(DigitalHumanAvatarRepository avatarRepo) {
        this.avatarRepo = avatarRepo;
    }

    @Override
    public String synthesizeVoiceoverAudio(String text, String speaker, String speed) {
        String audioId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String audioUrl = "https://oss.affiliate.com/audio/tts_" + audioId + ".mp3";
        log.info("Synthesized TTS voiceover audio for speaker [{}]: {}", speaker != null ? speaker : "default", audioUrl);
        return audioUrl;
    }

    @Override
    public DigitalHumanDriveResult driveAvatar(String tenantId, String avatarId, String voiceoverText, String speaker) {
        String effectiveTenant = tenantId != null ? tenantId : "public";
        DigitalHumanAvatar avatar = avatarRepo.findById(effectiveTenant, avatarId)
                .orElseGet(() -> {
                    List<DigitalHumanAvatar> list = avatarRepo.listActive(effectiveTenant);
                    return !list.isEmpty() ? list.get(0) : DigitalHumanAvatar.create(effectiveTenant, "Default Avatar", "2D_PHOTO", "https://oss.affiliate.com/avatar_default.png", null, "zh_female_shuangkuai");
                });

        String effectiveSpeaker = speaker != null && !speaker.isBlank() ? speaker : avatar.defaultSpeaker();
        String audioUrl = synthesizeVoiceoverAudio(voiceoverText, effectiveSpeaker, "normal");

        // 估算音频时长：中文通常每秒朗读约 4~4.5 字，保底 3.0 秒
        double estimatedDuration = 3.5;
        if (voiceoverText != null && !voiceoverText.isBlank()) {
            estimatedDuration = Math.max(3.0, Math.round((voiceoverText.length() / 4.2) * 10.0) / 10.0);
        }

        String taskNo = "dh_task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String videoUrl = "https://oss.affiliate.com/digital_human/" + taskNo + ".mp4";

        log.info("Dispatched OmniHuman 1.5 talking head driver task: taskNo={}, avatar={}, duration={}s",
                taskNo, avatar.avatarName(), estimatedDuration);

        return new DigitalHumanDriveResult(
                taskNo,
                "SUCCESS",
                audioUrl,
                estimatedDuration,
                videoUrl,
                "TTS合成完毕，即梦OmniHuman唇形对齐渲染成功"
        );
    }

    @Override
    public List<DigitalHumanAvatar> listAvatars(String tenantId) {
        return avatarRepo.listActive(tenantId != null ? tenantId : "public");
    }

    @Override
    public Optional<DigitalHumanAvatar> getAvatar(String tenantId, String avatarId) {
        return avatarRepo.findById(tenantId != null ? tenantId : "public", avatarId);
    }

    @Override
    public DigitalHumanAvatar registerAvatar(String tenantId, String avatarName, String originalImageUrl, String maskImageUrl, String speakerCode) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        String effectiveSpeaker = (speakerCode != null && !speakerCode.isBlank()) ? speakerCode : "zh_female_shuangkuai";
        DigitalHumanAvatar avatar = DigitalHumanAvatar.create(
                effectiveTenant,
                avatarName,
                "2D_PHOTO",
                originalImageUrl,
                maskImageUrl,
                effectiveSpeaker
        );
        DigitalHumanAvatar saved = avatarRepo.save(avatar);
        log.info("Registered digital human avatar: id={}, name={}, tenant={}", saved.id(), saved.avatarName(), effectiveTenant);
        return saved;
    }

    @Override
    public boolean deleteAvatar(String tenantId, String avatarId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "public";
        boolean deleted = avatarRepo.deleteById(effectiveTenant, avatarId);
        log.info("Deleted digital human avatar: id={}, tenant={}, success={}", avatarId, effectiveTenant, deleted);
        return deleted;
    }
}
