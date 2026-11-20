package ru.netology.moneytransfer.journal;

/**
 * Журнал переводов.
 */
public interface TransferJournal {

    void write(JournalEntry entry);
}
