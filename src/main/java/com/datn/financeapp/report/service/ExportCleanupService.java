package com.datn.financeapp.report.service;

// Public API của module Report cho tác vụ dọn dẹp các tệp và bản ghi xuất dữ liệu quá hạn.
public interface ExportCleanupService {

    int cleanUpExpiredAndStuck();
}
