package ru.netology.moneytransfer.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTransferJournalTest {

    @TempDir
    Path tempDir;

    @Test
    void writesLineWithAllRequiredFields() throws IOException {
        Path file = tempDir.resolve("logs/transfers.log");   // папки logs ещё нет: журнал должен создать её сам
        FileTransferJournal journal = new FileTransferJournal(file.toString());

        journal.write(new JournalEntry(LocalDateTime.of(2026, 6, 15, 10, 30, 5), "op-1",
                "1111111111111111", "2222222222222222", 10_050, 100, "RUR", "Успешно"));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.contains("2026-06-15"));
        assertTrue(line.contains("10:30:05"));
        assertTrue(line.contains("с карты: 1111********1111"));
        assertTrue(line.contains("на карту: 2222********2222"));
        assertTrue(line.contains("сумма: 100.50 RUR"));
        assertTrue(line.contains("комиссия: 1.00 RUR"));
        assertTrue(line.contains("результат: Успешно"));
        assertFalse(line.contains("1111111111111111"));   // полный номер карты в журнал не попадает
    }

    @Test
    void appendsLinesInsteadOfOverwriting() throws IOException {
        Path file = tempDir.resolve("transfers.log");
        FileTransferJournal journal = new FileTransferJournal(file.toString());
        JournalEntry entry = new JournalEntry(LocalDateTime.of(2026, 6, 15, 10, 30, 5), "op-1",
                "1111111111111111", "2222222222222222", 100, 1, "RUR", "Успешно");

        journal.write(entry);
        journal.write(entry);

        assertEquals(2, Files.readAllLines(file).size());
    }

    @Test
    void masksOnlyValidCardNumbers() {
        assertEquals("1234********5678", FileTransferJournal.mask("1234567812345678"));
        assertEquals("-", FileTransferJournal.mask(null));
        assertEquals("-", FileTransferJournal.mask(" "));
        assertEquals("abc", FileTransferJournal.mask("abc"));
    }

    @Test
    void formatsKopecks() {
        assertEquals("0.05", FileTransferJournal.money(5));
        assertEquals("1.00", FileTransferJournal.money(100));
        assertEquals("12345.67", FileTransferJournal.money(1_234_567));
    }
}
