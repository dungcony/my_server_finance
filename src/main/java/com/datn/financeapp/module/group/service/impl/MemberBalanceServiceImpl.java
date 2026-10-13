package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.module.group.helper.MemberBalances;
import com.datn.financeapp.module.group.repository.FundRepository;
import com.datn.financeapp.module.group.repository.MemberBalanceRepository;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Triển khai {@link MemberBalanceService}: đọc và cộng dồn bảng tổng hợp số dư thành viên.
 */
@Transactional
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
    public MemberBalances getBalancesForUpdate(UUID groupId, Collection<UUID> userIds) {
        return MemberBalances.from(memberBalanceRepository.findForUpdate(groupId, userIds));
    }

    @Override
    public void applyDelta(UUID groupId, MemberBalances before, MemberBalances after) {
        // sắp xếp theo thứ tự user_id cố định để chống deadlock
        Map<UUID, MemberBalances.Balance> deltas = new TreeMap<>(after.minus(before).members());

        // nếu không có thành viên nào thay đổi số dư thì không cần gọi repository
        boolean hasChange = deltas.values().stream().anyMatch(d -> !d.isZero());
        if (!hasChange) {
            return;
        }

        memberBalanceRepository.batchAddDelta(groupId, deltas);
    }

    @Override
    public void lockGroupForBalanceChange(UUID groupId) {
        // khoá dòng quỹ của nhóm, giữ tới khi transaction của lệnh ghi commit
        fundRepository.findByGroupIdForUpdate(groupId);
    }
}
