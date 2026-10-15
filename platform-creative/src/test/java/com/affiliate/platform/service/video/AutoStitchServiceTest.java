package com.affiliate.platform.service.video;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.ScalePaddingResult;
import com.affiliate.platform.service.video.impl.AutoStitchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AutoStitchService Tests")
class AutoStitchServiceTest {

    private AutoStitchServiceImpl autoStitchService;

    @BeforeEach
    void setUp() {
        autoStitchService = new AutoStitchServiceImpl();
    }

    @Test
    @DisplayName("Should resolve target dimensions from aspect ratios")
    void shouldResolveTargetDimension() {
        Dimension d1 = autoStitchService.resolveTargetDimension("9:16");
        assertEquals(1080, d1.width);
        assertEquals(1920, d1.height);

        Dimension d2 = autoStitchService.resolveTargetDimension("16:9");
        assertEquals(1920, d2.width);
        assertEquals(1080, d2.height);

        Dimension d3 = autoStitchService.resolveTargetDimension("1:1");
        assertEquals(1080, d3.width);
        assertEquals(1080, d3.height);

        Dimension d4 = autoStitchService.resolveTargetDimension(null);
        assertEquals(1080, d4.width);
        assertEquals(1920, d4.height);
    }

    @Test
    @DisplayName("Should accurately calculate scale factor and letterbox padding")
    void shouldCalculateScaleAndPad() {
        // 1. 相同尺寸：无需缩放或黑边
        ScalePaddingResult same = autoStitchService.calculateScaleAndPad(1080, 1920, 1080, 1920);
        assertTrue(same.isExactMatch());
        assertEquals(1.0, same.scaleFactor());
        assertEquals(0, same.padX());
        assertEquals(0, same.padY());

        // 2. 16:9 横屏转 9:16 竖屏 (1920x1080 -> 1080x1920)
        ScalePaddingResult horizToVert = autoStitchService.calculateScaleAndPad(1920, 1080, 1080, 1920);
        assertFalse(horizToVert.isExactMatch());
        assertEquals(1080, horizToVert.scaledWidth()); // 缩放后宽贴紧 1080
        assertEquals(0, horizToVert.padX());          // 左右无黑边
        assertTrue(horizToVert.padY() > 0);           // 上下填充黑边 (Pillarbox/Letterbox)
        assertEquals(1920, horizToVert.scaledHeight() + horizToVert.padY() * 2);

        // 3. 1:1 方屏转 9:16 竖屏 (1080x1080 -> 1080x1920)
        ScalePaddingResult squareToVert = autoStitchService.calculateScaleAndPad(1080, 1080, 1080, 1920);
        assertFalse(squareToVert.isExactMatch());
        assertEquals(1080, squareToVert.scaledWidth());
        assertEquals(1080, squareToVert.scaledHeight());
        assertEquals(0, squareToVert.padX());
        assertEquals(420, squareToVert.padY()); // (1920 - 1080) / 2 = 420
    }

    @Test
    @DisplayName("Should generate exact Cartesian product pairs (N x M)")
    void shouldGenerateCartesianPairs() {
        List<String> intros = List.of("h1.mp4", "h2.mp4", "h3.mp4", "h4.mp4", "h5.mp4");
        List<String> bodies = List.of(
                "b1.mp4", "b2.mp4", "b3.mp4", "b4.mp4", "b5.mp4",
                "b6.mp4", "b7.mp4", "b8.mp4", "b9.mp4", "b10.mp4"
        );

        List<Map<String, String>> pairs = autoStitchService.generateCartesianPairs(intros, bodies);
        assertEquals(50, pairs.size());

        // Verify first and last pair
        assertEquals("h1.mp4", pairs.get(0).get("introVideoUrl"));
        assertEquals("b1.mp4", pairs.get(0).get("bodyVideoUrl"));

        assertEquals("h5.mp4", pairs.get(49).get("introVideoUrl"));
        assertEquals("b10.mp4", pairs.get(49).get("bodyVideoUrl"));

        // Empty test
        assertTrue(autoStitchService.generateCartesianPairs(List.of(), bodies).isEmpty());
        assertTrue(autoStitchService.generateCartesianPairs(intros, null).isEmpty());
    }

    @Test
    @DisplayName("Should build adapt filters for letterbox, crop and scale")
    void shouldBuildAdaptFilter() {
        String filterLb = autoStitchService.buildAdaptFilter(1920, 1080, 1080, 1920, AlignMode.LETTERBOX);
        assertTrue(filterLb.contains("force_original_aspect_ratio=decrease"));

        String filterCrop = autoStitchService.buildAdaptFilter(1920, 1080, 1080, 1920, AlignMode.CROP);
        assertTrue(filterCrop.contains("crop=1080:1920"));

        String filterScale = autoStitchService.buildAdaptFilter(1920, 1080, 1080, 1920, AlignMode.SCALE);
        assertEquals("scale=1080:1920", filterScale);
    }
}
