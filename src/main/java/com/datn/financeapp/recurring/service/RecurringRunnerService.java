package com.datn.financeapp.recurring.service;

// Public API của module Recurring cho tác vụ chạy quét định kỳ (Scheduler).
public interface RecurringRunnerService {

    void runDueRecurring();
}
