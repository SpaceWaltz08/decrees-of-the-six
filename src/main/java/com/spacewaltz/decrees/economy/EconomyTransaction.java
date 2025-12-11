package com.spacewaltz.decrees.economy;

/**
 * Immutable snapshot of a money movement.
 */
public class EconomyTransaction {

    /**
     * Monotonically increasing transaction identifier.
     */
    public long id;

    /**
     * Source account id, or null for minting.
     */
    public String fromAccountId;

    /**
     * Destination account id, or null for burning.
     */
    public String toAccountId;

    /**
     * Amount moved, in copper units.
     */
    public int amountCopper;

    /**
     * Class of transaction.
     */
    public TransactionType type;

    /**
     * Epoch-millis timestamp when this transaction was created.
     */
    public long timestamp;

    /**
     * Optional free-form description or metadata.
     */
    public String description;
}
