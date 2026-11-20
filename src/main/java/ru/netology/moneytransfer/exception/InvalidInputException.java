package ru.netology.moneytransfer.exception;

/**
 * Ошибка во входных данных (код 400).
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
