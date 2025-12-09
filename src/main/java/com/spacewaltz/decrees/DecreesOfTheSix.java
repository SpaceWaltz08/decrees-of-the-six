package com.spacewaltz.decrees;

import com.spacewaltz.decrees.council.CouncilCommands;
import com.spacewaltz.decrees.council.CouncilConfig;
import com.spacewaltz.decrees.council.CouncilUtil;
import com.spacewaltz.decrees.council.SeatDefinition;
import com.spacewaltz.decrees.decree.Decree;
import com.spacewaltz.decrees.decree.DecreeStatus;
import com.spacewaltz.decrees.decree.DecreeStore;
import com.spacewaltz.decrees.decree.VotingRulesConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DecreesOfTheSix implements ModInitializer {
    public static final String MOD_ID = "decrees_of_the_six";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Decrees of the Six...");

        // Core toggles + voting rules
        DecreesConfig.load();      // decrees_config.json (enabled / opsOnly) – if you still use it
        CouncilConfig.load();      // council.json
        VotingRulesConfig.load();  // voting_rules.json

        // Decrees data
        DecreeStore.load();        // decrees.json

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CouncilCommands.register(dispatcher)
        );

        // Council-member join reminder with per-seat pending list
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            // Only care about council members
            SeatDefinition seat = CouncilUtil.getSeatFor(player);
            if (seat == null) {
                return;
            }

            // Find decrees in VOTING where this seat hasn't voted yet
            List<Decree> pending = DecreeStore.get().decrees.stream()
                    .filter(d -> d.status == DecreeStatus.VOTING)
                    .filter(d -> !d.votes.containsKey(seat.id))
                    .toList();

            if (pending.isEmpty()) {
                return;
            }

            int count = pending.size();
            String plural = (count == 1) ? "" : "s";

            // Show up to 5 decree IDs to avoid spam
            String idList = pending.stream()
                    .limit(5)
                    .map(d -> "#" + d.id)
                    .collect(Collectors.joining(", "));
            if (count > 5) {
                idList += ", …";
            }

            String prefix = getPrefix();

            player.sendMessage(
                    Text.literal(
                            prefix + " §e" + seat.displayName +
                                    "§6, you have §e" + count + "§6 decree" + plural +
                                    " awaiting your vote: §e" + idList +
                                    "§6. Use §e/decrees decree list active§6 for details."
                    ),
                    false
            );
        });

        LOGGER.info("Decrees of the Six initialized.");
    }

    /**
     * Shared prefix using the configured council name if present.
     * Falls back to [Decrees] if no name is set.
     */
    private static String getPrefix() {
        String name = CouncilConfig.get().councilName;
        if (name == null || name.isBlank()) {
            return "§6[Decrees]";
        }
        return "§6[" + name + "]";
    }
}
