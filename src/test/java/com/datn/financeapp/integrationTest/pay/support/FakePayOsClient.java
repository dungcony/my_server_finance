package com.datn.financeapp.integrationTest.pay.support;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.datn.financeapp.common.exception.ErrorCode;
import com.datn.financeapp.module.pay.config.PayOsProperties;
import com.datn.financeapp.module.pay.exceptions.PaymentException;
import com.datn.financeapp.module.pay.helper.PayOsClientHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

public class FakePayOsClient extends PayOsClientHelper {

    public static class FakeLink {
        public long orderCode;
        public long amount;
        public long amountPaid;
        public PaymentLinkStatus status;
        public String paymentLinkId;

        public FakeLink(long orderCode, long amount, String paymentLinkId) {
            this.orderCode = orderCode;
            this.amount = amount;
            this.amountPaid = 0L;
            this.status = PaymentLinkStatus.PENDING;
            this.paymentLinkId = paymentLinkId;
        }
    }

    private final Map<Long, FakeLink> links = new ConcurrentHashMap<>();

    public FakePayOsClient(PayOsProperties properties) {
        super(properties);
    }

    public synchronized void userPays(long orderCode, long paidAmount) {
        FakeLink link = links.get(orderCode);
        if (link != null && link.status == PaymentLinkStatus.PENDING) {
            link.amountPaid += paidAmount;
            if (link.amountPaid >= link.amount) {
                link.status = PaymentLinkStatus.PAID;
            }
        }
    }

    public synchronized void userPays(long orderCode) {
        FakeLink link = links.get(orderCode);
        if (link != null) {
            userPays(orderCode, link.amount);
        }
    }

    public synchronized long getMoneyReceived(long orderCode) {
        FakeLink link = links.get(orderCode);
        return link == null ? 0L : link.amountPaid;
    }

    @Override
    public synchronized CreatePaymentLinkResponse createLink(long orderCode, long amount, String description, long expiredAtEpochSecond) {
        super.createLink(orderCode, amount, description, expiredAtEpochSecond);
        String linkId = "fake-link-" + orderCode;
        links.put(orderCode, new FakeLink(orderCode, amount, linkId));

        CreatePaymentLinkResponse res = mock(CreatePaymentLinkResponse.class);
        lenient().when(res.getPaymentLinkId()).thenReturn(linkId);
        lenient().when(res.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/" + linkId);
        return res;
    }

    @Override
    public synchronized PaymentLink getLink(long orderCode) {
        FakeLink fake = links.get(orderCode);
        if (fake == null) {
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        PaymentLink link = mock(PaymentLink.class);
        lenient().when(link.getStatus()).thenReturn(fake.status);
        lenient().when(link.getAmount()).thenReturn(fake.amount);
        lenient().when(link.getAmountPaid()).thenReturn(fake.amountPaid);
        return link;
    }

    @Override
    public synchronized PaymentLink cancelLink(long orderCode) {
        FakeLink fake = links.get(orderCode);
        if (fake == null) {
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        if (fake.status == PaymentLinkStatus.PAID) {
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        fake.status = PaymentLinkStatus.CANCELLED;

        PaymentLink link = mock(PaymentLink.class);
        lenient().when(link.getStatus()).thenReturn(PaymentLinkStatus.CANCELLED);
        lenient().when(link.getAmount()).thenReturn(fake.amount);
        lenient().when(link.getAmountPaid()).thenReturn(fake.amountPaid);
        return link;
    }
}
