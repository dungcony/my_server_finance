package com.datn.financeapp.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity đầy đủ cho bảng {@code transactions} — bảng trung tâm của toàn hệ thống (db/migration/
 * V2__giao_dich.sql + V8__dieu_chinh_so_du.sql). {@code id} là {@code UUID} sinh ở tầng service
 * bằng {@code UUID.randomUUID()}, không dùng {@code @GeneratedValue} (cùng khuôn
 * {@code wallet.entity.Wallet}).
 *
 * <p>{@code updated_at} do trigger {@code trg_transactions_validate} tự set khi INSERT/UPDATE
 * (luôn gán {@code NEW.updated_at := now()}) — set tường minh ở tầng Java khi gọi {@code save()}/
 * insert thủ công vẫn an toàn vì trigger sẽ ghi đè, nhưng KHÔNG dựa vào giá trị Java set để đọc
 * lại ngay trong cùng transaction trước khi flush.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    // Chỉ khác NULL khi {@code type = 'transfer'} (ck_txn_shape).
    @Column(name = "destination_wallet_id")
    private UUID destinationWalletId;

    // NULL khi {@code type = 'transfer'}, bắt buộc khi {@code expense}/{@code income}.
    @Column(name = "category_id")
    private UUID categoryId;

    // CHECK IN ('expense','income','transfer') — ck_txn_type.
    @Column(name = "type", nullable = false)
    private String type;

    // LUÔN DƯƠNG (ck_txn_amount) — chiều tiền suy ra từ {@code type}.
    @Column(name = "amount", nullable = false)
    private Long amount;

    // Cột kiểu {@code DATE} — ngày phát sinh giao dịch, KHÔNG có múi giờ, không convert.
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "note")
    private String note;

    @Column(name = "display_name")
    private String displayName;

    // CHECK IN ('manual','text','ocr','auto','adjustment') — ck_txn_source (V2 + V8).
    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "recurring_id")
    private UUID recurringId;

    @Column(name = "draft_id")
    private UUID draftId;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Do trigger {@code trg_transactions_validate} sở hữu — xem Javadoc lớp.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Thêm bởi V8 — FALSE chỉ dùng cho giao dịch {@code source = 'adjustment'}.
    @Column(name = "counts_in_report", nullable = false)
    private Boolean countsInReport;
}
