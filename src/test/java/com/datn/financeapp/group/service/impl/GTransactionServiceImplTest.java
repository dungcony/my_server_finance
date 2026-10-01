package com.datn.financeapp.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.entity.GTransaction;
import com.datn.financeapp.group.enums.*;
import com.datn.financeapp.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.group.helper.MemberAuthInfo;
import com.datn.financeapp.group.helper.TransactionHelper;
import com.datn.financeapp.group.repository.GroupTransactionRepository;
import com.datn.financeapp.group.service.MemberService;
import com.datn.financeapp.group.validator.GroupPermissionValidator;
import com.datn.financeapp.group.validator.GroupTransactionPaticipantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử {@link GTransactionServiceImpl}:
 * <ul>
 * <li>{@link GTransactionServiceImpl#delete}: chủ nhóm xoá mềm giao dịch nhóm.</li>
 * <li>{@link GTransactionServiceImpl#confirm}, {@link GTransactionServiceImpl#reject},
 * {@link GTransactionServiceImpl#bulkConfirm}, {@link GTransactionServiceImpl#bulkReject}:
 * luồng duyệt giao dịch đang chờ.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GTransactionServiceImplTest {

    @Mock
    private GroupTransactionRepository transactionRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private TransactionHelper transactionHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private GroupTransactionPaticipantValidator transactionValidator;

    private GTransactionServiceImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        service = new GTransactionServiceImpl(transactionRepository, memberService, transactionHelper,
                eventPublisher, permissionValidator, transactionValidator, List.of(), List.of());
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        lenient().when(permissionValidator.getAuthInfo(groupId, ownerId)).thenReturn(
                new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE,
                        MemberRole.OWNER, ownerId));
        lenient().when(permissionValidator.getAuthInfo(groupId, memberId)).thenReturn(
                new MemberAuthInfo(groupId, memberId, GroupStatus.ACTIVE, true, MemberStatus.ACTIVE,
                        MemberRole.MEMBER, ownerId));
        // delta giả lập đúng chiều cộng: bằng số tiền giao dịch
        lenient().when(transactionHelper.calculateDelta(any(), any(), anyLong(), eq(false)))
                .thenAnswer(invocation -> invocation.<Long>getArgument(2));
    }

    @Test
    @DisplayName("Chủ nhóm xoá khoản góp thì đánh dấu xoá mềm và lưu lại")
    void delete_Contribution_SoftDeletes() {
        GTransaction contribution = txn(1_000_000L);
        stubFind(contribution);

        service.delete(ownerId, groupId, contribution.getId());

        assertThat(contribution.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(contribution);
    }

    @Test
    @DisplayName("Chủ nhóm duyệt giao dịch chờ thì đổi trạng thái, ghi người duyệt và cộng quỹ đúng số tiền")
    void confirm_ByOwner_MarksConfirmedAndPublishesDelta() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(pending.getReviewedBy()).isEqualTo(ownerId);
        assertThat(pending.getReviewedAt()).isNotNull();
        assertThat(capturePublishedEvents()).containsExactly(new FundBalanceChangedEvent(groupId, 100_000L));
    }

    @Test
    @DisplayName("Thành viên thường không duyệt được giao dịch và quỹ không đổi")
    void confirm_ByPlainMember_ThrowsForbidden() {
        GTransaction pending = pendingTxn(100_000L);

        assertThatThrownBy(() -> service.confirm(memberId, groupId, pending.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FORBIDDEN_TREASURER_REQUIRED.getCode()));

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Giao dịch không còn ở trạng thái chờ thì duyệt bị từ chối và quỹ không đổi")
    void confirm_WhenNotPending_ThrowsConflict() {
        GTransaction confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.confirm(ownerId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_PENDING.getCode()));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Từ chối giao dịch chờ thì đổi trạng thái sang REJECTED và không đụng tới quỹ")
    void reject_MarksRejectedWithoutFundChange() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.reject(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(pending.getReviewedBy()).isEqualTo(ownerId);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Từ chối giao dịch không còn ở trạng thái chờ thì bị chặn")
    void reject_WhenNotPending_ThrowsConflict() {
        GTransaction confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.reject(ownerId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_PENDING.getCode()));

        assertThat(confirmed.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Duyệt hàng loạt mà danh sách id rỗng thì trả 0 và không đụng tới quỹ")
    void bulkConfirm_EmptyIds_ReturnsZeroWithoutEvent() {
        int count = service.bulkConfirm(ownerId, groupId, new GroupTransactionBulkReviewReq(List.of()));

        assertThat(count).isZero();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Duyệt hàng loạt bởi thành viên thường thì bị chặn trước khi truy vấn giao dịch")
    void bulkConfirm_ByPlainMember_ThrowsForbidden() {
        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.bulkConfirm(memberId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FORBIDDEN_TREASURER_REQUIRED.getCode()));

        verify(transactionRepository, never())
                .findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Duyệt hàng loạt mà có id không còn ở trạng thái chờ thì báo lỗi và không đổi giao dịch nào")
    void bulkConfirm_SomeIdsNotPending_ThrowsValidationError() {
        GTransaction pending = pendingTxn(100_000L);
        UUID missingId = UUID.randomUUID();
        stubFindPendingBatch(List.of(pending.getId(), missingId), List.of(pending));

        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(pending.getId(), missingId));

        assertThatThrownBy(() -> service.bulkConfirm(ownerId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Duyệt hàng loạt có id trùng thì mỗi giao dịch duyệt một lần và quỹ chỉ nhận một sự kiện với tổng delta")
    void bulkConfirm_DuplicateIds_ConfirmsOnceAndPublishesSingleSummedEvent() {
        GTransaction first = pendingTxn(100_000L);
        GTransaction second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        int count = service.bulkConfirm(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), first.getId(), second.getId())));

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(first.getReviewedBy()).isEqualTo(ownerId);
        assertThat(capturePublishedEvents()).containsExactly(new FundBalanceChangedEvent(groupId, 350_000L));
    }

    @Test
    @DisplayName("Từ chối hàng loạt mà danh sách id rỗng thì trả 0")
    void bulkReject_EmptyIds_ReturnsZero() {
        int count = service.bulkReject(ownerId, groupId, new GroupTransactionBulkReviewReq(List.of()));

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Từ chối hàng loạt mà có id không còn ở trạng thái chờ thì báo lỗi")
    void bulkReject_SomeIdsNotPending_ThrowsValidationError() {
        GTransaction pending = pendingTxn(100_000L);
        UUID missingId = UUID.randomUUID();
        stubFindPendingBatch(List.of(pending.getId(), missingId), List.of(pending));

        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(pending.getId(), missingId));

        assertThatThrownBy(() -> service.bulkReject(ownerId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Từ chối hàng loạt thì tất cả sang REJECTED và không đụng tới quỹ")
    void bulkReject_MarksAllRejectedWithoutFundChange() {
        GTransaction first = pendingTxn(100_000L);
        GTransaction second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        int count = service.bulkReject(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), second.getId())));

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(second.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Thành viên thường xem danh sách giao dịch chờ duyệt thì bị chặn FORBIDDEN_TREASURER_REQUIRED")
    void listPending_ByNormalMember_Forbidden() {
        assertThatThrownBy(() -> service.listPending(memberId, groupId, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN_TREASURER_REQUIRED.getCode());

        verify(transactionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Chủ nhóm xem giao dịch chờ duyệt thì trả đúng các khoản PENDING kèm metadata phân trang")
    void listPending_ByOwner_ReturnsPendingItems() {
        GTransaction pending = pendingTxn(100_000L);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending)));

        var res = service.listPending(ownerId, groupId, 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.meta().totalItems()).isEqualTo(1);
        verify(transactionHelper).buildDetailRes(pending);
    }

    private List<Object> capturePublishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private void stubFindPendingBatch(List<UUID> ids, List<GTransaction> found) {
        when(transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(
                ids, groupId, TransactionStatus.PENDING)).thenReturn(found);
    }

    private GTransaction pendingTxn(long amount) {
        GTransaction pending = txn(amount);
        pending.setStatus(TransactionStatus.PENDING);
        return pending;
    }

    private void stubFind(GTransaction txn) {
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txn.getId(), groupId))
                .thenReturn(java.util.Optional.of(txn));
    }

    private GTransaction txn(long amount) {
        Instant now = Instant.now();
        return GTransaction.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .type(TransactionType.CONTRIBUTION)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(memberId)
                .createdBy(memberId)
                .status(TransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }
}
