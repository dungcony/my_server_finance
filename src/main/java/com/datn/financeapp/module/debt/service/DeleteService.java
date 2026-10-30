package com.datn.financeapp.module.debt.service;

import java.util.UUID;

public interface DeleteService {
    void delete(UUID id, boolean isAdmin);
}
