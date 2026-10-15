package com.affiliate.platform.service.video.storage;

import com.affiliate.platform.domain.video.DiskHealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地转码沙箱工作区与磁盘生命周期高水位防御管理器
 */
@Component
public class WorkDirManager {

    private static final Logger log = LoggerFactory.getLogger(WorkDirManager.class);

    @Value("${creative.video.storage.base-dir:#{systemProperties['java.io.tmpdir'] + '/creative_workspace'}}")
    private String baseWorkspaceDir;

    @Value("${creative.video.storage.high-watermark-threshold:80.0}")
    private double highWatermarkThreshold;

    public Path getWorkspaceRoot() {
        Path root = Paths.get(baseWorkspaceDir);
        if (!Files.exists(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                log.warn("Failed to create workspace root directory: {}", root, e);
            }
        }
        return root;
    }

    private final java.util.Set<Path> activeWorkDirs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 为具体任务创建隔离沙箱目录
     */
    public Path createSandboxWorkDir(String taskNo) {
        checkDiskHighWatermarkDefense();

        Path workDir = getWorkspaceRoot().resolve(taskNo);
        try {
            Files.createDirectories(workDir);
            activeWorkDirs.add(workDir);
            return workDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create sandbox work directory for task: " + taskNo, e);
        }
    }

    /**
     * 任务终态立即清空沙箱工作目录
     */
    public void cleanWorkDir(Path workDir) {
        if (workDir == null) {
            return;
        }
        activeWorkDirs.remove(workDir);
        if (!Files.exists(workDir)) {
            return;
        }
        try {
            Files.walkFileTree(workDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.debug("Successfully cleaned work directory: {}", workDir);
        } catch (Exception e) {
            log.warn("Failed to clean work directory {}: {}", workDir, e.getMessage());
        }
    }

    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        if (!activeWorkDirs.isEmpty()) {
            log.info("Container shutting down, gracefully cleaning {} active work directories", activeWorkDirs.size());
            for (Path dir : new java.util.ArrayList<>(activeWorkDirs)) {
                cleanWorkDir(dir);
            }
        }
    }

    private Long simulatedTotalSpace;
    private Long simulatedUsableSpace;

    public void setSimulatedSpace(Long total, Long usable) {
        this.simulatedTotalSpace = total;
        this.simulatedUsableSpace = usable;
    }

    public void setHighWatermarkThreshold(double threshold) {
        this.highWatermarkThreshold = threshold;
    }

    /**
     * 宿主机磁盘空间探针检测 (支持仿真模拟以实现可预测单元测试)
     */
    public DiskHealthStatus checkDiskHealth() {
        long totalSpace = simulatedTotalSpace != null ? simulatedTotalSpace : getWorkspaceRoot().toFile().getTotalSpace();
        long usableSpace = simulatedUsableSpace != null ? simulatedUsableSpace : getWorkspaceRoot().toFile().getUsableSpace();
        return DiskHealthStatus.evaluate(totalSpace, usableSpace, highWatermarkThreshold);
    }

    /**
     * 高水位防御拦截：若磁盘使用率超过阈值（如 80%），熔断阻断新任务
     */
    public void checkDiskHighWatermarkDefense() {
        DiskHealthStatus status = checkDiskHealth();
        if (!status.isSafe()) {
            String msg = String.format("Disk high-watermark defense tripped: usage %.2f%% exceeds threshold %.0f%%. Halting new video tasks.",
                    status.usedPercent(), status.alertThresholdPercent());
            log.error(msg);
            throw new DiskHighWatermarkException(msg);
        }
    }

    /**
     * 后台守护保洁：定期清理超过 2 小时的孤儿临时文件
     */
    @Scheduled(fixedDelay = 1800000) // 每 30 分钟
    public int cleanOrphanWorkDirs() {
        return cleanOrphanWorkDirs(120);
    }

    public int cleanOrphanWorkDirs(long olderThanMinutes) {
        Path root = getWorkspaceRoot();
        if (!Files.exists(root)) {
            return 0;
        }

        Instant threshold = Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES);
        AtomicInteger deletedCount = new AtomicInteger(0);

        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
                    Instant lastModified = attrs.lastModifiedTime().toInstant();
                    if (lastModified.isBefore(threshold)) {
                        log.info("Cleaning orphan expired work directory: {}", dir);
                        cleanWorkDir(dir);
                        deletedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Error inspecting orphan candidate {}: {}", dir, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Error scanning orphan work directories: {}", e.getMessage());
        }

        return deletedCount.get();
    }
}
