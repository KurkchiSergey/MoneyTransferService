package ru.netology.moneytransfer.model;

/**
 * Ответ с ошибкой (коды 400 и 500). FRONT показывает пользователю текст из поля message.
 */
public record ErrorResponse(String message, int id) {
}
