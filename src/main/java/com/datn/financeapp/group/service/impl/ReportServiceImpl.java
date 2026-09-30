package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.fund.GroupFundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.service.ReportService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Triển khai {@link ReportService} — Tổng hợp báo cáo tài chính nhóm.
 * <ul>
 * <li>{@link #getSummary}: Báo cáo tổng quan dòng tiền theo tháng (tổng thu,
 * tổng chi, số dư quỹ).</li>
 * <li>{@link #getBalances}: Bảng cân đối chi tiết từng thành viên (đóng góp,
 * chi hộ, số dư ròng, cần đóng thêm).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final GroupService groupService;
    private final MemberService memberService;
    private final GroupTransactionRepository groupTransactionRepository;
    private final ProfileService profileService;
    private final GroupPermissionValidator permissionValidator;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    public GroupSummaryReportRes getSummary(UUID operatorId, UUID groupId, String month) {
        permissionValidator.verifyActiveMemberInGroupActive(groupId, operatorId);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        GroupFundRes fundRes = group.fund();
        if (fundRes == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
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
                groupId, TransactionType.EXPENSE, fromTime, toTime);
        Long totalContribution = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                groupId, TransactionType.CONTRIBUTION, fromTime, toTime);

        return new GroupSummaryReportRes(
                group.id(),
                group.name(),
                group.target(),
                period,
                fundRes,
                totalExpense != null ? totalExpense : 0L,
                totalContribution != null ? totalContribution : 0L);
    }

    @Override
    public GroupBalanceReportRes getBalances(UUID operatorId, UUID groupId) {
        permissionValidator.verifyActiveMemberInGroupActive(groupId, operatorId);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        GroupFundRes fund = group.fund();
        if (fund == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
        }

        List<com.datn.financeapp.group.entity.GTransaction> allTxns = groupTransactionRepository
                .findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                        groupId, com.datn.financeapp.group.enums.TransactionStatus.CONFIRMED);

        List<UUID> allMemberIds = memberService.findIdAllMember(groupId);
        List<com.datn.financeapp.group.entity.Member> allMemberEntities = memberService.findMembers(groupId,
                allMemberIds);

        MemberBalances mb = com.datn.financeapp.group.helper.BalanceCalculator.calculateBalances(allTxns,
                allMemberEntities, null);
        List<MemberRes> allMembers = memberService.findMembers(groupId);
        List<MemberRes> activeMembers = allMembers.stream()
                .filter(m -> m.status() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.userId().toString()))
                .toList();

        List<MemberRes> displayMembers = buildDisplayMembers(allMembers, activeMembers, mb);

        List<UUID> displayUserIds = displayMembers.stream().map(MemberRes::userId).toList();
        Map<UUID, String> userNames = profileService.getDisplayNames(displayUserIds);

        boolean isSettlement = Boolean.TRUE.equals(group.isSettlementEnabled());
        Long target = group.target();

        List<GroupBalanceItemRes> balances = new ArrayList<>();
        long totalNeeded = 0L;

        for (MemberRes m : displayMembers) {
            GroupBalanceItemRes item = buildBalanceItem(m, mb, userNames);
            totalNeeded += item.neededContribution();
            balances.add(item);
        }

        Long totalNeededRes = isSettlement ? totalNeeded : null;

        return new GroupBalanceReportRes(
                group.id(),
                target,
                fund.currentBalance(),
                totalNeededRes,
                isSettlement,
                balances);
    }

    /**
     * Xây dựng danh sách thành viên cần hiển thị trong báo cáo.
     * ACTIVE luôn hiển thị; LEFT/REMOVED chỉ hiển thị khi net_balance != 0.
     */
    private List<MemberRes> buildDisplayMembers(
            List<MemberRes> allMembers,
            List<MemberRes> activeMembers,
            MemberBalances mb) {
        List<MemberRes> display = new ArrayList<>(activeMembers);
        for (MemberRes m : allMembers) {
            if (m.status() == MemberStatus.LEFT || m.status() == MemberStatus.REMOVED) {
                if (mb.getNetBalance(m.userId()) != 0L) {
                    display.add(m);
                }
            }
        }
        return display;
    }

    /**
     * Xây dựng item báo cáo cân đối cho một thành viên.
     * Công thức needed (mọi thành viên, có hay không có target): |net| nếu net < 0, ngược lại 0
     */
    private GroupBalanceItemRes buildBalanceItem(
            MemberRes m,
            MemberBalances mb,
            Map<UUID, String> userNames) {
        UUID uid = m.userId();
        String name = userNames.getOrDefault(uid, "Thành viên " + uid.toString().substring(0, 8));

        MemberBalanceAccumulator b = mb.get(uid);
        long outOfPocket = b.getPaidOutOfPocket();
        long refunded = b.getRefunded();
        long share = b.getShare();
        long net = b.getNetBalance();

        long needed = net < 0 ? Math.abs(net) : 0L;

        return new GroupBalanceItemRes(
                uid,
                name,
                m.status().name(),
                outOfPocket,
                refunded,
                share,
                net,
                needed);
    }
}
