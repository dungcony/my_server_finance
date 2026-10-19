# Lỗi module Group (nhóm chung quỹ)

[← Tổng quan](review-00-tong-quan.md)

Phần tính số dư thành viên (`BalanceCalculator`) đang tính đúng. Các lỗi dưới đây nằm ở chỗ **khi nào** thì gọi phép
tính, không nằm ở công thức.

<a id="g1"></a>

## G1. Tạo nhóm luôn lỗi — Nặng

- **Ở
  đâu:** [GroupServiceImpl.java:87](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java#L87)
- **Chuyện gì xảy ra:** Hàm khai báo là `create(operatorId, groupId)`
  ([FundServiceImpl.java:51](../../src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java#L51)),
  nhưng lại được gọi với 2 tham số đảo ngược:

```java
fundService.create(groupId, operatorId);
```

Kết quả: `group_funds.group_id` nhận id của **user**, `keepper_id` nhận id của **nhóm**. Cả 2 khoá ngoại đều sai, nên
**mọi lần tạo nhóm đều lỗi**.

- **Nên sửa:** Đổi lại thứ tự thành `fundService.create(operatorId, groupId)`.

<a id="g2"></a>

## G2. Duyệt được giao dịch của nhóm khác — Nặng

- **Ở
  đâu:** [GroupTxnUpdateImpl.java:116](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L116)
  (`status`), [GroupTxnUpdateImpl.java:149](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L149)
  (`statuses`)
- **Chuyện gì xảy ra:** Code kiểm tra người duyệt là chủ nhóm/thủ quỹ của nhóm `groupId` trên URL. Nhưng khi tìm giao
  dịch thì **không lọc theo `groupId`**.
    - Ví dụ: A là chủ nhóm X, gọi `/groups/X/...` với id giao dịch của nhóm Y → duyệt được.
    - Tệ hơn, tiền của giao dịch nhóm Y lại được **cộng vào quỹ và số dư của nhóm X**.
- **Nên sửa:** Dùng hàm có lọc `groupId`. Repo đã có sẵn
  [GroupTxnRepository](../../src/main/java/com/datn/financeapp/module/group/repository/GroupTxnRepository.java)
  `findByIdAndGroupIdAndDeletedAtIsNull` (rồi kiểm trạng thái `PENDING`) và
  `findByIdInAndGroupIdAndDeletedAtIsNullAndStatus`.

<a id="g3"></a>

## G3. Từ chối hàng loạt vẫn cộng tiền — Nặng

- **Ở
  đâu:** [GroupTxnUpdateImpl.java:141-175](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L141)
- **Chuyện gì xảy ra:** Hàm `statuses()` (duyệt nhiều giao dịch) luôn cộng số dư thành viên, cộng quỹ và gửi thông báo
  "ghi vào sổ cá nhân?", **bất kể** trạng thái mới là `CONFIRMED` hay `REJECTED`. Từ chối hàng loạt vì vậy có tác dụng
  như duyệt hàng loạt.
- **Nên sửa:** Chỉ tính tiền và gửi thông báo khi trạng thái mới là `CONFIRMED`.

<a id="g4"></a>

## G4. Đổi trạng thái sang `PENDING` → lỗi 500 — Nặng

- **Ở
  đâu:** [GroupTxnUpdateImpl.java:122](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L122), [GroupTxnUpdateImpl.java:160](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L160)
- **Chuyện gì xảy ra:** API duyệt nhận bất kỳ giá trị `status` nào (DTO chỉ `@NotNull`). Gửi `PENDING` thì code vẫn ghi
  `reviewed_by`/`reviewed_at`, vi phạm check `ck_gt_review` (giao dịch `PENDING` không được có người duyệt), nên DB báo
  lỗi.
- **Nên sửa:** Chỉ chấp nhận `CONFIRMED` hoặc `REJECTED`; giá trị khác thì trả lỗi validate.

<a id="g5"></a>

## G5. Sửa giao dịch đang chờ duyệt vẫn làm đổi quỹ — Nặng

- **Ở
  đâu:** [GroupTxnUpdateImpl.java:92-100](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L92)
- **Chuyện gì xảy ra:** Sửa một giao dịch `PENDING` thì phần số dư thành viên được chặn đúng (chỉ tính khi `CONFIRMED`),
  nhưng phần **quỹ thì không chặn**: `FundBalanceChangedEvent` được phát bất kể trạng thái.
    - Ví dụ: giao dịch chi từ quỹ 100k đang chờ duyệt, sửa thành 200k → quỹ bị trừ ngay 100k.
    - Khi duyệt, quỹ bị trừ thêm 200k. Tổng cộng trừ 300k, trong khi đúng ra chỉ trừ 200k.
- **Nên sửa:** Chỉ đổi quỹ khi giao dịch đang `CONFIRMED`.

<a id="g6"></a>

## G6. Xoá giao dịch chưa duyệt vẫn trừ số dư thành viên — Nặng

- **Ở
  đâu:** [GroupTxnDeleteImpl.java:51](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java#L51), [GroupTxnDeleteImpl.java:60](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnDeleteImpl.java#L60)
- **Chuyện gì xảy ra:** Khi xoá giao dịch, phần quỹ có kiểm `CONFIRMED`, nhưng phần **số dư thành viên** thì lúc nào
  cũng bị trừ. Giao dịch `PENDING`/`REJECTED` chưa từng được cộng vào số dư, vậy mà xoá đi thì số dư lại bị trừ, làm số
  dư thành viên bị **âm sai**.
- **Nên sửa:** Chỉ gọi `applyDelta` khi giao dịch đang `CONFIRMED`.

<a id="g7"></a>

## G7. Sửa giao dịch nhóm vẫn thiếu một số kiểm tra — Vừa

- **Đã sửa:** thêm `validTypeAndMoneySources` kiểm loại, nguồn tiền, người chi, danh mục bắt buộc cho `EXPENSE`.
- **Ở
  đâu:** [GroupTxnUpdateImpl.java:47-102](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L47)
- **Còn thiếu so với lúc tạo:**

| Thiếu gì                                                                                                                                                                 | Hậu quả                                                                                              |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Không gọi `validParticipants`                                                                                                                                            | Tổng tiền chia ≠ tổng giao dịch; chia cho người ngoài nhóm; id trùng thì vi phạm khoá chính, lỗi 500 |
| REFUND chưa kiểm hạn mức (còn ghi chú "kiểm tra hạn mức hoàn ở đây" ở [dòng 226](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L226)) | Sửa hoàn tiền vượt số tiền thành viên còn trong quỹ                                                  |
| Đổi loại sang `CONTRIBUTION`/`REFUND` nhưng không xoá `categoryId`                                                                                                       | Vi phạm check `ck_gt_shape`, lỗi 500                                                                 |
| Tìm danh mục bằng `cateCache.findId` ([dòng 82](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L82))                          | Gắn được cả danh mục **đã xoá**; còn import thẳng helper của module `category` (sai rule 2.2)        |
| Hàm `return null` ([dòng 102](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupTxnUpdateImpl.java#L102))                                           | App luôn nhận `data: null` sau khi sửa                                                               |

- **Nên sửa:** Dùng lại các bước kiểm như lúc tạo; tìm danh mục qua `CategoryService`; trả về `toDetailResponse(txn)`.

<a id="g8"></a>

## G8. Thủ quỹ kiểm kê quỹ bị lỗi 500 — Vừa (cần bạn chốt)

- **Ở
  đâu:** [GroupTxnHelper.java:158](../../src/main/java/com/datn/financeapp/module/group/helper/GroupTxnHelper.java#L158), [FundServiceImpl.java:100-160](../../src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java#L100)
- **Chuyện gì xảy ra:**
    - Chỉ **thủ quỹ** được gọi kiểm kê.
    - Thủ quỹ mà không phải chủ nhóm tạo giao dịch kiểm kê thì code để `PENDING` (chờ chủ nhóm duyệt).
    - Nhưng check `ck_gt_treasurer_confirmed`
      ([V4__group.sql:104](../../src/main/resources/db/migration_v2/V4__group.sql#L104)) bắt giao dịch kiểm
      kê/hoàn tiền **phải là `CONFIRMED`**, nên insert lỗi.
- **Cần chốt một trong hai:**
    - Kiểm kê luôn `CONFIRMED` (sửa code), hoặc
    - Bỏ check này ở DB (sửa migration).

<a id="g9"></a>

## G9. Thành viên thường thêm được người vào nhóm — Vừa

- **Ở
  đâu:** [MemberBehavierServiceImpl.java:77](../../src/main/java/com/datn/financeapp/module/group/service/impl/MemberBehavierServiceImpl.java#L77)
- **Chuyện gì xảy ra:** Hàm tên `ownerAddMembers` nhưng chỉ kiểm "nhóm chưa bị xoá" (`inGroupNotDeleted`), không kiểm
  người gọi là chủ nhóm. Thành viên thường vẫn thêm người được, và thêm được cả khi nhóm đã **lưu trữ (ARCHIVED)**.
- **Nên sửa:** Thêm điều kiện `isOwner` và `inGroupActive`.

<a id="g10"></a>

## G10. Thủ quỹ rời nhóm → quyền của chủ nhóm bị cache cũ — Vừa

- **Ở
  đâu:** [FundServiceImpl.java:167](../../src/main/java/com/datn/financeapp/module/group/service/impl/FundServiceImpl.java#L167)
- **Chuyện gì xảy ra:** Thủ quỹ rời hoặc bị xoá khỏi nhóm thì quỹ được giao lại cho chủ nhóm. Nhưng code chỉ xoá cache
  quyền của **người rời**, còn cache của **chủ nhóm** vẫn ghi thủ quỹ cũ. Trong tối đa 30 phút, chủ nhóm không làm được
  các việc của thủ quỹ (hoàn tiền, kiểm kê, lưu trữ nhóm…).
- **Nên sửa:** Trong `onMemberLeave`, khi đổi thủ quỹ thì gọi `authCache.evictGroup(groupId)`.

<a id="g11"></a>

## G11. Các lỗi nhỏ khác — Nhẹ

- **Vào nhóm bằng mã mời:** `catch (DataIntegrityViolationException)` không bắt được, vì lỗi trùng chỉ nổ lúc commit chứ
  không phải lúc `save`. Kết quả trả 500 thay vì "đã ở trong nhóm"
  ([GroupServiceImpl.java:277](../../src/main/java/com/datn/financeapp/module/group/service/impl/GroupServiceImpl.java#L277)).
- **`target` lệch giữa DTO và DB:** DTO cho phép `target = 0` (`@PositiveOrZero`), nhưng DB bắt `target > 0`
  ([V4__group.sql:22](../../src/main/resources/db/migration_v2/V4__group.sql#L22)), nên lỗi 500
  ([GroupCreateReq.java:15](../../src/main/java/com/datn/financeapp/module/group/dto/request/group/GroupCreateReq.java#L15)).
