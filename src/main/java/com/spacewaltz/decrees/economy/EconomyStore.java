package com.spacewaltz.decrees.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spacewaltz.decrees.DecreesOfTheSix;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores all economy data (accounts + transactions) in economy.json on disk.
 */
public class EconomyStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path STORE_PATH = CONFIG_DIR.resolve("economy.json");

    // Singleton instance
    private static EconomyStore INSTANCE = new EconomyStore();

    /**
     * Account id of the Treasury.
     */
    public String treasuryAccountId;

    /**
     * Next numeric part of account id (acc-<n>).
     */
    public int nextAccountId = 1;

    /**
     * Next transaction id.
     */
    public long nextTransactionId = 1L;

    /**
     * All accounts, keyed by internal id.
     */
    public Map<String, EconomyAccount> accounts = new HashMap<>();

    /**
     * All transactions, in chronological order.
     * (Phase 1: no rolling buffer; can be added later.)
     */
    public List<EconomyTransaction> transactions = new ArrayList<>();

    public static EconomyStore get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(STORE_PATH)) {
                INSTANCE = new EconomyStore();
                INSTANCE.ensureTreasuryAccount();
                save();
                return;
            }

            String json = Files.readString(STORE_PATH, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                INSTANCE = new EconomyStore();
                INSTANCE.ensureTreasuryAccount();
                return;
            }

            EconomyStore loaded = GSON.fromJson(json, EconomyStore.class);
            if (loaded == null) {
                INSTANCE = new EconomyStore();
            } else {
                INSTANCE = loaded;
            }
            INSTANCE.ensureTreasuryAccount();
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("Failed to load economy store from " + STORE_PATH, e);
            INSTANCE = new EconomyStore();
            INSTANCE.ensureTreasuryAccount();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            String json = GSON.toJson(INSTANCE);
            Files.writeString(STORE_PATH, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("Failed to save economy store to " + STORE_PATH, e);
        }
    }

    /**
     * Ensure we always have a Treasury account present.
     */
    private void ensureTreasuryAccount() {
        if (treasuryAccountId != null && accounts.containsKey(treasuryAccountId)) {
            EconomyAccount acc = accounts.get(treasuryAccountId);
            if (acc != null && acc.type == AccountType.TREASURY) {
                return;
            }
        }

        // Create new Treasury account
        EconomyAccount treasury = new EconomyAccount();
        treasury.id = generateAccountId();
        treasury.type = AccountType.TREASURY;
        treasury.ownerId = "TREASURY";
        treasury.balanceCopper = 0;

        accounts.put(treasury.id, treasury);
        treasuryAccountId = treasury.id;
    }

    /**
     * Allocate a new account id.
     */
    public String generateAccountId() {
        int id = nextAccountId++;
        return "acc-" + id;
    }
}