package com.spacewaltz.decrees;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves decrees_config.json from the config folder.
 */
public class DecreesConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("decrees_of_the_six");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("decrees_config.json");

    private static DecreesConfigData INSTANCE;

    public static DecreesConfigData get() {
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
                INSTANCE = new DecreesConfigData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                DecreesConfigData data = GSON.fromJson(reader, DecreesConfigData.class);
                if (data == null) {
                    data = new DecreesConfigData();
                }
                INSTANCE = data;
            }
        } catch (IOException e) {
            e.printStackTrace();
            INSTANCE = new DecreesConfigData();
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
            e.printStackTrace();
        }
    }
}
