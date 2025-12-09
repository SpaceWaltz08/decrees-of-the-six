package com.spacewaltz.decrees.decree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class VotingRulesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("decrees_of_the_six");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("voting_rules.json");

    private static VotingRulesData INSTANCE;

    /**
     * Get the current rules in memory, loading from disk if needed.
     */
    public static VotingRulesData get() {
        if (INSTANCE == null) {
            loadFromDisk();
        }
        return INSTANCE;
    }

    /**
     * Force a reload from disk.
     * Used by /decrees reload.
     */
    public static void load() {
        loadFromDisk();
    }

    /**
     * Internal loader that (re)reads voting_rules.json and updates INSTANCE.
     */
    private static void loadFromDisk() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(CONFIG_PATH)) {
                // No file yet → use defaults and write one
                INSTANCE = new VotingRulesData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                VotingRulesData data = GSON.fromJson(reader, VotingRulesData.class);
                if (data == null) {
                    data = new VotingRulesData();
                }
                INSTANCE = data;
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Fall back to defaults if something goes wrong
            INSTANCE = new VotingRulesData();
        }
    }

    /**
     * Save the current rules back to voting_rules.json.
     */
    public static void save() {
        if (INSTANCE == null) return;

        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
