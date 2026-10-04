package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.response.fund.FundRes;
import com.datn.financeapp.group.dto.response.group.GroupDetailRes;
import com.datn.financeapp.group.dto.response.member.MemberRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceItemRes;
import com.datn.financeapp.group.dto.response.report.GroupBalanceReportRes;
import com.datn.financeapp.group.dto.response.report.GroupSummaryReportRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.enums.MemberStatus;
import com.datn.financeapp.group.helper.BalanceCalculator;
import com.datn.financeapp.group.helper.MemberBalanceAccumulator;
import com.datn.financeapp.group.helper.MemberBalances;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.GTransactionService;
import com.datn.financeapp.group.service.GroupService;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.service.ReportService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.user.dto.response.UserNameDisplayRes;
import com.datn.financeapp.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Stream;

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
public class GReportServiceImpl implements ReportService {

    private final GroupService groupService;
    private final GTransactionService gTransactionService;
    private final GroupTransactionRepository transactionRepository;
    private final MemberService memberService;
    private final UserService userService;
    private final GroupPermissionValidator permissionValidator;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public GroupSummaryReportRes getSummary(UUID operatorId, UUID groupId, String month) {
        // xác thực thành viên đang hoạt động trong nhóm
        permissionValidator.verifyMember(groupId, operatorId, true);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        FundRes fundRes = group.fund();
        if (fundRes == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
        }

        String period = null;
        long totalExpense;
        long totalContribution;

        // kiểm tra điều kiện lọc theo tháng cụ thể hoặc toàn thời gian
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseYearMonth(month);
            period = ym.format(MONTH_FORMATTER);

            Instant fromTime = ym.atDay(1).atStartOfDay(VN_ZONE).toInstant();
            Instant toTime = ym.plusMonths(1).atDay(1).atStartOfDay(VN_ZONE).toInstant();

            totalExpense = gTransactionService.sumConfirmedAmount(
                    groupId, GTransactionType.EXPENSE, fromTime, toTime);
            totalContribution = gTransactionService.sumConfirmedAmount(
                    groupId, GTransactionType.CONTRIBUTION, fromTime, toTime);
        } else {
            totalExpense = gTransactionService.sumConfirmedAmount(
                    groupId, GTransactionType.EXPENSE);
            totalContribution = gTransactionService.sumConfirmedAmount(
                    groupId, GTransactionType.CONTRIBUTION);
        }

        return new GroupSummaryReportRes(
                group.id(),
                group.name(),
                group.target(),
                period,
                fundRes,
                totalExpense,
                totalContribution);
    }

    @Override
    public GroupBalanceReportRes getBalances(UUID operatorId, UUID groupId) {
        // xác thực thành viên đang hoạt động trong nhóm
        permissionValidator.verifyMember(groupId, operatorId, true);
        GroupDetailRes group = groupService.findNotDeletedById(groupId);
        FundRes fund = group.fund();
        if (fund == null) {
            throw new BusinessException(ErrorCode.GROUP_FUND_NOT_FOUND);
        }

        // lấy tất cả thành viên ngoại trừ trạng thái chờ duyệt
        List<MemberRes> allMembers = memberService.getMembersWithStatusIn(
                groupId, List.of(MemberStatus.ACTIVE, MemberStatus.LEFT, MemberStatus.REMOVED));

        // tính số dư thu chi của từng thành viên qua truy vấn tổng hợp SQL
        List<GroupTransactionRepository.MemberBalanceProjection> projections =
                transactionRepository.aggregateMemberBalancesByGroupId(groupId);
        MemberBalances mb = toMemberBalances(projections);

        // xây dựng danh sách thành viên cần hiển thị trên báo cáo
        List<MemberRes> displayMembers = buildDisplayMembers(allMembers, mb);

        // lấy danh sách tên hiển thị của các thành viên
        List<UUID> displayUserIds = displayMembers.stream()
                .map(MemberRes::userId).toList();

        Map<UUID, UserNameDisplayRes> userNames = userService.getNames(displayUserIds);

        boolean isSettlement = Boolean.TRUE.equals(group.isSettlementEnabled());
        Long target = group.target();

        List<GroupBalanceItemRes> balances = new ArrayList<>();
        long totalNeeded = 0L;

        // xây dựng từng dòng báo cáo cân đối thành viên
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

    // phân tích chuỗi tháng an toàn và bẫy lỗi định dạng
    private YearMonth parseYearMonth(String month) {
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng tháng không hợp lệ (YYYY-MM)");
        }
    }

    // xây dựng danh sách thành viên cần hiển thị trong báo cáo
    private List<MemberRes> buildDisplayMembers(List<MemberRes> allMembers, MemberBalances mb) {
        List<MemberRes> display = new ArrayList<>(allMembers.stream()
                .filter(m -> m.status() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(m -> m.userId().toString()))
                .toList());

        // người đã rời/bị xoá chỉ hiện khi còn số dư, xếp người vào nhóm gần nhất lên trước
        allMembers.stream()
                .filter(m -> m.status() == MemberStatus.LEFT || m.status() == MemberStatus.REMOVED)
                .filter(m -> mb.getNetBalance(m.userId()) != 0L)
                .sorted(Comparator.comparing(MemberRes::joinedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(display::add);

        return display;
    }

    // xây dựng item báo cáo cân đối cho một thành viên
    private GroupBalanceItemRes buildBalanceItem(
            MemberRes m,
            MemberBalances mb,
            Map<UUID, UserNameDisplayRes> userNames) {
        UUID uid = m.userId();
        UserNameDisplayRes user = userNames.get(uid);
        String name = user != null ? user.fullName() : "Thành viên " + uid.toString().substring(0, 8);

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

    // chuyển đổi kết quả tổng hợp SQL sang đối tượng cân đối thành viên
    private MemberBalances toMemberBalances(List<GroupTransactionRepository.MemberBalanceProjection> projections) {
        Map<UUID, MemberBalanceAccumulator> memberMap = new HashMap<>();
        for (var p : projections) {
            MemberBalanceAccumulator acc = new MemberBalanceAccumulator();
            acc.addPaidOutOfPocket(p.getPaidOutOfPocket());
            acc.addContribution(p.getContribution());
            acc.addRefund(p.getRefund());
            acc.addShare(p.getShare());
            memberMap.put(p.getUserId(), acc);
        }
        return new MemberBalances(memberMap);
    }
}
