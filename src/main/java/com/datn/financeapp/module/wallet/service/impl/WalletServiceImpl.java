package com.datn.financeapp.module.wallet.service.impl;

import com.datn.financeapp.module.wallet.service.WalletService;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.wallet.dto.request.CreateWalletRequest;
import com.datn.financeapp.module.wallet.dto.request.UpdateWalletRequest;
import com.datn.financeapp.module.wallet.dto.response.WalletDetailResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletRawBalanceResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletStatsResponse;
import com.datn.financeapp.module.wallet.dto.response.WalletSummaryResponse;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.mapper.WalletMapper;
import com.datn.financeapp.module.wallet.repository.WalletRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic CRUD ví (WALLET-01..05, api/02-VI.md mục 1-7). Mọi truy vấn chọn/sửa/xoá MỘT
 * ví theo id đi qua {@link WalletRepository} với điều kiện quyền D-27 sẵn có trong query —
 * không có quyền trả {@code NOT_FOUND} (404), không phải 403 (T-02-03).
 * <p>
 * {@code @Transactional} đặt TRÊN TỪNG PUBLIC METHOD theo mẫu {@code AuthService}.
 */
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    // Tên ví cấp sẵn cho mọi tài khoản mới. Hiện ra trước mắt người dùng nên để tiếng Việt.
    private static final String DEFAULT_WALLET_NAME = "Tiền mặt";

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;
    private final WalletMapper walletMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID userId, String type, Boolean onlyInTotal, boolean includeShared) {
        return walletRepository.findAllForUser(userId, type, onlyInTotal, includeShared).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void createDefaultCashWallet(UUID userId, Instant createdAt) {
        Wallet cashWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(DEFAULT_WALLET_NAME)
                .type("cash")
                .initialBalance(0L)
                .currentBalance(0L)
                .includeInTotal(true)
                .isDefault(true)
                .isDeleted(false)
                .createdAt(createdAt)
                .build();
        walletRepository.save(cashWallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public UUID findDefaultWalletId(UUID userId) {
        return walletRepository
                .findDefaultWalletId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_FOUND));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public WalletRefResponse findRefForUser(UUID userId, UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository
                .findByIdForUser(walletId, userId)
                .map(walletMapper::toRef)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<WalletRawBalanceResponse> listWithRawBalance(UUID userId, boolean includeShared) {
        return walletRepository.findAllForUser(userId, null, null, includeShared).stream()
                .map(w -> new WalletRawBalanceResponse(
                        w.getId(), w.getName(), w.getType(), w.getCurrentBalance()))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, WalletRefResponse> findRefsForUser(Collection<UUID> walletIds, UUID userId) {
        Map<UUID, WalletRefResponse> byId = new HashMap<>();
        for (UUID id : walletIds.stream().filter(Objects::nonNull).distinct().toList()) {
            WalletRefResponse ref = findRefForUser(userId, id);
            if (ref != null) {
                byId.put(id, ref);
            }
        }
        return byId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long requireRawBalance(UUID walletId) {
        return walletRepository.findCurrentBalanceNative(walletId).orElseThrow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void lockWalletsInOrder(Set<UUID> walletIds, UUID userId) {
        List<UUID> sortedIds = new ArrayList<>(walletIds);
        sortedIds.sort(UUID::compareTo);
        for (UUID walletId : sortedIds) {
            Wallet wallet = walletRepository
                    .findByIdForUpdate(walletId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
            if (!userId.equals(wallet.getUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void adjustBalance(UUID walletId, long delta) {
        walletRepository.adjustBalance(walletId, delta);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Long findRawBalance(UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository.findCurrentBalanceNative(walletId).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public WalletRefResponse findRefById(UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletRepository
                .findById(walletId)
                .map(walletMapper::toRef)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public long countActiveWallets(UUID userId) {
        return walletRepository.countByUserIdAndIsDeletedFalse(userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest req) {
        Map<String, Object> extra = req.extraFields();
        if (extra.containsKey("current_balance") || extra.containsKey("currentBalance")
                || extra.containsKey("type")) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_NOT_EDITABLE);
        }

        Wallet wallet = walletRepository
                .findByIdForUser(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (req.name() != null) {
            if (!req.name().equalsIgnoreCase(wallet.getName())
                    && walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, req.name())) {
                throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);
            }
            wallet.setName(req.name());
        }
        if (req.includeInTotal() != null) {
            wallet.setIncludeInTotal(req.includeInTotal());
        }
        if (req.isDefault() != null) {
            applyDefaultChange(userId, wallet, req.isDefault());
        }
        if (req.icon() != null) {
            wallet.setIcon(req.icon());
        }
        if (req.color() != null) {
            wallet.setColor(req.color());
        }
        walletRepository.save(wallet);

        return toResponse(wallet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID walletId, boolean deleteTransactions) {
        Wallet wallet = walletRepository
                .findByIdForUserIncludingDeleted(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (Boolean.TRUE.equals(wallet.getIsDeleted())) {
            // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
            return;
        }

        // trigger ở CSDL cũng chặn, nhưng chỉ báo lỗi lúc commit nên kiểm tra trước để trả lỗi nghiệp vụ
        if (Boolean.TRUE.equals(wallet.getIsDefault())) {
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_NOT_DELETABLE);
        }

        if (walletRepository.countByUserIdAndIsDeletedFalse(userId) <= 1) {
            throw new BusinessException(ErrorCode.WALLET_LAST_NOT_DELETABLE);
        }

        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND NOT is_deleted",
                Long.class, walletId);
        boolean hasTransactions = transactionCount != null && transactionCount > 0;

        if (hasTransactions && !deleteTransactions) {
            throw new BusinessException(ErrorCode.WALLET_HAS_TRANSACTIONS);
        }

        if (hasTransactions) {
            jdbcTemplate.update(
                    "UPDATE transactions SET is_deleted = TRUE WHERE wallet_id = ?",
                    walletId);
        }

        wallet.setIsDeleted(true);
        walletRepository.save(wallet);
    }

    // đặt ví này làm mặc định thì bỏ cờ của ví cũ trước; bỏ cờ mặc định trực tiếp không được phép
    private void applyDefaultChange(UUID userId, Wallet wallet, boolean makeDefault) {
        boolean alreadyDefault = Boolean.TRUE.equals(wallet.getIsDefault());
        if (makeDefault && !alreadyDefault) {
            walletRepository.clearDefault(userId);
            wallet.setIsDefault(true);
        } else if (!makeDefault && alreadyDefault) {
            throw new BusinessException(ErrorCode.WALLET_DEFAULT_REQUIRED);
        }
    }

    private WalletStatsResponse loadStats(UUID walletId) {
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND NOT is_deleted",
                Long.class, walletId);
        Long incomeThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type = 'INCOME' "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        Long expenseThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type IN ('EXPENSE', 'LOAN') "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        LocalDate lastTransactionDate = jdbcTemplate.query(
                "SELECT MAX(date) FROM transactions WHERE wallet_id = ? AND NOT is_deleted",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                walletId);

        return new WalletStatsResponse(
                transactionCount == null ? 0 : transactionCount,
                incomeThisMonth == null ? 0 : incomeThisMonth,
                expenseThisMonth == null ? 0 : expenseThisMonth,
                lastTransactionDate);
    }

    private WalletResponse toResponse(Wallet wallet) {
        // D-37/TXN-09: cùng logic trừ ngược như detail() — currentBalance API luôn là tiền thật
        // đến hết hôm nay, projectedBalance chỉ khác NULL khi ví có giao dịch tương lai.
        long currentBalanceAsOfToday = walletRepository
                .findBalanceAsOf(wallet.getId(), LocalDate.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));
        boolean hasFuture = walletRepository.hasFutureTransactions(wallet.getId());
        Long projectedBalance = hasFuture ? wallet.getCurrentBalance() : null;

        return walletMapper.toResponse(wallet, currentBalanceAsOfToday, projectedBalance);
    }
}
