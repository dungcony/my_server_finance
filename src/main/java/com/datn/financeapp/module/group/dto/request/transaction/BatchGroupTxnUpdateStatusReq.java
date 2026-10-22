package com.datn.financeapp.module.group.dto.request.transaction;

import com.datn.financeapp.module.group.enums.GroupTxnStatus;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.UniqueElements;

import java.util.List;
import java.util.UUID;

public record BatchGroupTxnUpdateStatusReq(
        @NotEmpty(message = "Danh sách cập nhật không được rỗng")
        @UniqueElements(message = "Danh sách txnId không được chứa giá trị trùng nhau")
        List<UUID> txnIds,

        GroupTxnStatus status
) {
}
