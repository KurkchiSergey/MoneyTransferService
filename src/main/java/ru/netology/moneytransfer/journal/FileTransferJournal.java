package ru.netology.moneytransfer.journal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Дописывает в текстовый файл по одной строке на каждое событие перевода:
 * дата | время | операция | карта списания | карта зачисления | сумма | комиссия | результат.
 * Номера карт маскируются (первые 4 и последние 4 цифры), CVV в журнал не попадает.
 */
@Component
public class FileTransferJournal implements TransferJournal {

    private static final Logger log = LoggerFactory.getLogger(FileTransferJournal.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path file;

    public FileTransferJournal(@Value("${transfer.journal-file}") String file) {
        this.file = Path.of(file);
    }

    @Override
    public synchronized void write(JournalEntry e) {
        String line = String.join(" | ",
                e.time().format(DATE),
                e.time().format(TIME),
                "операция: " + e.operationId(),
                "с карты: " + mask(e.cardFrom()),
                "на карту: " + mask(e.cardTo()),
                "сумма: " + money(e.amount()) + " " + e.currency(),
                "комиссия: " + money(e.commission()) + " " + e.currency(),
                "результат: " + e.result());
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            // Сбой записи журнала не должен ломать сам перевод
            log.error("Не удалось записать в журнал переводов {}: {}", file, ex.getMessage());
        }
    }

    static String mask(String card) {
        if (card == null || card.isBlank()) {
            return "-";
        }
        if (card.matches("\\d{16}")) {
            return card.substring(0, 4) + "********" + card.substring(12);
        }
        return card;
    }

    static String money(long kopecks) {
        return String.format(Locale.ROOT, "%d.%02d", kopecks / 100, kopecks % 100);
    }
}
