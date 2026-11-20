package ru.netology.moneytransfer.model;

/**
 * Сумма перевода. value — в копейках (FRONT отправляет рубли * 100), currency — код валюты (RUR).
 */
public record Amount(Long value, String currency) {
}
