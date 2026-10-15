package com.affiliate.platform.service.video.operator;

import com.affiliate.platform.domain.video.AlignMode;
import com.affiliate.platform.domain.video.VideoProbeResult;
import com.affiliate.platform.domain.video.VideoProcessType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Video Atomic Operators Tests")
class VideoOperatorsTest {

    private WatermarkRemovalOperator watermarkOp;
    private SubtitleProcessorOperator subtitleOp;
    private IntroSplicingOperator introOp;
    private CtaOverlayOperator ctaOp;
    private TrimOperator trimOp;
    private VideoOperatorFactory factory;

    @BeforeEach
    void setUp() {
        watermarkOp = new WatermarkRemovalOperator();
        subtitleOp = new SubtitleProcessorOperator();
        introOp = new IntroSplicingOperator();
        ctaOp = new CtaOverlayOperator();
        trimOp = new TrimOperator();
        factory = new VideoOperatorFactory(List.of(watermarkOp, subtitleOp, introOp, ctaOp, trimOp));
    }

    @Test
    @DisplayName("Factory should correctly register and retrieve all operators")
    void shouldRetrieveAllOperators() {
        assertEquals(watermarkOp, factory.getRequiredOperator(VideoProcessType.WATERMARK_REMOVE));
        assertEquals(subtitleOp, factory.getRequiredOperator(VideoProcessType.SUBTITLE));
        assertEquals(introOp, factory.getRequiredOperator(VideoProcessType.INTRO_STITCH));
        assertEquals(ctaOp, factory.getRequiredOperator(VideoProcessType.CTA_OVERLAY));
        assertEquals(trimOp, factory.getRequiredOperator(VideoProcessType.TRIM));

        assertThrows(IllegalArgumentException.class, () -> factory.getRequiredOperator(VideoProcessType.BATCH_ASSEMBLE));
    }

    @Test
    @DisplayName("WatermarkRemovalOperator should build accurate delogo filter with boundary protection")
    void testWatermarkRemovalOperator() {
        VideoProbeResult probe = new VideoProbeResult(1080, 1920, "9:16", 15.0, 30.0, "h264", 2500000, true, "aac", 48000, 2);
        Map<String, Object> config = Map.of("x", 850, "y", 30, "w", 200, "h", 80);

        String filter = watermarkOp.buildFilterCommand(probe, config);
        assertEquals("delogo=x=850:y=30:w=200:h=80:show=0", filter);

        // Boundary overflow test
        Map<String, Object> overflowConfig = Map.of("x", 1000, "y", 1900, "w", 500, "h", 300);
        String clampedFilter = watermarkOp.buildFilterCommand(probe, overflowConfig);
        assertTrue(clampedFilter.contains("x=1000:y=1900"));
        assertTrue(clampedFilter.contains("w=80:h=20")); // Clamped within 1080 and 1920
    }

    @Test
    @DisplayName("SubtitleProcessorOperator should format subtitles command with style")
    void testSubtitleProcessorOperator(@TempDir Path tempDir) throws Exception {
        Map<String, Object> config = Map.of(
                "fontName", "SourceHanSansCN-Bold",
                "fontSize", 24,
                "primaryColour", "&H00FFFFFF",
                "outlineColour", "&H00000000",
                "outline", 2
        );

        String filter = subtitleOp.buildFilterCommand(null, config);
        assertTrue(filter.contains("Fontname=SourceHanSansCN-Bold"));
        assertTrue(filter.contains("FontSize=24"));

        // Test apply creates srt file in workDir
        Path input = tempDir.resolve("in.mp4");
        Files.writeString(input, "input");
        Path output = subtitleOp.apply(input, Map.of("subtitleText", "Hello world"), tempDir);
        assertNotNull(output);
        assertTrue(Files.exists(tempDir.resolve("auto_subtitle.srt")));
        assertTrue(Files.readString(tempDir.resolve("auto_subtitle.srt")).contains("Hello world"));
    }

    @Test
    @DisplayName("IntroSplicingOperator should build scale and pad filters for letterbox, crop, scale")
    void testIntroSplicingOperator() {
        String letterbox = introOp.buildScalePadFilter(1080, 1920, AlignMode.LETTERBOX);
        assertTrue(letterbox.contains("scale=1080:1920:force_original_aspect_ratio=decrease"));
        assertTrue(letterbox.contains("pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black"));

        String crop = introOp.buildScalePadFilter(1080, 1920, AlignMode.CROP);
        assertEquals("scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920", crop);

        String scale = introOp.buildScalePadFilter(1080, 1920, AlignMode.SCALE);
        assertEquals("scale=1080:1920", scale);
    }

    @Test
    @DisplayName("CtaOverlayOperator should calculate startTime and format fade-in filter")
    void testCtaOverlayOperator() {
        VideoProbeResult probe = new VideoProbeResult(1080, 1920, "9:16", 30.0, 30.0, "h264", 2500000, true, "aac", 48000, 2);

        // Image overlay
        Map<String, Object> imageConfig = Map.of(
                "ctaImageUrl", "https://oss.affiliate.com/btn.png",
                "offsetFromEndSeconds", 3.5,
                "fadeInDuration", 0.5
        );
        String imageFilter = ctaOp.buildFilterCommand(probe, imageConfig);
        assertTrue(imageFilter.contains("fade=in:st=26.50:d=0.50:alpha=1[cta]"));
        assertTrue(imageFilter.contains("overlay=(W-w)/2:H-h-120:enable='gte(t,26.50)'"));

        // Text overlay
        Map<String, Object> textConfig = Map.of(
                "ctaText", "立即下载体验",
                "offsetFromEndSeconds", 5.0
        );
        String textFilter = ctaOp.buildFilterCommand(probe, textConfig);
        assertTrue(textFilter.contains("drawtext=text='立即下载体验'"));
        assertTrue(textFilter.contains("enable='gte(t,25.00)'"));
    }

    @Test
    @DisplayName("TrimOperator should format start and end time precisely")
    void testTrimOperator() {
        Map<String, Object> config = Map.of("startSeconds", 3.2, "endSeconds", 25.8);
        String command = trimOp.buildFilterCommand(null, config);
        assertTrue(command.contains("-ss 00:00:03.200"));
        assertTrue(command.contains("-to 00:00:25.800"));
    }

    @Test
    @DisplayName("Operator apply execution fallback should produce valid output file")
    void testOperatorApplyFallback(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("sample.mp4");
        Files.writeString(input, "video_bytes");

        Path outDelogo = watermarkOp.apply(input, Map.of("x", 10, "y", 10, "w", 50, "h", 50), tempDir);
        assertTrue(Files.exists(outDelogo));

        Path outIntro = introOp.apply(input, Map.of("targetWidth", 1080, "targetHeight", 1920), tempDir);
        assertTrue(Files.exists(outIntro));

        Path outTrim = trimOp.apply(input, Map.of("startSeconds", 0.0, "endSeconds", 5.0), tempDir);
        assertTrue(Files.exists(outTrim));
    }
}
