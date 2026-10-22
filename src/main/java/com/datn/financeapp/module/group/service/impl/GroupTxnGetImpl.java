package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.response.PageMeta;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnFilterReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnListRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnParticipantRes;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnSumaryRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.helper.GroupTxnSpecification;
import com.datn.financeapp.module.group.helper.MemberAuthCache;
import com.datn.financeapp.module.group.helper.MemberAuthInfo;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.GroupTxnGetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class GroupTxnGetImpl implements GroupTxnGetService {

    private final GroupTxnRepository groupTxnRepository;
    private final GroupTxnMapper groupTxnMapper;
    private final MemberAuthCache memberAuthCache;


    @Override
    @Transactional(readOnly = true)
    public GroupTxnListRes all(UUID groupId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter, Pageable pageable) {

        UUID operatorId = SecurityContextUtil.currentUserId();

        // xác thực người thực hiện đang trong group, nhóm đã lưu trữ vẫn xem được
        memberAuthCache.get(groupId, operatorId)
                .filter(m -> !m.isMember()) // k dành cho member
                .filter(MemberAuthInfo::inGroupNotDeleted) // lưu trữ vẫn xem được
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        return buildListRes(groupId, null, statuses, filter, pageable);
    }

    @Override
    public GroupTxnListRes generalList(UUID groupId, GroupTxnFilterReq filter, Pageable pageable) {
        UUID operatorId = SecurityContextUtil.currentUserId();

        // xác định có trong group
        memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupNotDeleted) // lưu trữ vẫn xem được
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        return buildListRes(groupId, null, List.of(GroupTxnStatus.CONFIRMED), filter, pageable);
    }

    @Override
    public GroupTxnListRes myList(UUID groupId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter, Pageable pageable) {
        UUID operatorId = SecurityContextUtil.currentUserId();

        // xác định có trong group
        memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupNotDeleted) // lưu trữ vẫn xem được
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        return buildListRes(groupId, operatorId, statuses, filter, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupTxnDetailRes detail(UUID groupId, UUID transactionId) {

        UUID operatorId = SecurityContextUtil.currentUserId();

        // xác thực người thực hiện đang trong group, nhóm đã lưu trữ vẫn xem được
        MemberAuthInfo authInfo = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupNotDeleted) // lưu trữ vẫn xem được
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        GroupTxn txn = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // member là người tạo hoặc là người thực hiện giao dịch
        if (!authInfo.isMember() || authInfo.isCreatorOrTransactorTxn(txn) || txn.getStatus() == GroupTxnStatus.CONFIRMED) {
            return groupTxnMapper.toDetailResponse(txn);
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupTxnParticipantRes> getParticipants(UUID groupId, UUID transactionId) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        // xác thực người thực hiện đang trong group, nhóm đã lưu trữ vẫn xem được
        MemberAuthInfo authInfo = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupNotDeleted) // lưu trữ vẫn xem được
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        GroupTxn txn = groupTxnRepository.findByIdAndGroupIdAndDeletedAtIsNull(transactionId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // member là người tạo hoặc là người thực hiện giao dịch
        if (!authInfo.isMember() || authInfo.isCreatorOrTransactorTxn(txn) || txn.getStatus() == GroupTxnStatus.CONFIRMED) {
            return txn.getParticipants().stream()
                    .map(groupTxnMapper::toParticipantResponse)
                    .toList();
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }


    @Override
    public long countPendingForGroup(UUID groupId) {
        return groupTxnRepository.countByGroupIdAndStatusAndDeletedAtIsNull(groupId, GroupTxnStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnresolvedForGroup(UUID groupId) {
        return groupTxnRepository.countByGroupIdAndStatusAndDeletedAtIsNull(groupId, GroupTxnStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long sumConfirmedAmount(UUID groupId, GroupTxnType type) {
        Long total = groupTxnRepository.sumAmountByGroupIdAndType(groupId, type);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long sumConfirmedAmount(UUID groupId, GroupTxnType type, Instant from, Instant to) {
        Long total = groupTxnRepository.sumAmountByGroupIdAndTypeAndPeriod(groupId, type, from, to);
        return total != null ? total : 0L;
    }


    // -------------------------------------PRIVATE----------------------------------//

    private GroupTxnListRes buildListRes(UUID groupId, UUID myId, List<GroupTxnStatus> statuses, GroupTxnFilterReq filter, Pageable pageable) {

        Specification<GroupTxn> spec = GroupTxnSpecification.filter(groupId, myId, statuses, filter);

        // truy vấn danh sách phân trang qua custom method findList không chạy count tự động
        Page<GroupTxn> pageRes = groupTxnRepository.findAll(spec, pageable);

        List<GroupTxnSumaryRes> items = pageRes.getContent().stream()
                .map(groupTxnMapper::toSumaryResponse) // hoặc mapper tương ứng của bạn
                .toList();

        // Trích xuất metadata từ pageResult
        PageMeta meta = new PageMeta(
                pageRes.getNumber() + 1,      // +1 nếu frontend quy ước trang bắt đầu từ 1
                pageRes.getSize(),
                pageRes.getTotalElements(),   // Tổng số bản ghi
                pageRes.getTotalPages()       // Tổng số trang
        );

        return GroupTxnListRes.of(items, meta);
    }
}
