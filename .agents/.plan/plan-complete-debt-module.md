# Kế hoạch hoàn thành module Debt (Sổ nợ cá nhân)

## 1. Bối cảnh & Hiện trạng

- Module `debt` quản lý toàn bộ nghiệp vụ sổ nợ cá nhân (Cho vay - `lending` và Đi vay - `borrowing`), tiến độ thanh toán từng đợt (`debt_payments`), xóa nợ khó đòi (`written_off`) và nhắc nợ định kỳ (`DebtReminderJob`).
- **Nguyên nhân bị loại trừ trong `pom.xml` ([pom.xml:237](file:///D:/projects/java/server/pom.xml#L237)):**
  - `<exclude>com/datn/financeapp/module/debt/**</exclude>` đang loại trừ module khỏi quá trình build dự án.
  - Dự án đã nâng cấp CSDL sang `migration_v2` và tái cấu trúc hệ thống:
    1. **Thiếu schema:** Trong thư mục `src/main/resources/db/migration_v2`, chưa có file `CREATE TABLE debts` và `debt_payments`, cũng như chưa có trigger đồng bộ `trg_debt_payments_sync` (mới chỉ có `V9__trigger_debt.sql` chặn xóa giao dịch nợ).
    2. **Mất class ghi giao dịch cũ:** `DebtServiceImpl` phụ thuộc vào `TransactionWriter` (class cũ đã bị xóa khi refactor module `transaction`). Hiện tại cần chuyển sang dùng `TxnBehavierService.create(...)`.
    3. **Chuyển đổi Category:** Trước đây dùng `categoryId` (UUID) qua `CategoryService.findSystemCategoryId`, nay hệ thống dùng mã chuỗi chuẩn hóa `category_code` (mã hệ thống cố định trong `V2__categories_icon.sql`: `'cho_vay'`, `'di_vay'`, `'thu_no'`, `'tra_no'`).
    4. **Chuyển đổi WalletService:** Các hàm `findRefForUser` cũ đã đổi thành `findSumaryByIdAndUser` và `findSummaryMapByIds`.
    5. **Chuyển đổi Entity:** `Debt.java` đã được chuẩn hóa `extends AssignedIdEntity` (có `created_at`, `updated_at`, `is_deleted`).

---

## 2. Quyết định kỹ thuật đã chốt

1. **CSDL & Trigger (PostgreSQL Migration):**
   - Tạo file migration `src/main/resources/db/migration_v2/V13__create_debt.sql`.
   - Bảng `debts`: Có đầy đủ `wallet_id NOT NULL`, `origin_transaction_id NOT NULL`, và các cột của `AssignedIdEntity` (`created_at`, `updated_at`, `is_deleted`).
   - Bảng `debt_payments`: Có `debt_id NOT NULL`, `transaction_id NOT NULL`, và các cột của `AssignedIdEntity` (`created_at`, `updated_at`, `is_deleted`).
   - Trigger `trg_debt_payments_sync`: Tự động cộng dồn `paid_amount` từ `debt_payments` và tự động cập nhật `status` (`outstanding` $\leftrightarrow$ `settled`) mỗi khi có đợt thanh toán được thêm/sửa/xóa.
2. **Kiến trúc dòng tiền & Giao dịch:**
   - Dùng `TxnBehavierService.create(userId, TxnCreateReq)` để sinh giao dịch:
     - Tạo nợ `lending` (Cho vay): `type = TxnType.EXPENSE`, `categoryCode = 'cho_vay'`.
     - Tạo nợ `borrowing` (Đi vay): `type = TxnType.INCOME`, `categoryCode = 'di_vay'`.
     - Trả nợ `lending` (Thu nợ): `type = TxnType.INCOME`, `categoryCode = 'thu_no'`.
     - Trả nợ `borrowing` (Trả nợ): `type = TxnType.EXPENSE`, `categoryCode = 'tra_no'`.
   - Hủy trả nợ / Xóa nợ: Dùng `TxnBehavierService.softDelete(transactionId, false)` để hoàn tác số dư ví.
3. **Kiểu dữ liệu thời gian:**
   - Ngày lập nợ (`issued_date`), ngày hẹn trả (`due_date`), ngày trả (`paid_date`) dùng **`LocalDate`** (`DATE` trong CSDL) để bảo toàn chính xác theo lịch dân sự, tuyệt đối không bị lệch ngày do múi giờ (timezone drift).
   - Thời gian phát sinh giao dịch liên kết (`Transaction.occurredAt`) dùng `Instant` (lấy thời điểm hiện tại `Instant.now()`).
4. **Tài liệu hóa & Comment:**
   - Giữ nguyên và bổ sung Javadoc tiếng Việt đầy đủ cho class, repository, service và entity để code trực quan, dễ bảo trì.

---

## 3. Các file sẽ tạo mới & thay đổi

### CSDL (Migration)
- `src/main/resources/db/migration_v2/V13__create_debt.sql` *(file mới)*:
  - Tạo bảng `debts` và `debt_payments`.
  - Tạo function `fn_debt_payments_sync()` và trigger `trg_debt_payments_sync`.
  - Tạo các index tối ưu: `idx_debts_user_status`, `idx_debts_due_date`, `idx_debt_payments_debt_id`.

### Cấu hình build
- `pom.xml:237`: Bỏ loại trừ `<exclude>com/datn/financeapp/module/debt/**</exclude>` trong `<excludes>`.
- `pom.xml:246`: Giữ `<testExclude>com/datn/financeapp/module/debt/**</testExclude>` trong `<testExcludes>` để không ảnh hưởng tới tiến độ build.

### Entity & Repository
- `src/main/java/com/datn/financeapp/module/debt/entity/Debt.java`: Đã hoàn thiện kế thừa `AssignedIdEntity`, đầy đủ `walletId`, `createdAt`, `updatedAt`, `isDeleted`.
- `src/main/java/com/datn/financeapp/module/debt/entity/DebtPayment.java`: Chuyển sang kế thừa `AssignedIdEntity` cho đồng bộ toàn hệ thống.
- `src/main/java/com/datn/financeapp/module/debt/repository/DebtRepository.java`: Cập nhật các câu query lọc thêm `NOT is_deleted`.
- `src/main/java/com/datn/financeapp/module/debt/repository/DebtPaymentRepository.java`: Lọc thêm `NOT is_deleted`.

### Service & Mapper
- `src/main/java/com/datn/financeapp/module/debt/service/impl/DebtServiceImpl.java`:
  - Xóa bỏ `TransactionWriter`, thay bằng `TxnBehavierService.create(...)`.
  - Xóa bỏ `findSystemCategoryId` (dùng trực tiếp mã category `'cho_vay'`, `'di_vay'`, `'thu_no'`, `'tra_no'`).
  - Cập nhật gọi `WalletService.findSumaryByIdAndUser` và `findSummaryMapByIds`.
  - Sửa hàm `cancelPayment` và `delete` gọi `txnBehavierService.softDelete(transactionId, false)`.
  - Sửa hàm `detail` lấy thông tin giao dịch gốc qua `txnBehavierService.get(originTransactionId, false)`.
- `src/main/java/com/datn/financeapp/module/debt/mapper/DebtMapper.java`:
  - Cập nhật mapping với `TxnDetailResponse` và `WalletSumaryRes`.

---

## 4. Hướng giải quyết / Code mẫu xem trước

### Bước 1: Migration `V13__create_debt.sql`
```sql
-- Bảng quản lý sổ nợ cá nhân (Cho vay & Đi vay)
CREATE TABLE debts (
    id                    UUID         PRIMARY KEY,
    user_id               UUID         NOT NULL,
    wallet_id             UUID         NOT NULL,
    type                  VARCHAR(10)  NOT NULL, -- 'lending' hoặc 'borrowing'
    counterparty_name     VARCHAR(100) NOT NULL,
    principal_amount      BIGINT       NOT NULL,
    paid_amount           BIGINT       NOT NULL DEFAULT 0,
    issued_date           DATE         NOT NULL,
    due_date              DATE,
    note                  TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'outstanding', -- 'outstanding', 'settled', 'written_off'
    origin_transaction_id UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_debt_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_txn    FOREIGN KEY (origin_transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_debt_type   CHECK (type IN ('lending', 'borrowing')),
    CONSTRAINT ck_debt_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_debt_paid      CHECK (paid_amount >= 0),
    CONSTRAINT ck_debt_status CHECK (status IN ('outstanding', 'settled', 'written_off')),
    CONSTRAINT ck_debt_due_date CHECK (due_date IS NULL OR due_date >= issued_date)
);

CREATE INDEX idx_debts_user_status ON debts (user_id, status) WHERE NOT is_deleted;
CREATE INDEX idx_debts_due_date ON debts (due_date) WHERE status = 'outstanding' AND NOT is_deleted;

-- Bảng lưu lịch sử các đợt thanh toán nợ
CREATE TABLE debt_payments (
    id             UUID        PRIMARY KEY,
    debt_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    paid_date      DATE        NOT NULL,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_dp_debt FOREIGN KEY (debt_id) REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_txn  FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_dp_amount CHECK (amount > 0)
);

CREATE INDEX idx_debt_payments_debt_id ON debt_payments (debt_id) WHERE NOT is_deleted;

-- Trigger tự động đồng bộ paid_amount và chuyển đổi trạng thái settled
CREATE OR REPLACE FUNCTION fn_debt_payments_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_debt_id UUID := COALESCE(NEW.debt_id, OLD.debt_id);
    v_total   BIGINT;
    v_debt    debts%ROWTYPE;
BEGIN
    SELECT * INTO v_debt FROM debts WHERE id = v_debt_id FOR UPDATE;

    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM debt_payments WHERE debt_id = v_debt_id AND NOT is_deleted;

    IF v_total > v_debt.principal_amount THEN
        RAISE EXCEPTION 'TỔNG ĐÃ TRẢ VƯỢT QUÁ SỐ NỢ GỐC' USING ERRCODE = 'P0001';
    END IF;

    UPDATE debts
       SET paid_amount = v_total,
           status = CASE
               WHEN status = 'written_off'      THEN 'written_off'
               WHEN v_total >= principal_amount THEN 'settled'
               ELSE 'outstanding'
           END
     WHERE id = v_debt_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debt_payments_sync
    AFTER INSERT OR UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_debt_payments_sync();
```

### Bước 2: Refactor `DebtPayment.java`
```java
package com.datn.financeapp.module.debt.entity;

import com.datn.financeapp.common.abstracts.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "debt_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtPayment extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "debt_id", nullable = false)
    private UUID debtId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(name = "note")
    private String note;
}
```

### Bước 3: Tạo giao dịch trong `DebtServiceImpl.java`
```java
// Khi tạo khoản nợ mới:
boolean lending = TYPE_LENDING.equals(req.type());
TxnType txnType = lending ? TxnType.EXPENSE : TxnType.INCOME;
String categoryCode = lending ? "cho_vay" : "di_vay";

TxnDetailResponse txnRes = txnBehavierService.create(userId, new TxnCreateReq(
        txnType,
        req.principalAmount(),
        wallet.id(),
        categoryCode,
        Instant.now(),
        req.counterpartyName(),
        req.note()
));
```

---

## 5. Bảng công việc chi tiết

| # | Công việc | Chi tiết | Trạng thái |
|---|---|---|---|
| 1 | Tạo migration `V13__create_debt.sql` | Tạo bảng `debts`, `debt_payments` và trigger `trg_debt_payments_sync` trong `migration_v2` | Hoàn thành |
| 2 | Mở khóa `debt` trong `pom.xml` | Bỏ `<exclude>` cho `debt` ở `pom.xml:237`, giữ loại trừ test ở `pom.xml:246` | Hoàn thành |
| 3 | Refactor `DebtPayment.java` | Kế thừa `AssignedIdEntity`, đồng bộ cột CSDL | Hoàn thành |
| 4 | Cập nhật Repository | Sửa `DebtRepository` và `DebtPaymentRepository` lọc thêm `NOT is_deleted` | Hoàn thành |
| 5 | Refactor `DebtServiceImpl.java` | Thay `TransactionWriter` bằng `TxnBehavierService`, cập nhật `category_code` và `WalletService` | Hoàn thành |
| 6 | Cập nhật `DebtMapper.java` | Ánh xạ DTO với `TxnDetailResponse` và `WalletSumaryRes` | Hoàn thành |
| 7 | Kiểm tra biên dịch | Chạy `mvn compile -DskipTests` xác nhận toàn bộ module `debt` build thành công 100% | Hoàn thành |
