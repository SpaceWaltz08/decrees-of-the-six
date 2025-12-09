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

public class DecreesOfTheSix implements ModInitializer {
    public static final String MOD_ID = "decrees_of_the_six";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Decrees of the Six...");

        // NEW: core toggles + voting rules
        DecreesConfig.load();      // decrees_config.json (enabled / opsOnly)
        CouncilConfig.load();      // council.json
        VotingRulesConfig.load();  // voting_rules.json

        // Decrees data
        DecreeStore.load();        // decrees.json

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CouncilCommands.register(dispatcher)
        );

        // Council-member join reminder (B.3)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            // Only care about council members
            SeatDefinition seat = CouncilUtil.getSeatFor(player);
            if (seat == null) {
                return;
            }

            // Count decrees in VOTING where this seat hasn't voted yet
            int pending = 0;
            for (Decree d : DecreeStore.get().decrees) {
                if (d.status == DecreeStatus.VOTING && !d.votes.containsKey(seat.id)) {
                    pending++;
                }
            }

            if (pending > 0) {
                String plural = (pending == 1) ? "" : "s";
                player.sendMessage(
                        Text.literal("§6[Decrees] §e" + seat.displayName +
                                "§6, you have §e" + pending + "§6 decree" + plural +
                                " awaiting your vote. Use §e/decrees decree list active§6."),
                        false
                );
            }
        });

        LOGGER.info("Decrees of the Six initialized.");
    }
}
