package com.moneytransfer.transaction.mapper;

import com.moneytransfer.transaction.dto.request.TransferRequest;
import com.moneytransfer.transaction.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {
    public Transaction fromTransferRequest(TransferRequest request){
        return new Transaction()
              .setReceiverId(request.receiverId())
              .setAmount(request.amount());
    }
}
