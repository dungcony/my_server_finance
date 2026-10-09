package com.datn.financeapp.common.upload;

import org.springframework.web.multipart.MultipartFile;

/**
 * Giao diện định nghĩa dịch vụ tải lên và quản lý tệp trên hệ thống lưu trữ đám mây.
 * <p>
 * Các hàm chính trong interface:
 * <ul>
 *   <li>{@link #upload(MultipartFile, String, String, boolean)}: Tải tệp hình ảnh lên đám mây và trả về thông tin truy cập.</li>
 *   <li>{@link #delete(String)}: Xóa tệp đã lưu trữ trên đám mây dựa trên định danh công khai.</li>
 * </ul>
 * </p>
 */
public interface UploadService {

    /**
     * Tải tệp lên hệ thống lưu trữ.
     *
     * @param file tệp tải lên nhận từ client
     * @param path đường dẫn thư mục phụ
     * @param value định danh hoặc tên tệp mong muốn
     * @param overwrite cờ xác định có ghi đè nếu trùng định danh hay không
     * @return {@link UploadRes} thông tin phản hồi chứa url và public_id
     */
    UploadRes upload(MultipartFile file, String path, String value, boolean overwrite);

    /**
     * Xóa tệp trên hệ thống lưu trữ.
     *
     * @param publicId định danh công khai của tệp cần xóa
     */
    void delete(String publicId);
}
