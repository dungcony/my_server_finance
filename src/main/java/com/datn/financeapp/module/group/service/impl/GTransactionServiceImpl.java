package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTransactionParticipantRes;
import com.datn.financeapp.module.group.entity.GTransaction;
import com.datn.financeapp.module.group.enums.GTransactionStatus;
import com.datn.financeapp.module.group.enums.GTransactionType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.MemberLeaveEvent;
import com.datn.financeapp.module.group.helper.BalanceCalculator;
import com.datn.financeapp.module.group.helper.GTransactionBuilder;
import com.datn.financeapp.module.group.helper.GroupTxCountHelper;
import com.datn.financeapp.module.group.helper.GroupTxNotifier;
import com.datn.financeapp.module.group.helper.MemberBalances;
import com.datn.financeapp.module.group.helper.PersonalSyncHelper;
import com.datn.financeapp.module.group.helper.GTransactionUpdate;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.TransactionHelper;
import com.datn.financeapp.module.group.repository.GroupTransactionRepository;
import com.datn.financeapp.module.group.helper.GTransactionSpecification;
import com.datn.financeapp.module.group.service.*;
import com.datn.financeapp.module.group.service.GTransactionService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.service.MemberService;
import com.datn.financeapp.module.group.validator.GroupPermissionValidator;
import com.datn.financeapp.module.group.validator.GroupTransactionPaticipantValidator;
import com.datn.financeapp.module.group.validator.GroupTransactionTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service triển khai các nghiệp vụ vòng đời của giao dịch nhóm (Group
 * Transaction).
 * <p>
 * <b>Phạm vi trách nhiệm:</b>
 * <ul>
 * <li>Tạo, sửa, xóa các giao dịch thường (EXPENSE: chi tiêu, CONTRIBUTION: nộp
 * quỹ).</li>
 * <li>Tạo và điều chỉnh các giao dịch nghiệp vụ quỹ đặc thù (REFUND: hoàn tiền).</li>
 * <li>Tạo giao dịch điều chỉnh số dư thực tế sau khi kiểm kê (ADJUSTMENT).</li>
 * <li>Tra cứu chi tiết và lọc danh sách lịch sử giao dịch.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GTransactionServiceImpl implements GTransactionService {

    // ảnh hưởng của giao dịch chưa tồn tại hoặc đã bị xoá
    private static final MemberBalances NO_EFFECT = new MemberBalances(Map.of());

    private final GroupTransactionRepository transactionRepository;
    private final MemberService memberService;
    private final TransactionHelper transactionHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberBalanceService memberBalanceService;
    private final GroupTxCountHelper groupTxCountHelper;
    private final PersonalSyncHelper personalSyncHelper;
    private final GroupTxNotifier notifier;

    // validator
    private final GroupPermissionValidator permissionValidator;
    private final GroupTransactionPaticipantValidator transactionValidator;

    // Stragy
    private final List<GTransactionBuilder> createStrategies;
    private final List<GTransactionUpdate> updateStrategies;

    @Override
    public GroupTransactionDetailRes create(UUID operatorId, UUID groupId, GroupTransactionCreateReq req) {
        // xác thực người thực hiện và lấy thông tin quyền hạn
        MemberAuthInfo authInfo = permissionValidator.getAuthInfo(groupId, operatorId);

        // validate thời gian giao dịch không ở tương lai
        transactionValidator.timeNotFuture(req.resolveOccurredAt());

        // validate người thực hiện bắt buộc phải là thành viên trong nhóm
        if (!memberService.allMemberInGroup(groupId, List.of(req.transactorId())))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_IN_GROUP);

        // tìm strategy phụ trách loại giao dịch này
        GTransactionBuilder strategy = createStrategies.stream()
                .filter(s -> s.supports(req.type()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TXN_TYPE_NOT_ALLOWED));

        // lấy trạng thái kiểm duyệt dựa vào quyền người thực hiện
        GTransactionStatus status = strategy.determineStatus(authInfo);

        // khoản dùng tiền cá nhân của người khác: chỉ thủ quỹ/chủ nhóm được ghi hộ và phải chờ người đó đồng ý
        if (personalSyncHelper.requiresConsent(
                req.type(), req.resolveMoneySource(), req.transactorId(), operatorId)) {
            if (!isReviewer(authInfo))
                throw new BusinessException(ErrorCode.GROUP_TXN_PROXY_NOT_ALLOWED);
            status = GTransactionStatus.AWAITING_TRANSACTOR;
        }

        // điều phối tới đúng strategy phụ trách loại giao dịch để xây dựng entity
        GTransaction gTransaction = strategy.build(operatorId, groupId, req, status, authInfo.isSettlementEnabled());

        transactionRepository.save(gTransaction);

        // khoản đã CONFIRMED thuộc loại ghi sổ cá nhân thì vào ví của người thực hiện
        syncLedger(null, gTransaction, operatorId);
        groupTxCountHelper.increment(groupId, status.isWaiting());
        memberBalanceService.applyDelta(groupId, NO_EFFECT, BalanceCalculator.effectOf(gTransaction));

        // cập nhật số dư quỹ nếu giao dịch được duyệt tự động
        if (status == GTransactionStatus.CONFIRMED) {
            publishFundChangeEvent(
                    groupId, transactionHelper.calculateDelta(
                            gTransaction.getType(),
                            gTransaction.getMoneySource(),
                            gTransaction.getAmount(),
                            false));
        }

        // báo người cần xử lý tiếp: thủ quỹ/chủ nhóm duyệt, hoặc người chi trả/nhận tiền đồng ý
        if (status == GTransactionStatus.PENDING)
            notifier.notifyReviewers(gTransaction);
        else if (status == GTransactionStatus.AWAITING_TRANSACTOR)
            notifier.notifyTransactor(gTransaction);

        return transactionHelper.buildDetailRes(gTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTransactionDetailRes detail(UUID operatorId, UUID groupId, UUID transactionId) {
        // xác thực người thực hiện đang trong group, nhóm đã lưu trữ vẫn xem được
        permissionValidator.verifyMember(groupId, operatorId, true);

        GTransaction txn = findActiveTransaction(transactionId, groupId);

        return transactionHelper.buildDetailRes(txn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupTransactionParticipantRes> getParticipants(UUID operatorId, UUID groupId, UUID transactionId) {
        // xác thực người thực hiện đang trong group, nhóm đã lưu trữ vẫn xem được
        permissionValidator.verifyMember(groupId, operatorId, true);

        GTransaction txn = findActiveTransaction(transactionId, groupId);

        return transactionHelper.buildParticipantsRes(txn);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTransactionListRes list(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter) {
        // xác thực người thực hiện và lấy thông tin quyền hạn, nhóm đã lưu trữ vẫn xem được
        MemberAuthInfo authInfo = permissionValidator.getAuthInfo(groupId, operatorId, true);

        // member thường không được thấy giao dịch đang chờ xử lý trong danh sách chung;
        // riêng khoản chờ người chi trả đồng ý thì chỉ thấy khoản liên quan mình
        if (!isReviewer(authInfo)) {
            if (filter.status() == null) {
                filter = filter.withExcludeWaiting(true);
            } else if (filter.status() == GTransactionStatus.AWAITING_TRANSACTOR) {
                filter = filter.withInvolvedUserId(operatorId);
            }
        }

        return buildListRes(groupId, filter);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTransactionListRes myList(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter) {
        // xác thực người thực hiện đang trong nhóm, nhóm đã lưu trữ vẫn xem được
        permissionValidator.verifyMember(groupId, operatorId, true);

        // ép điều kiện chỉ lấy giao dịch do chính mình tạo hoặc mình là người thực hiện
        GroupTransactionFilterReq myFilter = filter.withInvolvedUserId(operatorId);

        return buildListRes(groupId, myFilter);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTransactionListRes listPending(UUID operatorId, UUID groupId, Integer page, Integer size) {
        requireReviewer(groupId, operatorId, true);

        GroupTransactionFilterReq filter = new GroupTransactionFilterReq(
                null, null, GTransactionStatus.PENDING, null, null, null, null, null, page, size);

        return buildListRes(groupId, filter);
    }

    @Override
    public GroupTransactionDetailRes update(UUID operatorId, UUID groupId, UUID transactionId,
                                            GroupTransactionUpdateReq req) {
        // xác thực người thực hiện có mặt trong nhóm
        MemberAuthInfo authInfo = permissionValidator.getAuthInfo(groupId, operatorId);

        // tìm giao dịch hợp lệ
        GTransaction txn = transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));

        // giao dịch điều chỉnh từ kiểm kê không được phép sửa
        if (txn.getType() == GTransactionType.ADJUSTMENT_UP || txn.getType() == GTransactionType.ADJUSTMENT_DOWN) {
            throw new BusinessException(ErrorCode.GROUP_TXN_ADJUSTMENT_NOT_EDITABLE);
        }

        // kiểm tra quyền sửa giao dịch và thời gian hợp lệ
        permissionValidator.verifyTransactionEditPermission(txn, operatorId, authInfo);
        transactionValidator.timeNotFuture(req.resolveOccurredAt());
        verifyPersonalMoneyEditable(txn, req, operatorId, authInfo);

        // tính delta hoàn tác số tiền cũ nếu trạng thái cũ là CONFIRMED
        long delta = (txn.getStatus() == GTransactionStatus.CONFIRMED)
                ? transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true)
                : 0L;

        // chụp ảnh hưởng cũ bằng giá trị trước khi strategy sửa entity
        MemberBalances before = BalanceCalculator.effectOf(txn);
        PersonalSyncHelper.Snapshot syncBefore = personalSyncHelper.snapshotOf(txn);

        // điều phối strategy tương ứng cập nhật giao dịch
        updateStrategies.stream()
                .filter(s -> s.supports(txn.getType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TXN_TYPE_NOT_ALLOWED))
                .update(txn, operatorId, groupId, req, authInfo);

        // đối chiếu trước sau để ghi đè, xoá hoặc ghi mới khoản ở sổ cá nhân
        syncLedger(syncBefore, txn, operatorId);

        // cộng dồn delta mới nếu trạng thái mới là CONFIRMED
        if (txn.getStatus() == GTransactionStatus.CONFIRMED)
            delta += transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), false);

        // lưu cơ sở dữ liệu và gửi sự kiện biến động quỹ nếu có chênh lệch
        transactionRepository.save(txn);
        memberBalanceService.applyDelta(groupId, before, BalanceCalculator.effectOf(txn));
        if (delta != 0L)
            eventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, delta));

        return transactionHelper.buildDetailRes(txn);
    }

    @Override
    public void delete(UUID operatorId, UUID groupId, UUID transactionId) {
        // xác thực người thực hiện và lấy thông tin quyền hạn
        MemberAuthInfo authInfo = permissionValidator.getAuthInfo(groupId, operatorId);

        GTransaction txn = findActiveTransaction(transactionId, groupId);

        // kiểm tra quyền xóa theo vai trò
        permissionValidator.verifyTransactionDeletePermission(txn, operatorId, authInfo);
        MemberBalances before = BalanceCalculator.effectOf(txn);

        // hoàn tác số dư quỹ nếu giao dịch đã được xác nhận
        if (txn.getStatus() == GTransactionStatus.CONFIRMED) {
            long delta = transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
            publishFundChangeEvent(groupId, delta);
        }

        // đánh dấu xóa mềm
        PersonalSyncHelper.Snapshot syncBefore = personalSyncHelper.snapshotOf(txn);
        Instant now = Instant.now();
        txn.setDeletedAt(now);
        txn.setUpdatedAt(now);

        // xoá khoản đã ghi ở sổ cá nhân nếu có
        syncLedger(syncBefore, txn, operatorId);
        transactionRepository.save(txn);
        groupTxCountHelper.decrement(groupId, txn.getStatus().isWaiting());
        memberBalanceService.applyDelta(groupId, before, NO_EFFECT);
    }

    @Override
    public long countPendingForGroup(UUID groupId) {
        return transactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(groupId, GTransactionStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnresolvedForGroup(UUID groupId) {
        return transactionRepository.countByGroupIdAndStatusInAndDeletedAtIsNull(
                groupId, List.of(GTransactionStatus.PENDING, GTransactionStatus.AWAITING_TRANSACTOR));
    }

    @Override
    @Transactional(readOnly = true)
    public long sumConfirmedAmount(UUID groupId, GTransactionType type) {
        Long total = transactionRepository.sumAmountByGroupIdAndType(groupId, type);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long sumConfirmedAmount(UUID groupId, GTransactionType type, Instant from, Instant to) {
        Long total = transactionRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, type, from, to);
        return total != null ? total : 0L;
    }

    @Override
    public GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId) {
        requireReviewer(groupId, operatorId);
        GTransaction txn = findPendingTransaction(transactionId, groupId);

        PersonalSyncHelper.Snapshot syncBefore = personalSyncHelper.snapshotOf(txn);
        updateReviewStatus(txn, GTransactionStatus.CONFIRMED, operatorId, Instant.now());
        syncLedger(syncBefore, txn, operatorId);
        groupTxCountHelper.onTransactionReviewed(groupId);
        memberBalanceService.applyDelta(groupId, NO_EFFECT, BalanceCalculator.effectOf(txn));
        publishFundChangeEvent(groupId, deltaOf(txn));

        return transactionHelper.buildDetailRes(txn);
    }

    @Override
    public GroupTransactionDetailRes reject(UUID operatorId, UUID groupId, UUID transactionId) {
        requireReviewer(groupId, operatorId);
        GTransaction txn = findPendingTransaction(transactionId, groupId);

        updateReviewStatus(txn, GTransactionStatus.REJECTED, operatorId, Instant.now());

        return transactionHelper.buildDetailRes(txn);
    }

    @Override
    public GroupTransactionDetailRes respond(UUID operatorId, UUID groupId, UUID transactionId, boolean accept) {
        // thành viên của nhóm, nhóm đã lưu trữ thì chặn vì đồng ý sẽ đổi quỹ
        permissionValidator.verifyMember(groupId, operatorId, false);

        GTransaction txn = findActiveTransaction(transactionId, groupId);

        // chống IDOR: chỉ chính người thực hiện được trả lời, người khác thấy như không tồn tại
        if (!txn.getTransactorId().equals(operatorId))
            throw new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND);

        if (txn.getStatus() != GTransactionStatus.AWAITING_TRANSACTOR)
            throw new BusinessException(ErrorCode.GROUP_TXN_NOT_AWAITING_TRANSACTOR);

        Instant now = Instant.now();
        if (!accept) {
            updateReviewStatus(txn, GTransactionStatus.REJECTED, operatorId, now);
            groupTxCountHelper.onTransactionReviewed(groupId);
            return transactionHelper.buildDetailRes(txn);
        }

        // hoàn tiền: số dư của người nhận có thể đã đổi từ lúc tạo nên kiểm lại hạn mức
        if (txn.getType() == GTransactionType.REFUND) {
            MemberBalances balances = memberBalanceService.getBalancesForUpdate(groupId, List.of(operatorId));
            GroupTransactionTypeValidator.validRefundLimit(operatorId, txn.getAmount(), balances);
        }

        // thủ quỹ/chủ nhóm đã duyệt từ lúc tạo nên người duyệt là người tạo
        PersonalSyncHelper.Snapshot syncBefore = personalSyncHelper.snapshotOf(txn);
        updateReviewStatus(txn, GTransactionStatus.CONFIRMED, txn.getCreatedBy(), now);
        syncLedger(syncBefore, txn, operatorId);
        groupTxCountHelper.onTransactionReviewed(groupId);
        memberBalanceService.applyDelta(groupId, NO_EFFECT, BalanceCalculator.effectOf(txn));
        publishFundChangeEvent(groupId, deltaOf(txn));

        return transactionHelper.buildDetailRes(txn);
    }

    /**
     * Thành viên rời nhóm hoặc bị xoá: các giao dịch đang chờ người chi trả đồng ý mà họ là người tạo
     * hoặc người thực hiện tự chuyển sang {@code REJECTED} để không treo mãi. Các khoản này chưa vào quỹ
     * và chưa vào ví nên không cần hoàn tác.
     */
    @EventListener
    public void onMemberLeave(MemberLeaveEvent event) {
        List<GTransaction> awaiting = transactionRepository.findByStatusOfMember(
                event.groupId(), GTransactionStatus.AWAITING_TRANSACTOR, event.memberId());
        if (awaiting.isEmpty())
            return;

        Instant now = Instant.now();
        awaiting.forEach(t -> updateReviewStatus(t, GTransactionStatus.REJECTED, event.ownerId(), now));
        transactionRepository.saveAll(awaiting);
        groupTxCountHelper.onTransactionsBulkReviewed(event.groupId(), awaiting.size());
    }

    @Override
    public int bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req) {
        requireReviewer(groupId, operatorId);
        List<GTransaction> txns = findPendingBatch(groupId, req);

        long totalDelta = 0L;
        MemberBalances totalEffect = NO_EFFECT;
        Instant now = Instant.now();
        for (GTransaction t : txns) {
            PersonalSyncHelper.Snapshot syncBefore = personalSyncHelper.snapshotOf(t);
            updateReviewStatus(t, GTransactionStatus.CONFIRMED, operatorId, now);
            syncLedger(syncBefore, t, operatorId);
            totalDelta += deltaOf(t);
            totalEffect = totalEffect.plus(BalanceCalculator.effectOf(t));
        }

        // gộp ảnh hưởng cả lô rồi ghi bảng tổng hợp một lần
        groupTxCountHelper.onTransactionsBulkReviewed(groupId, txns.size());
        memberBalanceService.applyDelta(groupId, NO_EFFECT, totalEffect);

        // cập nhật số dư quỹ 1 lần duy nhất cho cả lô
        publishFundChangeEvent(groupId, totalDelta);

        return txns.size();
    }

    @Override
    public int bulkReject(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req) {
        requireReviewer(groupId, operatorId);
        List<GTransaction> txns = findPendingBatch(groupId, req);

        Instant now = Instant.now();
        txns.forEach(t -> updateReviewStatus(t, GTransactionStatus.REJECTED, operatorId, now));

        return txns.size();
    }

    // -------------------------------------PRIVATE----------------------------------//

    private boolean isReviewer(MemberAuthInfo authInfo) {
        return authInfo.isOwner() || authInfo.isTreasurer();
    }

    // đối chiếu sổ cá nhân; khoản vừa vào ví mà do người khác duyệt thì báo cho người thực hiện
    private void syncLedger(PersonalSyncHelper.Snapshot before, GTransaction txn, UUID actorId) {
        boolean newlyRecorded = personalSyncHelper.sync(before, txn);
        if (newlyRecorded && !txn.getTransactorId().equals(actorId)) {
            notifier.notifyRecorded(txn);
        }
    }

    // khoản dùng tiền cá nhân của người khác: không sửa khi đang chờ đồng ý, không đổi người chi trả/số tiền/nguồn tiền,
    // không mở lại khi bị từ chối (muốn đổi thì xoá rồi tạo lại để người đó đồng ý lại)
    private void verifyPersonalMoneyEditable(GTransaction txn, GroupTransactionUpdateReq req, UUID operatorId,
                                             MemberAuthInfo authInfo) {
        if (txn.getStatus() == GTransactionStatus.AWAITING_TRANSACTOR)
            throw new BusinessException(ErrorCode.GROUP_TXN_AWAITING_NOT_EDITABLE);

        GTransactionType type = txn.getType();
        UUID newTransactorId = req.transactorId() != null ? req.transactorId() : txn.getTransactorId();
        long newAmount = req.amount() != null ? req.amount() : txn.getAmount();
        MoneySource newSource = type == GTransactionType.EXPENSE && req.moneySource() != null
                ? req.moneySource()
                : txn.getMoneySource();

        boolean moneyChanged = !newTransactorId.equals(txn.getTransactorId())
                || newAmount != txn.getAmount()
                || newSource != txn.getMoneySource();
        boolean isOthersMoneyNow =
                personalSyncHelper.requiresConsent(type, txn.getMoneySource(), txn.getTransactorId(), operatorId);
        boolean isOthersMoneyAfter =
                personalSyncHelper.requiresConsent(type, newSource, newTransactorId, operatorId);

        if (moneyChanged && (isOthersMoneyNow || isOthersMoneyAfter)) {
            if (isOthersMoneyAfter && !isReviewer(authInfo))
                throw new BusinessException(ErrorCode.GROUP_TXN_PROXY_NOT_ALLOWED);
            throw new BusinessException(ErrorCode.GROUP_TXN_PERSONAL_MONEY_LOCKED);
        }
        if (txn.getStatus() == GTransactionStatus.REJECTED && isOthersMoneyNow)
            throw new BusinessException(ErrorCode.GROUP_TXN_PERSONAL_MONEY_LOCKED);
    }

    private GTransaction findActiveTransaction(UUID transactionId, UUID groupId) {
        return transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_TRANSACTION_NOT_FOUND));
    }

    // xác thực người thực hiện đang trong nhóm và là chủ nhóm hoặc thủ quỹ, mặc định chặn nhóm đã lưu trữ
    private void requireReviewer(UUID groupId, UUID operatorId) {
        requireReviewer(groupId, operatorId, false);
    }

    private void requireReviewer(UUID groupId, UUID operatorId, boolean allowArchived) {
        MemberAuthInfo info = permissionValidator.getAuthInfo(groupId, operatorId, allowArchived);
        if (!info.isOwner() && !info.isTreasurer())
            throw new BusinessException(ErrorCode.GROUP_TREASURER_REQUIRED);
    }

    private GTransaction findPendingTransaction(UUID transactionId, UUID groupId) {
        GTransaction txn = findActiveTransaction(transactionId, groupId);
        if (txn.getStatus() != GTransactionStatus.PENDING)
            throw new BusinessException(ErrorCode.GROUP_TXN_NOT_PENDING);
        return txn;
    }

    // trả danh sách rỗng khi request không có id, ném lỗi nếu có id không tìm thấy hoặc không còn chờ duyệt
    private List<GTransaction> findPendingBatch(UUID groupId, GroupTransactionBulkReviewReq req) {
        if (req.transactionIds() == null || req.transactionIds().isEmpty())
            return List.of();

        List<UUID> distinctIds = req.transactionIds().stream().distinct().toList();
        List<GTransaction> txns = transactionRepository
                .findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(distinctIds, groupId, GTransactionStatus.PENDING);

        if (txns.size() != distinctIds.size())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return txns;
    }

    private long deltaOf(GTransaction txn) {
        return transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), false);
    }

    private void publishFundChangeEvent(UUID groupId, long delta) {
        if (delta != 0)
            eventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, delta));

    }

    private GroupTransactionListRes buildListRes(UUID groupId, GroupTransactionFilterReq filter) {
        int pageNumber = Math.max(1, filter.getPageNumber());
        int pageSize = Math.max(1, filter.getPageSize());
        Pageable pageable = PageRequest.of(
                pageNumber - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Specification<GTransaction> spec = GTransactionSpecification.filter(groupId, filter);

        // truy vấn danh sách phân trang qua custom method findList không chạy count tự động
        List<GTransaction> txns = transactionRepository.findList(spec, pageable);

        // lấy tổng số bản ghi: ưu tiên bộ đếm cache nếu chỉ phân trang mặc định
        long totalElements = isDefaultPagingFilter(filter)
                ? groupTxCountHelper.getCount(groupId, filter.excludeWaiting())
                : transactionRepository.count(spec);

        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        List<GroupTransactionDetailRes> items = transactionHelper.buildDetailResList(txns);

        PageMeta meta = new PageMeta(pageNumber, pageSize, totalElements, totalPages);
        return GroupTransactionListRes.of(items, meta);
    }

    private boolean isDefaultPagingFilter(GroupTransactionFilterReq filter) {
        return filter.moneySource() == null
                && filter.type() == null
                && filter.status() == null
                && filter.transactorId() == null
                && filter.involvedUserId() == null
                && filter.fromOccurredAt() == null
                && filter.toOccurredAt() == null
                && filter.startDate() == null
                && filter.endDate() == null;
    }

    private void updateReviewStatus(GTransaction txn, GTransactionStatus status, UUID reviewerId, Instant timestamp) {
        txn.setStatus(status);
        txn.setReviewedBy(reviewerId);
        txn.setReviewedAt(timestamp);
        txn.setUpdatedAt(timestamp);
    }
}
