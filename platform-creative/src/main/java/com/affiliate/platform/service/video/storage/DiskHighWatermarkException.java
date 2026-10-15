package com.affiliate.platform.service.video.storage;

/**
 * 磁盘容量超过 80% 高水位阈值异常
 */
public class DiskHighWatermarkException extends RuntimeException {
    public DiskHighWatermarkException(String message) {
        super(message);
    }
}
