package com.spacewaltz.decrees.decree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.spacewaltz.decrees.DecreesOfTheSix;
import com.spacewaltz.decrees.council.CouncilConfig;
import com.spacewaltz.decrees.council.CouncilConfigData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists finalised decrees (ENACTED / REJECTED / CANCELLED)
 * into a season history file: decree_history.json
 */
public class DecreeHistoryLogger {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path HISTORY_PATH = CONFIG_DIR.resolve("decree_history.json");

    private static final Type LIST_TYPE =
            new TypeToken<List<DecreeHistoryEntry>>() {}.getType();

    // In-memory history
    private static List<DecreeHistoryEntry> HISTORY = new ArrayList<>();

    public static void init() {
        loadHistory();
    }

    private static void loadHistory() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(HISTORY_PATH)) {
                HISTORY = new ArrayList<>();
                saveHistory(); // create empty file
                return;
            }

            String json = Files.readString(HISTORY_PATH, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                HISTORY = new ArrayList<>();
                return;
            }

            List<DecreeHistoryEntry> loaded = GSON.fromJson(json, LIST_TYPE);
            if (loaded == null) {
                HISTORY = new ArrayList<>();
            } else {
                HISTORY = loaded;
            }

        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error(
                    "Failed to load decree history from " + HISTORY_PATH,
                    e
            );
            HISTORY = new ArrayList<>();
        }
    }

    private static void saveHistory() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            String json = GSON.toJson(HISTORY, LIST_TYPE);
            Files.writeString(HISTORY_PATH, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error(
                    "Failed to save decree history to " + HISTORY_PATH,
                    e
            );
        }
    }

    /**
     * Called whenever a decree leaves VOTING and becomes ENACTED / REJECTED / CANCELLED.
     *
     * @param decree       the decree object at time of closure
     * @param finalStatus  ENACTED / REJECTED / CANCELLED
     * @param votesYes     count of YES votes
     * @param votesNo      count of NO votes
     * @param votesAbstain count of ABSTAIN votes
     * @param quorumMet    whether quorum was considered met at closure (caller’s view)
     */
    public static synchronized void recordFinalState(
            Decree decree,
            DecreeStatus finalStatus,
            int votesYes,
            int votesNo,
            int votesAbstain,
            boolean quorumMet
    ) {
        // Total votes actually cast
        int totalVotes = votesYes + votesNo + votesAbstain;

        // Compute quorumRequired using current council + voting rules
        CouncilConfigData council = CouncilConfig.get();
        int totalActiveSeats = (int) council.seats.stream()
                .filter(s -> s.holderUuid != null)
                .count();

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        int quorumRequired;
        if (totalActiveSeats <= 0 || rules.minQuorumPercent <= 0) {
            quorumRequired = 0;
        } else if (rules.minQuorumPercent >= 100) {
            quorumRequired = totalActiveSeats;
        } else {
            double fraction = rules.minQuorumPercent / 100.0;
            quorumRequired = (int) Math.ceil(totalActiveSeats * fraction);
        }

        // If a quorumRequired is defined, re-derive quorumMet from that and totalVotes,
        // otherwise just keep the passed-in value.
        boolean effectiveQuorumMet = quorumMet;
        if (quorumRequired > 0) {
            effectiveQuorumMet = totalVotes >= quorumRequired;
        }

        DecreeHistoryEntry entry = new DecreeHistoryEntry();
        entry.decreeId = decree.id;
        entry.finalStatus = finalStatus;
        entry.title = decree.title; // <- NEW: persist title snapshot

        entry.votesYes = votesYes;
        entry.votesNo = votesNo;
        entry.votesAbstain = votesAbstain;

        entry.totalVotes = totalVotes;
        entry.quorumRequired = quorumRequired;
        entry.quorumMet = effectiveQuorumMet;

        entry.closedAtEpochMillis = System.currentTimeMillis();

        HISTORY.add(entry);
        saveHistory();
    }

    /**
     * Returns a defensive copy so callers can sort without mutating the backing list.
     */
    public static synchronized List<DecreeHistoryEntry> getHistorySnapshot() {
        return new ArrayList<>(HISTORY);
    }
}
