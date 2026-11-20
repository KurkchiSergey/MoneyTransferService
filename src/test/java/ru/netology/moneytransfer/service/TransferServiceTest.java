package ru.netology.moneytransfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.netology.moneytransfer.exception.InvalidInputException;
import ru.netology.moneytransfer.exception.TransferException;
import ru.netology.moneytransfer.journal.JournalEntry;
import ru.netology.moneytransfer.journal.TransferJournal;
import ru.netology.moneytransfer.model.Amount;
import ru.netology.moneytransfer.model.Card;
import ru.netology.moneytransfer.model.ConfirmRequest;
import ru.netology.moneytransfer.model.TransferRequest;
import ru.netology.moneytransfer.repository.CardRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String FROM = "1111111111111111";
    private static final String TO = "2222222222222222";

    // «Сейчас» — 15 июня 2026 года, чтобы проверка срока карты не зависела от реальной даты
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private CardRepository cardRepository;
    @Mock
    private TransferJournal journal;

    private TransferService service;
    private Card cardFrom;
    private Card cardTo;

    @BeforeEach
    void setUp() {
        service = new TransferService(cardRepository, journal, clock, "0000", 1);
        cardFrom = new Card(FROM, "12/30", "111", "RUR", 1_000_000);   // 10 000.00
        cardTo = new Card(TO, "11/30", "222", "RUR", 0);
    }

    private TransferRequest request(long amount) {
        return new TransferRequest(FROM, "12/30", "111", TO, new Amount(amount, "RUR"));
    }

    private void cardFromExists() {
        when(cardRepository.findByNumber(FROM)).thenReturn(Optional.of(cardFrom));
    }

    @Test
    void transferCreatesOperationAndDoesNotMoveMoney() {
        cardFromExists();

        String operationId = service.transfer(request(10_000));

        assertNotNull(operationId);
        assertEquals(1_000_000, cardFrom.getBalance());
        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journal).write(entry.capture());
        assertEquals(operationId, entry.getValue().operationId());
        assertEquals(100, entry.getValue().commission());   // 1% от 100.00 = 1.00
        assertTrue(entry.getValue().result().contains("ожидает подтверждения"));
    }

    @Test
    void confirmMovesMoneyAndTakesCommission() {
        cardFromExists();
        when(cardRepository.findByNumber(TO)).thenReturn(Optional.of(cardTo));
        String operationId = service.transfer(request(10_000));

        String confirmed = service.confirm(new ConfirmRequest(operationId, "0000"));

        assertEquals(operationId, confirmed);
        assertEquals(1_000_000 - 10_000 - 100, cardFrom.getBalance());   // списано сумма + комиссия
        assertEquals(10_000, cardTo.getBalance());                       // зачислена сумма без комиссии
    }

    @Test
    void confirmWorksWhenReceiverCardIsNotInStorage() {
        cardFromExists();
        when(cardRepository.findByNumber(TO)).thenReturn(Optional.empty());
        String operationId = service.transfer(request(10_000));

        service.confirm(new ConfirmRequest(operationId, "0000"));

        assertEquals(1_000_000 - 10_000 - 100, cardFrom.getBalance());
    }

    @Test
    void confirmWritesSuccessToJournal() {
        cardFromExists();
        when(cardRepository.findByNumber(TO)).thenReturn(Optional.of(cardTo));
        String operationId = service.transfer(request(10_000));

        service.confirm(new ConfirmRequest(operationId, "0000"));

        ArgumentCaptor<JournalEntry> entries = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journal, org.mockito.Mockito.times(2)).write(entries.capture());
        assertEquals("Успешно", entries.getAllValues().get(1).result());
    }

    @Test
    void confirmWithWrongCodeThrowsAndKeepsMoney() {
        cardFromExists();
        String operationId = service.transfer(request(10_000));

        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> service.confirm(new ConfirmRequest(operationId, "1234")));

        assertEquals("Неверный код подтверждения", e.getMessage());
        assertEquals(1_000_000, cardFrom.getBalance());
    }

    @Test
    void confirmUnknownOperationThrows() {
        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> service.confirm(new ConfirmRequest("unknown", "0000")));

        assertEquals("Операция не найдена", e.getMessage());
    }

    @Test
    void confirmWithoutOperationIdThrows() {
        assertThrows(InvalidInputException.class, () -> service.confirm(new ConfirmRequest(null, "0000")));
        assertThrows(InvalidInputException.class, () -> service.confirm(null));
    }

    @Test
    void confirmSameOperationTwiceThrows() {
        cardFromExists();
        when(cardRepository.findByNumber(TO)).thenReturn(Optional.of(cardTo));
        String operationId = service.transfer(request(10_000));
        service.confirm(new ConfirmRequest(operationId, "0000"));

        InvalidInputException e = assertThrows(InvalidInputException.class,
                () -> service.confirm(new ConfirmRequest(operationId, "0000")));

        assertEquals("Операция уже обработана", e.getMessage());
        assertEquals(10_000, cardTo.getBalance());   // деньги зачислены только один раз
    }

    @Test
    void confirmThrowsTransferExceptionWhenMoneyIsGone() {
        cardFromExists();
        String operationId = service.transfer(request(10_000));
        cardFrom.setBalance(0);   // деньги ушли между созданием и подтверждением операции

        assertThrows(TransferException.class, () -> service.confirm(new ConfirmRequest(operationId, "0000")));
    }

    @Test
    void transferWithUnknownCardThrows() {
        when(cardRepository.findByNumber(FROM)).thenReturn(Optional.empty());

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> service.transfer(request(100)));

        assertEquals("Карта отправителя не найдена", e.getMessage());
    }

    @Test
    void transferWithWrongCvvThrows() {
        cardFromExists();
        TransferRequest wrongCvv = new TransferRequest(FROM, "12/30", "999", TO, new Amount(100L, "RUR"));

        assertThrows(InvalidInputException.class, () -> service.transfer(wrongCvv));
    }

    @Test
    void transferWithWrongValidTillThrows() {
        cardFromExists();
        TransferRequest wrongDate = new TransferRequest(FROM, "11/30", "111", TO, new Amount(100L, "RUR"));

        assertThrows(InvalidInputException.class, () -> service.transfer(wrongDate));
    }

    @Test
    void transferWithExpiredCardThrows() {
        TransferRequest expired = new TransferRequest(FROM, "05/26", "111", TO, new Amount(100L, "RUR"));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> service.transfer(expired));

        assertEquals("Истёк срок действия карты", e.getMessage());
    }

    @Test
    void cardIsValidUntilEndOfItsLastMonth() {
        // «Сейчас» июнь 2026: карта со сроком 06/26 ещё действует
        cardFrom.setValidTill("06/26");
        cardFromExists();
        TransferRequest request = new TransferRequest(FROM, "06/26", "111", TO, new Amount(100L, "RUR"));

        assertNotNull(service.transfer(request));
    }

    @Test
    void transferWithInsufficientFundsThrows() {
        cardFrom.setBalance(10_050);   // хватает на сумму 100.00, но не хватает на комиссию 1.00
        cardFromExists();

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> service.transfer(request(10_000)));

        assertEquals("Недостаточно средств на карте отправителя", e.getMessage());
    }

    @Test
    void transferWithNonPositiveAmountThrows() {
        assertThrows(InvalidInputException.class, () -> service.transfer(request(0)));
        assertThrows(InvalidInputException.class, () -> service.transfer(request(-5)));
    }

    @Test
    void transferWithUnsupportedCurrencyThrows() {
        TransferRequest usd = new TransferRequest(FROM, "12/30", "111", TO, new Amount(100L, "USD"));

        assertThrows(InvalidInputException.class, () -> service.transfer(usd));
    }

    @Test
    void transferToTheSameCardThrows() {
        TransferRequest same = new TransferRequest(FROM, "12/30", "111", FROM, new Amount(100L, "RUR"));

        assertThrows(InvalidInputException.class, () -> service.transfer(same));
    }

    @Test
    void transferWithMalformedFieldsThrows() {
        assertThrows(InvalidInputException.class, () -> service.transfer(null));
        assertThrows(InvalidInputException.class, () -> service.transfer(
                new TransferRequest("123", "12/30", "111", TO, new Amount(100L, "RUR"))));
        assertThrows(InvalidInputException.class, () -> service.transfer(
                new TransferRequest(FROM, "12/30", "11", TO, new Amount(100L, "RUR"))));
        assertThrows(InvalidInputException.class, () -> service.transfer(
                new TransferRequest(FROM, "1230", "111", TO, new Amount(100L, "RUR"))));
        assertThrows(InvalidInputException.class, () -> service.transfer(
                new TransferRequest(FROM, "12/30", "111", TO, null)));
    }

    @Test
    void rejectedTransferIsWrittenToJournalAndDoesNotCreateOperation() {
        when(cardRepository.findByNumber(FROM)).thenReturn(Optional.empty());

        assertThrows(InvalidInputException.class, () -> service.transfer(request(100)));

        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journal).write(entry.capture());
        assertTrue(entry.getValue().result().startsWith("Отклонено"));
        verify(cardRepository, never()).findByNumber(TO);
    }
}
