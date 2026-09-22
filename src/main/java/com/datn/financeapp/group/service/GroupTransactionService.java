package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.transaction.GroupRefundReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionBulkReviewRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupWithdrawalReq;
import java.util.List;
import java.util.UUID;

/**
 * Service quản lý giao dịch tài chính nhóm (Group Transaction).
 * <p>
 * Phụ trách các luồng nghiệp vụ cốt lõi:
 * <ul>
 *     <li>Tạo, xem chi tiết, cập nhật và xóa giao dịch nhóm (Chi tiêu EXPENSE, Đóng góp CONTRIBUTION, Điều chỉnh quỹ ADJUSTMENT).</li>
 *     <li>Quy trình duyệt giao dịch: Phê duyệt (confirm), từ chối (reject) đơn lẻ hoặc hàng loạt (bulk review).</li>
 *     <li>Giao dịch hoàn tiền (Refund) và rút tiền từ quỹ (Withdrawal).</li>
 *     <li>Đồng bộ và cập nhật biến động số dư ví nhóm khi giao dịch được xác nhận hoặc hủy bỏ.</li>
 * </ul>
 * </p>
 */
public interface GroupTransactionService {

    /**
     * Tạo một giao dịch nhóm mới.
     * <p>
     * Hỗ trợ các loại giao dịch: Chi tiêu (EXPENSE), Đóng góp (CONTRIBUTION),
     * Điều chỉnh tăng (ADJUSTMENT_UP), Điều chỉnh giảm (ADJUSTMENT_DOWN).<br/>
     * Xử lý nguồn tiền FUND (từ quỹ nhóm) hoặc PERSONAL (thành viên tự bỏ túi chi trước).<br/>
     * Nếu người tạo là Owner/Admin, giao dịch có thể được tự động CONFIRMED; ngược lại sẽ ở trạng thái PENDING_APPROVAL.
     * </p>
     *
     * @param userId  ID người tạo giao dịch
     * @param groupId ID nhóm
     * @param req     Dữ liệu tạo giao dịch (loại, số tiền, nguồn tiền, ngày xảy ra, danh sách người tham gia phân bổ...)
     * @return Thông tin chi tiết giao dịch vừa tạo
     */
    GroupTransactionDetailRes create(UUID userId, UUID groupId, GroupTransactionCreateReq req);

    /**
     * Lấy danh sách giao dịch của nhóm theo bộ lọc (phân trang).
     *
     * @param userId  ID người dùng yêu cầu
     * @param groupId ID nhóm
     * @param filter  Điều kiện lọc (loại giao dịch, trạng thái, người tạo, khoảng ngày, pagination...)
     * @return Danh sách giao dịch thỏa mãn điều kiện
     */
    List<GroupTransactionDetailRes> list(UUID userId, UUID groupId, GroupTransactionFilterReq filter);

    /**
     * Xem thông tin chi tiết của một giao dịch cụ thể kèm danh sách người cùng chi trả/hưởng (participants).
     *
     * @param userId        ID người dùng xem
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch
     * @return Chi tiết giao dịch
     */
    GroupTransactionDetailRes detail(UUID userId, UUID groupId, UUID transactionId);

    /**
     * Cập nhật thông tin giao dịch nhóm đã tồn tại.
     * <p>
     * Chỉ áp dụng khi giao dịch chưa bị khóa hoặc người dùng có đủ thẩm quyền (Owner/Admin/Chính chủ).
     * Hệ thống sẽ tính toán lại phần phân bổ và số dư ví nếu có sự thay đổi về số tiền hoặc nguồn tiền.
     * </p>
     *
     * @param userId        ID người thực hiện cập nhật
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần sửa
     * @param req           Dữ liệu cập nhật
     * @return Chi tiết giao dịch sau khi cập nhật
     */
    GroupTransactionDetailRes update(UUID userId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req);

    /**
     * Xóa mềm (soft-delete) một giao dịch nhóm.
     * <p>
     * Nếu giao dịch đã ở trạng thái CONFIRMED và sử dụng nguồn tiền FUND,
     * hệ thống sẽ hoàn tác (rollback) số dư ví nhóm tương ứng.
     * </p>
     *
     * @param userId        ID người thực hiện xóa (Owner/Admin hoặc người tạo giao dịch)
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần xóa
     */
    void delete(UUID userId, UUID groupId, UUID transactionId);

    /**
     * Phê duyệt (confirm) một giao dịch đang chờ duyệt (PENDING_APPROVAL).
     * <p>
     * Khi được duyệt, giao dịch chuyển sang trạng thái CONFIRMED. Nếu nguồn tiền từ FUND,
     * số dư ví quỹ nhóm sẽ được cập nhật tức thì. Yêu cầu quyền: Owner hoặc Admin.
     * </p>
     *
     * @param userId        ID người duyệt (Owner/Admin)
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần duyệt
     * @return Chi tiết giao dịch sau khi đã xác nhận
     */
    GroupTransactionDetailRes confirm(UUID userId, UUID groupId, UUID transactionId);

    /**
     * Từ chối (reject) một giao dịch đang chờ duyệt.
     * <p>
     * Giao dịch chuyển sang trạng thái REJECTED và không ảnh hưởng đến số dư quỹ hay bảng tính nợ.
     * Yêu cầu quyền: Owner hoặc Admin.
     * </p>
     *
     * @param userId        ID người từ chối
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần từ chối
     * @return Chi tiết giao dịch sau khi từ chối
     */
    GroupTransactionDetailRes reject(UUID userId, UUID groupId, UUID transactionId);

    /**
     * Phê duyệt hàng loạt giao dịch đang ở trạng thái PENDING_APPROVAL.
     *
     * @param userId  ID người duyệt (Owner/Admin)
     * @param groupId ID nhóm
     * @param req     Danh sách các ID giao dịch cần phê duyệt
     * @return Kết quả thống kê số lượng duyệt thành công và thất bại
     */
    GroupTransactionBulkReviewRes bulkConfirm(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req);

    /**
     * Từ chối hàng loạt giao dịch đang ở trạng thái PENDING_APPROVAL.
     *
     * @param userId  ID người từ chối (Owner/Admin)
     * @param groupId ID nhóm
     * @param req     Danh sách các ID giao dịch cần từ chối
     * @return Kết quả thống kê số lượng từ chối thành công và thất bại
     */
    GroupTransactionBulkReviewRes bulkReject(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req);

    /**
     * Tạo một giao dịch hoàn trả tiền (REFUND).
     * <p>
     * Dùng để hoàn tiền cho thành viên đã tự bỏ túi chi hộ nhóm,
     * hoặc trả lại khoản đóng góp thừa.
     * </p>
     *
     * @param userId  ID người tạo yêu cầu hoàn tiền
     * @param groupId ID nhóm
     * @param req     Thông tin hoàn tiền (người nhận, số tiền, nguồn ví, ghi chú...)
     * @return Chi tiết giao dịch hoàn tiền
     */
    GroupTransactionDetailRes createRefund(UUID userId, UUID groupId, GroupRefundReq req);

    /**
     * Cập nhật thông tin giao dịch hoàn tiền đã tạo.
     *
     * @param userId        ID người thực hiện sửa
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch hoàn tiền
     * @param req           Dữ liệu mới cần cập nhật
     * @return Chi tiết giao dịch sau cập nhật
     */
    GroupTransactionDetailRes updateRefund(UUID userId, UUID groupId, UUID transactionId, GroupRefundReq req);

    /**
     * Tạo một giao dịch rút tiền khỏi quỹ nhóm (WITHDRAWAL).
     * <p>
     * Thành viên rút bớt tiền từ quỹ nhóm về tài khoản cá nhân.
     * Số tiền rút tối đa không được vượt quá số tiền đóng góp còn lại của thành viên đó trong quỹ.
     * </p>
     *
     * @param userId  ID người tạo yêu cầu rút tiền
     * @param groupId ID nhóm
     * @param req     Thông tin rút tiền (số tiền, lý do...)
     * @return Chi tiết giao dịch rút tiền
     */
    GroupTransactionDetailRes createWithdrawal(UUID userId, UUID groupId, GroupWithdrawalReq req);

    /**
     * Cập nhật thông tin giao dịch rút tiền đã tạo.
     *
     * @param userId        ID người thực hiện sửa
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch rút tiền
     * @param req           Dữ liệu mới cần cập nhật
     * @return Chi tiết giao dịch sau cập nhật
     */
    GroupTransactionDetailRes updateWithdrawal(UUID userId, UUID groupId, UUID transactionId, GroupWithdrawalReq req);
}
