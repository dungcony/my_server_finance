package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletStatsResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletSummaryResponse;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletBehavierImpl implements WalletBehavierService {
    // Tên ví cấp sẵn cho mọi tài khoản mới. Hiện ra trước mắt người dùng nên để tiếng Việt.
    private static final String DEFAULT_WALLET_NAME = "Tiền mặt";

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WalletMapper walletMapper;

    @Override
    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID userId, String type, Boolean onlyInTotal, boolean includeShared) {
        return walletRepository.findAllForUser(userId, type, onlyInTotal, includeShared).stream()
                .map(this::toResponse)
                .toList();
    }


    @Override
    @Transactional
    public WalletResponse create(UUID userId, CreateWalletRequest req) {
        if (walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, req.name())) {
            throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);
        }

        if (req.groupId() != null) {
            // Group thuộc Phase 5 — chưa tồn tại entity/nghiệp vụ kiểm tra thành viên. Ném lỗi
            // tường minh thay vì tự bịa logic kiểm tra thành viên nhóm không kiểm chứng được
            // (T-02-05).
            throw new BusinessException(ErrorCode.GROUP_MEMBER_REQUIRED, "Chưa hỗ trợ tạo ví chung ở Phase 2 — Group thuộc Phase 5.");
        }

        Instant now = Instant.now();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(req.name())
                .type(req.type())
                .initialBalance(req.initialBalance())
                .currentBalance(req.initialBalance())
                .includeInTotal(req.includeInTotal() == null ? true : req.includeInTotal())
                .icon(req.icon())
                .color(req.color())
                .isDeleted(false)
                .createdAt(now)
                .build();
        walletRepository.save(wallet);

        return toResponse(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(UUID userId) {
        Long personalTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE user_id = ? AND include_in_total AND NOT is_deleted",
                Long.class, userId);
        Long personalWalletCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE user_id = ? AND include_in_total AND NOT is_deleted",
                Long.class, userId);

        // Mô hình hai sổ ghi tách biệt (Two Separate Ledgers): ví cá nhân và quỹ nhóm là hai bảng
        // độc lập hoàn toàn. Sổ cá nhân không còn chứa ví nhóm.
        Long sharedTotal = 0L;
        Long sharedWalletCount = 0L;

        List<WalletSummaryResponse.ByTypeItem> byType = jdbcTemplate.query(
                "SELECT type, COALESCE(SUM(current_balance), 0) AS total, COUNT(*) AS wallet_count "
                        + "FROM wallets WHERE user_id = ? AND NOT is_deleted GROUP BY type",
                (rs, rowNum) -> new WalletSummaryResponse.ByTypeItem(
                        rs.getString("type"), rs.getLong("total"), rs.getLong("wallet_count")),
                userId);

        return new WalletSummaryResponse(
                personalTotal == null ? 0 : personalTotal,
                personalWalletCount == null ? 0 : personalWalletCount,
                sharedTotal == null ? 0 : sharedTotal,
                sharedWalletCount == null ? 0 : sharedWalletCount,
                byType);
    }


    @Override
    @Transactional(readOnly = true)
    public WalletDetailResponse detail(UUID userId, UUID walletId) {
        Wallet wallet = walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        WalletStatsResponse stats = loadStats(wallet.getId());

        // D-37/TXN-09: currentBalance API = tiền thật đến hết hôm nay (trừ ngược giao dịch
        // tương lai) — KHÔNG map thẳng cột wallet.getCurrentBalance() (đã gồm cả tương lai).
        long currentBalanceAsOfToday = walletRepository
                .findBalanceAsOf(wallet.getId(), LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
        boolean hasFuture = walletRepository.hasFutureTransactions(wallet.getId());
        Long projectedBalance = hasFuture ? wallet.getCurrentBalance() : null;

        return new WalletDetailResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getType(),
                wallet.getInitialBalance(),
                currentBalanceAsOfToday,
                wallet.getIncludeInTotal(),
                Boolean.TRUE.equals(wallet.getIsDefault()),
                false,
                null,
                wallet.getIcon(),
                wallet.getColor(),
                stats,
                wallet.getCreatedAt(),
                projectedBalance);
    }


}
