package com.datn.financeapp.module.icon.repository;

import com.datn.financeapp.module.icon.entity.Icon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

// Repository JPA cho {@link Icon} — kho biểu tượng hệ thống.
public interface IconRepository extends JpaRepository<Icon, UUID> {

    boolean existsByCode(String code);

    Optional<Icon> findByCodeAndIsDeletedFalse(String code);

    Optional<Icon> findByIdAndIsDeletedFalse(UUID id);

    Slice<Icon> findByIsDeletedFalse(Pageable pageable);

    Optional<Icon> findByCode(String code);

    List<Icon> findAllByCodeInAndIsDeleted(List<String> codes, boolean isDeleted);
}
