package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.*;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.helper.GroupTxnHelper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import com.datn.financeapp.module.group.validator.GroupTransactionParticipantValidator;
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
 * Kiểm thử {@link GroupTxnGetImpl}:
 * <ul>
 * <li>{@link GroupTxnGetImpl#delete}: xoá mềm giao dịch nhóm, phân quyền theo vai trò (OWNER/THỦ QUỸ/MEMBER).</li>
 * <li>{@link GroupTxnGetImpl#confirm}, {@link GroupTxnGetImpl#reject},
 * {@link GroupTxnGetImpl#bulkConfirm}, {@link GroupTxnGetImpl#bulkReject}:
 * luồng duyệt giao dịch đang chờ.</li>
 * <li>{@link GroupTxnGetImpl#sumConfirmedAmount}, {@link GroupTxnGetImpl#findConfirmedTransactions}:
 * cộng tổng và lấy danh sách giao dịch đã xác nhận cho báo cáo nhóm.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GroupTxnMapperImplTest {

    @Mock
    private GroupTxnRepository transactionRepository;
    @Mock
    private GroupTxnHelper groupTxnHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MemberBalanceService memberBalanceService;
    @Mock
    private GroupTxnCache groupTxnCache;
    @Mock
    private GroupPermissionValidator permissionValidator;
    @Mock
    private GroupTransactionParticipantValidator transactionValidator;

    private GroupTxnGetImpl service;

    private UUID groupId;
    private UUID ownerId;
    private UUID memberId;
    private UUID treasurerId;

    @BeforeEach
    void setUp() {
        service = new GroupTxnGetImpl(transactionRepository, memberBalanceService, groupTxnHelper,
                groupTxnCache, eventPublisher, permissionValidator, transactionValidator, List.of());
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
        lenient().when(groupTxnHelper.calculateDelta(any(), any(), anyLong(), eq(false)))
                .thenAnswer(invocation -> invocation.<Long>getArgument(2));
        // delta giả lập hoàn tác: âm số tiền giao dịch
        lenient().when(groupTxnHelper.calculateDelta(any(), any(), anyLong(), eq(true)))
                .thenAnswer(invocation -> -invocation.<Long>getArgument(2));
    }

    @Test
    @DisplayName("Chủ nhóm xoá khoản góp thì đánh dấu xoá mềm và lưu lại")
    void delete_Contribution_SoftDeletes() {
        GroupTxn contribution = txn(1_000_000L);
        stubFind(contribution);

        service.delete(ownerId, groupId, contribution.getId());

        assertThat(contribution.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(contribution);
    }

    @Test
    @DisplayName("Chủ nhóm xóa giao dịch đã xác nhận thì hoàn tác quỹ và xóa mềm")
    void delete_ConfirmedByOwner_ReversesFundAndSoftDeletes() {
        GroupTxn confirmed = txn(500_000L);
        stubFind(confirmed);

        service.delete(ownerId, groupId, confirmed.getId());

        assertThat(confirmed.getDeletedAt()).isNotNull();
        assertThat(capturePublishedEvents(FundBalanceChangedEvent.class))
                .containsExactly(new FundBalanceChangedEvent(groupId, -500_000L));
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch PENDING do mình tạo thì xóa mềm thành công")
    void delete_PendingByCreator_SoftDeletes() {
        GroupTxn pending = pendingTxn(100_000L);
        stubFind(pending);

        service.delete(memberId, groupId, pending.getId());

        assertThat(pending.getDeletedAt()).isNotNull();
        verify(transactionRepository).save(pending);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch CONFIRMED do mình tạo thì bị chặn")
    void delete_ConfirmedByCreator_ThrowsForbidden() {
        GroupTxn confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.delete(memberId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_DELETE_FORBIDDEN.getCode()));

        assertThat(confirmed.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("Thành viên xóa giao dịch PENDING do người khác tạo thì bị chặn")
    void delete_PendingByNonCreator_ThrowsForbidden() {
        GroupTxn pending = pendingTxn(100_000L);
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
        GroupTxn pending = pendingTxn(100_000L);
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
        GroupTxn pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(pending.getReviewedBy()).isEqualTo(ownerId);
        assertThat(pending.getReviewedAt()).isNotNull();
        assertThat(capturePublishedEvents(FundBalanceChangedEvent.class)).containsExactly(new FundBalanceChangedEvent(groupId, 100_000L));
    }

    @Test
    @DisplayName("Thành viên thường không duyệt được giao dịch và quỹ không đổi")
    void confirm_ByPlainMember_ThrowsForbidden() {
        GroupTxn pending = pendingTxn(100_000L);

        assertThatThrownBy(() -> service.confirm(memberId, groupId, pending.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TREASURER_REQUIRED.getCode()));

        assertThat(pending.getStatus()).isEqualTo(GroupTxnStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Giao dịch không còn ở trạng thái chờ thì duyệt bị từ chối và quỹ không đổi")
    void confirm_WhenNotPending_ThrowsConflict() {
        GroupTxn confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.confirm(ownerId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_NOT_PENDING.getCode()));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Từ chối giao dịch chờ thì đổi trạng thái sang REJECTED và không đụng tới quỹ")
    void reject_MarksRejectedWithoutFundChange() {
        GroupTxn pending = pendingTxn(100_000L);
        stubFind(pending);

        service.reject(ownerId, groupId, pending.getId());

        assertThat(pending.getStatus()).isEqualTo(GroupTxnStatus.REJECTED);
        assertThat(pending.getReviewedBy()).isEqualTo(ownerId);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Từ chối giao dịch không còn ở trạng thái chờ thì bị chặn")
    void reject_WhenNotPending_ThrowsConflict() {
        GroupTxn confirmed = txn(100_000L);
        stubFind(confirmed);

        assertThatThrownBy(() -> service.reject(ownerId, groupId, confirmed.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GROUP_TXN_NOT_PENDING.getCode()));

        assertThat(confirmed.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
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
        GroupTxn pending = pendingTxn(100_000L);
        UUID missingId = UUID.randomUUID();
        stubFindPendingBatch(List.of(pending.getId(), missingId), List.of(pending));

        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(pending.getId(), missingId));

        assertThatThrownBy(() -> service.bulkConfirm(ownerId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(pending.getStatus()).isEqualTo(GroupTxnStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Duyệt hàng loạt có id trùng thì mỗi giao dịch duyệt một lần và quỹ chỉ nhận một sự kiện với tổng delta")
    void bulkConfirm_DuplicateIds_ConfirmsOnceAndPublishesSingleSummedEvent() {
        GroupTxn first = pendingTxn(100_000L);
        GroupTxn second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        int count = service.bulkConfirm(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), first.getId(), second.getId())));

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(GroupTxnStatus.CONFIRMED);
        assertThat(first.getReviewedBy()).isEqualTo(ownerId);
        assertThat(capturePublishedEvents(FundBalanceChangedEvent.class)).containsExactly(new FundBalanceChangedEvent(groupId, 350_000L));
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
        GroupTxn pending = pendingTxn(100_000L);
        UUID missingId = UUID.randomUUID();
        stubFindPendingBatch(List.of(pending.getId(), missingId), List.of(pending));

        GroupTransactionBulkReviewReq req = new GroupTransactionBulkReviewReq(List.of(pending.getId(), missingId));

        assertThatThrownBy(() -> service.bulkReject(ownerId, groupId, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode()));

        assertThat(pending.getStatus()).isEqualTo(GroupTxnStatus.PENDING);
    }

    @Test
    @DisplayName("Từ chối hàng loạt thì tất cả sang REJECTED và không đụng tới quỹ")
    void bulkReject_MarksAllRejectedWithoutFundChange() {
        GroupTxn first = pendingTxn(100_000L);
        GroupTxn second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        int count = service.bulkReject(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), second.getId())));

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(GroupTxnStatus.REJECTED);
        assertThat(second.getStatus()).isEqualTo(GroupTxnStatus.REJECTED);
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
        GroupTxn pending = pendingTxn(100_000L);
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(transactionRepository.count(any(Specification.class)))
                .thenReturn(1L);

        var res = service.listPending(ownerId, groupId, 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.meta().totalItems()).isEqualTo(1);
        verify(groupTxnHelper).buildDetailRes(pending);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận toàn thời gian trả đúng số repository cộng được")
    void sumConfirmedAmount_AllTime_ReturnsRepositorySum() {
        when(transactionRepository.sumAmountByGroupIdAndType(groupId, GroupTxnType.EXPENSE))
                .thenReturn(2_000_000L);

        long total = service.sumConfirmedAmount(groupId, GroupTxnType.EXPENSE);

        assertThat(total).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận toàn thời gian mà repository trả null thì quy về 0")
    void sumConfirmedAmount_AllTime_NullBecomesZero() {
        when(transactionRepository.sumAmountByGroupIdAndType(groupId, GroupTxnType.CONTRIBUTION))
                .thenReturn(null);

        long total = service.sumConfirmedAmount(groupId, GroupTxnType.CONTRIBUTION);

        assertThat(total).isZero();
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận theo kỳ thì truyền đúng khoảng thời gian xuống repository")
    void sumConfirmedAmount_Period_PassesRangeToRepository() {
        Instant from = Instant.parse("2026-08-31T17:00:00Z");
        Instant to = Instant.parse("2026-09-30T17:00:00Z");
        when(transactionRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, GroupTxnType.EXPENSE, from, to))
                .thenReturn(800_000L);

        long total = service.sumConfirmedAmount(groupId, GroupTxnType.EXPENSE, from, to);

        assertThat(total).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("Tổng giao dịch đã xác nhận theo kỳ mà repository trả null thì quy về 0")
    void sumConfirmedAmount_Period_NullBecomesZero() {
        Instant from = Instant.parse("2026-08-31T17:00:00Z");
        Instant to = Instant.parse("2026-09-30T17:00:00Z");
        when(transactionRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, GroupTxnType.CONTRIBUTION, from, to))
                .thenReturn(null);

        long total = service.sumConfirmedAmount(groupId, GroupTxnType.CONTRIBUTION, from, to);

        assertThat(total).isZero();
    }

    @Test
    @DisplayName("Xem chi tiết giao dịch chỉ cần kiểm tra là thành viên và cho phép nhóm lưu trữ")
    void detail_UsesAllowArchivedValidation() {
        GroupTxn confirmed = txn(100_000L);
        stubFind(confirmed);

        service.detail(ownerId, groupId, confirmed.getId());

        verify(permissionValidator).verifyMember(groupId, ownerId, true);
    }

    @Test
    @DisplayName("Danh sách giao dịch chung dùng bản xác thực cho phép nhóm lưu trữ")
    void list_UsesAllowArchivedValidation() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.list(ownerId, groupId, new GroupTxnFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(permissionValidator).getAuthInfo(groupId, ownerId, true);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId);
    }

    @Test
    @DisplayName("Danh sách giao dịch của tôi chỉ cần kiểm tra là thành viên và cho phép nhóm lưu trữ")
    void myList_UsesAllowArchivedValidation() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.myList(memberId, groupId, new GroupTxnFilterReq(null, null, null, null, null, null, null, null, 1, 20));

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
        GroupTxn pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        verify(permissionValidator).getAuthInfo(groupId, ownerId, false);
        verify(permissionValidator, never()).getAuthInfo(groupId, ownerId, true);
    }

    // ------------------------------- event hỏi ghi sổ cá nhân -------------------------------

    @Test
    @DisplayName("Duyệt giao dịch: phát GroupTxnConfirmedEvent để hỏi người liên quan có muốn ghi sổ cá nhân")
    void confirm_PublishesConfirmedEvent() {
        GroupTxn pending = pendingTxn(100_000L);
        stubFind(pending);

        service.confirm(ownerId, groupId, pending.getId());

        assertThat(capturePublishedEvents(GroupTxnConfirmedEvent.class))
                .extracting(GroupTxnConfirmedEvent::groupTxnId)
                .containsExactly(pending.getId());
    }

    @Test
    @DisplayName("Duyệt hàng loạt: mỗi giao dịch phát một GroupTxnConfirmedEvent")
    void bulkConfirm_PublishesConfirmedEventForEachTransaction() {
        GroupTxn first = pendingTxn(100_000L);
        GroupTxn second = pendingTxn(250_000L);
        stubFindPendingBatch(List.of(first.getId(), second.getId()), List.of(first, second));

        service.bulkConfirm(ownerId, groupId,
                new GroupTransactionBulkReviewReq(List.of(first.getId(), second.getId())));

        assertThat(capturePublishedEvents(GroupTxnConfirmedEvent.class))
                .extracting(GroupTxnConfirmedEvent::groupTxnId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    // ------------------------------- danh sách, đếm -------------------------------

    @Test
    @DisplayName("Member thường xem danh sách chung không lọc trạng thái: loại các khoản đang chờ xử lý")
    void list_MemberWithoutStatus_ExcludesWaiting() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class))).thenReturn(List.of());
        when(groupTxnCache.getCount(groupId, true)).thenReturn(0L);

        service.list(memberId, groupId, new GroupTxnFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(groupTxnCache).getCount(groupId, true);
    }

    @Test
    @DisplayName("Thủ quỹ xem danh sách chung không lọc: thấy tất cả, dùng bộ đếm đầy đủ")
    void list_TreasurerWithoutStatus_SeesAll() {
        when(transactionRepository.findList(any(Specification.class), any(Pageable.class))).thenReturn(List.of());
        when(groupTxnCache.getCount(groupId, false)).thenReturn(0L);

        service.list(treasurerId, groupId, new GroupTxnFilterReq(null, null, null, null, null, null, null, null, 1, 20));

        verify(groupTxnCache).getCount(groupId, false);
    }

    @Test
    @DisplayName("Đếm giao dịch chưa xử lý xong chỉ tính các khoản PENDING")
    void countUnresolvedForGroup_CountsPending() {
        when(transactionRepository.countByGroupIdAndStatusAndDeletedAtIsNull(groupId, GroupTxnStatus.PENDING))
                .thenReturn(3L);

        assertThat(service.countUnresolvedForGroup(groupId)).isEqualTo(3L);
    }

    // các event đã phát thuộc đúng một loại
    private <T> List<T> capturePublishedEvents(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }

    private void stubFindPendingBatch(List<UUID> ids, List<GroupTxn> found) {
        when(transactionRepository.findByIdInAndGroupIdAndDeletedAtIsNullAndStatus(
                ids, groupId, GroupTxnStatus.PENDING)).thenReturn(found);
    }

    private GroupTxn pendingTxn(long amount) {
        GroupTxn pending = txn(amount);
        pending.setStatus(GroupTxnStatus.PENDING);
        return pending;
    }

    private void stubFind(GroupTxn txn) {
        when(transactionRepository.findByIdAndGroupIdAndDeletedAtIsNull(txn.getId(), groupId))
                .thenReturn(java.util.Optional.of(txn));
    }

    private GroupTxn txn(long amount) {
        Instant now = Instant.now();
        return GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .type(GroupTxnType.CONTRIBUTION)
                .moneySource(MoneySource.PERSONAL)
                .transactorId(memberId)
                .createdBy(memberId)
                .status(GroupTxnStatus.CONFIRMED)
                .amount(amount)
                .participants(new ArrayList<>())
                .occurredAt(now)
                .createdAt(now)
                .build();
    }
}
