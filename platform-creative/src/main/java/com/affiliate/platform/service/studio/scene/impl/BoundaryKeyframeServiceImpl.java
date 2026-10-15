package com.affiliate.platform.service.studio.scene.impl;

import com.affiliate.platform.domain.studio.StudioProject;
import com.affiliate.platform.domain.studio.StudioScene;
import com.affiliate.platform.service.studio.scene.BoundaryKeyframeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 边界关键帧法服务实现
 */
@Service
public class BoundaryKeyframeServiceImpl implements BoundaryKeyframeService {

    private static final Logger log = LoggerFactory.getLogger(BoundaryKeyframeServiceImpl.class);

    @Override
    public List<StudioScene> bindBoundaryKeyframes(StudioProject project, List<StudioScene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }

        int n = scenes.size();
        // 预生成 N+1 张边界关键帧 (K0 ~ Kn)
        List<String> keyframeUrls = new ArrayList<>(n + 1);
        String baseOssPrefix = "https://oss.affiliate.com/keyframes/" + project.id();

        for (int i = 0; i <= n; i++) {
            String role = (i == 0) ? "k0_initial" : (i == n) ? "k" + i + "_final" : "k" + i + "_boundary";
            String url = String.format("%s/%s_%s.jpg", baseOssPrefix, project.id(), role);
            keyframeUrls.add(url);
        }

        List<StudioScene> boundScenes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            StudioScene s = scenes.get(i);
            String startFrame = keyframeUrls.get(i);
            String endFrame = keyframeUrls.get(i + 1);
            boundScenes.add(s.withKeyframes(startFrame, endFrame));
        }

        log.info("Successfully bound {} boundary keyframes to {} scenes for project {}",
                keyframeUrls.size(), boundScenes.size(), project.id());

        return boundScenes;
    }
}
