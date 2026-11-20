package ru.netology.moneytransfer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.netology.moneytransfer.exception.InvalidInputException;
import ru.netology.moneytransfer.exception.TransferException;
import ru.netology.moneytransfer.journal.JournalEntry;
import ru.netology.moneytransfer.journal.TransferJournal;
import ru.netology.moneytransfer.model.Card;
import ru.netology.moneytransfer.model.ConfirmRequest;
import ru.netology.moneytransfer.model.Operation;
import ru.netology.moneytransfer.model.TransferRequest;
import ru.netology.moneytransfer.repository.CardRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Бизнес-логика перевода в два шага:
 * 1) transfer — проверяет данные и создаёт операцию (деньги ещё не списаны);
 * 2) confirm — проверяет код и выполняет списание и зачисление.
 */
@Service
public class TransferService {

    public static final String CURRENCY = "RUR";

    private static final Pattern CARD_NUMBER = Pattern.compile("\\d{16}");
    private static final Pattern CVV = Pattern.compile("\\d{3}");
    private static final Pattern VALID_TILL = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");

    private final CardRepository cardRepository;
    private final TransferJournal journal;
    private final Clock clock;
    private final String confirmationCode;
    private final long commissionPercent;

    private final Map<String, Operation> operations = new ConcurrentHashMap<>();

    public TransferService(CardRepository cardRepository,
                           TransferJournal journal,
                           Clock clock,
                           @Value("${transfer.confirmation-code}") String confirmationCode,
                           @Value("${transfer.commission-percent}") long commissionPercent) {
        this.cardRepository = cardRepository;
        this.journal = journal;
        this.clock = clock;
        this.confirmationCode = confirmationCode;
        this.commissionPercent = commissionPercent;
    }

    /**
     * Шаг 1. Проверяет данные перевода и создаёт операцию, ожидающую подтверждения.
     */
    public synchronized String transfer(TransferRequest request) {
        String operationId = UUID.randomUUID().toString();
        try {
            validate(request);

            long amount = request.amount().value();
            long commission = calculateCommission(amount);

            Card from = cardRepository.findByNumber(request.cardFromNumber())
                    .orElseThrow(() -> new InvalidInputException("Карта отправителя не найдена"));
            if (!from.getValidTill().equals(request.cardFromValidTill())
                    || !from.getCvv().equals(request.cardFromCVV())) {
                throw new InvalidInputException("Неверные данные карты отправителя");
            }
            if (from.getBalance() < amount + commission) {
                throw new InvalidInputException("Недостаточно средств на карте отправителя");
            }

            Operation operation = new Operation(operationId, request.cardFromNumber(), request.cardToNumber(),
                    amount, commission, CURRENCY);
            operations.put(operationId, operation);
            write(operation, "Операция создана, ожидает подтверждения");
            return operationId;
        } catch (InvalidInputException e) {
            writeRejected(operationId, request, "Отклонено: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Шаг 2. Подтверждает операцию кодом и выполняет перевод.
     */
    public synchronized String confirm(ConfirmRequest request) {
        if (request == null || isBlank(request.operationId())) {
            throw new InvalidInputException("Не указан идентификатор операции");
        }
        Operation operation = operations.get(request.operationId());
        if (operation == null) {
            throw new InvalidInputException("Операция не найдена");
        }
        if (operation.getStatus() != Operation.Status.PENDING) {
            throw new InvalidInputException("Операция уже обработана");
        }
        if (request.code() == null || !confirmationCode.equals(request.code())) {
            write(operation, "Неверный код подтверждения");
            throw new InvalidInputException("Неверный код подтверждения");
        }

        long total = operation.getAmount() + operation.getCommission();
        Card from = cardRepository.findByNumber(operation.getCardFrom()).orElse(null);
        if (from == null || from.getBalance() < total) {
            operation.setStatus(Operation.Status.FAILED);
            write(operation, "Ошибка: недостаточно средств на момент списания");
            throw new TransferException("Ошибка перевода: недостаточно средств");
        }

        from.withdraw(total);
        // Карта получателя может быть чужой (её нет в нашем хранилище): тогда деньги просто уходят с карты отправителя
        cardRepository.findByNumber(operation.getCardTo()).ifPresent(to -> to.deposit(operation.getAmount()));

        operation.setStatus(Operation.Status.COMPLETED);
        write(operation, "Успешно");
        return operation.getId();
    }

    private void validate(TransferRequest r) {
        if (r == null) {
            throw new InvalidInputException("Пустой запрос");
        }
        if (r.cardFromNumber() == null || !CARD_NUMBER.matcher(r.cardFromNumber()).matches()) {
            throw new InvalidInputException("Номер карты отправителя должен состоять из 16 цифр");
        }
        if (r.cardToNumber() == null || !CARD_NUMBER.matcher(r.cardToNumber()).matches()) {
            throw new InvalidInputException("Номер карты получателя должен состоять из 16 цифр");
        }
        if (r.cardFromNumber().equals(r.cardToNumber())) {
            throw new InvalidInputException("Карты отправителя и получателя совпадают");
        }
        if (r.cardFromCVV() == null || !CVV.matcher(r.cardFromCVV()).matches()) {
            throw new InvalidInputException("CVC должен состоять из 3 цифр");
        }
        validateValidTill(r.cardFromValidTill());
        if (r.amount() == null || r.amount().value() == null || r.amount().value() <= 0) {
            throw new InvalidInputException("Сумма перевода должна быть больше нуля");
        }
        if (!CURRENCY.equals(r.amount().currency())) {
            throw new InvalidInputException("Поддерживается только валюта " + CURRENCY);
        }
    }

    private void validateValidTill(String validTill) {
        Matcher m = validTill == null ? null : VALID_TILL.matcher(validTill);
        if (m == null || !m.matches()) {
            throw new InvalidInputException("Срок действия карты должен быть в формате ММ/ГГ");
        }
        // Карта действует до конца указанного месяца
        YearMonth lastMonth = YearMonth.of(2000 + Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        if (lastMonth.isBefore(YearMonth.now(clock))) {
            throw new InvalidInputException("Истёк срок действия карты");
        }
    }

    private long calculateCommission(long amount) {
        return amount * commissionPercent / 100;
    }

    private void write(Operation op, String result) {
        journal.write(new JournalEntry(LocalDateTime.now(clock), op.getId(), op.getCardFrom(), op.getCardTo(),
                op.getAmount(), op.getCommission(), op.getCurrency(), result));
    }

    // Запись об отклонённом запросе: данные могут быть неполными, поэтому берём то, что есть
    private void writeRejected(String operationId, TransferRequest r, String result) {
        String from = r == null ? null : r.cardFromNumber();
        String to = r == null ? null : r.cardToNumber();
        long amount = 0;
        if (r != null && r.amount() != null && r.amount().value() != null && r.amount().value() > 0) {
            amount = r.amount().value();
        }
        journal.write(new JournalEntry(LocalDateTime.now(clock), operationId, from, to,
                amount, calculateCommission(amount), CURRENCY, result));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
