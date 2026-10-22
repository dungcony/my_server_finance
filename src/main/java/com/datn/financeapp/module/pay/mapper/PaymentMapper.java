package com.datn.financeapp.module.pay.mapper;

import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Chuyển {@link Payment} thành các DTO trả ra ngoài module.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {

    @Mapping(target = "orderCode", source = "payment.orderCode")
    @Mapping(target = "referenceId", source = "payment.referenceId")
    @Mapping(target = "amount", source = "payment.amount")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(target = "checkoutUrl", source = "checkoutUrl")
    @Mapping(target = "expiresAt", source = "payment.expiresAt")
    PaymentCreateRes toCreateRes(Payment payment, String checkoutUrl);

    @Mapping(target = "excessAmount", expression = "java(calculateExcess(payment))")
    PaymentStatusRes toStatusRes(Payment payment);

    @Mapping(target = "excessAmount", expression = "java(calculateExcess(payment))")
    PaymentAdminRes toAdminRes(Payment payment);

    default Long calculateExcess(Payment payment) {
        if (payment == null || payment.getAmountPaid() == null || payment.getAmount() == null) {
            return 0L;
        }
        long excess = payment.getAmountPaid() - payment.getAmount();
        return excess > 0 ? excess : 0L;
    }
}
