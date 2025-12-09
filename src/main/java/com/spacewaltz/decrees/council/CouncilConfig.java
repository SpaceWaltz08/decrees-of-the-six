package com.spacewaltz.decrees.council;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class CouncilConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("decrees_of_the_six");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("council.json");

    private static CouncilConfigData INSTANCE;

    public static CouncilConfigData get() {
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
                // create default council
                CouncilConfigData defaults = new CouncilConfigData();
                defaults.seats.add(new SeatDefinition("overseer_regent", "Overseer-Regent", null));
                defaults.seats.add(new SeatDefinition("grand_artifex", "Grand Artifex of Stone", null));
                defaults.seats.add(new SeatDefinition("vox_imperion", "Vox Imperion", null));
                defaults.seats.add(new SeatDefinition("ledger_exarch", "Exarch of the Ledger", null));
                defaults.seats.add(new SeatDefinition("luminous_hierophant", "Hierophant of the Luminous Archive", null));
                defaults.seats.add(new SeatDefinition("justiciar_praetor", "Justiciar-Praetor", null));


                INSTANCE = defaults;
                save(); // write default file
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                CouncilConfigData data = GSON.fromJson(reader, CouncilConfigData.class);
                if (data == null || data.seats == null) {
                    data = new CouncilConfigData();
                }
                INSTANCE = data;
            }
        } catch (IOException e) {
            e.printStackTrace();
            INSTANCE = new CouncilConfigData();
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

    public static SeatDefinition findSeat(String id) {
        for (SeatDefinition seat : get().seats) {
            if (seat.id.equalsIgnoreCase(id)) {
                return seat;
            }
        }
        return null;
    }

    public static SeatDefinition findSeatByHolder(UUID uuid) {
        for (SeatDefinition seat : get().seats) {
            if (uuid.equals(seat.holderUuid)) {
                return seat;
            }
        }
        return null;
    }
}
