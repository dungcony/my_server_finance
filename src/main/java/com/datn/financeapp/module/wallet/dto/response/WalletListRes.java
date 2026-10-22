package com.datn.financeapp.module.wallet.dto.response;

import com.datn.financeapp.common.response.PageMeta;

import java.util.List;

public record WalletListRes(
        List<WalletSumaryRes> wallets,
        PageMeta pageMeta
) {
}
