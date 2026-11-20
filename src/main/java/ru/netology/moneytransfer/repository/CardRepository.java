package ru.netology.moneytransfer.repository;

import ru.netology.moneytransfer.model.Card;

import java.util.Optional;

public interface CardRepository {

    Optional<Card> findByNumber(String number);
}
