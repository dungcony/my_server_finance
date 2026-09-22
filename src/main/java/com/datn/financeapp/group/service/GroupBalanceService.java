package com.datn.financeapp.group.service;

import com.datn.financeapp.group.helper.MemberBalances;
import java.util.UUID;

/**
 * Service tính toán và điều phối dữ liệu bảng cân đối tài chính cho nhóm (Group Balance Service).
 * <p>
 * Chịu trách nhiệm:
 * <ul>
 *     <li>Truy vấn dữ liệu giao dịch và nạp theo lô (batch loading) các participants để triệt tiêu lỗi N+1 Query.</li>
 *     <li>Chuyển giao dữ liệu đã chuẩn bị cho {@link com.datn.financeapp.group.helper.GroupBalanceCalculator} tính toán.</li>
 *     <li>Cung cấp số liệu đóng góp còn lại và số dư ròng của thành viên.</li>
 * </ul>
 * </p>
 */
public interface GroupBalanceService {

    /**
     * Tính toán bảng cân đối tài chính toàn diện cho các thành viên trong nhóm.
     * Chỉ tính các giao dịch CONFIRMED và chưa bị xóa mềm.
     *
     * @param groupId      ID nhóm cần tính toán
     * @param excludeTxnId ID giao dịch cần loại trừ (nếu có, dùng khi cập nhật/xóa giao dịch)
     * @return Bảng cân đối tài chính chi tiết của từng thành viên
     */
    MemberBalances calculateBalances(UUID groupId, UUID excludeTxnId);

    /**
     * Lấy số tiền đóng góp còn lại (= tổng đóng góp - tổng rút) của một thành viên trong nhóm.
     *
     * @param groupId      ID nhóm
     * @param userId       ID thành viên
     * @param excludeTxnId ID giao dịch cần loại trừ
     * @return Số tiền đóng góp còn lại
     */
    long getRemainingContribution(UUID groupId, UUID userId, UUID excludeTxnId);

    /**
     * Lấy số dư ròng (Net balance = C + P - R - W - S) của một thành viên trong nhóm.
     *
     * @param groupId      ID nhóm
     * @param userId       ID thành viên
     * @param excludeTxnId ID giao dịch cần loại trừ
     * @return Số dư ròng (dương: nhóm nợ thành viên, âm: thành viên nợ nhóm)
     */
    long getNetBalance(UUID groupId, UUID userId, UUID excludeTxnId);
}
