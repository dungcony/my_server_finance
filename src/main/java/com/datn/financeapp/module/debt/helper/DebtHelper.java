package com.datn.financeapp.module.debt.helper;

import com.datn.financeapp.common.exception.BusinessException;
import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.common.security.SecurityContextUtil;
import com.datn.financeapp.module.debt.entity.Debt;
import com.datn.financeapp.module.debt.repository.DebtRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DebtHelper {

    private final DebtRepository debtRepository;

    public Debt findDebt(UUID id, boolean isAdmin) {

        Debt res;
        UUID userId = SecurityContextUtil.currentUserId();
        if (isAdmin)
            res = debtRepository.findByIdAndIsDeletedFalse(id).orElse(null);
        else
            res = debtRepository.findByIdAndIsDeletedFalseAndUserId(id, userId).orElse(null);

        if (res == null)
            throw new BusinessException(ErrorCode.DEBT_NOT_FOUND);

        return res;
    }

    /**
     *
     * @param id     debtId not null
     * @param userId userId nullable
     */
    public void validDebt(@NotNull UUID id, UUID userId) {

        Debt res = debtRepository.findByIdAndIsDeletedFalse(id).orElse(null);

        if (res == null)
            throw new BusinessException(ErrorCode.DEBT_NOT_FOUND);

        if (userId != null && !res.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.DEBT_NOT_FOUND);

    }
}
