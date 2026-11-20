package ru.netology.moneytransfer.model;

/**
 * Тело запроса POST /confirmOperation.
 */
public record ConfirmRequest(String operationId, String code) {
}
