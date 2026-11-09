package com.datn.financeapp.module.group.repository;

import com.datn.financeapp.module.group.entity.GroupTxn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface GroupTxnRepositoryCustom {

    List<GroupTxn> findList(Specification<GroupTxn> spec, Pageable pageable);
}
