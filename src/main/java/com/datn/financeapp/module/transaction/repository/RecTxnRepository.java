package com.datn.financeapp.module.transaction.repository;

import com.datn.financeapp.module.transaction.entity.RecTxn;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.datn.financeapp.module.transaction.entity.RecTxnSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecTxnRepository extends JpaRepository<RecTxn, UUID> {

    /**
     * Tìm khoản giao dịch định kỳ theo ID và thuộc về người dùng hiện tại.
     *
     * @param id          ID giao dịch định kỳ
     * @param currentUser ID người dùng hiện tại
     * @return {@link Optional} chứa giao dịch định kỳ nếu tìm thấy và thuộc về người dùng
     */
    Optional<RecTxn> findByIdAndUserId(UUID id, UUID currentUser);

    Page<RecTxn> findAllByUserId(UUID userId, Pageable pageable);
}
