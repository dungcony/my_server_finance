package com.datn.financeapp.module.pay.mapper;

import com.datn.financeapp.module.pay.dto.res.WebhookLogRes;
import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentWebhookLogMapper {

    WebhookLogRes toRes(PaymentWebhookLog log);
}
