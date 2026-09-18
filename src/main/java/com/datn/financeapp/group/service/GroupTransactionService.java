package com.datn.financeapp.group.service;

import com.datn.financeapp.group.dto.transaction.GroupRefundReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionBulkReviewRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionCreateReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionDetailRes;
import com.datn.financeapp.group.dto.transaction.GroupTransactionFilterReq;
import com.datn.financeapp.group.dto.transaction.GroupTransactionUpdateReq;
import com.datn.financeapp.group.dto.transaction.GroupWithdrawalReq;
import java.util.List;
import java.util.UUID;

public interface GroupTransactionService {

    GroupTransactionDetailRes create(UUID userId, UUID groupId, GroupTransactionCreateReq req);

    List<GroupTransactionDetailRes> list(UUID userId, UUID groupId, GroupTransactionFilterReq filter);

    GroupTransactionDetailRes detail(UUID userId, UUID groupId, UUID transactionId);

    GroupTransactionDetailRes update(UUID userId, UUID groupId, UUID transactionId, GroupTransactionUpdateReq req);

    void delete(UUID userId, UUID groupId, UUID transactionId);

    GroupTransactionDetailRes confirm(UUID userId, UUID groupId, UUID transactionId);

    GroupTransactionDetailRes reject(UUID userId, UUID groupId, UUID transactionId);

    GroupTransactionBulkReviewRes bulkConfirm(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req);

    GroupTransactionBulkReviewRes bulkReject(UUID userId, UUID groupId, GroupTransactionBulkReviewReq req);

    GroupTransactionDetailRes createRefund(UUID userId, UUID groupId, GroupRefundReq req);

    GroupTransactionDetailRes updateRefund(UUID userId, UUID groupId, UUID transactionId, GroupRefundReq req);

    GroupTransactionDetailRes createWithdrawal(UUID userId, UUID groupId, GroupWithdrawalReq req);

    GroupTransactionDetailRes updateWithdrawal(UUID userId, UUID groupId, UUID transactionId, GroupWithdrawalReq req);
}
