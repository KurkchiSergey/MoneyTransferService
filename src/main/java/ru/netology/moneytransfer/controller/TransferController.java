package ru.netology.moneytransfer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.netology.moneytransfer.model.ConfirmRequest;
import ru.netology.moneytransfer.model.OperationResponse;
import ru.netology.moneytransfer.model.TransferRequest;
import ru.netology.moneytransfer.service.TransferService;


@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public OperationResponse transfer(@RequestBody TransferRequest request) {
        return new OperationResponse(transferService.transfer(request));
    }

    @PostMapping("/confirmOperation")
    public OperationResponse confirmOperation(@RequestBody ConfirmRequest request) {
        return new OperationResponse(transferService.confirm(request));
    }
}
