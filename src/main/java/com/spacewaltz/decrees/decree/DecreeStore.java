package com.spacewaltz.decrees.decree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spacewaltz.decrees.council.CouncilConfig;
import com.spacewaltz.decrees.council.CouncilConfigData;
import com.spacewaltz.decrees.DecreesOfTheSix;
import com.spacewaltz.decrees.council.SeatDefinition;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores all decrees in decrees.json on disk.
 */
public class DecreeStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path STORE_PATH = CONFIG_DIR.resolve("decrees.json");

    // Singleton instance
    private static DecreeStore INSTANCE = new DecreeStore();

    public int nextId = 1;
    public List<Decree> decrees = new ArrayList<>();

    public static DecreeStore get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(STORE_PATH)) {
                INSTANCE = new DecreeStore();
                save();
                return;
            }

            String json = Files.readString(STORE_PATH, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                INSTANCE = new DecreeStore();
                return;
            }

            DecreeStore loaded = GSON.fromJson(json, DecreeStore.class);
            if (loaded == null) {
                INSTANCE = new DecreeStore();
            } else {
                INSTANCE = loaded;
            }
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("Failed to load decrees from " + STORE_PATH, e);
            INSTANCE = new DecreeStore();
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
            DecreesOfTheSix.LOGGER.error("Failed to save decrees to " + STORE_PATH, e);
        }
    }

    public static Decree createDecree(String title, String createdBySeatId) {
        DecreeStore store = get();

        Decree decree = new Decree();
        decree.id = store.nextId++;
        decree.status = DecreeStatus.DRAFT;
        decree.title = title;
        decree.createdBySeatId = createdBySeatId;
        // decree.createdAt = System.currentTimeMillis(); // <-- REMOVED THIS LINE (keeping this in case I need it again)
        decree.votes.clear();

        store.decrees.add(decree);
        save();
        return decree;
    }

    public static boolean setStatus(Decree decree, DecreeStatus newStatus, String reason) {
        if (decree == null) {
            return false;
        }

        DecreeStatus oldStatus = decree.status;
        if (oldStatus == newStatus) {
            return false;
        }

        // Guardrails: prevent clearly illegal transitions.
        if (!isLegalTransition(oldStatus, newStatus)) {
            DecreesOfTheSix.LOGGER.warn(
                    "Blocked illegal decree state transition {} -> {} on #{} (reason: {}).",
                    oldStatus, newStatus, decree.id, reason
            );
            return false;
        }

        decree.status = newStatus;

        // For final states, log to season history
        if (newStatus == DecreeStatus.ENACTED
                || newStatus == DecreeStatus.REJECTED
                || newStatus == DecreeStatus.CANCELLED) {

            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : decree.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }

            CouncilConfigData cfg = CouncilConfig.get();
            int totalActiveSeats = (int) cfg.seats.stream()
                    .filter(s -> s.holderUuid != null)
                    .count();

            VotingRulesData rules = VotingRulesConfig.get();
            if (rules == null) {
                rules = new VotingRulesData();
            }

            int minVotesRequired;
            if (totalActiveSeats <= 0 || rules.minQuorumPercent <= 0) {
                minVotesRequired = 0;
            } else if (rules.minQuorumPercent >= 100) {
                minVotesRequired = totalActiveSeats;
            } else {
                double fraction = rules.minQuorumPercent / 100.0;
                minVotesRequired = (int) Math.ceil(totalActiveSeats * fraction);
            }

            int votesCast = decree.votes.size();
            boolean hasQuorum = (minVotesRequired == 0) || (votesCast >= minVotesRequired);

            DecreeHistoryLogger.recordFinalState(
                    decree,
                    newStatus,
                    yes,
                    no,
                    abstain,
                    hasQuorum
            );
        }

        save();

        DecreesOfTheSix.LOGGER.info(
                "Decree #{} status {} -> {} (reason: {}).",
                decree.id, oldStatus, newStatus, reason
        );

        return true;
    }

    private static boolean isLegalTransition(DecreeStatus oldStatus, DecreeStatus newStatus) {
        if (oldStatus == null) {
            return true;
        }

        switch (oldStatus) {
            case DRAFT:
                // DRAFT can go to VOTING or any final state.
                return newStatus == DecreeStatus.VOTING
                        || newStatus == DecreeStatus.CANCELLED
                        || newStatus == DecreeStatus.ENACTED
                        || newStatus == DecreeStatus.REJECTED;
            case VOTING:
                // VOTING can only go to final states.
                return newStatus == DecreeStatus.ENACTED
                        || newStatus == DecreeStatus.REJECTED
                        || newStatus == DecreeStatus.CANCELLED;
            case ENACTED:
            case REJECTED:
            case CANCELLED:
                // Final states are terminal.
                return newStatus == oldStatus;
            default:
                return false;
        }
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
