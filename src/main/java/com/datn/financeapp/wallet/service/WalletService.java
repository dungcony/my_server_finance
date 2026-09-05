package com.datn.financeapp.wallet.service;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.wallet.dto.CreateWalletRequest;
import com.datn.financeapp.wallet.dto.ReorderWalletsRequest;
import com.datn.financeapp.wallet.dto.UpdateWalletRequest;
import com.datn.financeapp.wallet.dto.WalletDetailResponse;
import com.datn.financeapp.wallet.dto.WalletResponse;
import com.datn.financeapp.wallet.dto.WalletStatsResponse;
import com.datn.financeapp.wallet.dto.WalletSummaryResponse;
import com.datn.financeapp.wallet.entity.Wallet;
import com.datn.financeapp.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic CRUD ví (WALLET-01..05, api/02-VI.md mục 1-7). Mọi truy vấn chọn/sửa/xoá MỘT
 * ví theo id đi qua {@link WalletRepository} với điều kiện quyền D-27 sẵn có trong query —
 * không có quyền trả {@code NOT_FOUND} (404), không phải 403 (T-02-03).
 *
 * {@code @Transactional} đặt TRÊN TỪNG PUBLIC METHOD theo mẫu {@code AuthService}.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    /** Tên ví cấp sẵn cho mọi tài khoản mới. Hiện ra trước mắt người dùng nên để tiếng Việt. */
    private static final String DEFAULT_WALLET_NAME = "Tiền mặt";

    private final WalletRepository walletRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID userId, String type, Boolean onlyInTotal, boolean includeShared) {
        return walletRepository.findAllForUser(userId, type, onlyInTotal, includeShared).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * api/02-VI.md mục 2: {@code personal_total} và {@code shared_total} tính RIÊNG, KHÔNG
     * cộng đôi — nếu cộng gộp thì hai thành viên cùng nhóm sẽ cùng thấy ví chung trong tổng
     * tài sản cá nhân của mỗi người, thổi phồng tổng tài sản gia đình.
     */
    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(UUID userId) {
        Long personalTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE user_id = ? AND include_in_total AND NOT is_deleted",
                Long.class, userId);
        Long personalWalletCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets WHERE user_id = ? AND include_in_total AND NOT is_deleted",
                Long.class, userId);

        // Ví chung: user phải là thành viên active của nhóm sở hữu ví. Phase 2 chưa ai INSERT
        // vào group_members nên hai giá trị này luôn 0 — vẫn viết đúng logic để Phase 5 không
        // phải sửa lại (D-27).
        Long sharedTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(current_balance), 0) FROM wallets "
                        + "WHERE group_id IN (SELECT group_id FROM group_members WHERE user_id = ? AND is_active) "
                        + "AND include_in_total AND NOT is_deleted",
                Long.class, userId);
        Long sharedWalletCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets "
                        + "WHERE group_id IN (SELECT group_id FROM group_members WHERE user_id = ? AND is_active) "
                        + "AND include_in_total AND NOT is_deleted",
                Long.class, userId);

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
                wallet.getGroupId() != null,
                wallet.getGroupId(),
                wallet.getIcon(),
                wallet.getColor(),
                wallet.getSortOrder(),
                stats,
                wallet.getCreatedAt(),
                projectedBalance);
    }

    /**
     * api/02-VI.md mục 4. Ví mới KHÔNG sinh giao dịch nào — {@code current_balance} =
     * {@code initial_balance} là điểm xuất phát, không phải một khoản thu.
     */
    @Transactional
    public WalletResponse create(UUID userId, CreateWalletRequest req) {
        if (walletRepository.existsByUserIdAndNameIgnoreCaseAndIsDeletedFalse(userId, req.name())) {
            throw new BusinessException(ErrorCode.WALLET_NAME_EXISTS);
        }

        if (req.groupId() != null) {
            // Group thuộc Phase 5 — chưa tồn tại entity/nghiệp vụ kiểm tra thành viên. Ném lỗi
            // tường minh thay vì tự bịa logic kiểm tra thành viên nhóm không kiểm chứng được
            // (T-02-05).
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER, "Chưa hỗ trợ tạo ví chung ở Phase 2 — Group thuộc Phase 5.");
        }

        Integer maxSortOrder = walletRepository.findMaxSortOrderByUserId(userId);
        Instant now = Instant.now();

        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .groupId(null)
                .name(req.name())
                .type(req.type())
                .initialBalance(req.initialBalance())
                .currentBalance(req.initialBalance())
                .includeInTotal(req.includeInTotal() == null ? true : req.includeInTotal())
                .icon(req.icon())
                .color(req.color())
                .sortOrder((maxSortOrder == null ? -1 : maxSortOrder) + 1)
                .isDeleted(false)
                .createdAt(now)
                .build();
        walletRepository.save(wallet);

        return toResponse(wallet);
    }

    /**
     * Ví "Tiền mặt" số dư 0 cấp cho tài khoản vừa tạo — dùng chung cho cả đăng ký bằng email lẫn
     * bằng Google. Thiếu ví thì người dùng mở app lên thấy màn hình trống và không ghi được giao
     * dịch nào, nên nó phải nằm trong CÙNG transaction với việc tạo tài khoản. Gọi từ
     * {@code AuthService} vẫn giữ được điều đó vì {@code @Transactional} mặc định lan truyền kiểu
     * {@code REQUIRED} — tham gia transaction đang mở chứ không mở transaction mới.
     *
     * <p><b>Không gọi {@link #create} thay cho method này.</b> {@code create} nhận
     * {@code CreateWalletRequest} và còn kiểm trùng tên lẫn ném {@code NOT_GROUP_MEMBER} — đều
     * vô nghĩa với một tài khoản chưa có ví nào. Tách riêng để hai luồng không ràng buộc nhau.
     *
     * <p>Trước đây khối dựng ví này được chép nguyên văn ở hai chỗ trong {@code AuthService}
     * (đăng ký thường và đăng ký bằng Google). Thêm một cột vào bảng {@code wallets} mà quên một
     * trong hai chỗ là lỗi chỉ lộ ra ở đúng một luồng đăng ký — rất khó nhận ra.
     */
    @Transactional
    public void createDefaultCashWallet(UUID userId, Instant createdAt) {
        Wallet cashWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .groupId(null)
                .name(DEFAULT_WALLET_NAME)
                .type("cash")
                .initialBalance(0L)
                .currentBalance(0L)
                .includeInTotal(true)
                .sortOrder(0)
                .isDeleted(false)
                .createdAt(createdAt)
                .build();
        walletRepository.save(cashWallet);
    }

    /**
     * Số ví còn sống của một người dùng — phục vụ khối {@code stats} của {@code GET /auth/me}.
     * Có mặt ở đây để {@code auth} không phải đụng thẳng vào {@code WalletRepository}.
     */
    @Transactional(readOnly = true)
    public long countActiveWallets(UUID userId) {
        return walletRepository.countByUserIdAndIsDeletedFalse(userId);
    }

    /**
     * api/02-VI.md mục 5. {@code UpdateWalletRequest} không có field {@code currentBalance}/
     * {@code type} — nếu client vẫn cố gửi (bắt qua {@code extraFields}), trả
     * {@code BALANCE_NOT_EDITABLE} tường minh thay vì Jackson âm thầm bỏ qua (T-02-04).
     */
    @Transactional
    public WalletResponse update(UUID userId, UUID walletId, UpdateWalletRequest req) {
        Map<String, Object> extra = req.extraFields();
        if (extra.containsKey("current_balance") || extra.containsKey("currentBalance")
                || extra.containsKey("type")) {
            throw new BusinessException(ErrorCode.BALANCE_NOT_EDITABLE);
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
     * api/02-VI.md mục 6. Idempotent theo CORE-06: gọi lần 2 trên ví đã {@code is_deleted=true}
     * vẫn trả về bình thường (không 404) — chỉ 404 khi bản ghi không tồn tại/không thuộc quyền.
     */
    @Transactional
    public void delete(UUID userId, UUID walletId, boolean deleteTransactions) {
        Wallet wallet = walletRepository
                .findByIdForUserIncludingDeleted(walletId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví."));

        if (Boolean.TRUE.equals(wallet.getIsDeleted())) {
            // Đã xoá mềm từ trước — idempotent, không làm gì thêm, không ném lỗi.
            return;
        }

        if (walletRepository.countByUserIdAndIsDeletedFalse(userId) <= 1) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_LAST_WALLET);
        }

        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE (wallet_id = ? OR destination_wallet_id = ?) AND NOT is_deleted",
                Long.class, walletId, walletId);
        boolean hasTransactions = transactionCount != null && transactionCount > 0;

        if (hasTransactions && !deleteTransactions) {
            throw new BusinessException(ErrorCode.WALLET_HAS_TRANSACTIONS);
        }

        if (hasTransactions) {
            jdbcTemplate.update(
                    "UPDATE transactions SET is_deleted = TRUE WHERE wallet_id = ? OR destination_wallet_id = ?",
                    walletId, walletId);
        }

        wallet.setIsDeleted(true);
        walletRepository.save(wallet);
    }

    /** api/02-VI.md mục 7 — gán sort_order theo vị trí trong mảng, chỉ update ví thuộc quyền user. */
    @Transactional
    public void reorder(UUID userId, ReorderWalletsRequest req) {
        List<UUID> ids = req.sortOrder();
        for (int i = 0; i < ids.size(); i++) {
            walletRepository.updateSortOrder(ids.get(i), i, userId);
        }
    }

    private WalletStatsResponse loadStats(UUID walletId) {
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE (wallet_id = ? OR destination_wallet_id = ?) AND NOT is_deleted",
                Long.class, walletId, walletId);
        Long incomeThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type = 'income' "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        Long expenseThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE wallet_id = ? AND type = 'expense' "
                        + "AND NOT is_deleted AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)",
                Long.class, walletId);
        LocalDate lastTransactionDate = jdbcTemplate.query(
                        "SELECT MAX(date) FROM transactions WHERE (wallet_id = ? OR destination_wallet_id = ?) AND NOT is_deleted",
                        rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                        walletId, walletId);

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

        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getType(),
                currentBalanceAsOfToday,
                wallet.getIncludeInTotal(),
                wallet.getGroupId() != null,
                wallet.getGroupId(),
                wallet.getIcon(),
                wallet.getColor(),
                wallet.getSortOrder(),
                wallet.getCreatedAt(),
                projectedBalance);
    }
}
