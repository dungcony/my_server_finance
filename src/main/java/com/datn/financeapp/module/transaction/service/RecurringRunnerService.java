package com.datn.financeapp.module.transaction.service;

/**
 * Service phụ trách chạy quét sinh các giao dịch định kỳ đến hạn (JOB-01).
 * <p>
 * <b>Các hàm chính:</b>
 * <ul>
 *   <li>{@link #runDueRecurring()}: Quét và sinh giao dịch tự động cho mọi bản ghi đến hạn trong hệ thống.</li>
 * </ul>
 * </p>
 */
public interface RecurringRunnerService {

    /**
     * Quét và thực hiện sinh giao dịch tự động cho các bản ghi giao dịch định kỳ đã đến hạn.
     */
    void runDueRecurring();
}
