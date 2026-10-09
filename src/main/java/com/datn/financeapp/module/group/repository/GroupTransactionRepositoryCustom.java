package com.datn.financeapp.module.group.repository;

import com.datn.financeapp.module.group.entity.GTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface GroupTransactionRepositoryCustom {

    List<GTransaction> findList(Specification<GTransaction> spec, Pageable pageable);
}
