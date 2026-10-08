package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.group.enums.GTransactionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service công khai (Public API) quản lý giao dịch tài chính nhóm (Group
 * Transaction).
 * <p>
 * <b>Phạm vi trách nhiệm:</b>
 * <ul>
 * <li>Tạo, chỉnh sửa, xóa và tra cứu các giao dịch thường: Chi tiêu
 * ({@code EXPENSE}), Nộp quỹ ({@code CONTRIBUTION}).</li>
 * <li>Tạo và điều chỉnh các giao dịch nghiệp vụ quỹ đặc thù: Hoàn tiền
 * ({@code REFUND}).</li>
 * <li>Tạo giao dịch điều chỉnh số dư sau khi kiểm kê quỹ thực tế
 * ({@code ADJUSTMENT_UP}, {@code ADJUSTMENT_DOWN}).</li>
 * <li>Thống kê số lượng giao dịch đang chờ phê duyệt.</li>
 * </ul>
 * </p>
 * <p>
 * Các hàm trong interface:
 * <ul>
 * <li>{@link #create}, {@link #update}, {@link #delete}: Thao tác giao dịch
 * thường. Input: operatorId, groupId, transactionId, req... Output:
 * GroupTransactionDetailRes (tùy hàm).</li>
 * <li>{@link #list}: Lấy danh sách giao dịch. Input: operatorId, groupId,
 * filter. Output: List&lt;GroupTransactionDetailRes&gt;.</li>
 * <li>{@link #listPending}: Lấy giao dịch chờ duyệt. Input: operatorId, groupId, page, size. Output:
 * GroupTransactionListRes.</li>
 * <li>{@link #detail}: Lấy chi tiết. Input: operatorId, groupId, transactionId.
 * Output: GroupTransactionDetailRes.</li>
 * <li>{@link #getParticipants}: Lấy danh sách người tham gia chia tiền. Input: operatorId, groupId, transactionId.
 * Output: List&lt;GroupTransactionParticipantRes&gt;.</li>
 * <li>{@link #countPendingForGroup}: Đếm giao dịch chờ duyệt. Input: groupId.
 * Output: số lượng (long).</li>
 * <li>{@link #sumConfirmedAmount}: Cộng tổng tiền giao dịch đã xác nhận theo loại, toàn thời gian hoặc trong một kỳ.
 * Input: groupId, type, [from, to). Output: tổng tiền (long).</li>
 * <li>{@link #confirm}, {@link #reject}: Duyệt/Từ chối 1 giao dịch. Input: operatorId, groupId, transactionId. Output: GroupTransactionDetailRes.</li>
 * <li>{@link #bulkConfirm}, {@link #bulkReject}: Duyệt/Từ chối hàng loạt. Input: operatorId, groupId, req. Output: GroupTransactionBulkReviewRes.</li>
 * </ul>
 * </p>
 */
public interface GTransactionService {
    GroupTransactionDetailRes create(UUID operatorId, UUID groupId, GroupTransactionCreateReq req);

    /**
     * Lọc và phân trang danh sách lịch sử giao dịch của nhóm.
     *
     * @param operatorId ID thành viên gọi yêu cầu
     * @param groupId    ID nhóm
     * @param filter     Tiêu chí lọc (nguồn tiền, loại giao dịch, trạng thái, người
     *                   tạo, khoảng thời gian, phân trang)
     * @return Danh sách giao dịch thỏa mãn điều kiện kèm metadata phân trang
     */
    GroupTransactionListRes list(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter);

    /**
     * Danh sách giao dịch do chính người gọi tạo, bao gồm cả {@code PENDING}.
     *
     * @param operatorId ID thành viên gọi yêu cầu
     * @param groupId    ID nhóm
     * @param filter     Tiêu chí lọc
     * @return Danh sách giao dịch của mình kèm metadata phân trang
     */
    GroupTransactionListRes myList(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter);

    /**
     * Danh sách giao dịch đang chờ duyệt ({@code PENDING}) của nhóm, dành cho người duyệt.
     *
     * @param operatorId ID người gọi (phải là Owner hoặc Thủ quỹ)
     * @param groupId    ID nhóm
     * @param page       trang (bắt đầu từ 1), có thể null
     * @param size       số dòng mỗi trang, có thể null
     * @return giao dịch chờ duyệt kèm metadata phân trang
     * @throws com.datn.financeapp.common.exception.BusinessException nếu không phải Owner/Thủ quỹ
     *                                                                ({@code GROUP_TREASURER_REQUIRED})
     */
    GroupTransactionListRes listPending(UUID operatorId, UUID groupId, Integer page, Integer size);

    /**
     * Xem thông tin chi tiết của một giao dịch cụ thể kèm danh sách phân bổ chi
     * phí.
     *
     * @param operatorId    ID thành viên gọi yêu cầu
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần tra cứu
     * @return Chi tiết giao dịch và danh sách người tham gia phân bổ
     */
    GroupTransactionDetailRes detail(UUID operatorId, UUID groupId, UUID transactionId);

    /**
     * Xem danh sách người tham gia phân bổ chi phí của một giao dịch cụ thể.
     *
     * @param operatorId    ID thành viên gọi yêu cầu
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần tra cứu
     * @return Danh sách người tham gia phân bổ chi phí
     */
    List<GroupTransactionParticipantRes> getParticipants(UUID operatorId, UUID groupId, UUID transactionId);

    /**
     * Cập nhật thông tin giao dịch nhóm thông thường (EXPENSE hoặc CONTRIBUTION).
     * <p>
     * <b>Quy tắc phân quyền:</b> Chỉ người tạo giao dịch (creator) hoặc Trưởng nhóm
     * (OWNER) mới có quyền sửa.
     * <br>
     * <b>Tác động số dư:</b>
     * <ul>
     * <li>Nếu giao dịch cũ đã {@code CONFIRMED}: Hoàn tác số tiền cũ khỏi quỹ.</li>
     * <li>Nếu người sửa là Owner/Thủ quỹ: Giao dịch giữ trạng thái
     * {@code CONFIRMED} và áp dụng số tiền mới vào quỹ.</li>
     * <li>Nếu người sửa là thành viên thường: Giao dịch chuyển về {@code PENDING}
     * chờ duyệt lại.</li>
     * </ul>
     * </p>
     *
     * @param operatorId    ID người thực hiện chỉnh sửa
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần cập nhật
     * @param req           Dữ liệu cập nhật mới
     * @return Chi tiết giao dịch sau khi cập nhật
     */
    GroupTransactionDetailRes update(UUID operatorId, UUID groupId, UUID transactionId,
                                     GroupTransactionUpdateReq req);

    /**
     * Xóa mềm (soft-delete) một giao dịch nhóm bằng cách đánh dấu thời điểm
     * {@code deletedAt}.
     * <p>
     * <b>Phân quyền:</b> Chỉ Trưởng nhóm (OWNER).
     * <br>
     * <b>Hoàn tác quỹ:</b> Nếu giao dịch đã {@code CONFIRMED}, số tiền sẽ được tự
     * động hoàn tác (reversal) vào quỹ nhóm.
     * </p>
     *
     * @param operatorId    ID người thực hiện xóa (phải là OWNER)
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần xóa
     */
    void delete(UUID operatorId, UUID groupId, UUID transactionId);

    /**
     * Đếm tổng số giao dịch đang ở trạng thái chờ duyệt (PENDING) của nhóm.
     *
     * @param groupId ID nhóm
     * @return Số lượng giao dịch PENDING chưa xóa
     */
    long countPendingForGroup(UUID groupId);

    /**
     * Tổng tiền các giao dịch đã xác nhận ({@code CONFIRMED}, chưa xóa) theo loại, trên toàn thời gian.
     * <p>Không kiểm tra quyền — nơi gọi phải xác thực thành viên trước.</p>
     *
     * @param groupId ID nhóm
     * @param type    loại giao dịch cần cộng
     * @return Tổng tiền, 0 nếu chưa có giao dịch nào
     */
    long sumConfirmedAmount(UUID groupId, GTransactionType type);

    /**
     * Như {@link #sumConfirmedAmount(UUID, GTransactionType)} nhưng chỉ tính giao dịch phát sinh trong khoảng
     * {@code [from, to)}.
     *
     * @param groupId ID nhóm
     * @param type    loại giao dịch cần cộng
     * @param from    mốc bắt đầu (tính cả mốc này)
     * @param to      mốc kết thúc (không tính mốc này)
     * @return Tổng tiền, 0 nếu trong kỳ chưa có giao dịch nào
     */
    long sumConfirmedAmount(UUID groupId, GTransactionType type, Instant from, Instant to);

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
