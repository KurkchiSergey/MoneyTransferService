package ru.netology.moneytransfer.model;

/**
 * Созданная, но ещё не подтверждённая (или уже обработанная) операция перевода.
 */
public class Operation {

    public enum Status {PENDING, COMPLETED, FAILED}

    private final String id;
    private final String cardFrom;
    private final String cardTo;
    private final long amount;      // копейки
    private final long commission;  // копейки
    private final String currency;
    private Status status = Status.PENDING;

    public Operation(String id, String cardFrom, String cardTo, long amount, long commission, String currency) {
        this.id = id;
        this.cardFrom = cardFrom;
        this.cardTo = cardTo;
        this.amount = amount;
        this.commission = commission;
        this.currency = currency;
    }

    public String getId() {
        return id;
    }

    public String getCardFrom() {
        return cardFrom;
    }

    public String getCardTo() {
        return cardTo;
    }

    public long getAmount() {
        return amount;
    }

    public long getCommission() {
        return commission;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
