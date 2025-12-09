package com.spacewaltz.decrees.council;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spacewaltz.decrees.DecreesOfTheSix;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loads & validates council.json with safe fallbacks.
 */
public final class CouncilConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path COUNCIL_PATH = CONFIG_DIR.resolve("council.json");

    private static CouncilConfigData CURRENT = defaultConfig();

    private CouncilConfig() {
    }

    private static CouncilConfigData defaultConfig() {
        return new CouncilConfigData();
    }

    public static synchronized CouncilConfigData get() {
        if (CURRENT == null) {
            load();
        }
        return CURRENT;
    }

    /**
     * Load council.json from disk, validate, and fall back safely if anything is wrong.
     */
    public static synchronized void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(COUNCIL_PATH)) {
                // First run → generate a template.
                CouncilConfigData template = defaultConfig();
                template.decreesEnabled = false;
                template.opsOnly = false;
                template.seats = new ArrayList<>();

                saveInternal(template);

                CURRENT = template;

                DecreesOfTheSix.LOGGER.warn(
                        "[Decrees] council.json was missing; a template has been generated at {}. " +
                                "Edit seats & set \"decreesEnabled\": true when ready.",
                        COUNCIL_PATH.toAbsolutePath()
                );
                return;
            }

            String json = Files.readString(COUNCIL_PATH, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                throw new IllegalStateException("council.json is empty");
            }

            CouncilConfigData loaded = GSON.fromJson(json, CouncilConfigData.class);
            if (loaded == null) {
                throw new IllegalStateException("council.json parsed as null");
            }

            normalizeAndValidate(loaded);
            CURRENT = loaded;

        } catch (Exception e) {
            DecreesOfTheSix.LOGGER.error(
                    "[Decrees] Failed to load council.json – decrees system will be disabled.", e
            );
            CouncilConfigData fallback = defaultConfig();
            fallback.decreesEnabled = false;
            fallback.opsOnly = false;
            fallback.seats = new ArrayList<>();
            CURRENT = fallback;
        }
    }

    /**
     * Save current config to disk.
     */
    public static synchronized void save() {
        saveInternal(get());
    }

    private static void saveInternal(CouncilConfigData data) {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            String json = GSON.toJson(data);
            Files.writeString(COUNCIL_PATH, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error(
                    "[Decrees] Failed to save council.json to " + COUNCIL_PATH, e
            );
        }
    }

    /**
     * Basic sanity checks & normalisation:
     * - ensures seats list is non-null
     * - drops seats with missing IDs
     * - backfills displayName from title/id
     * - backfills holderName from holder
     * - ensures councilName is non-empty
     * - if decreesEnabled but no seats → disable system
     */
    private static void normalizeAndValidate(CouncilConfigData cfg) {
        if (cfg.seats == null) {
            cfg.seats = new ArrayList<>();
        }

        List<SeatDefinition> valid = new ArrayList<>();

        for (SeatDefinition seat : cfg.seats) {
            if (seat == null) continue;

            if (seat.id == null || seat.id.isBlank()) {
                DecreesOfTheSix.LOGGER.warn(
                        "[Decrees] Ignoring seat with missing id in council.json."
                );
                continue;
            }

            // displayName: prefer explicit, then title, then id
            if (seat.displayName == null || seat.displayName.isBlank()) {
                if (seat.title != null && !seat.title.isBlank()) {
                    seat.displayName = seat.title;
                } else {
                    seat.displayName = seat.id;
                }
            }

            // holderName: prefer explicit, then legacy 'holder'
            if ((seat.holderName == null || seat.holderName.isBlank())
                    && seat.holder != null && !seat.holder.isBlank()) {
                seat.holderName = seat.holder;
            }

            valid.add(seat);
        }

        cfg.seats = valid;

        if (cfg.councilName == null || cfg.councilName.isBlank()) {
            cfg.councilName = "The Council";
        }
        if (cfg.ceremonySound == null || cfg.ceremonySound.isBlank()) {
            cfg.ceremonySound = "minecraft:ui.toast.challenge_complete";
        }

        if (cfg.decreesEnabled && cfg.seats.isEmpty()) {
            DecreesOfTheSix.LOGGER.warn(
                    "[Decrees] council.json has \"decreesEnabled\": true but no valid seats; " +
                            "disabling decree system to avoid crashes."
            );
            cfg.decreesEnabled = false;
        }
    }

    // ---------------------------------------------------------------------
    // Helpers used from commands / logic
    // ---------------------------------------------------------------------

    public static SeatDefinition findSeat(String id) {
        if (id == null) return null;
        CouncilConfigData cfg = get();
        if (cfg.seats == null) return null;

        for (SeatDefinition seat : cfg.seats) {
            if (seat != null && seat.id != null && seat.id.equalsIgnoreCase(id)) {
                return seat;
            }
        }
        return null;
    }

    public static SeatDefinition findSeatByHolder(UUID holderUuid) {
        if (holderUuid == null) return null;
        CouncilConfigData cfg = get();
        if (cfg.seats == null) return null;

        for (SeatDefinition seat : cfg.seats) {
            if (seat != null && holderUuid.equals(seat.holderUuid)) {
                return seat;
            }
        }
        return null;
    }
}
