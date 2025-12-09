package com.spacewaltz.decrees.decree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class DecreeStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path DATA_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("decrees_of_the_six");

    private static final Path DATA_PATH = DATA_DIR.resolve("decrees.json");

    private static DecreeData INSTANCE;

    public static DecreeData get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }

            if (!Files.exists(DATA_PATH)) {
                INSTANCE = new DecreeData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(DATA_PATH)) {
                DecreeData data = GSON.fromJson(reader, DecreeData.class);
                if (data == null || data.decrees == null) {
                    data = new DecreeData();
                }
                INSTANCE = data;
            }
        } catch (IOException e) {
            e.printStackTrace();
            INSTANCE = new DecreeData();
        }
    }

    public static void save() {
        if (INSTANCE == null) return;

        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }

            try (Writer writer = Files.newBufferedWriter(DATA_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Decree createDecree(String title, String createdBySeatId) {
        DecreeData data = get();

        int maxId = 0;
        for (Decree d : data.decrees) {
            if (d.id > maxId) {
                maxId = d.id;
            }
        }

        Decree decree = new Decree();
        decree.id = maxId + 1;
        decree.title = title;
        decree.description = "";
        decree.category = "";
        decree.expiresAt = null;

        decree.createdBySeatId = createdBySeatId;
        decree.status = DecreeStatus.DRAFT;
        decree.createdAt = System.currentTimeMillis();
        decree.votingOpenedAt = null;

        data.decrees.add(decree);
        save();

        return decree;
    }

    public static Decree find(int id) {
        for (Decree d : get().decrees) {
            if (d.id == id) {
                return d;
            }
        }
        return null;
    }
}
