package com.datn.financeapp.common.upload;

/**
 * Thông tin phản hồi sau khi tải ảnh lên Cloudinary thành công.
 *
 * @param url đường dẫn HTTPS an toàn của tệp ảnh
 * @param id  định danh công khai (public ID) của tệp ảnh trên Cloudinary
 */
public record UploadRes(
        String url,
        String id
) {
}
