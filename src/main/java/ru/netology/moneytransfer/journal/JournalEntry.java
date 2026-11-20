package ru.netology.moneytransfer.journal;

import java.time.LocalDateTime;

/**
 * Одна запись журнала переводов. Суммы — в копейках.
 */
public record JournalEntry(LocalDateTime time,
                           String operationId,
                           String cardFrom,
                           String cardTo,
                           long amount,
                           long commission,
                           String currency,
                           String result) {
}
