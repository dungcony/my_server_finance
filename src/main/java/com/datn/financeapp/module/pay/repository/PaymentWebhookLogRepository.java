package com.datn.financeapp.module.pay.repository;

import com.datn.financeapp.module.pay.entity.PaymentWebhookLog;
import com.datn.financeapp.module.pay.enums.WebhookResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookLogRepository
        extends JpaRepository<PaymentWebhookLog, UUID>, JpaSpecificationExecutor<PaymentWebhookLog> {

    /** Một dòng thống kê: số request (đã tính cả dòng gộp) theo từng loại kết quả. */
    interface ResultCount {
        WebhookResult getResult();

        Long getTotal();
    }

    long countByResultInAndAggregatedFalseAndReceivedAtGreaterThanEqual(Collection<WebhookResult> results, Instant since);

    Optional<PaymentWebhookLog> findFirstByResultAndAggregatedTrueAndReceivedAtGreaterThanEqual(WebhookResult result, Instant since);

    @Modifying
    @Query("delete from PaymentWebhookLog l where l.receivedAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    @Query("""
            select l.result as result, sum(l.occurrences) as total
            from PaymentWebhookLog l
            where l.receivedAt >= :since
            group by l.result
            """)
    List<ResultCount> countGroupedByResult(@Param("since") Instant since);

    @Query("select max(l.receivedAt) from PaymentWebhookLog l where l.result in :results")
    Instant lastReceivedAt(@Param("results") Collection<WebhookResult> results);

    /**
     * Đơn đã PAID trong khoảng [since, paidBefore) mà không có webhook xác nhận (PROCESSED/ALREADY_PAID) cho order_code đó:
     * đơn được ghi PAID qua đường khác (polling, job) còn webhook thì không tới.
     */
    @Query("""
            select p.orderCode from Payment p
            where p.status = com.datn.financeapp.module.pay.enums.PaymentStatus.PAID
              and p.paidAt >= :since and p.paidAt < :paidBefore
              and not exists (
                  select 1 from PaymentWebhookLog l
                  where l.orderCode = p.orderCode and l.result in :okResults)
            order by p.paidAt desc
            """)
    List<Long> findPaidWithoutWebhook(@Param("since") Instant since,
                                      @Param("paidBefore") Instant paidBefore,
                                      @Param("okResults") Collection<WebhookResult> okResults,
                                      Pageable pageable);
}
