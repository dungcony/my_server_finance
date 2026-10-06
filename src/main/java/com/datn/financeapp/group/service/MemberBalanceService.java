package com.datn.financeapp.group.service;

import com.datn.financeapp.group.helper.MemberBalances;

import java.util.Collection;
import java.util.UUID;

/**
 * Service quản lý bảng tổng hợp số dư thành viên nhóm ({@code group_member_balances}).
 * <p>
 * Các hàm trong interface:
 * <ul>
 *   <li>{@link #getBalances(UUID)}: Đọc số dư của cả nhóm, dùng cho báo cáo. Input: groupId. Output: MemberBalances.</li>
 *   <li>{@link #getBalancesForUpdate(UUID, Collection)}: Đọc số dư kèm khoá dòng, dùng khi kiểm hạn mức hoàn tiền. Input: groupId, userIds. Output: MemberBalances.</li>
 *   <li>{@link #applyDelta(UUID, MemberBalances, MemberBalances)}: Ghi phần chênh lệch giữa trước và sau một lần ghi giao dịch. Input: groupId, before, after. Output: void.</li>
 *   <li>{@link #lockGroupForBalanceChange(UUID)}: Khoá nhóm để các thao tác làm đổi số dư chạy lần lượt. Input: groupId. Output: void.</li>
 * </ul>
 * </p>
 */
public interface MemberBalanceService {

    /**
     * Đọc số dư của mọi thành viên có giao dịch được tính trong nhóm.
     *
     * @param groupId ID nhóm
     * @return Số dư theo từng thành viên; thành viên vắng mặt coi như mọi chỉ số bằng 0
     */
    MemberBalances getBalances(UUID groupId);

    /**
     * Đọc số dư của các thành viên chỉ định và khoá các dòng đó tới hết transaction hiện tại.
     *
     * @param groupId ID nhóm
     * @param userIds Các thành viên cần đọc
     * @return Số dư theo từng thành viên được chỉ định
     */
    MemberBalances getBalancesForUpdate(UUID groupId, Collection<UUID> userIds);

    /**
     * Cộng phần chênh lệch {@code after - before} vào bảng tổng hợp, theo thứ tự {@code user_id} cố định
     * và bỏ qua thành viên không có chênh lệch.
     *
     * @param groupId ID nhóm
     * @param before  Ảnh hưởng của giao dịch trước khi ghi
     * @param after   Ảnh hưởng của giao dịch sau khi ghi
     */
    void applyDelta(UUID groupId, MemberBalances before, MemberBalances after);

    /**
     * Khoá dòng quỹ của nhóm tới hết transaction hiện tại để mọi thao tác ghi làm đổi số dư
     * trong cùng nhóm chạy lần lượt. Nhóm không có quỹ thì bỏ qua.
     *
     * @param groupId ID nhóm
     */
    void lockGroupForBalanceChange(UUID groupId);
}
