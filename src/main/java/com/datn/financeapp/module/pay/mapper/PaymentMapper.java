package com.datn.financeapp.module.pay.mapper;

import com.datn.financeapp.module.pay.dto.res.PaymentAdminRes;
import com.datn.financeapp.module.pay.dto.res.PaymentCreateRes;
import com.datn.financeapp.module.pay.dto.res.PaymentStatusRes;
import com.datn.financeapp.module.pay.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Chuyển đổi giữa entity {@link Payment} và các đối tượng DTO phản hồi.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {

    // ánh xạ thông tin đơn vừa tạo cùng link checkout payOS
    @Mapping(target = "orderCode", source = "payment.orderCode")
    @Mapping(target = "referenceId", source = "payment.referenceId")
    @Mapping(target = "amount", source = "payment.amount")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(target = "checkoutUrl", source = "checkoutUrl")
    @Mapping(target = "expiresAt", source = "payment.expiresAt")
    PaymentCreateRes toCreateRes(Payment payment, String checkoutUrl);

    // ánh xạ trạng thái đơn hàng trả về cho người dùng
    @Mapping(target = "excessAmount", expression = "java(calculateExcess(payment))")
    @Mapping(target = "amountRemaining", expression = "java(calculateRemaining(payment))")
    PaymentStatusRes toStatusRes(Payment payment);

    // ánh xạ trạng thái chi tiết đơn hàng trả về cho quản trị viên
    @Mapping(target = "excessAmount", expression = "java(calculateExcess(payment))")
    @Mapping(target = "amountRemaining", expression = "java(calculateRemaining(payment))")
    PaymentAdminRes toAdminRes(Payment payment);

    // tính số tiền chuyển thừa (nếu có)
    default Long calculateExcess(Payment payment) {
        if (payment == null || payment.getAmountPaid() == null || payment.getAmount() == null) {
            return 0L;
        }
        long excess = payment.getAmountPaid() - payment.getAmount();
        return excess > 0 ? excess : 0L;
    }

    // tính số tiền còn thiếu người dùng cần nạp nốt
    default Long calculateRemaining(Payment payment) {
        if (payment == null || payment.getAmount() == null) {
            return 0L;
        }
        long paid = payment.getAmountPaid() == null ? 0L : payment.getAmountPaid();
        long diff = payment.getAmount() - paid;
        return diff > 0 ? diff : 0L;
    }
}
