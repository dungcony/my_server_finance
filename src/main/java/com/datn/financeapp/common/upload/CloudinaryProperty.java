package com.datn.financeapp.common.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính cấu hình tích hợp dịch vụ lưu trữ đám mây Cloudinary.
 *
 * @param cloudName        tên cloud định danh tài khoản trên Cloudinary
 * @param apiKey           khóa công khai (API Key) dùng để xác thực
 * @param apiSecret        khóa bí mật (API Secret) dùng để ký chữ ký số
 * @param folder           thư mục gốc lưu trữ tệp trên Cloudinary (mặc định: {@code finance})
 * @param maxFileSizeBytes dung lượng tệp tối đa tính bằng byte (mặc định: 10MB)
 */
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperty(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder,
        long maxFileSizeBytes
) {
    private static final String DEFAULT_FOLDER = "finance";
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    public CloudinaryProperty {
        if (folder == null || folder.isBlank()) {
            folder = DEFAULT_FOLDER;
        }
        if (maxFileSizeBytes <= 0) {
            maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
        }
    }

    public boolean configured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    public String resolvedFolder() {
        String value = folder.trim();
        while (value.endsWith("/") || value.endsWith("\\")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
