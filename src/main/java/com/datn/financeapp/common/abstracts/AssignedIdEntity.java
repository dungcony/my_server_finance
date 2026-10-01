package com.datn.financeapp.common.abstracts;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    // mặc định: vừa tạo bằng code → là mới
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    // đã load từ DB hoặc đã lưu → không còn mới
    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
