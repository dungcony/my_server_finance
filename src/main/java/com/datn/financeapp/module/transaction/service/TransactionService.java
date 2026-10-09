package com.datn.financeapp.module.transaction.service;

import com.datn.financeapp.common.response.PageRequestParams;
import com.datn.financeapp.module.transaction.dto.request.CreateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.DuplicateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.request.TransactionFilterRequest;
import com.datn.financeapp.module.transaction.dto.request.UpdateTransactionRequest;
import com.datn.financeapp.module.transaction.dto.response.CreateTransactionResponse;
import com.datn.financeapp.module.transaction.dto.response.DeleteTransactionResponse;
import com.datn.financeapp.module.transaction.dto.response.GeneratedTransactionResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionByDateResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionDetailResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionListItemResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionListResponse;
import com.datn.financeapp.module.transaction.dto.response.TransactionRefResponse;
import com.datn.financeapp.module.transaction.entity.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * Interface định nghĩa dịch vụ quản lý giao dịch tài chính (CRUD thu/chi/chuyển tiền).
 * Cung cấp các phương thức chính như {@link #create(UUID, CreateTransactionRequest)}, {@link #update(UUID, UUID, UpdateTransactionRequest)},
 * {@link #delete(UUID, UUID)}, {@link #duplicate(UUID, UUID, DuplicateTransactionRequest)}, {@link #detail(UUID, UUID)} và {@link #list(UUID, TransactionFilterRequest, PageRequestParams)}.
 */
public interface TransactionService {

    /**
     * Tạo mới một giao dịch đơn lẻ.
     *
     * @param userId ID người dùng
     * @param req    thông tin yêu cầu tạo giao dịch
     * @return {@link CreateTransactionResponse} kết quả tạo giao dịch
     */
    CreateTransactionResponse create(UUID userId, CreateTransactionRequest req);

    /**
     * Cập nhật thông tin giao dịch hiện có.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch cần sửa
     * @param req           thông tin cập nhật
     * @return {@link CreateTransactionResponse} kết quả cập nhật
     */
    CreateTransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest req);

    /**
     * Xóa mềm một giao dịch và hoàn tất cập nhật lại số dư ví.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch cần xóa
     * @return {@link DeleteTransactionResponse} kết quả xóa giao dịch
     */
    DeleteTransactionResponse delete(UUID userId, UUID transactionId);

    /**
     * Nhân bản một giao dịch đã tồn tại.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch mẫu
     * @param req           thông tin thay đổi (tùy chọn)
     * @return {@link CreateTransactionResponse} kết quả tạo giao dịch mới
     */
    CreateTransactionResponse duplicate(UUID userId, UUID transactionId, DuplicateTransactionRequest req);

    /**
     * Đếm số lượng giao dịch đã phát sinh từ khoản định kỳ.
     *
     * @param recurringId ID khoản định kỳ
     * @return số lượng giao dịch
     */
    long countByRecurringId(UUID recurringId);

    /**
     * Đếm tổng số giao dịch đang hoạt động của người dùng.
     *
     * @param userId ID người dùng
     * @return số lượng giao dịch
     */
    long countActiveByUserId(UUID userId);

    /**
     * Tìm danh sách các giao dịch đã phát sinh từ khoản định kỳ.
     *
     * @param recurringId ID khoản định kỳ
     * @return danh sách {@link GeneratedTransactionResponse}
     */
    List<GeneratedTransactionResponse> findGeneratedByRecurringId(UUID recurringId);

    /**
     * Tìm thông tin tham chiếu tóm tắt của giao dịch theo ID.
     *
     * @param transactionId ID giao dịch
     * @return {@link TransactionRefResponse}
     */
    TransactionRefResponse findRefById(UUID transactionId);

    /**
     * Truy vấn danh sách giao dịch phân trang theo bộ lọc.
     *
     * @param userId  ID người dùng
     * @param filters thông tin bộ lọc
     * @param page    thông tin phân trang và sắp xếp
     * @return {@link TransactionListResponse}
     */
    TransactionListResponse list(UUID userId, TransactionFilterRequest filters, PageRequestParams page);

    /**
     * Xem chi tiết thông tin của một giao dịch.
     *
     * @param userId        ID người dùng
     * @param transactionId ID giao dịch
     * @return {@link TransactionDetailResponse}
     */
    TransactionDetailResponse detail(UUID userId, UUID transactionId);

    /**
     * Truy vấn danh sách giao dịch gom nhóm theo ngày.
     *
     * @param userId  ID người dùng
     * @param filters thông tin bộ lọc
     * @return {@link TransactionByDateResponse}
     */
    TransactionByDateResponse listByDate(UUID userId, TransactionFilterRequest filters);

    /**
     * Tìm entity giao dịch đã lưu trữ hoặc ném ngoại lệ nếu không tìm thấy.
     *
     * @param transactionId ID giao dịch
     * @return entity {@link Transaction}
     */
    Transaction findPersistedOrThrow(UUID transactionId);

    /**
     * Chuyển đổi entity giao dịch sang DTO dạng item danh sách.
     *
     * @param txn entity giao dịch
     * @return DTO {@link TransactionListItemResponse}
     */
    TransactionListItemResponse buildListItemResponse(Transaction txn);

    /**
     * Kiểm tra tính hợp lệ về cấu trúc của loại giao dịch (income, expense, transfer).
     *
     * @param type                loại giao dịch
     * @param categoryId          ID danh mục
     * @param destinationWalletId ID ví đích (nếu là chuyển tiền)
     * @param walletId            ID ví nguồn
     */
    void validateShape(String type, UUID categoryId, UUID destinationWalletId, UUID walletId);
}
