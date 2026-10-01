package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.entity.Member;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.enums.TransactionStatus;
import com.datn.financeapp.group.enums.TransactionType;
import com.datn.financeapp.group.helper.BalanceCalculator;
import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.MemberRepository;
import com.datn.financeapp.group.service.GroupService;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Triển khai {@link ReportService} — Tổng hợp báo cáo tài chính nhóm.
 * <ul>
 * <li>{@link #getSummary}: Báo cáo tổng quan dòng tiền theo tháng hoặc toàn thời gian (tổng thu, tổng chi, số dư quỹ).</li>
 * <li>{@link #getBalances}: Bảng cân đối chi tiết từng thành viên (đóng góp, chi hộ, số dư ròng, cần đóng thêm).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final GroupService groupService;
    private final GroupTransactionRepository groupTransactionRepository;
    private final MemberRepository memberRepository;
    private final ProfileService profileService;
    private final GroupPermissionValidator permissionValidator;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public GroupSummaryReportRes getSummary(UUID operatorId, UUID groupId, String month) {
        // xác thực thành viên đang hoạt động trong nhóm
        permissionValidator.getAuthInfo(groupId, operatorId);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        FundRes fundRes = group.fund();
        if (fundRes == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
        }

        String period = null;
        Long totalExpense;
        Long totalContribution;

        // kiểm tra điều kiện lọc theo tháng cụ thể hoặc toàn thời gian
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseYearMonth(month);
            period = ym.format(MONTH_FORMATTER);

            Instant fromTime = ym.atDay(1).atStartOfDay(VN_ZONE).toInstant();
            Instant toTime = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

            totalExpense = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                    groupId, TransactionType.EXPENSE, fromTime, toTime);
            totalContribution = groupTransactionRepository.sumAmountByGroupIdAndTypeAndPeriod(
                    groupId, TransactionType.CONTRIBUTION, fromTime, toTime);
        } else {
            totalExpense = groupTransactionRepository.sumAmountByGroupIdAndType(
                    groupId, TransactionType.EXPENSE);
            totalContribution = groupTransactionRepository.sumAmountByGroupIdAndType(
                    groupId, TransactionType.CONTRIBUTION);
        }

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
        // xác thực thành viên đang hoạt động trong nhóm
        permissionValidator.getAuthInfo(groupId, operatorId);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        FundRes fund = group.fund();
        if (fund == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
        }

        // lấy danh sách toàn bộ giao dịch đã xác nhận để tính toán số dư
        List<GTransaction> allTxns = groupTransactionRepository
                .findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                        groupId, TransactionStatus.CONFIRMED);

        // lấy tất cả thành viên ngoại trừ trạng thái chờ duyệt
        List<Member> allMembers = memberRepository
                .findByGroupIdAndStatusNotOrderByJoinedAtDesc(groupId, MemberStatus.PENDING);

        // tính toán số dư thu chi của từng thành viên
        MemberBalances mb = BalanceCalculator.calculateBalances(allTxns, allMembers, null);

        // xây dựng danh sách thành viên cần hiển thị trên báo cáo
        List<Member> displayMembers = buildDisplayMembers(allMembers, mb);

        // lấy danh sách tên hiển thị của các thành viên
        List<UUID> displayUserIds = displayMembers.stream().map(Member::getUserId).toList();
        Map<UUID, String> userNames = profileService.getDisplayNames(displayUserIds);

        boolean isSettlement = Boolean.TRUE.equals(group.isSettlementEnabled());
        Long target = group.target();

        List<GroupBalanceItemRes> balances = new ArrayList<>();
        long totalNeeded = 0L;

        // xây dựng từng dòng báo cáo cân đối thành viên
        for (Member m : displayMembers) {
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

    // phân tích chuỗi tháng an toàn và bẫy lỗi định dạng
    private YearMonth parseYearMonth(String month) {
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng tháng không hợp lệ (YYYY-MM)");
        }
    }

    // xây dựng danh sách thành viên cần hiển thị trong báo cáo
    private List<Member> buildDisplayMembers(
            List<Member> allMembers,
            MemberBalances mb) {
        List<Member> activeMembers = allMembers.stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.getUserId().toString()))
                .toList();

        List<Member> display = new ArrayList<>(activeMembers);
        for (Member m : allMembers) {
            if (m.getStatus() == MemberStatus.LEFT || m.getStatus() == MemberStatus.REMOVED) {
                if (mb.getNetBalance(m.getUserId()) != 0L) {
                    display.add(m);
                }
            }
        }
        return display;
    }

    // xây dựng item báo cáo cân đối cho một thành viên
    private GroupBalanceItemRes buildBalanceItem(
            Member m,
            MemberBalances mb,
            Map<UUID, String> userNames) {
        UUID uid = m.getUserId();
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
                m.getStatus().name(),
                outOfPocket,
                refunded,
                share,
                net,
                needed);
    }
}
