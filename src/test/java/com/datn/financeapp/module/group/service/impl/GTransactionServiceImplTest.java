package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.module.group.entity.GTransaction;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.MemberLeaveEvent;
import com.datn.financeapp.module.group.helper.GTransactionBuilder;
import com.datn.financeapp.module.group.helper.GroupTxCountHelper;
import com.datn.financeapp.module.group.helper.GroupTxNotifier;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.PersonalSyncHelper;
import com.datn.financeapp.module.group.helper.TransactionHelper;
import com.datn.financeapp.module.group.repository.GroupTransactionRepository;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.service.MemberService;
import com.datn.financeapp.module.group.service.impl.GTransactionServiceImpl;
import com.datn.financeapp.module.group.validator.GroupPermissionValidator;
import com.datn.financeapp.module.group.validator.GroupTransactionPaticipantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
 * <li>{@link GTransactionServiceImpl#delete}: xoá mềm giao dịch nhóm, phân quyền theo vai trò (OWNER/THỦ QUỸ/MEMBER).</li>
 * <li>{@link GTransactionServiceImpl#confirm}, {@link GTransactionServiceImpl#reject},
 * {@link GTransactionServiceImpl#bulkConfirm}, {@link GTransactionServiceImpl#bulkReject}:
 * luồng duyệt giao dịch đang chờ.</li>
 * <li>{@link GTransactionServiceImpl#sumConfirmedAmount}, {@link GTransactionServiceImpl#findConfirmedTransactions}:
 * cộng tổng và lấy danh sách giao dịch đã xác nhận cho báo cáo nhóm.</li>
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
    private MemberBalanceService memberBalanceService;
    @Mock
    private GroupTxCountHelper groupTxCountHelper;
    @Mock
    private PersonalSyncHelper personalSyncHelper;
    @Mock
    private GroupTxNotifier notifier;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private GroupTransactionPaticipantValidator transactionValidator;

    private GTransactionServiceImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;
    private UUID treasurerId;

    @BeforeEach
    void setUp() {
        service = new GTransactionServiceImpl(transactionRepository, memberService, transactionHelper,
                eventPublisher, memberBalanceService, groupTxCountHelper, personalSyncHelper, notifier,
                permissionValidator, transactionValidator, List.of(), List.of());
        // khoản dùng tiền cá nhân (nộp quỹ, chi tiền túi, hoàn tiền) mà người gọi không phải người thực hiện thì cần đồng ý
        lenient().when(personalSyncHelper.requiresConsent(any(), any(), any(), any())).thenAnswer(inv -> {
            GTransactionType type = inv.getArgument(0);
            MoneySource source = inv.getArgument(1);
            UUID transactorId = inv.getArgument(2);
            UUID operatorId = inv.getArgument(3);
            boolean recordedToLedger = type == GTransactionType.CONTRIBUTION || type == GTransactionType.REFUND
                    || (type == GTransactionType.EXPENSE && source == MoneySource.PERSONAL);
            return recordedToLedger && !transactorId.equals(operatorId);
        });
        groupId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        treasurerId = UUID.randomUUID();
        MemberAuthInfo ownerInfo = new MemberAuthInfo(groupId, ownerId, GroupStatus.ACTIVE, true,
                MemberStatus.ACTIVE, MemberRole.OWNER, ownerId);
        MemberAuthInfo memberInfo = new MemberAuthInfo(groupId, memberId, GroupStatus.ACTIVE, true,
                MemberStatus.ACTIVE, MemberRole.MEMBER, ownerId);
        MemberAuthInfo treasurerInfo = new MemberAuthInfo(groupId, treasurerId, GroupStatus.ACTIVE, true,
                MemberStatus.ACTIVE, MemberRole.MEMBER, treasurerId);

        // hàm ghi dùng bản mặc định (chặn nhóm lưu trữ); bản 2 tham số và bản 3 tham số với false là cùng một việc,
        // nhưng mock không tự chuyển tiếp nên phải stub cả hai (hàm duyệt gọi thẳng bản 3 tham số)
        lenient().when(permissionValidator.getAuthInfo(groupId, ownerId)).thenReturn(ownerInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, memberId)).thenReturn(memberInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, treasurerId)).thenReturn(treasurerInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, ownerId, false)).thenReturn(ownerInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, memberId, false)).thenReturn(memberInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, treasurerId, false)).thenReturn(treasurerInfo);

        // hàm đọc dùng bản cho phép nhóm lưu trữ
        lenient().when(permissionValidator.getAuthInfo(groupId, ownerId, true)).thenReturn(ownerInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, memberId, true)).thenReturn(memberInfo);
        lenient().when(permissionValidator.getAuthInfo(groupId, treasurerId, true)).thenReturn(treasurerInfo);

        // gọi logic thật cho kiểm tra quyền xóa
        lenient().doCallRealMethod().when(permissionValidator)
                .verifyTransactionDeletePermission(any(), any(), any());

        // delta giả lập đúng chiều cộng: bằng số tiền giao dịch
        lenient().when(transactionHelper.calculateDelta(any(), any(), anyLong(), eq(false)))
                .thenAnswer(invocation -> invocation.<Long>getArgument(2));
        // delta giả lập hoàn tác: âm số tiền giao dịch
        lenient().when(transactionHelper.calculateDelta(any(), any(), anyLong(), eq(true)))
                .thenAnswer(invocation -> -invocation.<Long>getArgument(2));
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
    @DisplayName("Chủ nhóm xóa giao dịch đã xác nhận thì hoàn tác quỹ và xóa mềm")
    void delete_ConfirmedByOwner_ReversesFundAndSoftDeletes() {
        GTransaction confirmed = txn(500_000L);
        stubFind(confirmed);

        service.delete(ownerId, groupId, confirmed.getId());

        assertThat(confirmed.getDeletedAt()).isNotNull();
        assertThat(capturePublishedEvents())
                .containsExactly(new FundBalanceChangedEvent(groupId, -500_000L));
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch PENDING do mình tạo thì xóa mềm thành công")
    void delete_PendingByCreator_SoftDeletes() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.delete(memberId, groupId, pending.getId());

        assertThat(pending.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(pending);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch CONFIRMED do mình tạo thì bị chặn")
    void delete_ConfirmedByCreator_ThrowsForbidden() {
        GTransaction confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.delete(memberId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN.getCode()));

        assertThat(confirmed.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch PENDING do người khác tạo thì bị chặn")
    void delete_PendingByNonCreator_ThrowsForbidden() {
        GTransaction pending = pendingTxn(100_000L);
        pending.setCreatedBy(ownerId);
        stubFind(pending);

        assertThatThrownBy(() -> service.delete(memberId, groupId, pending.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN.getCode()));

        assertThat(pending.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Thủ quỹ xóa giao dịch PENDING do chính mình tạo thì bị chặn")
    void delete_PendingByTreasurer_ThrowsForbidden() {
        GTransaction pending = pendingTxn(100_000L);
        pending.setCreatedBy(treasurerId);
        stubFind(pending);

        assertThatThrownBy(() -> service.delete(treasurerId, groupId, pending.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN.getCode()));

        assertThat(pending.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Chủ nhóm duyệt giao dịch chờ thì đổi trạng thái, ghi người duyệt và cộng quỹ đúng số tiền")
    void confirm_ByOwner_MarksConfirmedAndPublishesDelta() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
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
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TREASURER_REQUIRED.getCode()));

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Giao dịch không còn ở trạng thái chờ thì duyệt bị từ chối và quỹ không đổi")
    void confirm_WhenNotPending_ThrowsConflict() {
        GTransaction confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.confirm(ownerId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_NOT_PENDING.getCode()));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Từ chối giao dịch chờ thì đổi trạng thái sang REJECTED và không đụng tới quỹ")
    void reject_MarksRejectedWithoutFundChange() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.reject(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
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
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_NOT_PENDING.getCode()));

        assertThat(confirmed.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
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
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TREASURER_REQUIRED.getCode()));

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

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.PENDING);
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
        assertThat(first.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
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

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.PENDING);
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
        assertThat(first.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
        assertThat(second.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Thành viên thường xem danh sách giao dịch chờ duyệt thì bị chặn GROUP_TREASURER_REQUIRED")
    void listPending_ByNormalMember_Forbidden() {
        assertThatThrownBy(() -> service.listPending(memberId, groupId, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_TREASURER_REQUIRED.getCode());

        verify(transactionRepository, never()).findList(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Chủ nhóm xem giao dịch chờ duyệt thì trả đúng các khoản PENDING kèm metadata phân trang")
    void listPending_ByOwner_ReturnsPendingItems() {
        GTransaction pending = pendingTxn(100_000L);
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(transactionRepository.count(any(Specification.class)))
                .thenReturn(1L);

        var res = service.listPending(ownerId, groupId, 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.meta().totalItems()).isEqualTo(1);
        verify(transactionHelper).buildDetailRes(pending);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận toàn thời gian trả đúng số repository cộng được")
    void sumConfirmedAmount_AllTime_ReturnsRepositorySum() {
        when(transactionRepository.sumAmountByGroupIdAndType(groupId, GTransactionType.EXPENSE))
                .thenReturn(2_000_000L);

        long total = service.sumConfirmedAmount(groupId, GTransactionType.EXPENSE);

        assertThat(total).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận toàn thời gian mà repository trả null thì quy về 0")
    void sumConfirmedAmount_AllTime_NullBecomesZero() {
        when(transactionRepository.sumAmountByGroupIdAndType(groupId, GTransactionType.CONTRIBUTION))
                .thenReturn(null);

        long total = service.sumConfirmedAmount(groupId, GTransactionType.CONTRIBUTION);

        assertThat(total).isZero();
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận theo kỳ thì truyền đúng khoảng thời gian xuống repository")
    void sumConfirmedAmount_Period_PassesRangeToRepository() {
        Instant from = Instant.parse("2026-08-31T17:00:00Z");
        Instant to = Instant.parse("2026-09-30T17:00:00Z");
        when(transactionRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, GTransactionType.EXPENSE, from, to))
                .thenReturn(800_000L);

        long total = service.sumConfirmedAmount(groupId, GTransactionType.EXPENSE, from, to);

        assertThat(total).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận theo kỳ mà repository trả null thì quy về 0")
    void sumConfirmedAmount_Period_NullBecomesZero() {
        Instant from = Instant.parse("2026-08-31T17:00:00Z");
        Instant to = Instant.parse("2026-09-30T17:00:00Z");
        when(transactionRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, GTransactionType.CONTRIBUTION, from, to))
                .thenReturn(null);

        long total = service.sumConfirmedAmount(groupId, GTransactionType.CONTRIBUTION, from, to);

        assertThat(total).isZero();
    }

    @Test
    @DisplayName("Xem chi tiết giao dịch chỉ cần kiểm tra là thành viên và cho phép nhóm lưu trữ")
    void detail_UsesAllowArchivedValidation() {
        GTransaction confirmed = txn(100_000L);
        stubFind(confirmed);

        service.detail(ownerId, groupId, confirmed.getId());

        verify(permissionValidator).verifyMember(groupId, ownerId, true);
    }

    @Test
    @DisplayName("Danh sách giao dịch chung dùng bản xác thực cho phép nhóm lưu trữ")
    void list_UsesAllowArchivedValidation() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.list(ownerId, groupId, new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(permissionValidator).getAuthInfo(groupId, ownerId, true);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId);
    }

    @Test
    @DisplayName("Danh sách giao dịch của tôi chỉ cần kiểm tra là thành viên và cho phép nhóm lưu trữ")
    void myList_UsesAllowArchivedValidation() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.myList(memberId, groupId, new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(permissionValidator).verifyMember(groupId, memberId, true);
    }

    @Test
    @DisplayName("Danh sách giao dịch chờ duyệt dùng bản xác thực cho phép nhóm lưu trữ")
    void listPending_UsesAllowArchivedValidation() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.listPending(ownerId, groupId, 1, 20);

        verify(permissionValidator).getAuthInfo(groupId, ownerId, true);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId);
    }

    @Test
    @DisplayName("Duyệt giao dịch là thao tác ghi nên dùng bản xác thực mặc định, nhóm lưu trữ bị chặn")
    void confirm_UsesDefaultValidationThatBlocksArchived() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        verify(permissionValidator).getAuthInfo(groupId, ownerId, false);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId, true);
    }

    // ------------------------------- ghi sổ cá nhân và thông báo -------------------------------

    @Test
    @DisplayName("Xoá giao dịch: chụp trạng thái trước, đặt xoá mềm rồi nhờ helper gỡ khoản ở sổ cá nhân")
    void delete_SyncsLedgerAfterSoftDelete() {
        GTransaction confirmed = txn(500_000L);
        stubFind(confirmed);
        PersonalSyncHelper.Snapshot snapshot = snapshotOf(confirmed);
        when(personalSyncHelper.snapshotOf(confirmed)).thenReturn(snapshot);

        service.delete(ownerId, groupId, confirmed.getId());

        verify(personalSyncHelper).sync(snapshot, confirmed);
        assertThat(confirmed.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Thủ quỹ huỷ giao dịch ghi hộ đang chờ người chi trả đồng ý do chính mình tạo thì xoá mềm được")
    void delete_AwaitingByCreatorTreasurer_SoftDeletes() {
        GTransaction awaiting = awaitingTxn(300_000L);
        stubFind(awaiting);

        service.delete(treasurerId, groupId, awaiting.getId());

        assertThat(awaiting.getDeletedAt()).isNotNull();
        verify(groupTxCountHelper).decrement(groupId, true);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Thủ quỹ huỷ giao dịch ghi hộ chờ đồng ý do người khác tạo thì bị chặn")
    void delete_AwaitingByOtherTreasurer_ThrowsForbidden() {
        GTransaction awaiting = awaitingTxn(300_000L);
        awaiting.setCreatedBy(ownerId);
        stubFind(awaiting);

        assertThatThrownBy(() -> service.delete(treasurerId, groupId, awaiting.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN.getCode()));

        assertThat(awaiting.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Duyệt giao dịch: nhờ helper ghi vào ví và báo người thực hiện khi người duyệt là người khác")
    void confirm_RecordsToLedgerAndNotifiesTransactor() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);
        PersonalSyncHelper.Snapshot snapshot = snapshotOf(pending);
        when(personalSyncHelper.snapshotOf(pending)).thenReturn(snapshot);
        when(personalSyncHelper.sync(snapshot, pending)).thenReturn(true);

        service.confirm(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
        verify(notifier).notifyRecorded(pending);
    }

    @Test
    @DisplayName("Duyệt giao dịch mà khoản không vào ví (VD chi tiền quỹ): không gửi thông báo đã ghi")
    void confirm_NotRecorded_DoesNotNotify() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);
        when(personalSyncHelper.sync(any(), eq(pending))).thenReturn(false);

        service.confirm(ownerId, groupId, pending.getId());

        verify(notifier, never()).notifyRecorded(any());
    }

    @Test
    @DisplayName("Từ chối giao dịch chờ duyệt: khoản chưa từng vào ví nên không đụng sổ cá nhân")
    void reject_DoesNotTouchLedger() {
        GTransaction pending = pendingTxn(100_000L);
        stubFind(pending);

        service.reject(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
        verify(personalSyncHelper, never()).sync(any(), any());
    }

    @Test
    @DisplayName("Duyệt hàng loạt: đối chiếu sổ cá nhân từng giao dịch")
    void bulkConfirm_SyncsLedgerForEachTransaction() {
        GTransaction first = pendingTxn(100_000L);
        GTransaction second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        service.bulkConfirm(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), second.getId())));

        verify(personalSyncHelper).sync(any(), eq(first));
        verify(personalSyncHelper).sync(any(), eq(second));
    }

    // ------------------------------- tạo giao dịch ghi hộ -------------------------------

    private GTransactionServiceImpl serviceWithStrategy(GTransactionBuilder strategy) {
        return new GTransactionServiceImpl(transactionRepository, memberService, transactionHelper,
                eventPublisher, memberBalanceService, groupTxCountHelper, personalSyncHelper, notifier,
                permissionValidator, transactionValidator, List.of(strategy), List.of());
    }

    // strategy giả: trạng thái do test chỉ định, entity dựng từ request và trạng thái nhận được
    private GTransactionBuilder strategyReturning(GTransactionStatus statusForReviewer, GTransactionStatus statusForMember) {
        GTransactionBuilder strategy = mock(GTransactionBuilder.class);
        when(strategy.supports(any())).thenReturn(true);
        when(strategy.determineStatus(any())).thenAnswer(inv -> {
            MemberAuthInfo info = inv.getArgument(0);
            return info.isOwner() || info.isTreasurer() ? statusForReviewer : statusForMember;
        });
        lenient().when(strategy.build(any(), any(), any(), any(), anyBoolean())).thenAnswer(inv -> {
            GroupTransactionCreateReq req = inv.getArgument(2);
            GTransactionStatus status = inv.getArgument(3);
            return GTransaction.builder()
                    .id(UUID.randomUUID())
                    .groupId(groupId)
                    .type(req.type())
                    .moneySource(req.resolveMoneySource())
                    .transactorId(req.transactorId())
                    .createdBy(inv.getArgument(0))
                    .status(status)
                    .amount(req.amount())
                    .participants(new ArrayList<>())
                    .occurredAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
        });
        return strategy;
    }

    private GroupTransactionCreateReq contributionReq(UUID transactorId) {
        return new GroupTransactionCreateReq(GTransactionType.CONTRIBUTION, MoneySource.PERSONAL, 300_000L,
                null, null, null, transactorId, null, List.of());
    }

    @Test
    @DisplayName("Thủ quỹ ghi hộ nộp quỹ cho member: AWAITING_TRANSACTOR, quỹ chưa đổi, báo người đó, chưa vào ví")
    void create_TreasurerOnBehalfOfMember_WaitsForTransactor() {
        GTransactionServiceImpl svc = serviceWithStrategy(
                strategyReturning(GTransactionStatus.CONFIRMED, GTransactionStatus.PENDING));
        when(memberService.allMemberInGroup(groupId, List.of(memberId))).thenReturn(true);

        svc.create(treasurerId, groupId, contributionReq(memberId));

        ArgumentCaptor<GTransaction> saved = ArgumentCaptor.forClass(GTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GTransactionStatus.AWAITING_TRANSACTOR);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(groupTxCountHelper).increment(groupId, true);
        verify(notifier).notifyTransactor(saved.getValue());
        verify(notifier, never()).notifyReviewers(any());
    }

    @Test
    @DisplayName("Thủ quỹ tự nộp quỹ cho mình: CONFIRMED ngay, quỹ đổi, không cần đồng ý")
    void create_TreasurerForSelf_ConfirmedImmediately() {
        GTransactionServiceImpl svc = serviceWithStrategy(
                strategyReturning(GTransactionStatus.CONFIRMED, GTransactionStatus.PENDING));
        when(memberService.allMemberInGroup(groupId, List.of(treasurerId))).thenReturn(true);

        svc.create(treasurerId, groupId, contributionReq(treasurerId));

        ArgumentCaptor<GTransaction> saved = ArgumentCaptor.forClass(GTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
        assertThat(capturePublishedEvents()).containsExactly(new FundBalanceChangedEvent(groupId, 300_000L));
        verify(notifier, never()).notifyTransactor(any());
        verify(notifier, never()).notifyReviewers(any());
    }

    @Test
    @DisplayName("Member tự nộp quỹ: PENDING, báo chủ nhóm/thủ quỹ duyệt")
    void create_MemberForSelf_PendingAndNotifiesReviewers() {
        GTransactionServiceImpl svc = serviceWithStrategy(
                strategyReturning(GTransactionStatus.CONFIRMED, GTransactionStatus.PENDING));
        when(memberService.allMemberInGroup(groupId, List.of(memberId))).thenReturn(true);

        svc.create(memberId, groupId, contributionReq(memberId));

        ArgumentCaptor<GTransaction> saved = ArgumentCaptor.forClass(GTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GTransactionStatus.PENDING);
        verify(groupTxCountHelper).increment(groupId, true);
        verify(notifier).notifyReviewers(saved.getValue());
    }

    @Test
    @DisplayName("Member ghi hộ nộp quỹ cho người khác: bị chặn GROUP_TXN_PROXY_NOT_ALLOWED, không lưu")
    void create_MemberOnBehalfOfOther_ThrowsProxyNotAllowed() {
        GTransactionServiceImpl svc = serviceWithStrategy(
                strategyReturning(GTransactionStatus.CONFIRMED, GTransactionStatus.PENDING));
        when(memberService.allMemberInGroup(groupId, List.of(treasurerId))).thenReturn(true);

        assertThatThrownBy(() -> svc.create(memberId, groupId, contributionReq(treasurerId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_PROXY_NOT_ALLOWED.getCode()));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thủ quỹ ghi chi tiêu bằng tiền quỹ cho member: không cần đồng ý, CONFIRMED ngay")
    void create_FundExpenseForOther_NoConsentNeeded() {
        GTransactionServiceImpl svc = serviceWithStrategy(
                strategyReturning(GTransactionStatus.CONFIRMED, GTransactionStatus.PENDING));
        when(memberService.allMemberInGroup(groupId, List.of(memberId))).thenReturn(true);
        GroupTransactionCreateReq req = new GroupTransactionCreateReq(GTransactionType.EXPENSE, MoneySource.FUND,
                200_000L, null, null, UUID.randomUUID(), memberId, null, List.of());

        svc.create(treasurerId, groupId, req);

        ArgumentCaptor<GTransaction> saved = ArgumentCaptor.forClass(GTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
    }

    // ------------------------------- người chi trả đồng ý hoặc từ chối -------------------------------

    @Test
    @DisplayName("Người chi trả đồng ý: CONFIRMED, quỹ đổi, vào ví, người duyệt là người tạo")
    void respond_Accept_ConfirmsAndAppliesFundAndLedger() {
        GTransaction awaiting = awaitingTxn(300_000L);
        stubFind(awaiting);
        PersonalSyncHelper.Snapshot snapshot = snapshotOf(awaiting);
        when(personalSyncHelper.snapshotOf(awaiting)).thenReturn(snapshot);

        service.respond(memberId, groupId, awaiting.getId(), true);

        verify(permissionValidator).verifyMember(groupId, memberId, false);
        assertThat(awaiting.getStatus()).isEqualTo(GTransactionStatus.CONFIRMED);
        assertThat(awaiting.getReviewedBy()).isEqualTo(treasurerId);
        assertThat(awaiting.getReviewedAt()).isNotNull();
        verify(personalSyncHelper).sync(snapshot, awaiting);
        verify(groupTxCountHelper).onTransactionReviewed(groupId);
        assertThat(capturePublishedEvents()).containsExactly(new FundBalanceChangedEvent(groupId, 300_000L));
        verify(notifier, never()).notifyRecorded(any());
    }

    @Test
    @DisplayName("Người chi trả từ chối: REJECTED, người duyệt là chính họ, quỹ và ví không đổi")
    void respond_Decline_RejectsWithoutFundOrLedgerChange() {
        GTransaction awaiting = awaitingTxn(300_000L);
        stubFind(awaiting);

        service.respond(memberId, groupId, awaiting.getId(), false);

        assertThat(awaiting.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
        assertThat(awaiting.getReviewedBy()).isEqualTo(memberId);
        verify(personalSyncHelper, never()).sync(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(groupTxCountHelper).onTransactionReviewed(groupId);
    }

    @Test
    @DisplayName("Người không phải người chi trả trả lời thì thấy như không tồn tại, trạng thái không đổi")
    void respond_ByAnotherUser_ThrowsNotFound() {
        GTransaction awaiting = awaitingTxn(300_000L);
        stubFind(awaiting);

        assertThatThrownBy(() -> service.respond(ownerId, groupId, awaiting.getId(), true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TRANSACTION_NOT_FOUND.getCode()));

        assertThat(awaiting.getStatus()).isEqualTo(GTransactionStatus.AWAITING_TRANSACTOR);
    }

    @Test
    @DisplayName("Trả lời giao dịch không ở trạng thái chờ người chi trả: GROUP_TXN_NOT_AWAITING_TRANSACTOR")
    void respond_NotAwaiting_Throws() {
        GTransaction confirmed = txn(300_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.respond(memberId, groupId, confirmed.getId(), true))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_NOT_AWAITING_TRANSACTOR.getCode()));

        verify(personalSyncHelper, never()).sync(any(), any());
    }

    // ------------------------------- sửa giao dịch -------------------------------

    private GroupTransactionUpdateReq updateReq(Long amount, UUID transactorId) {
        return new GroupTransactionUpdateReq(amount, null, null, null, null, transactorId, null, null);
    }

    private void assertUpdateFails(UUID operatorId, GTransaction txn, GroupTransactionUpdateReq req, ErrorCode code) {
        assertThatThrownBy(() -> service.update(operatorId, groupId, txn.getId(), req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(code.getCode()));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa giao dịch đang chờ người chi trả đồng ý: GROUP_TXN_AWAITING_NOT_EDITABLE")
    void update_Awaiting_ThrowsNotEditable() {
        GTransaction awaiting = awaitingTxn(300_000L);
        stubFind(awaiting);

        assertUpdateFails(treasurerId, awaiting, updateReq(null, null), ErrorCode.GROUP_TXN_AWAITING_NOT_EDITABLE);
    }

    @Test
    @DisplayName("Chủ nhóm đổi số tiền khoản nộp quỹ của member: GROUP_TXN_PERSONAL_MONEY_LOCKED")
    void update_OwnerChangesAmountOfMembersContribution_ThrowsLocked() {
        GTransaction confirmed = txn(300_000L);
        stubFind(confirmed);

        assertUpdateFails(ownerId, confirmed, updateReq(500_000L, null), ErrorCode.GROUP_TXN_PERSONAL_MONEY_LOCKED);
    }

    @Test
    @DisplayName("Chủ nhóm đổi người nộp của khoản nộp quỹ sang người khác: GROUP_TXN_PERSONAL_MONEY_LOCKED")
    void update_OwnerChangesTransactor_ThrowsLocked() {
        GTransaction confirmed = txn(300_000L);
        stubFind(confirmed);

        assertUpdateFails(ownerId, confirmed, updateReq(null, treasurerId), ErrorCode.GROUP_TXN_PERSONAL_MONEY_LOCKED);
    }

    @Test
    @DisplayName("Member sửa khoản của mình thành ghi hộ người khác: GROUP_TXN_PROXY_NOT_ALLOWED")
    void update_MemberTurnsOwnContributionIntoProxy_ThrowsProxyNotAllowed() {
        GTransaction pending = pendingTxn(300_000L);
        stubFind(pending);

        assertUpdateFails(memberId, pending, updateReq(null, treasurerId), ErrorCode.GROUP_TXN_PROXY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Chủ nhóm mở lại khoản nộp quỹ của member đã bị từ chối: GROUP_TXN_PERSONAL_MONEY_LOCKED")
    void update_OwnerReopensRejectedOthersContribution_ThrowsLocked() {
        GTransaction rejected = txn(300_000L);
        rejected.setStatus(GTransactionStatus.REJECTED);
        stubFind(rejected);

        assertUpdateFails(ownerId, rejected, updateReq(null, null), ErrorCode.GROUP_TXN_PERSONAL_MONEY_LOCKED);
    }

    // ------------------------------- rời nhóm, danh sách, đếm -------------------------------

    @Test
    @DisplayName("Thành viên rời nhóm: giao dịch chờ đồng ý liên quan tới họ tự REJECTED")
    void onMemberLeave_RejectsAwaitingTransactionsOfMember() {
        GTransaction awaiting = awaitingTxn(300_000L);
        when(transactionRepository.findByStatusOfMember(groupId, GTransactionStatus.AWAITING_TRANSACTOR, memberId))
                .thenReturn(List.of(awaiting));

        service.onMemberLeave(new MemberLeaveEvent(groupId, memberId, ownerId));

        assertThat(awaiting.getStatus()).isEqualTo(GTransactionStatus.REJECTED);
        assertThat(awaiting.getReviewedBy()).isEqualTo(ownerId);
        verify(transactionRepository).saveAll(List.of(awaiting));
        verify(groupTxCountHelper).onTransactionsBulkReviewed(groupId, 1);
    }

    @Test
    @DisplayName("Thành viên rời nhóm mà không có giao dịch chờ: không ghi gì")
    void onMemberLeave_NothingAwaiting_DoesNothing() {
        when(transactionRepository.findByStatusOfMember(groupId, GTransactionStatus.AWAITING_TRANSACTOR, memberId))
                .thenReturn(List.of());

        service.onMemberLeave(new MemberLeaveEvent(groupId, memberId, ownerId));

        verify(transactionRepository, never()).saveAll(any());
        verifyNoInteractions(groupTxCountHelper);
    }

    @Test
    @DisplayName("Member thường xem danh sách chung không lọc trạng thái: loại các khoản đang chờ xử lý")
    void list_MemberWithoutStatus_ExcludesWaiting() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class))).thenReturn(List.of());
        when(groupTxCountHelper.getCount(groupId, true)).thenReturn(0L);

        service.list(memberId, groupId, new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(groupTxCountHelper).getCount(groupId, true);
    }

    @Test
    @DisplayName("Thủ quỹ xem danh sách chung không lọc: thấy tất cả, dùng bộ đếm đầy đủ")
    void list_TreasurerWithoutStatus_SeesAll() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class))).thenReturn(List.of());
        when(groupTxCountHelper.getCount(groupId, false)).thenReturn(0L);

        service.list(treasurerId, groupId, new GroupTransactionFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(groupTxCountHelper).getCount(groupId, false);
    }

    @Test
    @DisplayName("Đếm giao dịch chưa xử lý xong gồm cả PENDING và AWAITING_TRANSACTOR")
    void countUnresolvedForGroup_CountsBothWaitingStatuses() {
        when(transactionRepository.countByGroupIdAndStatusInAndDeletedAtIsNull(
                groupId, List.of(GTransactionStatus.PENDING, GTransactionStatus.AWAITING_TRANSACTOR)))
                .thenReturn(3L);

        assertThat(service.countUnresolvedForGroup(groupId)).isEqualTo(3L);
    }

    private List<Object> capturePublishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private void stubFindPendingBatch(List<UUID> ids, List<GTransaction> found) {
        when(transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(
                ids, groupId, GTransactionStatus.PENDING)).thenReturn(found);
    }

    private PersonalSyncHelper.Snapshot snapshotOf(GTransaction t) {
        return new PersonalSyncHelper.Snapshot(t.getStatus(), t.getType(), t.getMoneySource(), t.getTransactorId());
    }

    // giao dịch nộp quỹ do thủ quỹ ghi hộ cho member, đang chờ member đồng ý
    private GTransaction awaitingTxn(long amount) {
        GTransaction awaiting = txn(amount);
        awaiting.setStatus(GTransactionStatus.AWAITING_TRANSACTOR);
        awaiting.setCreatedBy(treasurerId);
        return awaiting;
    }

    private GTransaction pendingTxn(long amount) {
        GTransaction pending = txn(amount);
        pending.setStatus(GTransactionStatus.PENDING);
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
                .type(GTransactionType.CONTRIBUTION)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(memberId)
                .createdBy(memberId)
                .status(GTransactionStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }
}
