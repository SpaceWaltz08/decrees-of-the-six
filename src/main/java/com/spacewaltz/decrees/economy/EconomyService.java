package com.spacewaltz.decrees.economy;

import com.spacewaltz.decrees.DecreesOfTheSix;

import java.util.UUID;

/**
 * Core helpers for creating accounts and performing balance operations.
 */
public final class EconomyService {

    private EconomyService() {
    }

    public static EconomyAccount getOrCreatePlayerAccount(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        EconomyStore store = EconomyStore.get();
        String ownerId = playerUuid.toString();

        // Search for existing account
        for (EconomyAccount acc : store.accounts.values()) {
            if (acc.type == AccountType.PLAYER && ownerId.equals(acc.ownerId)) {
                return acc;
            }
        }

        // Create new
        EconomyAccount acc = new EconomyAccount();
        acc.id = store.generateAccountId();
        acc.type = AccountType.PLAYER;
        acc.ownerId = ownerId;
        acc.balanceCopper = EconomyConfig.get().startingBalanceCopper;

        store.accounts.put(acc.id, acc);
        EconomyStore.save();
        return acc;
    }

    public static EconomyAccount getTreasuryAccount() {
        EconomyStore store = EconomyStore.get();
        if (store.treasuryAccountId != null) {
            EconomyAccount existing = store.accounts.get(store.treasuryAccountId);
            if (existing != null && existing.type == AccountType.TREASURY) {
                return existing;
            }
        }

        // Fallback: create a new one
        DecreesOfTheSix.LOGGER.warn("Treasury account missing or invalid, creating a new one.");
        EconomyAccount treasury = new EconomyAccount();
        treasury.id = store.generateAccountId();
        treasury.type = AccountType.TREASURY;
        treasury.ownerId = "TREASURY";
        treasury.balanceCopper = 0;
        store.accounts.put(treasury.id, treasury);
        store.treasuryAccountId = treasury.id;
        EconomyStore.save();
        return treasury;
    }

    /**
     * Creates a non-player SYSTEM account, used for things like guild treasuries.
     *
     * @param ownerId logical owner identifier, e.g. "GUILD:<id>".
     */
    public static EconomyAccount createSystemAccount(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            DecreesOfTheSix.LOGGER.warn("Attempted to create system account with empty ownerId.");
            return null;
        }

        EconomyStore store = EconomyStore.get();

        EconomyAccount account = new EconomyAccount();
        account.id = store.generateAccountId();
        account.type = AccountType.SYSTEM;
        account.ownerId = ownerId;
        account.balanceCopper = 0;

        store.accounts.put(account.id, account);
        EconomyStore.save();

        return account;
    }

    /**
     * Looks up any account by its id.
     *
     * @return the account, or null if not found.
     */
    public static EconomyAccount getAccountById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        EconomyStore store = EconomyStore.get();
        return store.accounts.get(accountId);
    }

    public static int getBalanceCopper(EconomyAccount account) {
        return account != null ? account.balanceCopper : 0;
    }

    public static boolean canTransfer(EconomyAccount from, int amountCopper) {
        if (from == null) return false;
        if (amountCopper <= 0) return false;
        return from.balanceCopper >= amountCopper;
    }

    public static boolean transfer(EconomyAccount from,
                                   EconomyAccount to,
                                   int amountCopper,
                                   TransactionType type,
                                   String description) {
        if (from == null || to == null) return false;
        if (amountCopper <= 0) return false;
        if (from.balanceCopper < amountCopper) return false;

        from.balanceCopper -= amountCopper;
        to.balanceCopper += amountCopper;

        recordTransaction(from.id, to.id, amountCopper, type, description);
        return true;
    }

    public static boolean mint(EconomyAccount target,
                               int amountCopper,
                               String description) {
        if (target == null) return false;
        if (amountCopper <= 0) return false;

        target.balanceCopper += amountCopper;
        recordTransaction(null, target.id, amountCopper, TransactionType.ADMIN_MINT, description);
        return true;
    }

    public static boolean burn(EconomyAccount target,
                               int amountCopper,
                               String description) {
        if (target == null) return false;
        if (amountCopper <= 0) return false;
        if (target.balanceCopper < amountCopper) return false;

        target.balanceCopper -= amountCopper;
        recordTransaction(target.id, null, amountCopper, TransactionType.ADMIN_BURN, description);
        return true;
    }

    /**
     * Seize funds from a player/system account into the Treasury.
     */
    public static boolean seizeToTreasury(EconomyAccount from,
                                          int amountCopper,
                                          String description) {
        EconomyAccount treasury = getTreasuryAccount();
        return transfer(from, treasury, amountCopper, TransactionType.ADMIN_SEIZURE, description);
    }

    private static void recordTransaction(String fromId,
                                          String toId,
                                          int amountCopper,
                                          TransactionType type,
                                          String description) {
        EconomyStore store = EconomyStore.get();

        EconomyTransaction tx = new EconomyTransaction();
        tx.id = store.nextTransactionId++;
        tx.fromAccountId = fromId;
        tx.toAccountId = toId;
        tx.amountCopper = amountCopper;
        tx.type = type;
        tx.timestamp = System.currentTimeMillis();
        tx.description = description;

        store.transactions.add(tx);
        EconomyStore.save();
    }
}