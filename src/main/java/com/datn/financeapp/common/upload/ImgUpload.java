package com.datn.financeapp.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Xử lý tải ảnh lên và xóa ảnh trên dịch vụ đám mây Cloudinary.
 * <p>
 * Các hàm chính trong class:
 * <ul>
 *   <li>{@link #upload(MultipartFile, String, String, boolean)}: Kiểm tra và tải ảnh lên Cloudinary.</li>
 *   <li>{@link #delete(String)}: Xóa ảnh đã tồn tại trên Cloudinary qua public ID.</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ImgUpload implements UploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final Cloudinary cloudinary;
    private final CloudinaryProperty properties;

    /**
     * {@inheritDoc}
     * <p>
     * Thực hiện xác thực định dạng/dung lượng ảnh và truyền dữ liệu lên dịch vụ Cloudinary.
     * </p>
     *
     * @throws BusinessException nếu cấu hình thiếu, tệp không hợp lệ hoặc tải ảnh lên Cloudinary thất bại
     */
    @Override
    public UploadRes upload(MultipartFile file, String path, String value, boolean overwrite) {
        validateCloudinaryConfig();
        validateImage(file);

        String publicId = buildPublicId(path, value);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", properties.resolvedFolder(),
                    "public_id", publicId,
                    "overwrite", overwrite
            ));

            Object secureUrl = result.get("secure_url");
            Object uploadedPublicId = result.get("public_id");
            if (secureUrl == null || uploadedPublicId == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Cloudinary upload response is invalid");
            }

            return new UploadRes(secureUrl.toString(), uploadedPublicId.toString());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Upload image to Cloudinary failed");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gọi Cloudinary Uploader API để tiêu hủy tệp theo public ID.
     * </p>
     *
     * @throws BusinessException nếu cấu hình Cloudinary thiếu hoặc gọi API xóa Cloudinary thất bại
     */
    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        validateCloudinaryConfig();

        try {
            cloudinary.uploader().destroy(publicId.trim(), ObjectUtils.emptyMap());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Delete image from Cloudinary failed");
        }
    }

    private void validateCloudinaryConfig() {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "CLOUDINARY_NOT_CONFIGURED");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Image file is required");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Image file is too large");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Only JPG, PNG, WEBP and GIF images are allowed");
        }
    }

    private String buildPublicId(String path, String value) {
        String sanitized = sanitizePublicId(value);
        if (path == null || path.isBlank()) {
            return sanitized;
        }
        // Chuẩn hóa đường dẫn: loại bỏ dấu gạch chéo dư thừa ở đầu và cuối
        String cleanPath = path.trim().replaceAll("^[/\\\\]+|[/\\\\]+$", "");
        return cleanPath.isBlank() ? sanitized : cleanPath + "/" + sanitized;
    }

    private String sanitizePublicId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String sanitized = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isBlank() ? UUID.randomUUID().toString() : sanitized;
    }
}
