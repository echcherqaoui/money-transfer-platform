package com.moneytransfer.wallet.controller;

import com.moneytransfer.wallet.dto.DepositRequest;
import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.service.IDepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("${api.base-path}/wallets")
@RequiredArgsConstructor
public class DepositController {
    private final IDepositService depositService;

    @PostMapping("/deposits/{userId}")
    public ResponseEntity<WalletResponse> deposit(@PathVariable UUID userId,
                                                  @RequestBody @Valid DepositRequest request) {
        return ResponseEntity.ok(depositService.deposit(userId, request.amount()));
    }
}
