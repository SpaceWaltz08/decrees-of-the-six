package com.spacewaltz.decrees.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spacewaltz.decrees.DecreesOfTheSix;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves economy_config.json from the config folder.
 */
public class EconomyConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("economy_config.json");

    private static EconomyConfigData INSTANCE;

    public static EconomyConfigData get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(CONFIG_PATH)) {
                // First time: write defaults
                INSTANCE = new EconomyConfigData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                EconomyConfigData data = GSON.fromJson(reader, EconomyConfigData.class);
                if (data == null) {
                    data = new EconomyConfigData();
                }
                INSTANCE = data;
            }
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("Failed to load economy config from " + CONFIG_PATH, e);
            INSTANCE = new EconomyConfigData();
        }
    }

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
            DecreesOfTheSix.LOGGER.error("Failed to save economy config to " + CONFIG_PATH, e);
        }
    }
}
