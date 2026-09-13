package com.datn.financeapp.auth.repository;

import com.datn.financeapp.auth.entity.OtpModel;
import com.datn.financeapp.auth.enums.OtpType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends CrudRepository<OtpModel, String> {
    default Optional<OtpModel> findByTypeAndEmail(OtpType type, String email) {
        return findById(OtpModel.buildId(type, email));
    }

    default void deleteByTypeAndEmail(OtpType type, String email) {
        deleteById(OtpModel.buildId(type, email));
    }
}
