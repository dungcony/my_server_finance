package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
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
 * operatorId, treasurerUserId, type, amount, note, excludedUserIds, occurredAt.
 * Output: ID giao dịch (UUID).</li>
 * <li>{@link #countPendingForGroup}: Đếm giao dịch chờ duyệt. Input: groupId.
 * Output: số lượng (long).</li>
 * <li>{@link #sumConfirmedAmount}: Cộng tổng tiền giao dịch đã xác nhận theo loại, toàn thời gian hoặc trong một kỳ.
 * Input: groupId, type, [from, to). Output: tổng tiền (long).</li>
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
     *                                                               ({@code GROUP_TREASURER_REQUIRED})
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
}
