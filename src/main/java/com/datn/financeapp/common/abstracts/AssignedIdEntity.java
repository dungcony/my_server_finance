package com.datn.financeapp.common.abstracts;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    // mặc định: vừa tạo bằng code → là mới
    @Transient
    private boolean isNew = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
