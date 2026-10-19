package com.datn.financeapp.module.goal.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.goal.dto.request.CreateContributionRequest;
import com.datn.financeapp.module.goal.dto.request.CreateGoalRequest;
import com.datn.financeapp.module.goal.dto.request.GoalFilterRequest;
import com.datn.financeapp.module.goal.dto.request.UpdateGoalRequest;
import com.datn.financeapp.module.goal.dto.response.CreateContributionResponse;
import com.datn.financeapp.module.goal.dto.response.CreateGoalResponse;
import com.datn.financeapp.module.goal.dto.response.GoalDetailResponse;
import com.datn.financeapp.module.goal.dto.response.GoalListItemResponse;
import com.datn.financeapp.module.goal.entity.GoalContribution;
import com.datn.financeapp.module.goal.entity.SavingsGoal;
import com.datn.financeapp.module.goal.event.GoalCompletedEvent;
import com.datn.financeapp.module.goal.helper.GoalSpecs;
import com.datn.financeapp.module.goal.mapper.GoalMapper;
import com.datn.financeapp.module.goal.repository.GoalContributionRepository;
import com.datn.financeapp.module.goal.repository.SavingsGoalRepository;
import com.datn.financeapp.module.goal.service.GoalService;
import com.datn.financeapp.module.icon.dto.response.IconRes;
import com.datn.financeapp.module.icon.entity.Icon;
import com.datn.financeapp.module.icon.mapper.IconMapper;
import com.datn.financeapp.module.icon.repository.IconRepository;
import com.datn.financeapp.module.transaction.dto.request.TxnCreateReq;
import com.datn.financeapp.module.transaction.dto.response.TxnDetailRes;
import com.datn.financeapp.module.transaction.enums.TxnType;
import com.datn.financeapp.module.transaction.service.TxnBehavierService;
import com.datn.financeapp.module.wallet.dto.request.WalletCreateReq;
import com.datn.financeapp.module.wallet.dto.response.WalletRes;
import com.datn.financeapp.module.wallet.dto.response.WalletSumaryRes;
import com.datn.financeapp.module.wallet.entity.Wallet;
import com.datn.financeapp.module.wallet.enums.WalletType;
import com.datn.financeapp.module.wallet.repository.WalletRepository;
import com.datn.financeapp.module.wallet.service.WalletBehavierService;
import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ {@link GoalService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalServiceImpl implements GoalService {

    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_CANCELLED = "cancelled";

    private final SavingsGoalRepository savingsGoalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final WalletBehavierService walletBehavierService;
    private final TxnBehavierService txnBehavierService;
    private final IconRepository iconRepository;
    private final IconMapper iconMapper;
    private final GoalMapper goalMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    // ---------------------------------------------------------------------
    // Nghiệp vụ Người dùng (User)
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public CreateGoalResponse create(UUID userId, CreateGoalRequest req) {
        UUID walletId = null;

        if (Boolean.TRUE.equals(req.manageWithWallet())) {
            String iconCode = "wallet";
            if (req.iconId() != null) {
                iconCode = iconRepository.findByIdAndIsDeletedFalse(req.iconId())
                        .map(Icon::getCode)
                        .orElse("wallet");
            }
            WalletCreateReq walletReq = new WalletCreateReq(
                    "[Tiết kiệm] " + req.name(),
                    WalletType.SAVINGS,
                    0L,
                    userId,
                    iconCode,
                    "#4CAF50"
            );
            WalletRes createdWallet = walletBehavierService.create(walletReq, false);
            walletId = createdWallet.id();
        } else if (req.walletId() != null) {
            WalletSumaryRes existing = walletService.findSumaryByIdAndUser(req.walletId(), userId, false)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND, "Không tìm thấy ví liên kết."));
            walletId = existing.id();
        }

        SavingsGoal goal = SavingsGoal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .walletId(walletId)
                .name(req.name())
                .targetAmount(req.targetAmount())
                .savedAmount(0L)
                .targetDate(req.targetDate())
                .iconId(req.iconId())
                .status(STATUS_IN_PROGRESS)
                .build();
        goal = savingsGoalRepository.save(goal);

        if (req.initialAmount() != null && req.initialAmount() > 0) {
            GoalContribution initContrib = GoalContribution.builder()
                    .id(UUID.randomUUID())
                    .goalId(goal.getId())
                    .amount(req.initialAmount())
                    .contributedDate(java.time.LocalDate.now())
                    .build();
            goalContributionRepository.save(initContrib);
        }

        WalletSumaryRes wallet = loadWalletOrNull(userId, goal.getWalletId());
        GoalListItemResponse item = toListItem(goal, wallet);
        return goalMapper.toCreateGoalResponse(item);
    }

    @Override
    @Transactional
    public CreateContributionResponse addContribution(UUID userId, UUID goalId, CreateContributionRequest req) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));

        UUID transactionId = null;
        UUID targetTransactionId = null;
        CreateContributionResponse.TransactionSummary transactionSummary = null;
        CreateContributionResponse.NewBalance newBalance = null;

        if (Boolean.TRUE.equals(req.createTransaction())) {
            if (req.sourceWalletId() == null) {
                throw new BusinessException(ErrorCode.INVALID_SOURCE_WALLET, "Ví nguồn không được để trống khi yêu cầu tạo giao dịch.");
            }
            if (goal.getWalletId() == null) {
                throw new BusinessException(ErrorCode.GOAL_WALLET_REQUIRED, "Mục tiêu chưa gắn ví, không thể tạo giao dịch thật.");
            }
            Wallet sourceWallet = walletRepository.findByIdAndUserIdAndIsDeleted(req.sourceWalletId(), userId, false)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND, "Không tìm thấy ví nguồn."));

            if (sourceWallet.getType() == WalletType.SAVINGS) {
                throw new BusinessException(ErrorCode.INVALID_SOURCE_WALLET, "Không thể dùng ví tiết kiệm làm ví nguồn để nạp tiền.");
            }
            if (sourceWallet.getBalance() < req.amount()) {
                throw new BusinessException(ErrorCode.WALLET_BALANCE_INSUFFICIENT, "Số dư ví nguồn không đủ để nạp vào mục tiêu.");
            }

            Instant occurredAt = req.contributedDate() != null
                    ? req.contributedDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                    : Instant.now();

            // Giao dịch 1: Trừ tiền ví nguồn (EXPENSE)
            TxnDetailRes srcTxn = txnBehavierService.create(userId, new TxnCreateReq(
                    TxnType.EXPENSE,
                    req.amount(),
                    sourceWallet.getId(),
                    "tiet_kiem",
                    occurredAt,
                    "Nạp tiền mục tiêu: " + goal.getName(),
                    "Nạp tiền vào mục tiêu tiết kiệm: " + goal.getName()
            ));
            transactionId = srcTxn.id();
            transactionSummary = new CreateContributionResponse.TransactionSummary(srcTxn.id(), "transfer", req.amount());
            newBalance = new CreateContributionResponse.NewBalance(sourceWallet.getId(), sourceWallet.getBalance() - req.amount());

            // Giao dịch 2: Cộng tiền vào ví tiết kiệm liên kết (INCOME) nếu goal có ví
            if (goal.getWalletId() != null) {
                TxnDetailRes tgtTxn = txnBehavierService.create(userId, new TxnCreateReq(
                        TxnType.INCOME,
                        req.amount(),
                        goal.getWalletId(),
                        "tiet_kiem",
                        occurredAt,
                        "Tích lũy từ ví: " + sourceWallet.getName(),
                        "Khoản tiền tích lũy cho mục tiêu: " + goal.getName()
                ));
                targetTransactionId = tgtTxn.id();
            }
        }

        GoalContribution contribution = GoalContribution.builder()
                .id(UUID.randomUUID())
                .goalId(goal.getId())
                .amount(req.amount())
                .contributedDate(req.contributedDate() != null ? req.contributedDate() : java.time.LocalDate.now())
                .transactionId(transactionId)
                .targetTransactionId(targetTransactionId)
                .build();
        contribution = goalContributionRepository.save(contribution);

        long refreshedSavedAmount = savingsGoalRepository.findSavedAmountNative(goal.getId())
                .orElse(goal.getSavedAmount() + req.amount());
        String refreshedStatus = savingsGoalRepository.findStatusNative(goal.getId())
                .orElse(refreshedSavedAmount >= goal.getTargetAmount() ? STATUS_COMPLETED : STATUS_IN_PROGRESS);

        // Phát sự kiện hoàn thành mục tiêu tiết kiệm nếu trạng thái trước đó chưa completed mà hiện tại chuyển sang completed
        if (!STATUS_COMPLETED.equalsIgnoreCase(goal.getStatus()) && STATUS_COMPLETED.equalsIgnoreCase(refreshedStatus)) {
            applicationEventPublisher.publishEvent(new GoalCompletedEvent(
                    userId,
                    goal.getId(),
                    goal.getName(),
                    goal.getTargetAmount()
            ));
        }

        BigDecimal progress = goalMapper.progressRatio(refreshedSavedAmount, goal.getTargetAmount());

        return goalMapper.toContributionResponse(
                contribution,
                refreshedSavedAmount,
                progress,
                refreshedStatus,
                transactionSummary,
                newBalance
        );
    }

    @Override
    @Transactional
    public void cancelContribution(UUID userId, UUID goalId, UUID contributionId) {
        savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));

        GoalContribution contribution = goalContributionRepository.findByIdAndGoalIdAndIsDeletedFalse(contributionId, goalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy lượt đóng góp."));

        if (contribution.getTransactionId() != null) {
            txnBehavierService.softDelete(contribution.getTransactionId(), false);
        }
        if (contribution.getTargetTransactionId() != null) {
            txnBehavierService.softDelete(contribution.getTargetTransactionId(), false);
        }

        contribution.setIsDeleted(true);
        goalContributionRepository.save(contribution);
    }

    @Override
    @Transactional
    public GoalListItemResponse update(UUID userId, UUID goalId, UpdateGoalRequest req) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));

        if (req.name() != null && !req.name().isBlank()) {
            goal.setName(req.name());
        }
        if (req.targetAmount() != null) {
            goal.setTargetAmount(req.targetAmount());
        }
        if (req.targetDate() != null) {
            goal.setTargetDate(req.targetDate());
        }
        if (req.iconId() != null) {
            goal.setIconId(req.iconId());
        }
        if (req.status() != null && (STATUS_IN_PROGRESS.equals(req.status()) || STATUS_CANCELLED.equals(req.status()))) {
            goal.setStatus(req.status());
        }

        goal = savingsGoalRepository.save(goal);
        WalletSumaryRes wallet = loadWalletOrNull(userId, goal.getWalletId());
        return toListItem(goal, wallet);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID goalId, boolean revertTransactions, boolean deleteWallet) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
        deleteInternal(goal, revertTransactions, deleteWallet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoalListItemResponse> list(UUID userId, String status) {
        List<SavingsGoal> goals = savingsGoalRepository.findAllForUser(userId, status);
        if (goals.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> walletIds = goals.stream()
                .map(SavingsGoal::getWalletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, WalletSumaryRes> walletMap = walletIds.isEmpty() ? Collections.emptyMap() : walletService.findSummaryMapByIds(walletIds);

        return goals.stream()
                .map(g -> toListItem(g, g.getWalletId() != null ? walletMap.get(g.getWalletId()) : null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GoalDetailResponse detail(UUID userId, UUID goalId) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
        WalletSumaryRes wallet = loadWalletOrNull(userId, goal.getWalletId());
        GoalListItemResponse item = toListItem(goal, wallet);
        List<GoalDetailResponse.ContributionHistoryItem> contributions =
                goalContributionRepository.findByGoalIdAndIsDeletedFalseOrderByContributedDateDesc(goalId).stream()
                        .map(goalMapper::toHistoryItem)
                        .toList();
        return goalMapper.toDetailResponse(item, contributions);
    }

    // ---------------------------------------------------------------------
    // Phân hệ Quản trị (Admin)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<GoalListItemResponse> adminList(GoalFilterRequest filter, Pageable pageable) {
        Pageable effectivePageable = pageable;
        if (effectivePageable == null || effectivePageable.isUnpaged()) {
            effectivePageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        } else if (effectivePageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(
                    effectivePageable.getPageNumber(),
                    effectivePageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Page<SavingsGoal> page = savingsGoalRepository.findAll(GoalSpecs.filter(filter), effectivePageable);

        Set<UUID> walletIds = page.getContent().stream()
                .map(SavingsGoal::getWalletId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, WalletSumaryRes> walletMap = walletIds.isEmpty() ? Collections.emptyMap() : walletService.findSummaryMapByIds(walletIds);

        return page.map(goal -> {
            WalletSumaryRes wallet = goal.getWalletId() != null ? walletMap.get(goal.getWalletId()) : null;
            return toListItem(goal, wallet);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public GoalDetailResponse adminDetail(UUID goalId) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndIsDeletedFalse(goalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
        WalletSumaryRes wallet = loadWalletOrNull(goal.getUserId(), goal.getWalletId());
        GoalListItemResponse item = toListItem(goal, wallet);
        List<GoalDetailResponse.ContributionHistoryItem> contributions =
                goalContributionRepository.findByGoalIdAndIsDeletedFalseOrderByContributedDateDesc(goalId).stream()
                        .map(goalMapper::toHistoryItem)
                        .toList();
        return goalMapper.toDetailResponse(item, contributions);
    }

    @Override
    @Transactional
    public void adminDelete(UUID goalId, boolean revertTransactions, boolean deleteWallet) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndIsDeletedFalse(goalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục tiêu."));
        deleteInternal(goal, revertTransactions, deleteWallet);
    }

    // ---------------------------------------------------------------------
    // PRIVATE MOTHOD
    // ---------------------------------------------------------------------

    private void deleteInternal(SavingsGoal goal, boolean revertTransactions, boolean deleteWallet) {
        List<GoalContribution> contributions = goalContributionRepository.findByGoalIdAndIsDeletedFalse(goal.getId());
        for (GoalContribution c : contributions) {
            if (revertTransactions) {
                if (c.getTransactionId() != null) {
                    txnBehavierService.softDelete(c.getTransactionId(), false);
                }
                if (c.getTargetTransactionId() != null) {
                    txnBehavierService.softDelete(c.getTargetTransactionId(), false);
                }
            }
            c.setIsDeleted(true);
            goalContributionRepository.save(c);
        }

        goal.setIsDeleted(true);
        savingsGoalRepository.save(goal);

        if (deleteWallet && goal.getWalletId() != null) {
            try {
                walletBehavierService.softDelete(goal.getWalletId(), false);
            } catch (Exception e) {
                log.warn("Không thể xóa ví liên kết {} khi xóa mục tiêu {}: {}", goal.getWalletId(), goal.getId(), e.getMessage());
            }
        }
    }

    private WalletSumaryRes loadWalletOrNull(UUID userId, UUID walletId) {
        if (walletId == null) {
            return null;
        }
        return walletService.findSumaryByIdAndUser(walletId, userId, false).orElse(null);
    }

    private GoalListItemResponse toListItem(SavingsGoal goal, WalletSumaryRes wallet) {
        long savedAmount = savingsGoalRepository.findSavedAmountNative(goal.getId())
                .orElse(goal.getSavedAmount() != null ? goal.getSavedAmount() : 0L);
        String status = savingsGoalRepository.findStatusNative(goal.getId())
                .orElse(goal.getStatus() != null ? goal.getStatus() : STATUS_IN_PROGRESS);

        IconRes iconRef = null;
        if (goal.getIconId() != null) {
            iconRef = iconRepository.findByIdAndIsDeletedFalse(goal.getIconId())
                    .map(iconMapper::toIconResponse)
                    .orElse(null);
        }

        int contributionCount = goalContributionRepository.countByGoalIdAndIsDeletedFalse(goal.getId());
        return goalMapper.toListItem(goal, wallet, savedAmount, status, iconRef, contributionCount);
    }
}
