package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.report.GroupSummaryReportRes;
import com.datn.financeapp.group.dto.wallet.GroupWalletRes;
import com.datn.financeapp.group.entity.Group;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.entity.GroupWallet;
import com.datn.financeapp.group.enums.GroupStatus;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.GroupWalletRepository;
import com.datn.financeapp.group.service.GroupBalanceCalculator;
import com.datn.financeapp.group.service.GroupBalanceCalculator.MemberBalances;
import com.datn.financeapp.group.service.GroupReportService;
import com.datn.financeapp.user.entity.User;
import com.datn.financeapp.user.repository.UserRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupReportServiceImpl implements GroupReportService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupWalletRepository groupWalletRepository;
    private final GroupTransactionRepository groupTransactionRepository;
    private final UserRepository userRepository;
    private final GroupBalanceCalculator balanceCalculator;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public GroupSummaryReportRes getSummary(UUID userId, UUID groupId, String month) {
        verifyGroupMember(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        YearMonth ym;
        if (month != null && !month.isBlank()) {
            ym = YearMonth.parse(month.trim());
        } else {
            ym = YearMonth.now(VN_ZONE);
        }
        String period = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        Instant fromTime = ym.atDay(1).atStartOfDay(VN_ZONE).toInstant();
        Instant toTime = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

        Long totalExpense = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                groupId, GroupTransactionType.EXPENSE, fromTime, toTime);
        Long totalContribution = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                groupId, GroupTransactionType.CONTRIBUTION, fromTime, toTime);

        GroupWalletRes fundRes = mapToWalletRes(wallet);

        return new GroupSummaryReportRes(
                group.getId(),
                group.getName(),
                group.getTarget(),
                period,
                fundRes,
                totalExpense != null ? totalExpense : 0L,
                totalContribution != null ? totalContribution : 0L
        );
    }

    @Override
    public GroupBalanceReportRes getBalances(UUID userId, UUID groupId) {
        verifyGroupMember(groupId, userId);
        Group group = findActiveGroup(groupId);
        GroupWallet wallet = getActiveWallet(groupId);

        MemberBalances mb = balanceCalculator.calculateBalances(groupId, null);
        Map<UUID, Long> contributedMap = mb.getContributedMap();
        Map<UUID, Long> paidOutOfPocketMap = mb.getPaidOutOfPocketMap();
        Map<UUID, Long> refundedMap = mb.getRefundedMap();
        Map<UUID, Long> withdrawnMap = mb.getWithdrawnMap();
        Map<UUID, Long> shareMap = mb.getShareMap();
        Map<UUID, Long> netBalanceMap = mb.getNetBalanceMap();

        List<GroupMember> allMembers = groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(groupId);
        List<GroupMember> activeMembers = allMembers.stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.getUserId().toString()))
                .toList();

        // Danh sách thành viên cần hiển thị:
        // ACTIVE luôn hiển thị; LEFT/REMOVED chỉ hiển thị khi net_balance != 0
        List<GroupMember> displayMembers = new ArrayList<>();
        displayMembers.addAll(activeMembers);

        for (GroupMember m : allMembers) {
            if (m.getStatus() == MemberStatus.LEFT || m.getStatus() == MemberStatus.REMOVED) {
                long net = netBalanceMap.getOrDefault(m.getUserId(), 0L);
                if (net != 0L) {
                    displayMembers.add(m);
                }
            }
        }

        List<UUID> displayUserIds = displayMembers.stream().map(GroupMember::getUserId).distinct().toList();
        Map<UUID, String> userNames = new HashMap<>();
        if (!displayUserIds.isEmpty()) {
            List<User> users = userRepository.findAllById(displayUserIds);
            for (User u : users) {
                userNames.put(u.getId(), buildUserDisplayName(u));
            }
        }

        boolean isSettlement = Boolean.TRUE.equals(group.getIsSettlementEnabled());
        Long target = group.getTarget();

        // Tính target quota cho từng thành viên ACTIVE nếu có target
        Map<UUID, Long> targetQuotaMap = new HashMap<>();
        if (isSettlement && target != null && target > 0 && !activeMembers.isEmpty()) {
            int n = activeMembers.size();
            long base = target / n;
            long rem = target % n;
            for (int i = 0; i < n; i++) {
                long quota = base + (i < rem ? 1L : 0L);
                targetQuotaMap.put(activeMembers.get(i).getUserId(), quota);
            }
        }

        List<GroupBalanceItemRes> balances = new ArrayList<>();
        long totalNeeded = 0L;

        for (GroupMember m : displayMembers) {
            UUID uid = m.getUserId();
            String name = userNames.getOrDefault(uid, "Thành viên " + uid.toString().substring(0, 8));
            long contributed = contributedMap.getOrDefault(uid, 0L);

            if (!isSettlement) {
                // Tắt tính thừa thiếu: chỉ hiển thị total_contributed
                balances.add(new GroupBalanceItemRes(
                        uid,
                        name,
                        m.getStatus().name(),
                        contributed,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                long outOfPocket = paidOutOfPocketMap.getOrDefault(uid, 0L);
                long refunded = refundedMap.getOrDefault(uid, 0L);
                long withdrawn = withdrawnMap.getOrDefault(uid, 0L);
                long share = shareMap.getOrDefault(uid, 0L);
                long net = netBalanceMap.getOrDefault(uid, 0L);

                long needed;
                if (m.getStatus() == MemberStatus.ACTIVE) {
                    if (target != null && target > 0) {
                        long quota = targetQuotaMap.getOrDefault(uid, 0L);
                        needed = Math.max(0L, quota - contributed);
                    } else {
                        needed = net < 0 ? Math.abs(net) : 0L;
                    }
                } else {
                    // Cựu thành viên: needed = |net| nếu net < 0, ngược lại 0
                    needed = net < 0 ? Math.abs(net) : 0L;
                }

                totalNeeded += needed;

                balances.add(new GroupBalanceItemRes(
                        uid,
                        name,
                        m.getStatus().name(),
                        contributed,
                        outOfPocket,
                        refunded,
                        withdrawn,
                        share,
                        net,
                        needed
                ));
            }
        }

        Long totalNeededRes = isSettlement ? totalNeeded : null;

        return new GroupBalanceReportRes(
                group.getId(),
                target,
                wallet.getCurrentBalance(),
                totalNeededRes,
                isSettlement,
                balances
        );
    }

    private String buildUserDisplayName(User u) {
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (last + " " + first).trim();
        if (!full.isBlank()) {
            return full;
        }
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            return u.getEmail();
        }
        return "Người dùng " + u.getId().toString().substring(0, 8);
    }

    private Group findActiveGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .filter(g -> g.getStatus() != GroupStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private GroupWallet getActiveWallet(UUID groupId) {
        return groupWalletRepository.findFirstByGroupIdAndStatus(groupId, GroupWalletStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND));
    }

    private void verifyGroupMember(UUID groupId, UUID userId) {
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserIdAndStatus(
                groupId, userId, MemberStatus.ACTIVE
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_GROUP_MEMBER);
        }
    }

    private GroupWalletRes mapToWalletRes(GroupWallet wallet) {
        return new GroupWalletRes(
                wallet.getId(),
                wallet.getGroupId(),
                wallet.getHeldByUserId(),
                wallet.getName(),
                0L,
                wallet.getCurrentBalance(),
                wallet.getStatus(),
                wallet.getCreatedAt()
        );
    }
}

