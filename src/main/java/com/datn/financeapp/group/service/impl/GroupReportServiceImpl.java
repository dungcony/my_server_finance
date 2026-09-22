package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.dto.response.wallet.GroupWalletRes;
import com.datn.financeapp.group.entity.GroupMember;
import com.datn.financeapp.group.enums.GroupTransactionType;
import com.datn.financeapp.group.enums.GroupWalletStatus;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupMemberRepository;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.GroupBalanceService;
import com.datn.financeapp.group.service.GroupReportService;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
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

    private final GroupService groupService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupTransactionRepository groupTransactionRepository;
    private final UserRepository userRepository;
    private final GroupBalanceService balanceService;
    private final GroupPermissionValidator permissionValidator;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public GroupSummaryReportRes getSummary(UUID userId, UUID groupId, String month) {
        permissionValidator.verifyActiveMember(groupId, userId);
        GroupDetailRes group = groupService.findById(groupId);
        GroupWalletRes fundRes = group.fund();
        if (fundRes == null || fundRes.status() != GroupWalletStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND);
        }

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

        return new GroupSummaryReportRes(
                group.id(),
                group.name(),
                group.target(),
                period,
                fundRes,
                totalExpense != null ? totalExpense : 0L,
                totalContribution != null ? totalContribution : 0L
        );
    }

    @Override
    public GroupBalanceReportRes getBalances(UUID userId, UUID groupId) {
        permissionValidator.verifyActiveMember(groupId, userId);
        GroupDetailRes group = groupService.findById(groupId);
        GroupWalletRes fund = group.fund();
        if (fund == null || fund.status() != GroupWalletStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_WALLET_NOT_FOUND);
        }

        MemberBalances mb = balanceService.calculateBalances(groupId, null);

        List<GroupMember> allMembers = groupMemberRepository.findByGroupIdOrderByJoinedAtDesc(groupId);
        List<GroupMember> activeMembers = allMembers.stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.getUserId().toString()))
                .toList();

        // Danh sách thành viên cần hiển thị:
        // ACTIVE luôn hiển thị; LEFT/REMOVED chỉ hiển thị khi net_balance != 0
        List<GroupMember> displayMembers = new ArrayList<>(activeMembers);

        for (GroupMember m : allMembers) {
            if (m.getStatus() == MemberStatus.LEFT || m.getStatus() == MemberStatus.REMOVED) {
                long net = mb.getNetBalance(m.getUserId());
                if (net != 0L) {
                    displayMembers.add(m);
                }
            }
        }

        // Batch load profile user cho các member cần hiển thị
        List<UUID> displayUserIds = displayMembers.stream().map(GroupMember::getUserId).toList();
        Map<UUID, String> userNames = new HashMap<>();
        if (!displayUserIds.isEmpty()) {
            List<User> users = userRepository.findAllById(displayUserIds);
            for (User u : users) {
                userNames.put(u.getId(), buildUserDisplayName(u));
            }
        }

        boolean isSettlement = Boolean.TRUE.equals(group.isSettlementEnabled());
        Long target = group.target();

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

            MemberBalanceAccumulator b = mb.get(uid);
            if (b == null) {
                long needed = 0L;
                if (m.getStatus() == MemberStatus.ACTIVE) {
                    if (target != null && target > 0) {
                        needed = targetQuotaMap.getOrDefault(uid, 0L);
                    }
                }
                totalNeeded += needed;

                balances.add(new GroupBalanceItemRes(
                        uid,
                        name,
                        m.getStatus().name(),
                        0L, 0L, 0L, 0L, 0L, 0L,
                        needed
                ));
            } else {
                long contributed = b.getRawContribution();
                long outOfPocket = b.getPaidOutOfPocket();
                long refunded = b.getRefunded();
                long withdrawn = b.getWithdrawn();
                long share = b.getShare();
                long net = b.getNetBalance();

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
                group.id(),
                target,
                fund.currentBalance(),
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
}
