package com.datn.financeapp.module.group.service;

import com.datn.financeapp.module.group.dto.request.transaction.BatchGroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateReq;
import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnUpdateStatusReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;

import java.util.UUID;

/**
 * service dành cho việc cập nhật giao dịch trong group
 */
public interface GroupTxnUpdateService {
    GroupTxnDetailRes update(UUID groupId, GroupTxnUpdateReq req);

    /**
     * Hàm này dành cho việc cập nhật trạng thái của giao dịch
     *
     */
    GroupTxnDetailRes status(UUID groupId, GroupTxnUpdateStatusReq req);

    /**
     * Hàm này dành cho việc cập nhật trạng thái của nhiều giao dịch cùng 1 lúc
     *
     */
    int statuses(UUID groupId, BatchGroupTxnUpdateStatusReq req);


}
