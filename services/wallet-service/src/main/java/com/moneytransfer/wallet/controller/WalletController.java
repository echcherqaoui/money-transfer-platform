package com.moneytransfer.wallet.controller;

import com.moneytransfer.wallet.dto.WalletResponse;
import com.moneytransfer.wallet.service.ISseEmitterService;
import com.moneytransfer.wallet.service.IWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController
@RequestMapping("${api.base-path}/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final IWalletService walletQueryService;
    private final ISseEmitterService sseEmitterService;

    @PostMapping("/me")
    public ResponseEntity<WalletResponse> createWallet() {
        return ResponseEntity
              .status(CREATED)
              .body(walletQueryService.createWallet());
    }

    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getMyWallet() {
        return ResponseEntity.ok(walletQueryService.getMyWallet());
    }

    @GetMapping("/user/{userId}/exists")
    public ResponseEntity<Boolean> hasWallet(@PathVariable UUID userId) {
        return ResponseEntity.ok(walletQueryService.hasWallet(userId));
    }

    @GetMapping(value = "/stream", produces = TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamBalance() {
        return ResponseEntity.ok()
              .header("X-Accel-Buffering", "no")
              .body(sseEmitterService.register());
    }
}