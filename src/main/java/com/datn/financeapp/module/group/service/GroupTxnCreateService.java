package com.datn.financeapp.module.group.service;

import com.datn.financeapp.module.group.dto.request.transaction.GroupTxnCreateReq;
import com.datn.financeapp.module.group.dto.response.transaction.GroupTxnDetailRes;

import java.util.UUID;

public interface GroupTxnCreateService {
    GroupTxnDetailRes create(UUID groupId, GroupTxnCreateReq req);
}
