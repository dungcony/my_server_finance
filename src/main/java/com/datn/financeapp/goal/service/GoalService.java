package com.datn.financeapp.goal.service;

import com.datn.financeapp.category.dto.response.IconRefResponse;
import com.datn.financeapp.category.service.CategoryService;
import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.goal.entity.GoalContribution;
import com.datn.financeapp.goal.entity.SavingsGoal;
import com.datn.financeapp.goal.repository.GoalContributionRepository;
import com.datn.financeapp.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.transaction.service.TransactionService;
import com.datn.financeapp.transaction.service.TransactionWriteCommand;
import com.datn.financeapp.transaction.service.TransactionWriter;
import com.datn.financeapp.wallet.dto.response.WalletRefResponse;
import com.datn.financeapp.wallet.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic mục tiêu tiết kiệm (GOAL-01..04, api/09-DINH-KY-MUC-TIEU.md Phần B).
 *
 * <p><b>Hai nguyên tắc chi phối toàn bộ lớp này:</b>
 *
 * <p>1. <b>KHÔNG viết lại logic cập nhật số dư ví.</b> Mọi giao dịch sinh ra từ mục tiêu đều đi
 * qua {@link TransactionWriter} (D-31), mọi lần hoàn tác đều đi qua
 * {@link TransactionService#delete} — nơi đã cài đúng luồng 3 bước của CLAUDE.md quy tắc 4. Lớp
 * này không được tự gọi {@code walletRepository.adjustBalance}.
 *
 * <p>2. <b>KHÔNG ghi saved_amount / status của bảng savings_goals.</b> Hai cột do trigger
 * {@code trg_goal_contributions_sync} (V4) sở hữu. Backend chỉ chèn/xoá bản ghi
 * {@code goal_contributions}; muốn biết giá trị mới thì ĐỌC LẠI bằng
 * {@link SavingsGoalRepository#findSavedAmountNative} / {@code findStatusNative} (query trả
 * SCALAR — xem Javadoc ở repository để biết vì sao không được trả entity).
 *
 * <p>Khác sổ nợ ở chỗ mục tiêu KHÔNG có ngoại lệ kiểu {@code writeOff} nào được ghi tay
 * {@code status}: nơi duy nhất chạm tới cột đó là {@link #update} khi người dùng chủ động đặt
 * {@code cancelled} theo api/09 mục B5 — và trigger tôn trọng giá trị này bằng nhánh
 * {@code WHEN status = 'cancelled' THEN 'cancelled'} nên nó không bị ghi đè về sau.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_CANCELLED = "cancelled";

    private final SavingsGoalRepository savingsGoalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionWriter transactionWriter;
    private final TransactionService transactionService;

    // ---------------------------------------------------------------------
    // Ghi
    // ---------------------------------------------------------------------

    /**
     * GOAL-01, api/09 mục B2.
     *
     * <p><b>Cách xử lý {@code initial_amount} — điểm dễ hiểu sai nhất của endpoint này.</b> Đặc
     * tả nói {@code initial_amount} "không sinh giao dịch — đó là số tiền người dùng đã tích được
     * từ trước, chỉ ghi nhận điểm xuất phát". Nhưng schema V4 KHÔNG có cột
     * {@code savings_goals.initial_amount}: nó là trường REQUEST, không phải trường lưu trữ.
     *
     * <p>Nếu bỏ qua hẳn thì {@code saved_amount} sẽ mãi là 0 (trigger chỉ tính {@code SUM()} trên
     * {@code goal_contributions}) và tiến độ ban đầu người dùng nhập mất sạch. Nếu ghi thẳng vào
     * {@code saved_amount} thì vi phạm ranh giới trigger — và lần chèn contribution kế tiếp trigger
     * sẽ {@code SUM()} lại từ đầu, thổi bay con số đó.
     *
     * <p>Lối thoát duy nhất đúng cả hai phía: tạo NGAY một {@link GoalContribution} với
     * {@code transactionId = null} — tức chế độ "chỉ ghi nhận tiến độ", đúng ngữ nghĩa "tiền đã có
     * sẵn từ trước". KHÔNG giao dịch nào được sinh ra (đúng đặc tả), mà trigger vẫn cộng đúng vào
     * {@code saved_amount}.
     */
    @Transactional
    public CreateGoalResponse create(UUID userId, CreateGoalRequest req) {
        if (req.targetAmount() == null || req.targetAmount() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
        if (req.targetDate() != null && !req.targetDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_DATE);
        }
        if (req.initialAmount() != null && req.initialAmount() < 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Số tiền đã có sẵn không được âm.");
        }

        WalletRefResponse wallet = null;
        if (req.walletId() != null) {
            wallet = walletService.findRefForUser(userId, req.walletId());
            if (wallet == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví.");
            }
        }

        UUID goalId = UUID.randomUUID();
        SavingsGoal goal = SavingsGoal.builder()
                .id(goalId)
                .userId(userId)
                .walletId(wallet == null ? null : wallet.id())
                .name(req.name())
                .targetAmount(req.targetAmount())
                // Giá trị KHỞI TẠO của bản ghi mới, không phải ghi đè cột do trigger sở hữu: chưa
                // có bản ghi goal_contributions nào nên trigger chưa từng chạy cho mục tiêu này.
                .savedAmount(0L)
                .targetDate(req.targetDate())
                .iconId(req.iconId())
                .status(STATUS_IN_PROGRESS)
                .createdAt(Instant.now())
                .build();
        savingsGoalRepository.saveAndFlush(goal);

        if (req.initialAmount() != null && req.initialAmount() > 0) {
            goalContributionRepository.saveAndFlush(GoalContribution.builder()
                    .id(UUID.randomUUID())
                    .goalId(goalId)
                    // NULL — đây chính là điểm khiến initial_amount KHÔNG sinh giao dịch nào.
                    .transactionId(null)
                    .amount(req.initialAmount())
                    .contributedDate(LocalDate.now())
                    .createdAt(Instant.now())
                    .build());
        }

        return new CreateGoalResponse(toListItem(goal, wallet));
    }

    /**
     * GOAL-03, api/09 mục B3 — hai chế độ nạp tiền theo D-49.
     *
     * <p><b>Chế độ đọc TƯỜNG MINH từ {@code req.createTransaction()}, KHÔNG suy đoán từ
     * {@code goal.getWalletId() != null}.</b> Hai chuyện độc lập: một mục tiêu CÓ gắn ví vẫn được
     * nạp kiểu "chỉ ghi nhận tiến độ" khi tiền thực tế nằm ngoài ứng dụng. Suy đoán sẽ tạo giao
     * dịch ma và trừ oan ví người dùng.
     */
    @Transactional
    public CreateContributionResponse addContribution(UUID userId, UUID goalId, CreateContributionRequest req) {
        SavingsGoal goal = loadOwnedGoal(userId, goalId);

        if (STATUS_CANCELLED.equals(goal.getStatus())) {
            throw new BusinessException(ErrorCode.GOAL_CANCELLED);
        }
        if (STATUS_COMPLETED.equals(goal.getStatus())) {
            throw new BusinessException(ErrorCode.GOAL_ALREADY_COMPLETED);
        }

        LocalDate contributedDate = req.contributedDate() != null ? req.contributedDate() : LocalDate.now();

        // Mặc định true theo api/09 mục B3 — null nghĩa là client không gửi trường này.
        boolean createTransaction = req.createTransaction() == null || req.createTransaction();

        UUID transactionId = null;
        CreateContributionResponse.TransactionSummary transactionSummary = null;
        CreateContributionResponse.NewBalance newBalance = null;

        if (createTransaction) {
            if (goal.getWalletId() == null) {
                throw new BusinessException(ErrorCode.GOAL_WALLET_REQUIRED);
            }
            if (req.sourceWalletId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu ví nguồn để chuyển tiền.");
            }
            WalletRefResponse sourceWallet = walletService.findRefForUser(userId, req.sourceWalletId());
            if (sourceWallet == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy ví nguồn.");
            }

            // LUÔN là transfer: tiền chỉ đổi chỗ giữa hai ví của cùng người dùng, không phải thu
            // cũng không phải chi. Nhờ vậy tự động bị loại khỏi mọi báo cáo thu-chi qua điều kiện
            // type != 'transfer' — đúng mong muốn của api/09 ("chuyển tiền vào tiết kiệm không
            // phải là tiêu tiền"), nên KHÔNG cần đặt countsInReport = false.
            // categoryId = null theo ck_txn_shape: giao dịch transfer không mang danh mục.
            TransactionWriter.WriteResult result = transactionWriter.write(new TransactionWriteCommand(
                    null,
                    userId,
                    sourceWallet.id(),
                    goal.getWalletId(),
                    null,
                    "transfer",
                    req.amount(),
                    contributedDate,
                    req.note(),
                    goal.getName(),
                    "manual",
                    true,
                    null,
                    null,
                    null));
            transactionId = result.transactionId();
            transactionSummary =
                    new CreateContributionResponse.TransactionSummary(transactionId, "transfer", req.amount());
            newBalance = new CreateContributionResponse.NewBalance(sourceWallet.id(), result.walletNewBalance());
        }

        GoalContribution contribution = GoalContribution.builder()
                .id(UUID.randomUUID())
                .goalId(goalId)
                .transactionId(transactionId)
                .amount(req.amount())
                .contributedDate(contributedDate)
                .createdAt(Instant.now())
                .build();
        // saveAndFlush: đẩy INSERT xuống CSDL NGAY để trigger chạy trước khi ta đọc lại phía dưới.
        goalContributionRepository.saveAndFlush(contribution);

        // BẮT BUỘC đọc lại bằng query trả SCALAR — trigger vừa UPDATE bảng savings_goals ở tầng
        // CSDL trong cùng transaction, nhưng instance goal phía trên vẫn mang saved_amount/status
        // CŨ trong Hibernate identity map. Xem Javadoc SavingsGoalRepository.findSavedAmountNative.
        long refreshedSavedAmount =
                savingsGoalRepository.findSavedAmountNative(goalId).orElseThrow();
        String refreshedStatus =
                savingsGoalRepository.findStatusNative(goalId).orElseThrow();

        return new CreateContributionResponse(
                new CreateContributionResponse.Contribution(
                        contribution.getId(),
                        contribution.getAmount(),
                        contribution.getContributedDate(),
                        contribution.getTransactionId()),
                new CreateContributionResponse.GoalProgress(
                        refreshedSavedAmount,
                        progressRatio(refreshedSavedAmount, goal.getTargetAmount()),
                        refreshedStatus),
                transactionSummary,
                newBalance);
    }

    /**
     * GOAL-04, api/09 mục B4 — THỨ TỰ NGƯỢC với sổ nợ (D-46), có lý do schema rõ ràng:
     *
     * <ol>
     *   <li>Hoàn tác + xoá mềm giao dịch kèm theo (nếu có) qua {@link TransactionService#delete}
     *   <li>Xoá bản ghi {@code goal_contributions}, trigger tự trừ lại {@code saved_amount} và tự
     *       MỞ LẠI {@code completed → in_progress}
     * </ol>
     *
     * <p>Ở sổ nợ phải xoá bản ghi con TRƯỚC vì {@code debt_payments.transaction_id} là
     * {@code ON DELETE RESTRICT} và vì {@code TransactionService.delete} chặn 409 các giao dịch
     * còn gắn sổ nợ (D-32). Cả hai lý do đều KHÔNG áp dụng ở đây:
     * {@code goal_contributions.transaction_id} là {@code ON DELETE SET NULL}, và kiểm tra D-32
     * chỉ soi bảng {@code debt_payments} chứ không soi {@code goal_contributions}.
     *
     * <p>Ngược lại, làm theo thứ tự này còn tránh được một cái bẫy: nếu xoá contribution trước,
     * {@code ON DELETE SET NULL} có thể đã xoá mất tham chiếu {@code transaction_id} cần dùng để
     * hoàn tác.
     */
    @Transactional
    public void cancelContribution(UUID userId, UUID goalId, UUID contributionId) {
        loadOwnedGoal(userId, goalId);

        GoalContribution contribution = goalContributionRepository
                .findByIdAndGoalId(contributionId, goalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lần nạp."));

        // Bước 1 — hoàn tác ví qua đúng luồng 3 bước có sẵn, KHÔNG tự viết lại.
        if (contribution.getTransactionId() != null) {
            transactionService.delete(userId, contribution.getTransactionId());
        }

        // Bước 2 — flush ngay để trigger chạy và tính lại tiến độ trong cùng transaction.
        goalContributionRepository.delete(contribution);
        goalContributionRepository.flush();
    }

    /**
     * GOAL-02, api/09 mục B5 — sửa được name/target_amount/target_date/icon_id/status.
     *
     * <p>{@code status} chỉ nhận {@code cancelled} (dừng theo dõi, giữ lịch sử) hoặc
     * {@code in_progress} (mở lại mục tiêu đã huỷ). KHÔNG cho đặt tay {@code completed} — trạng
     * thái đó phải do trigger suy ra từ {@code saved_amount >= target_amount}, đặt tay sẽ tạo ra
     * mục tiêu "hoàn thành" mà tiền chưa đủ.
     */
    @Transactional
    public GoalListItemResponse update(UUID userId, UUID goalId, UpdateGoalRequest req) {
        SavingsGoal goal = loadOwnedGoal(userId, goalId);

        if (req.name() != null) {
            goal.setName(req.name());
        }
        if (req.targetAmount() != null) {
            if (req.targetAmount() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_AMOUNT);
            }
            goal.setTargetAmount(req.targetAmount());
        }
        if (req.targetDate() != null) {
            if (!req.targetDate().isAfter(LocalDate.now())) {
                throw new BusinessException(ErrorCode.INVALID_TARGET_DATE);
            }
            goal.setTargetDate(req.targetDate());
        }
        if (req.iconId() != null) {
            goal.setIconId(req.iconId());
        }
        if (req.status() != null) {
            if (!STATUS_CANCELLED.equals(req.status()) && !STATUS_IN_PROGRESS.equals(req.status())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ đặt được trạng thái huỷ hoặc đang thực hiện — hoàn thành do hệ thống tự xác định.");
            }
            goal.setStatus(req.status());
        }
        savingsGoalRepository.saveAndFlush(goal);

        // Đổi target_amount có thể làm mục tiêu vừa đủ/không còn đủ, nhưng trigger chỉ chạy khi
        // bảng con thay đổi — nên đọc lại để phản ánh đúng giá trị hiện tại dưới CSDL, không tự suy.
        return toListItem(goal, loadWalletOrNull(userId, goal.getWalletId()));
    }

    /**
     * api/09 mục B5. {@code revertTransactions} mặc định {@code false}: tiền đã chuyển vào ví tiết
     * kiệm thì vẫn nằm đó, chỉ là không theo dõi mục tiêu nữa.
     *
     * <p>Khi {@code true}, phải đọc danh sách lần nạp TRƯỚC khi xoá mục tiêu vì
     * {@code ON DELETE CASCADE} sẽ cuốn theo chúng.
     */
    @Transactional
    public void delete(UUID userId, UUID goalId, boolean revertTransactions) {
        SavingsGoal goal = loadOwnedGoal(userId, goalId);

        List<UUID> transactionIds = goalContributionRepository.findByGoalId(goalId).stream()
                .map(GoalContribution::getTransactionId)
                .filter(java.util.Objects::nonNull)
                .toList();

        savingsGoalRepository.delete(goal);
        savingsGoalRepository.flush();

        if (revertTransactions) {
            for (UUID transactionId : transactionIds) {
                transactionService.delete(userId, transactionId);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Đọc
    // ---------------------------------------------------------------------

    /** GOAL-02, api/09 mục B1. */
    @Transactional(readOnly = true)
    public List<GoalListItemResponse> list(UUID userId, String status) {
        List<SavingsGoal> goals = savingsGoalRepository.findAllForUser(userId, status);
        List<GoalListItemResponse> result = new ArrayList<>();
        for (SavingsGoal goal : goals) {
            result.add(toListItem(goal, loadWalletOrNull(userId, goal.getWalletId())));
        }
        return result;
    }

    /** api/09 mục B1 — chi tiết kèm lịch sử nạp. */
    @Transactional(readOnly = true)
    public GoalDetailResponse detail(UUID userId, UUID goalId) {
        SavingsGoal goal = loadOwnedGoal(userId, goalId);
        List<GoalDetailResponse.ContributionHistoryItem> history =
                goalContributionRepository.findByGoalIdOrderByContributedDateDesc(goalId).stream()
                        .map(c -> new GoalDetailResponse.ContributionHistoryItem(
                                c.getId(), c.getAmount(), c.getContributedDate(), c.getTransactionId()))
                        .toList();
        return new GoalDetailResponse(toListItem(goal, loadWalletOrNull(userId, goal.getWalletId())), history);
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    /** Quyền nằm trong SQL; không có quyền thì 404, không phải 403 (T-04-12). */
    private SavingsGoal loadOwnedGoal(UUID userId, UUID goalId) {
        return savingsGoalRepository
                .findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
    }

    private WalletRefResponse loadWalletOrNull(UUID userId, UUID walletId) {
        return walletService.findRefForUser(userId, walletId);
    }

    private BigDecimal progressRatio(long savedAmount, long targetAmount) {
        return BigDecimal.valueOf(savedAmount).divide(BigDecimal.valueOf(targetAmount), 4, RoundingMode.HALF_UP);
    }

    /**
     * Dựng response từ bản ghi mục tiêu.
     *
     * <p>{@code savedAmount} LUÔN đọc lại từ CSDL bằng query scalar thay vì lấy từ entity: entity
     * có thể là instance cũ trong Hibernate identity map từ trước lúc trigger chạy.
     */
    private GoalListItemResponse toListItem(SavingsGoal goal, WalletRefResponse wallet) {
        long savedAmount = savingsGoalRepository
                .findSavedAmountNative(goal.getId())
                .orElse(0L);
        String status = savingsGoalRepository.findStatusNative(goal.getId()).orElse(goal.getStatus());

        long missingAmount = Math.max(0, goal.getTargetAmount() - savedAmount);
        BigDecimal progress = progressRatio(savedAmount, goal.getTargetAmount());
        String progressLabel = progress.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";

        LocalDate today = LocalDate.now();
        Integer daysRemaining =
                goal.getTargetDate() == null ? null : (int) ChronoUnit.DAYS.between(today, goal.getTargetDate());

        GoalListItemResponse.IconSummary icon = null;
        if (goal.getIconId() != null) {
            IconRefResponse ref = categoryService.findIconRef(goal.getIconId());
            icon = ref == null ? null : new GoalListItemResponse.IconSummary(ref.code(), ref.pathData());
        }

        return new GoalListItemResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                savedAmount,
                missingAmount,
                progress,
                progressLabel,
                goal.getTargetDate(),
                daysRemaining,
                status,
                icon,
                wallet == null ? null : new GoalListItemResponse.WalletSummary(wallet.id(), wallet.name()),
                buildSuggestion(goal, missingAmount, today),
                goalContributionRepository.countByGoalId(goal.getId()));
    }

    /**
     * api/09 mục B1 "Cách tính gợi ý" — giúp người dùng biết mục tiêu có thực tế không NGAY TỪ LÚC
     * ĐẶT, thay vì ba tháng sau mới nhận ra không kham nổi.
     *
     * <p>Trả null khi mục tiêu không đặt {@code target_date} (không có mốc thì không suy ra được
     * mức nạp hằng tháng) hoặc đã đủ tiền.
     *
     * <p><b>Giới hạn hiện tại của {@code assessment}:</b> ngưỡng feasible/challenging cần "thu nhập
     * trung bình tháng" của người dùng — số này đến từ báo cáo thu-chi, sẽ có ở Plan 06. Tạm thời
     * để null thay vì đoán bừa một mốc cứng: một đánh giá sai còn tệ hơn không đánh giá, vì người
     * dùng sẽ tin nó khi đặt mục tiêu.
     */
    private GoalListItemResponse.Suggestion buildSuggestion(SavingsGoal goal, long missingAmount, LocalDate today) {
        if (goal.getTargetDate() == null || missingAmount <= 0) {
            return null;
        }
        long monthsRemaining = Math.max(1, ChronoUnit.MONTHS.between(today, goal.getTargetDate()));
        long monthlyRequired = (long) Math.ceil((double) missingAmount / monthsRemaining);
        String content = String.format("Cần nạp %,d đ mỗi tháng để đạt mục tiêu đúng hạn.", monthlyRequired);
        return new GoalListItemResponse.Suggestion(monthlyRequired, content, null);
    }
}
