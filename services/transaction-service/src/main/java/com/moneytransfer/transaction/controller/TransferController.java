package com.moneytransfer.transaction.controller;

import com.moneytransfer.transaction.dto.request.TransferRequest;
import com.moneytransfer.transaction.dto.response.PaginatedResponse;
import com.moneytransfer.transaction.dto.response.TransactionDetailResponse;
import com.moneytransfer.transaction.dto.response.TransactionResponse;
import com.moneytransfer.transaction.dto.response.TransactionStatsResponse;
import com.moneytransfer.transaction.enums.TransactionStatus;
import com.moneytransfer.transaction.service.ISseEmitterService;
import com.moneytransfer.transaction.service.ITransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@RestController
@RequestMapping("${api.base-path}/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final ITransactionService transactionService;
    private final ISseEmitterService sseEmitterService;

    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity
              .status(CREATED)
              .body(transactionService.initiateTransfer(request));
    }

    @GetMapping("/me")
    public ResponseEntity<PaginatedResponse<TransactionResponse>> getMyTransfers(@RequestParam(required = false, defaultValue = "0") int page,
                                                                                 @RequestParam(required = false, defaultValue = "10") int size,
                                                                                 @RequestParam(required = false) TransactionStatus status) {
        return ResponseEntity.ok(transactionService.getTransfers(page, size, status));
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<TransactionDetailResponse> getTransfer(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getTransfer(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<TransactionStatsResponse> getStats() {
        return ResponseEntity.ok(transactionService.getStats());
    }

    @GetMapping(value = "/stream", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTransferUpdates() {
        return sseEmitterService.registerTransferEmitter();
    }
}