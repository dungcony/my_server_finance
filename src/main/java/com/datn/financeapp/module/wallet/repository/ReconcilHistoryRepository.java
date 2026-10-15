package com.datn.financeapp.module.wallet.repository;

import com.datn.financeapp.module.wallet.entity.ReconcilHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconcilHistoryRepository extends JpaRepository<ReconcilHistory, UUID> {
}
