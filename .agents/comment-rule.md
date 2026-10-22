### QUY TẮC INLINE COMMENT (TRONG THÂN HÀM)

#### 1. Tuyệt đối không đánh số thứ tự các bước

* **CẤM:** `// 1.`, `// 2.`, `// 1/`, `// Bước 1:`, `// Step 1:`...
* **Lý do:** Khi thêm/bớt logic sẽ làm sai lệch thứ tự, gây nhiễu git diff khi phải re-number lại hàng loạt dòng.
* **Xử lý:**
    * Sai: `// 1. Xác thực người thực hiện`
    * Đúng: `// Xác thực người thực hiện`
    * Tốt nhất: Tách hẳn thành method `validateActor(...)` để code tự giải thích.

#### 2. Nguyên tắc "Giải thích TẠI SAO (Why), không giải thích LÀM GÌ (What)"

* Code nói lên **LÀM CÁI GÌ**, comment chỉ dùng để giải thích **TẠI SAO LẠI LÀM NHƯ VẬY**.
* **Sai:**
  ```java
  // Lấy danh sách giao dịch
  List<Transaction> transactions = getTransactions();