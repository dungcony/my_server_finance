package com.datn.financeapp.module.transaction.events;

import com.datn.financeapp.module.transaction.entity.RecTxn;

/**
 * Phát ra khi sửa giao dịch định kỳ làm thay đổi số tiền hoặc lịch lặp.
 *
 * @param recTxn          giao dịch định kỳ sau khi sửa
 * @param scheduleChanged true nếu đổi tần suất, bước lặp hoặc ngày kết thúc
 */
public record RecTxnUpdateEvent(RecTxn recTxn, boolean scheduleChanged) {
}
