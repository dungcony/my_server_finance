package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.module.transaction.dto.request.*;
import com.datn.financeapp.module.transaction.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa dịch vụ quản lý giao dịch tài chính (CRUD thu/chi/chuyển tiền).
 *
 */
public interface TxnBehavierService {

    /**
     * Tạo mới một giao dịch đơn lẻ.
     *
     * @param userId ID người dùng
     * @param req    thông tin yêu cầu tạo giao dịch
     * @return {@link TxnDetailResponse} kết quả tạo giao dịch
     */
    TxnDetailResponse create(UUID userId, TxnCreateReq req);

    /**
     * Truy vấn danh sách giao dịch phân trang theo bộ lọc.
     *
     * @param filters thông tin bộ lọc
     * @param page    thông tin phân trang và sắp xếp
     * @return {@link TxnListRes}
     */
    TxnListRes gets(TxnFilterRequest filters, Pageable page);


    /**
     * Cập nhật thông tin giao dịch hiện có.
     *
     * @param transactionId ID giao dịch cần sửa
     * @param req           thông tin cập nhật
     * @return {@link TxnDetailResponse} kết quả cập nhật
     */
    TxnDetailResponse update(UUID transactionId, TxnUpdateRequest req);

    /**
     * Xóa mềm một giao dịch và hoàn tất cập nhật lại số dư ví.
     *
     * @param transactionId ID giao dịch cần xóa
     */
    void softDelete(UUID transactionId);

}
