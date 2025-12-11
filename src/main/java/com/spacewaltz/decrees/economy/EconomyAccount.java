package com.spacewaltz.decrees.economy;

/**
 * One logical balance in the economy.
 * All balances are stored in copper units.
 */
public class EconomyAccount {

    /**
     * Internal account identifier (e.g. "acc-1").
     */
    public String id;

    /**
     * Owner type (player/system/treasury).
     */
    public AccountType type;

    /**
     * Owner identifier:
     * - PLAYER   => player UUID string
     * - SYSTEM   => free-form string
     * - TREASURY => usually "TREASURY"
     */
    public String ownerId;

    /**
     * Current balance in copper units.
     */
    public int balanceCopper;
}
