package com.affiliate.platform.service.studio.human;

import com.affiliate.platform.domain.studio.DigitalHumanAvatar;
import com.affiliate.platform.repository.studio.InMemoryDigitalHumanAvatarRepository;
import com.affiliate.platform.service.studio.human.impl.DigitalHumanDriverServiceImpl;
import com.affiliate.platform.web.studio.StudioProjectController;
import com.affiliate.platform.web.studio.dto.AvatarRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DigitalHumanAvatarManagementTest {

    private InMemoryDigitalHumanAvatarRepository avatarRepo;
    private DigitalHumanDriverServiceImpl driverService;
    private StudioProjectController controller;

    @BeforeEach
    void setUp() {
        avatarRepo = new InMemoryDigitalHumanAvatarRepository();
        driverService = new DigitalHumanDriverServiceImpl(avatarRepo);
        controller = new StudioProjectController(null, driverService);
    }

    @Test
    void testAvatarRegistrationQueryAndDeletion() {
        // 1. Initial active list contains 2 seeded default avatars
        List<DigitalHumanAvatar> initialList = controller.listDigitalHumanAvatars();
        assertTrue(initialList.size() >= 2);

        // 2. Register a new custom digital human avatar
        AvatarRegisterRequest req = new AvatarRegisterRequest(
                "电商带货达人-琳琳",
                "https://oss.affiliate.com/avatars/linlin.png",
                "https://oss.affiliate.com/avatars/linlin_mask.png",
                "zh_female_linlin"
        );
        DigitalHumanAvatar created = controller.registerAvatar(req);
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("电商带货达人-琳琳", created.avatarName());
        assertEquals("zh_female_linlin", created.defaultSpeaker());

        // 3. Query avatar by ID
        DigitalHumanAvatar queried = controller.getAvatar(created.id());
        assertNotNull(queried);
        assertEquals(created.id(), queried.id());

        // 4. Delete avatar
        controller.deleteAvatar(created.id());

        // 5. Query after deletion should throw exception
        assertThrows(IllegalArgumentException.class, () -> controller.getAvatar(created.id()));
    }
}
