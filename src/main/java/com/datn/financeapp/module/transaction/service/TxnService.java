package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.transaction.dto.request.*;
import com.datn.financeapp.module.transaction.dto.response.*;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa dịch vụ quản lý giao dịch tài chính (CRUD thu/chi/chuyển tiền).
 *
 */
public interface TxnService {

    /**
     * Tạo mới một giao dịch đơn lẻ.
     *
     * @param userId ID người dùng
     * @param req    thông tin yêu cầu tạo giao dịch
     * @return {@link TxnDetailResponse} kết quả tạo giao dịch
     */
    TxnDetailResponse create(UUID userId, TxnCreateReq req);

    /**
     * Cập nhật thông tin giao dịch hiện có.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch cần sửa
     * @param req           thông tin cập nhật
     * @return {@link TxnDetailResponse} kết quả cập nhật
     */
    TxnDetailResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req);

    /**
     * Xóa mềm một giao dịch và hoàn tất cập nhật lại số dư ví.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch cần xóa
     * @return {@link TxnDetailResponse} kết quả xóa giao dịch
     */
    TxnDetailResponse delete(UUID userId, UUID transactionId);

    /**
     * Đếm số lượng giao dịch đã phát sinh từ khoản định kỳ.
     *
     * @param userId ID người dùng
     * @return số lượng giao dịch
     */
    long countByRecurring(UUID userId);

    /**
     * Tìm danh sách các giao dịch đã phát sinh từ khoản định kỳ.
     *
     * @param userId      ID người dùng (null là admin lấy toàn bộ giao dịch rec của hệ thống)
     * @param recurringId ID khoản định kỳ (null là user lấy toàn bộ giao dịch rec của họ
     * @return danh sách {@link List<TxnDetailResponse>}
     */
    List<TxnDetailResponse> getByRecurring(UUID userId, UUID recurringId);

    /**
     * Truy vấn danh sách giao dịch phân trang theo bộ lọc.
     *
     * @param userId  ID người dùng
     * @param filters thông tin bộ lọc
     * @param page    thông tin phân trang và sắp xếp
     * @return {@link TxnSummaryResponse}
     */
    List<TxnSummaryResponse> list(UUID userId, TransactionFilterRequest filters, PageRequestParams page);


    /**
     * Truy vấn danh sách giao dịch gom nhóm theo ngày.
     *
     * @param userId  ID người dùng
     * @param filters thông tin bộ lọc
     * @return {@link List<TxnSummaryResponse>}
     */
    List<TxnSummaryResponse> listByDate(UUID userId, TransactionFilterRequest filters);

}
