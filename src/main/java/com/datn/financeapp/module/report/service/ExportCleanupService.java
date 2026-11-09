package com.datn.financeapp.module.report.service;

/**
 * Interface định nghĩa dịch vụ dọn dẹp các tệp và bản ghi xuất dữ liệu hết hạn hoặc bị kẹt.
 * Cung cấp phương thức chính {@link #cleanUpExpiredAndStuck()} để thu hồi tài nguyên.
 */
public interface ExportCleanupService {

    /**
     * Thực hiện quét và dọn dẹp các tệp tạm cũng như bản ghi export job hết hạn hoặc bị kẹt.
     *
     * @return số lượng bản ghi export job đã bị dọn dẹp hoặc đánh dấu thất bại
     */
    int cleanUpExpiredAndStuck();
}
