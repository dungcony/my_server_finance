package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.FundRepository;
import com.datn.financeapp.group.repository.MemberBalanceRepository;
import com.datn.financeapp.group.service.MemberBalanceService;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai {@link MemberBalanceService}: đọc và cộng dồn bảng tổng hợp số dư thành viên.
 * <p>
 * Các hàm trong class:
 * <ul>
 *   <li>{@link #getBalances(UUID)}: Đọc số dư cả nhóm.</li>
 *   <li>{@link #getBalancesForUpdate(UUID, Collection)}: Đọc số dư kèm khoá dòng.</li>
 *   <li>{@link #applyDelta(UUID, MemberBalances, MemberBalances)}: Ghi phần chênh lệch theo thứ tự {@code user_id}.</li>
 *   <li>{@link #lockGroupForBalanceChange(UUID)}: Khoá dòng quỹ của nhóm.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MemberBalanceServiceImpl implements MemberBalanceService {

    private final MemberBalanceRepository memberBalanceRepository;
    private final FundRepository fundRepository;

    @Override
    @Transactional(readOnly = true)
    public MemberBalances getBalances(UUID groupId) {
        return MemberBalances.from(memberBalanceRepository.findByGroupId(groupId));
    }

    @Override
    @Transactional
    public MemberBalances getBalancesForUpdate(UUID groupId, Collection<UUID> userIds) {
        return MemberBalances.from(memberBalanceRepository.findForUpdate(groupId, userIds));
    }

    @Override
    @Transactional
    public void applyDelta(UUID groupId, MemberBalances before, MemberBalances after) {
        // sắp xếp theo thứ tự user_id cố định để chống deadlock
        Map<UUID, MemberBalanceAccumulator> deltas = new TreeMap<>(after.minus(before).members());

        // nếu không có thành viên nào thay đổi số dư thì không cần gọi repository
        boolean hasChange = deltas.values().stream().anyMatch(d -> !d.isZero());
        if (!hasChange) {
            return;
        }

        memberBalanceRepository.batchAddDelta(groupId, deltas);
    }

    @Override
    @Transactional
    public void lockGroupForBalanceChange(UUID groupId) {
        // khoá dòng quỹ của nhóm, giữ tới khi transaction của lệnh ghi commit
        fundRepository.findByGroupIdForUpdate(groupId);
    }
}
