package ru.netology.moneytransfer.exception;

/**
 * Ошибка при выполнении перевода (код 500).
 */
public class TransferException extends RuntimeException {

    public TransferException(String message) {
        super(message);
    }
}
