package com.affiliate.platform.service.studio.human;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;
import com.affiliate.platform.domain.studio.DigitalHumanDriveResult;
import com.affiliate.platform.repository.studio.InMemoryDigitalHumanAvatarRepository;
import com.affiliate.platform.service.studio.human.impl.DigitalHumanDriverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DigitalHumanDriverService Tests - Doubao TTS & OmniHuman 1.5")
class DigitalHumanDriverServiceTest {

    private DigitalHumanDriverServiceImpl humanService;
    private InMemoryDigitalHumanAvatarRepository avatarRepo;

    @BeforeEach
    void setUp() {
        avatarRepo = new InMemoryDigitalHumanAvatarRepository();
        humanService = new DigitalHumanDriverServiceImpl(avatarRepo);
    }

    @Test
    @DisplayName("Should list pre-seeded digital human avatars")
    void shouldListSeededAvatars() {
        List<DigitalHumanAvatar> avatars = humanService.listAvatars("public");
        assertFalse(avatars.isEmpty());
        assertTrue(avatars.size() >= 2);
    }

    @Test
    @DisplayName("Should synthesize TTS audio and execute OmniHuman lip sync drive")
    void shouldDriveAvatar() {
        DigitalHumanAvatar avatar = humanService.listAvatars("public").get(0);

        String voiceover = "看看这款研发3年的固发黑科技，高纯度活性肽直接直达发根毛囊！";
        DigitalHumanDriveResult result = humanService.driveAvatar(
                "tenant_test", avatar.id(), voiceover, avatar.defaultSpeaker()
        );

        assertNotNull(result);
        assertNotNull(result.taskNo());
        assertEquals("SUCCESS", result.status());
        assertNotNull(result.audioUrl());
        assertTrue(result.audioDurationSeconds() >= 3.0);
        assertNotNull(result.videoUrl());
        assertTrue(result.message().contains("OmniHuman"));
    }
}
