package com.moneytransfer.transaction.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface ISseEmitterService {

    SseEmitter registerTransferEmitter();

    void pushToEmitter(String data,
                       UUID userId,
                       String eventName);
}
