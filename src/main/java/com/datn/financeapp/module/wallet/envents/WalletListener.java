package com.datn.financeapp.module.wallet.envents;

import com.datn.financeapp.module.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletListener {

    private final WalletService walletService;

}
