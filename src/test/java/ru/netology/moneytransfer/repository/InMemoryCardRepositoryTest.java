package ru.netology.moneytransfer.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import ru.netology.moneytransfer.model.Card;

import java.io.UncheckedIOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCardRepositoryTest {

    @Test
    void loadsCardsFromJsonFile() {
        InMemoryCardRepository repository =
                new InMemoryCardRepository(new ObjectMapper(), new ClassPathResource("cards.json"));

        Optional<Card> card = repository.findByNumber("1111111111111111");

        assertTrue(card.isPresent());
        assertEquals("12/30", card.get().getValidTill());
        assertEquals("111", card.get().getCvv());
        assertEquals("RUR", card.get().getCurrency());
        assertEquals(10_000_000, card.get().getBalance());
    }

    @Test
    void returnsEmptyForUnknownOrNullNumber() {
        InMemoryCardRepository repository =
                new InMemoryCardRepository(new ObjectMapper(), new ClassPathResource("cards.json"));

        assertTrue(repository.findByNumber("0000000000000000").isEmpty());
        assertTrue(repository.findByNumber(null).isEmpty());
    }

    @Test
    void failsOnBrokenFile() {
        ByteArrayResource broken = new ByteArrayResource("not json".getBytes());

        assertThrows(UncheckedIOException.class, () -> new InMemoryCardRepository(new ObjectMapper(), broken));
    }
}
