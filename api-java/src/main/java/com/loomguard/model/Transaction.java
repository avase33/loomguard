package com.loomguard.model;

/**
 * A raw checkout event (see {@code proto/protocol.md}).
 *
 * @param ts epoch milliseconds; the API stamps arrival time when this is 0
 */
public record Transaction(
        String txnId,
        String userId,
        String cardId,
        double amount,
        String currency,
        String merchant,
        String country,
        long ts) {

    /** Returns a copy with defaults filled in for any missing field. */
    public Transaction normalized(long nowMillis) {
        return new Transaction(
                txnId == null || txnId.isBlank() ? "t_" + nowMillis : txnId,
                userId == null || userId.isBlank() ? "unknown" : userId,
                cardId == null || cardId.isBlank() ? "unknown" : cardId,
                amount,
                currency == null || currency.isBlank() ? "USD" : currency,
                merchant == null || merchant.isBlank() ? "unknown" : merchant,
                country == null || country.isBlank() ? "unknown" : country,
                ts > 0 ? ts : nowMillis);
    }
}
