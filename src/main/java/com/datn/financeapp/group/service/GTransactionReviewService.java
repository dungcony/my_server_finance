package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;

import java.util.UUID;

/**
 * Service công khai (Public API) quản lý luồng kiểm duyệt giao dịch tài chính nhóm.
 * <p>
 * Phụ trách:
 * <ul>
 *   <li>Phê duyệt (CONFIRM) giao dịch đơn lẻ hoặc hàng loạt: Chuyển trạng thái sang {@code CONFIRMED}
 *       và cập nhật số dư quỹ nhóm có khóa bi quan (Pessimistic Lock).</li>
 *   <li>Từ chối (REJECT) giao dịch đơn lẻ hoặc hàng loạt: Chuyển trạng thái sang {@code REJECTED}
 *       mà không làm biến động số dư quỹ.</li>
 * </ul>
 * <b>Yêu cầu phân quyền:</b> Tất cả các phương thức đều bắt buộc người thực hiện (operator)
 * phải là Trưởng nhóm (OWNER) hoặc Thủ quỹ đang nắm giữ quỹ (TREASURER).
 * </p>
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #confirm}, {@link #reject}: Duyệt/Từ chối 1 giao dịch. Input: operatorId, groupId, transactionId. Output: GroupTransactionDetailRes.</li>
 *   <li>{@link #bulkConfirm}, {@link #bulkReject}: Duyệt/Từ chối hàng loạt. Input: operatorId, groupId, req. Output: GroupTransactionBulkReviewRes.</li>
 * </ul>
 * </p>
 */
public interface GTransactionReviewService {

    /**
     * Phê duyệt một giao dịch đơn lẻ đang ở trạng thái {@code PENDING}.
     * <p>
     * <b>Quy trình xử lý:</b>
     * <ul>
     *   <li>Chuyển trạng thái giao dịch sang {@code CONFIRMED}.</li>
     *   <li>Ghi nhận người duyệt ({@code reviewedBy}) và thời điểm duyệt ({@code reviewedAt}).</li>
     *   <li>Tự động tăng/giảm số dư quỹ nhóm tương ứng (khóa bi quan chống race condition).</li>
     * </ul>
     * </p>
     *
     * @param operatorId    ID người thực hiện (Owner hoặc Treasurer)
     * @param groupId       ID nhóm tài chính
     * @param transactionId ID giao dịch cần phê duyệt
     * @return Chi tiết giao dịch sau khi đã xác nhận thành công
     */
    GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId);

    /**
     * Từ chối một giao dịch đơn lẻ đang ở trạng thái {@code PENDING}.
     * <p>
     * Chuyển trạng thái giao dịch sang {@code REJECTED}, ghi nhận người từ chối và thời điểm từ chối.
     * Số dư quỹ nhóm không bị biến động.
     * </p>
     *
     * @param operatorId    ID người thực hiện (Owner hoặc Treasurer)
     * @param groupId       ID nhóm tài chính
     * @param transactionId ID giao dịch cần từ chối
     * @return Chi tiết giao dịch sau khi đã chuyển sang REJECTED
     */
    GroupTransactionDetailRes reject(UUID operatorId, UUID groupId, UUID transactionId);

    /**
     * Phê duyệt hàng loạt các giao dịch trong danh sách yêu cầu.
     * <p>
     * Hệ thống lọc và chỉ áp dụng phê duyệt cho các giao dịch thực sự đang ở trạng thái {@code PENDING}.
     * Toàn bộ biến động số dư quỹ của các giao dịch hợp lệ được cộng dồn và cập nhật an toàn trong một giao dịch.
     * </p>
     *
     * @param operatorId ID người thực hiện (Owner hoặc Treasurer)
     * @param groupId    ID nhóm tài chính
     * @param req        Dữ liệu chứa danh sách các transaction ID cần duyệt
     * @return Kết quả tổng hợp phê duyệt hàng loạt
     */
    int bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);

    /**
     * Từ chối hàng loạt các giao dịch trong danh sách yêu cầu.
     * <p>
     * Chỉ áp dụng cho các giao dịch thuộc nhóm và đang ở trạng thái {@code PENDING}.
     * </p>
     *
     * @param operatorId ID người thực hiện (Owner hoặc Treasurer)
     * @param groupId    ID nhóm tài chính
     * @param req        Dữ liệu chứa danh sách các transaction ID cần từ chối
     * @return Kết quả tổng hợp từ chối hàng loạt
     */
    int bulkReject(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req);
}
