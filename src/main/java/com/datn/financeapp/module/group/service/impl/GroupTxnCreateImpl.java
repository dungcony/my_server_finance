package com.datn.financeapp.module.group.service.impl;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.category.service.CategoryService;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnParticipantReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;
import com.datn.financeapp.module.group.entity.GroupTxn;
import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import com.datn.financeapp.module.group.enums.GroupTxnType;
import com.datn.financeapp.module.group.enums.MoneySource;
import com.datn.financeapp.module.group.events.FundBalanceChangedEvent;
import com.datn.financeapp.module.group.events.GroupTxnConfirmedEvent;
import com.datn.financeapp.module.group.helper.*;
import com.datn.financeapp.module.group.mapper.GroupTxnMapper;
import com.datn.financeapp.module.group.repository.GroupTxnRepository;
import com.datn.financeapp.module.group.service.GroupTxnCreateService;
import com.datn.financeapp.module.group.service.MemberBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupTxnCreateImpl implements GroupTxnCreateService {

    private final GroupTxnRepository groupTxnRepository;

    private final CategoryService categoryService;
    private final MemberBalanceService memberBalanceService;

    //helper
    private final GroupTxnHelper groupTxnHelper;

    private final GroupTxnMapper groupTxnMapper;
    private final MemberAuthCache memberAuthCache;

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public GroupTxnDetailRes create(UUID groupId, GroupTxnCreateReq req) {
        UUID operatorId = SecurityContextUtil.currentUserId();
        GroupTxnType type = req.type();
        MemberAuthInfo authInfo = memberAuthCache.get(groupId, operatorId)
                .filter(MemberAuthInfo::inGroupActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        GroupTxnStatus status = GroupTxnHelper.getStatus(operatorId, req, authInfo);
        GroupTxn transaction = baseBuilder(
                operatorId,
                groupId,
                req,
                status,
                req.occurredAt()
        );

        switch (type) {
            case CONTRIBUTION -> {
                if (req.transactorId() == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);

                if (req.moneySource() != MoneySource.PERSONAL)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);

                contribute(req, transaction);
            }
            case EXPENSE -> {

                if (req.moneySource() == MoneySource.PERSONAL && req.transactorId() == null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED);

                if (req.moneySource() == MoneySource.FUND && req.transactorId() != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);

                expense(req, transaction);
            }
            case REFUND -> {

                if (req.transactorId() != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);

                if (req.moneySource() != MoneySource.FUND)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);


                // chỉ thủ quỹ được tạo, và phải đứng tên chính mình
                if (!authInfo.isTreasurer())
                    throw new BusinessException(ErrorCode.FORBIDDEN);

                refund(groupId, req, transaction);
            }
            default -> {

                if (req.transactorId() != null)
                    throw new BusinessException(ErrorCode.GROUP_TXN_TRANSACTOR_REQUIRED_NULL);

                if (req.moneySource() != MoneySource.FUND)
                    throw new BusinessException(ErrorCode.GROUP_TXN_MONEY_SOURCE_INVALID);

                transaction.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
            }
        }

        // kiểm tra người chi và danh sách chia tiền có ở trong group không và số tiền có chính xác không
        groupTxnHelper.validParticipants(groupId, req.transactorId(), req.participants(), req.amount());

        groupTxnRepository.save(transaction);

        if (status.equals(GroupTxnStatus.CONFIRMED)) {
            // Tính chênh lệch vào bảng tổng
            memberBalanceService.applyDelta(groupId, new MemberBalances(), BalanceCalculator.effectOf(transaction));
            long newDelta = GroupTxnHelper.calculateDelta(transaction);

            applicationEventPublisher.publishEvent(new FundBalanceChangedEvent(groupId, newDelta));
            if (transaction.getType() != GroupTxnType.ADJUSTMENT_UP && transaction.getType() != GroupTxnType.ADJUSTMENT_DOWN) {
                applicationEventPublisher.publishEvent(GroupTxnConfirmedEvent.of(transaction));
            }
        }

        return groupTxnMapper.toDetailResponse(transaction);
    }

    //----------------------PRIVATE----------------------------//
    // giao dịch hoàn tiền
    private void refund(UUID groupId, GroupTxnCreateReq req, GroupTxn transaction) {
        // những người được nhận hoàn tiền
        List<UUID> receiverIds = req.participants().stream()
                .map(GroupTxnParticipantReq::userId)
                .toList();


        // đọc số dư hiện tại của người nhận kèm khoá dòng để kiểm hạn mức hoàn trả
        MemberBalances mb = memberBalanceService.getBalancesForUpdate(groupId, receiverIds);

        // mỗi người không được nhận quá phần còn lại của mình
        for (GroupTxnParticipantReq p : req.participants()) {
            if (p.shareAmount() > mb.get(p.userId()).net())
                throw new BusinessException(ErrorCode.GROUP_TXN_REFUND_EXCEEDS_BALANCE);
        }

        transaction.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
    }

    // giao dịch góp quỹ

    private void contribute(GroupTxnCreateReq req, GroupTxn transaction) {
        if (req.participants() == null || req.participants().size() != 1)
            throw new BusinessException(ErrorCode.GROUP_TXN_PARTICIPANTS_NOT_ALLOWED);

        if (!req.participants().get(0).userId().equals(req.transactorId()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Người tham gia góp quỹ phải là chính người nộp tiền.");

        transaction.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
    }


    // giao dich chi mua đồ
    private void expense(GroupTxnCreateReq req, GroupTxn txn) {
        // phải có danh mục
        if (req.categoryCode() == null || req.categoryCode().isBlank())
            throw new BusinessException(ErrorCode.GROUP_TXN_CATEGORY_REQUIRED);

        categoryService.findByCodeAndIsDeletedAndIsLeaf(req.categoryCode(), false, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại hoặc không phải danh mục con."));

        txn.setParticipants(GroupTxnHelper.getParticipants(req.participants()));
        txn.setCategoryCode(req.categoryCode());
    }


    private GroupTxn baseBuilder(UUID operatorId, UUID groupId, GroupTxnCreateReq req, GroupTxnStatus status, Instant now) {
        return GroupTxn.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .transactorId(req.transactorId())
                .type(req.type())
                .amount(req.amount())
                .occurredAt(req.occurredAt())
                .note(req.note())
                .moneySource(req.moneySource())
                .status(status)
                .createdBy(operatorId)
                .reviewedBy(status == GroupTxnStatus.CONFIRMED ? operatorId : null)
                .reviewedAt(status == GroupTxnStatus.CONFIRMED ? now : null)
                .build();
    }
}
