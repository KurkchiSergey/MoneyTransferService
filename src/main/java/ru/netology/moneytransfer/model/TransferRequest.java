package ru.netology.moneytransfer.model;

/**
 * Тело запроса POST /transfer (поля из спецификации).
 */
public record TransferRequest(String cardFromNumber,
                              String cardFromValidTill,
                              String cardFromCVV,
                              String cardToNumber,
                              Amount amount) {
}
