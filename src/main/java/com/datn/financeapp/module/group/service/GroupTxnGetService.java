package com.datn.financeapp.module.group.service;

import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnListRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service công khai (Public API) quản lý giao dịch tài chính nhóm (Group
 * Transaction).
 * <p>
 * <b>Phạm vi trách nhiệm:</b>
 * <ul>
 * </ul>
 * </p>
 */
public interface GroupTxnGetService {


    /**
     * Lọc và phân trang toàn bộ danh sách giao dịch (dánh cho owner và thủ quỹ)
     *
     * @param groupId ID nhóm
     * @return Danh sách giao dịch thỏa mãn điều kiện kèm metadata phân trang
     */
    GroupTxnListRes all(UUID groupId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter, Pageable pageable);

    /**
     * Lọc và phân trang danh sách lịch sử giao dịch của nhóm mà ai cũng xem được (chỉ xem được confirm)
     *
     * @param groupId ID nhóm
     * @return Danh sách giao dịch thỏa mãn điều kiện kèm metadata phân trang
     */
    GroupTxnListRes generalList(UUID groupId, GroupTxnFilterReq filter, Pageable pageable);

    /**
     * Danh sách giao dịch do chính người gọi tạo theo status
     *
     * @param statuses Filter status
     * @param groupId  ID nhóm
     * @param filter   Tiêu chí lọc
     * @return Danh sách giao dịch của mình kèm metadata phân trang
     */
    GroupTxnListRes myList(UUID groupId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter, Pageable pageable);

    /**
     * Xem thông tin chi tiết của một giao dịch cụ thể kèm danh sách phân bổ chi
     * phí.
     *
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần tra cứu
     * @return Chi tiết giao dịch và danh sách người tham gia phân bổ
     */
    GroupTxnDetailRes detail(UUID groupId, UUID transactionId);

    /**
     * Xem danh sách người tham gia phân bổ chi phí của một giao dịch cụ thể.
     *
     * @param groupId       ID nhóm
     * @param transactionId ID giao dịch cần tra cứu
     * @return Danh sách người tham gia phân bổ chi phí
     */
    List<GroupTxnParticipantRes> getParticipants(UUID groupId, UUID transactionId);


    /**
     * Đếm tổng số giao dịch đang ở trạng thái chờ duyệt (PENDING) của nhóm.
     *
     * @param groupId ID nhóm
     * @return Số lượng giao dịch PENDING chưa xóa
     */
    long countPendingForGroup(UUID groupId);

    /**
     * Đếm giao dịch chưa xử lý xong của nhóm: đang chờ thủ quỹ/chủ nhóm duyệt ({@code PENDING}).
     * Dùng để chặn lưu trữ, xoá nhóm và kiểm kê.
     *
     * @param groupId ID nhóm
     * @return Số lượng giao dịch {@code PENDING} chưa xoá
     */
    long countUnresolvedForGroup(UUID groupId);

    /**
     * Tổng tiền các giao dịch đã xác nhận ({@code CONFIRMED}, chưa xóa) theo loại, trên toàn thời gian.
     * <p>Không kiểm tra quyền — nơi gọi phải xác thực thành viên trước.</p>
     *
     * @param groupId ID nhóm
     * @param type    loại giao dịch cần cộng
     * @return Tổng tiền, 0 nếu chưa có giao dịch nào
     */
    long sumConfirmedAmount(UUID groupId, GroupTxnType type);

    /**
     * Như {@link #sumConfirmedAmount(UUID, GroupTxnType)} nhưng chỉ tính giao dịch phát sinh trong khoảng
     * {@code [from, to)}.
     *
     * @param groupId ID nhóm
     * @param type    loại giao dịch cần cộng
     * @param from    mốc bắt đầu (tính cả mốc này)
     * @param to      mốc kết thúc (không tính mốc này)
     * @return Tổng tiền, 0 nếu trong kỳ chưa có giao dịch nào
     */
    long sumConfirmedAmount(UUID groupId, GroupTxnType type, Instant from, Instant to);
}
