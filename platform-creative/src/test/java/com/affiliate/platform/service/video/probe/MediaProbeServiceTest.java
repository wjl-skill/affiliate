package com.affiliate.platform.service.video.probe;

import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.service.video.probe.impl.MediaProbeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MediaProbeService Tests")
class MediaProbeServiceTest {

    private MediaProbeServiceImpl probeService;

    @BeforeEach
    void setUp() {
        probeService = new MediaProbeServiceImpl();
    }

    @Test
    @DisplayName("Should accurately resolve standard aspect ratios")
    void shouldResolveAspectRatios() {
        assertEquals("9:16", probeService.resolveAspectRatio(1080, 1920));
        assertEquals("9:16", probeService.resolveAspectRatio(720, 1280));
        assertEquals("16:9", probeService.resolveAspectRatio(1920, 1080));
        assertEquals("16:9", probeService.resolveAspectRatio(1280, 720));
        assertEquals("1:1", probeService.resolveAspectRatio(1080, 1080));
        assertEquals("4:5", probeService.resolveAspectRatio(1080, 1350));
        assertEquals("3:4", probeService.resolveAspectRatio(1080, 1440));
        assertEquals("4:3", probeService.resolveAspectRatio(1440, 1080));
    }

    @Test
    @DisplayName("Fallback probe should extract metadata based on hints")
    void shouldFallbackProbeWithHints() {
        VideoProbeResult horizontal = probeService.probe("https://example.com/videos/horizontal_1920x1080_body.mp4");
        assertNotNull(horizontal);
        assertEquals(1920, horizontal.width());
        assertEquals(1080, horizontal.height());
        assertEquals("16:9", horizontal.aspectRatio());
        assertEquals(20.0, horizontal.durationSeconds());

        VideoProbeResult verticalHook = probeService.probe("https://example.com/videos/vertical_hook_intro.mp4");
        assertNotNull(verticalHook);
        assertEquals(1080, verticalHook.width());
        assertEquals(1920, verticalHook.height());
        assertEquals("9:16", verticalHook.aspectRatio());
        assertEquals(3.5, verticalHook.durationSeconds());

        VideoProbeResult square = probeService.probe("test_square_1080x1080.mp4");
        assertNotNull(square);
        assertEquals(1080, square.width());
        assertEquals(1080, square.height());
        assertEquals("1:1", square.aspectRatio());
    }

    @Test
    @DisplayName("Probe null or blank input should return default vertical result safely")
    void shouldReturnDefaultOnNullOrBlank() {
        VideoProbeResult resNull = probeService.probe((String) null);
        assertNotNull(resNull);
        assertEquals(1080, resNull.width());
        assertEquals(1920, resNull.height());
        assertEquals("9:16", resNull.aspectRatio());

        VideoProbeResult resBlank = probeService.probe("   ");
        assertNotNull(resBlank);
        assertEquals(1080, resBlank.width());
        assertEquals(1920, resBlank.height());
    }
}
