package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.helper.GroupBalanceCalculator;
import com.datn.financeapp.group.helper.GroupMemberPeriod;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.GroupBalanceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBalanceServiceImpl implements GroupBalanceService {

    private final GroupTransactionRepository transactionRepository;
    private final GroupTransactionParticipantRepository participantRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupBalanceCalculator balanceCalculator;

    @Override
    public MemberBalances calculateBalances(UUID groupId, UUID excludeTxnId) {
        // 1. Lấy toàn bộ giao dịch CONFIRMED chưa xóa
        List<GroupTransaction> allTxns = transactionRepository
                .findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                        groupId, GroupTransactionStatus.CONFIRMED);

        // Lọc bỏ giao dịch loại trừ nếu có
        List<GroupTransaction> txns = (excludeTxnId == null)
                ? allTxns
                : allTxns.stream().filter(t -> !t.getId().equals(excludeTxnId)).toList();

        // 2. Query toàn bộ lịch sử thành viên của nhóm 1 lần duy nhất để triệt tiêu N+1 Query (Mục 9)
        List<GroupMember> allMembers = memberRepository.findByGroupIdOrderByJoinedAtDesc(groupId);
        List<GroupMemberPeriod> memberPeriods = allMembers.stream()
                .map(m -> new GroupMemberPeriod(m.getUserId(), m.getJoinedAt(), m.getLeftAt()))
                .toList();

        if (txns.isEmpty()) {
            return balanceCalculator.calculateBalances(List.of(), Map.of(), memberPeriods);
        }

        // 3. Batch load toàn bộ participants của tất cả transactions để triệt tiêu lỗi N+1 query
        List<UUID> txnIds = txns.stream().map(GroupTransaction::getId).toList();
        List<GroupTransactionParticipant> allParticipants = participantRepository.findByGroupTransactionIdIn(txnIds);
        Map<UUID, List<GroupTransactionParticipant>> participantsByTxnId = allParticipants.stream()
                .collect(Collectors.groupingBy(GroupTransactionParticipant::getGroupTransactionId));

        // 4. Ủy quyền cho Calculator thực hiện tính toán thuần túy in-memory (pure calculation)
        return balanceCalculator.calculateBalances(txns, participantsByTxnId, memberPeriods);
    }

    @Override
    public long getRemainingContribution(UUID groupId, UUID userId, UUID excludeTxnId) {
        MemberBalances mb = calculateBalances(groupId, excludeTxnId);
        return mb.getRemainingContribution(userId);
    }

    @Override
    public long getNetBalance(UUID groupId, UUID userId, UUID excludeTxnId) {
        MemberBalances mb = calculateBalances(groupId, excludeTxnId);
        return mb.getNetBalance(userId);
    }
}
