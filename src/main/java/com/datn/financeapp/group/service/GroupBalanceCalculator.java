package com.datn.financeapp.group.service;

import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupTransaction;
import com.datn.financeapp.group.entity.GroupTransactionParticipant;
import com.datn.financeapp.group.enums.GroupTransactionStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.MoneySource;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupTransactionParticipantRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupBalanceCalculator {

    private final GroupTransactionRepository transactionRepository;
    private final GroupTransactionParticipantRepository participantRepository;
    private final GroupMemberRepository memberRepository;

    @Getter
    @Builder
    public static class MemberBalances {
        private final Map<UUID, Long> contributedMap;       // Σ CONTRIBUTION - Σ WITHDRAWAL
        private final Map<UUID, Long> paidOutOfPocketMap;   // Σ EXPENSE (PERSONAL)
        private final Map<UUID, Long> refundedMap;          // Σ REFUND
        private final Map<UUID, Long> withdrawnMap;         // Σ WITHDRAWAL
        private final Map<UUID, Long> shareMap;             // Σ phần phải chịu (chi tiêu + thiếu) - phần hưởng (thừa)
        private final Map<UUID, Long> netBalanceMap;        // phần của mỗi người trong quỹ
    }

    /**
     * Tính toán bảng cân đối cho nhóm, chỉ tính các giao dịch CONFIRMED và chưa xóa.
     * @param groupId ID nhóm
     * @param excludeTxnId Có thể truyền ID giao dịch cần loại trừ (ví dụ khi đang sửa giao dịch)
     */
    public MemberBalances calculateBalances(UUID groupId, UUID excludeTxnId) {
        List<GroupTransaction> txns = transactionRepository
                .findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                        groupId, GroupTransactionStatus.CONFIRMED);

        Map<UUID, Long> rawContribution = new HashMap<>();
        Map<UUID, Long> paidOutOfPocket = new HashMap<>();
        Map<UUID, Long> refunded = new HashMap<>();
        Map<UUID, Long> withdrawn = new HashMap<>();
        Map<UUID, Long> share = new HashMap<>();

        for (GroupTransaction txn : txns) {
            if (excludeTxnId != null && txn.getId().equals(excludeTxnId)) {
                continue;
            }

            GroupTransactionType type = txn.getType();
            long amount = txn.getAmount();
            UUID uid = txn.getUserId();

            switch (type) {
                case CONTRIBUTION -> rawContribution.merge(uid, amount, Long::sum);
                case EXPENSE -> {
                    if (txn.getMoneySource() == MoneySource.PERSONAL) {
                        paidOutOfPocket.merge(uid, amount, Long::sum);
                    }
                    distributeShare(groupId, txn, amount, share, false);
                }
                case REFUND -> refunded.merge(uid, amount, Long::sum);
                case WITHDRAWAL -> withdrawn.merge(uid, amount, Long::sum);
                case ADJUSTMENT_DOWN -> distributeShare(groupId, txn, amount, share, false);
                case ADJUSTMENT_UP -> distributeShare(groupId, txn, amount, share, true);
            }
        }

        Map<UUID, Long> netBalance = new HashMap<>();
        Map<UUID, Long> remainingContribution = new HashMap<>();

        Set<UUID> allUserIds = new HashSet<>();
        allUserIds.addAll(rawContribution.keySet());
        allUserIds.addAll(paidOutOfPocket.keySet());
        allUserIds.addAll(refunded.keySet());
        allUserIds.addAll(withdrawn.keySet());
        allUserIds.addAll(share.keySet());

        for (UUID uid : allUserIds) {
            long c = rawContribution.getOrDefault(uid, 0L);
            long p = paidOutOfPocket.getOrDefault(uid, 0L);
            long r = refunded.getOrDefault(uid, 0L);
            long w = withdrawn.getOrDefault(uid, 0L);
            long s = share.getOrDefault(uid, 0L);

            remainingContribution.put(uid, c - w);
            // Công thức bất biến: phần của X = C + P - R - W - S
            netBalance.put(uid, c + p - r - w - s);
        }

        return MemberBalances.builder()
                .contributedMap(remainingContribution)
                .paidOutOfPocketMap(paidOutOfPocket)
                .refundedMap(refunded)
                .withdrawnMap(withdrawn)
                .shareMap(share)
                .netBalanceMap(netBalance)
                .build();
    }

    public long getRemainingContribution(UUID groupId, UUID userId, UUID excludeTxnId) {
        MemberBalances mb = calculateBalances(groupId, excludeTxnId);
        return mb.getContributedMap().getOrDefault(userId, 0L);
    }

    public long getNetBalance(UUID groupId, UUID userId, UUID excludeTxnId) {
        MemberBalances mb = calculateBalances(groupId, excludeTxnId);
        return mb.getNetBalanceMap().getOrDefault(userId, 0L);
    }

    private void distributeShare(UUID groupId, GroupTransaction txn, long amount, Map<UUID, Long> shareMap, boolean isCredit) {
        List<GroupTransactionParticipant> raw = participantRepository.findByGroupTransactionId(txn.getId());
        long factor = isCredit ? -1L : 1L;

        if (!raw.isEmpty()) {
            boolean anyNull = raw.stream().anyMatch(p -> p.getShareAmount() == null);
            if (anyNull) {
                int n = raw.size();
                long base = amount / n;
                long rem = amount % n;

                List<GroupTransactionParticipant> sorted = raw.stream()
                        .sorted(Comparator.comparing(p -> p.getUserId().toString()))
                        .toList();

                for (int i = 0; i < n; i++) {
                    long portion = base + (i < rem ? 1 : 0);
                    shareMap.merge(sorted.get(i).getUserId(), portion * factor, Long::sum);
                }
            } else {
                for (GroupTransactionParticipant p : raw) {
                    shareMap.merge(p.getUserId(), p.getShareAmount() * factor, Long::sum);
                }
            }
        } else {
            // Vắng dòng: tất cả thành viên có mặt tại occurredAt
            List<UUID> memberUserIds = memberRepository.findMemberUserIdsAtOccurredAt(groupId, txn.getOccurredAt());
            if (memberUserIds.isEmpty()) {
                memberUserIds = memberRepository.findByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)
                        .stream()
                        .map(GroupMember::getUserId)
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList();
            }

            if (!memberUserIds.isEmpty()) {
                int n = memberUserIds.size();
                long base = amount / n;
                long rem = amount % n;

                List<UUID> sorted = memberUserIds.stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList();

                for (int i = 0; i < n; i++) {
                    long portion = base + (i < rem ? 1 : 0);
                    shareMap.merge(sorted.get(i), portion * factor, Long::sum);
                }
            }
        }
    }
}
