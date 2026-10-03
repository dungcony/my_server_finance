package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.response.transaction.GroupTransactionListRes;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.GTransactionStatus;
import com.datn.financeapp.group.enums.GTransactionType;
import com.datn.financeapp.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.group.helper.GTransactionBuilder;
import com.datn.financeapp.group.helper.GTransactionUpdate;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.repository.specification.GTransactionSpecification;
import com.datn.financeapp.group.service.*;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
public class GTransactionServiceImpl implements GTransactionService, GTransactionReviewService {

    private final GroupTransactionRepository transactionRepository;
    private final MemberService memberService;
    private final TransactionHelper transactionHelper;
    private final ApplicationEventPublisher eventPublisher;

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
        if (!memberService.allMemberInGroup(groupId, List.of(req.transactorId()))) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_EXTSIS_NOT_IN);
        }

        // tìm strategy phụ trách loại giao dịch này
        GTransactionBuilder strategy = createStrategies.stream()
                .filter(s -> s.supports(req.type()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED));

        // lấy trạng thái kiểm duyệt dựa vào quyền người thực hiện
        GTransactionStatus status = strategy.determineStatus(authInfo);

        // điều phối tới đúng strategy phụ trách loại giao dịch để xây dựng entity
        GTransaction gTransaction = strategy.build(operatorId, groupId, req, status, authInfo.isSettlementEnabled());

        transactionRepository.save(gTransaction);

        // cập nhật số dư quỹ nếu giao dịch được duyệt tự động
        if (status == GTransactionStatus.CONFIRMED) {
            publishFundChangeEvent(
                    groupId, transactionHelper.calculateDelta(
                            gTransaction.getType(),
                            gTransaction.getMoneySource(),
                            gTransaction.getAmount(),
                            false));
        }

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
    public GroupTransactionListRes list(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter) {
        // xác thực người thực hiện và lấy thông tin quyền hạn, nhóm đã lưu trữ vẫn xem được
        MemberAuthInfo authInfo = permissionValidator.getAuthInfo(groupId, operatorId, true);

        // member thường không được thấy giao dịch chờ duyệt trong danh sách chung
        boolean isReviewer = authInfo.isOwner() || authInfo.isTreasurer();
        if (!isReviewer && filter.status() == null) {
            filter = filter.withExcludeStatus(GTransactionStatus.PENDING);
        }

        return buildListRes(groupId, filter);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTransactionListRes myList(UUID operatorId, UUID groupId, GroupTransactionFilterReq filter) {
        // xác thực người thực hiện đang trong nhóm, nhóm đã lưu trữ vẫn xem được
        permissionValidator.verifyMember(groupId, operatorId, true);

        // ép điều kiện chỉ lấy giao dịch do chính mình tạo
        GroupTransactionFilterReq myFilter = filter.withCreatedBy(operatorId);

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
            throw new BusinessException(ErrorCode.ADJUSTMENT_NOT_EDITABLE);
        }

        // kiểm tra quyền sửa giao dịch và thời gian hợp lệ
        permissionValidator.verifyTransactionEditPermission(txn, operatorId, authInfo);
        transactionValidator.timeNotFuture(req.resolveOccurredAt());

        // tính delta hoàn tác số tiền cũ nếu trạng thái cũ là CONFIRMED
        long delta = (txn.getStatus() == GTransactionStatus.CONFIRMED)
                ? transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true)
                : 0L;

        // điều phối strategy tương ứng cập nhật giao dịch
        updateStrategies.stream()
                .filter(s -> s.supports(txn.getType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_TYPE_NOT_ALLOWED))
                .update(txn, operatorId, groupId, req, authInfo);

        // cộng dồn delta mới nếu trạng thái mới là CONFIRMED
        if (txn.getStatus() == GTransactionStatus.CONFIRMED)
            delta += transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), false);

        // lưu cơ sở dữ liệu và gửi sự kiện biến động quỹ nếu có chênh lệch
        transactionRepository.save(txn);
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

        // hoàn tác số dư quỹ nếu giao dịch đã được xác nhận
        if (txn.getStatus() == GTransactionStatus.CONFIRMED) {
            long delta = transactionHelper.calculateDelta(txn.getType(), txn.getMoneySource(), txn.getAmount(), true);
            publishFundChangeEvent(groupId, delta);
        }

        // đánh dấu xóa mềm
        Instant now = Instant.now();
        txn.setDeletedAt(now);
        txn.setUpdatedAt(now);
        transactionRepository.save(txn);
    }

    @Override
    public long countPendingForGroup(UUID groupId) {
        return transactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(groupId, GTransactionStatus.PENDING);
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
    @Transactional(readOnly = true)
    public List<GTransaction> findConfirmedTransactions(UUID groupId) {
        return transactionRepository.findByGroupIdAndStatusAndDeletedAtIsNullOrderByOccurredAtAscCreatedAtAsc(
                groupId, GTransactionStatus.CONFIRMED);
    }

    @Override
    public GroupTransactionDetailRes confirm(UUID operatorId, UUID groupId, UUID transactionId) {
        requireReviewer(groupId, operatorId);
        GTransaction txn = findPendingTransaction(transactionId, groupId);

        updateReviewStatus(txn, GTransactionStatus.CONFIRMED, operatorId, Instant.now());
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
    public int bulkConfirm(UUID operatorId, UUID groupId, GroupTransactionBulkReviewReq req) {
        requireReviewer(groupId, operatorId);
        List<GTransaction> txns = findPendingBatch(groupId, req);

        long totalDelta = 0L;
        Instant now = Instant.now();
        for (GTransaction t : txns) {
            updateReviewStatus(t, GTransactionStatus.CONFIRMED, operatorId, now);
            totalDelta += deltaOf(t);
        }

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
            throw new BusinessException(ErrorCode.FORBIDDEN_TREASURER_REQUIRED);
    }

    private GTransaction findPendingTransaction(UUID transactionId, UUID groupId) {
        GTransaction txn = findActiveTransaction(transactionId, groupId);
        if (txn.getStatus() != GTransactionStatus.PENDING)
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_PENDING);
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
        Page<GTransaction> txnPage = getPage(groupId, filter);

        List<GroupTransactionDetailRes> items = txnPage.getContent()
                .stream()
                .map(transactionHelper::buildDetailRes)
                .toList();

        PageMeta meta = new PageMeta(
                filter.getPageNumber(),
                filter.getPageSize(),
                txnPage.getTotalElements(),
                txnPage.getTotalPages());

        return GroupTransactionListRes.of(items, meta);
    }

    private Page<GTransaction> getPage(UUID groupId, GroupTransactionFilterReq filter) {
        Pageable pageable = PageRequest.of(
                Math.max(0, filter.getPageNumber() - 1),
                filter.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        // gọi qua specification
        Specification<GTransaction> spec = GTransactionSpecification.filter(groupId, filter);

        return transactionRepository.findAll(spec, pageable);
    }

    private void updateReviewStatus(GTransaction txn, GTransactionStatus status, UUID reviewerId, Instant timestamp) {
        txn.setStatus(status);
        txn.setReviewedBy(reviewerId);
        txn.setReviewedAt(timestamp);
        txn.setUpdatedAt(timestamp);
    }
}
