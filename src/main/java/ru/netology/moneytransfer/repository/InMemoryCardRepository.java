package ru.netology.moneytransfer.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import ru.netology.moneytransfer.model.Card;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит карты в памяти. При старте читает их из JSON-файла (transfer.cards-file).
 */
@Repository
public class InMemoryCardRepository implements CardRepository {

    private final Map<String, Card> cards = new ConcurrentHashMap<>();

    public InMemoryCardRepository(ObjectMapper objectMapper,
                                  @Value("${transfer.cards-file}") Resource cardsFile) {
        try (InputStream in = cardsFile.getInputStream()) {
            List<Card> loaded = objectMapper.readValue(in, new TypeReference<List<Card>>() {
            });
            loaded.forEach(card -> cards.put(card.getNumber(), card));
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать файл с картами: " + cardsFile, e);
        }
    }

    @Override
    public Optional<Card> findByNumber(String number) {
        return Optional.ofNullable(number).map(cards::get);
    }
}
