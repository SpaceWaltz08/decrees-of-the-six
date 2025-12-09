package com.spacewaltz.decrees.decree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import com.spacewaltz.decrees.council.CouncilConfig;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class DecreeHistoryCommand {

    private static final int PAGE_SIZE = 7;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());

    // Same style prefix as CouncilCommands
    private static String getPrefix() {
        String name = CouncilConfig.get().councilName;
        if (name == null || name.isBlank()) {
            return "§6[Decrees]";
        }
        return "§6[" + name + "]";
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var historyLiteral = CommandManager.literal("history")
                .executes(ctx -> showHistory(ctx.getSource(), 1))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showHistory(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "page")
                        ))
                );

        // Attach under /decrees
        dispatcher.register(
                CommandManager.literal("decrees").then(historyLiteral)
        );
    }

    private static int showHistory(ServerCommandSource src, int page) {
        // NOTE: use the top-level DecreeHistoryEntry, not DecreeHistoryLogger.DecreeHistoryEntry
        List<DecreeHistoryEntry> all = DecreeHistoryLogger.getHistorySnapshot();

        if (all.isEmpty()) {
            src.sendMessage(Text.literal(getPrefix() + " §7There are no completed decrees yet."));
            return 1;
        }

        // Newest-first
        all.sort(Comparator.comparing(DecreeHistoryEntry::getClosedAtInstant).reversed());

        int totalPages = (int) Math.ceil(all.size() / (double) PAGE_SIZE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, all.size());

        src.sendMessage(Text.literal(
                getPrefix() + " §6Decree history (§e" + page + "§6/§e" + totalPages + "§6):"
        ));

        for (int i = start; i < end; i++) {
            DecreeHistoryEntry entry = all.get(i);

            String title = (entry.title == null || entry.title.isBlank())
                    ? "§8<no title>"
                    : entry.title;

            String dateStr = DATE_FMT.format(entry.getClosedAtInstant());

            String statusColor;
            if (entry.finalStatus == DecreeStatus.ENACTED) {
                statusColor = "§a";
            } else if (entry.finalStatus == DecreeStatus.REJECTED) {
                statusColor = "§c";
            } else if (entry.finalStatus == DecreeStatus.CANCELLED) {
                statusColor = "§7";
            } else {
                statusColor = "§e";
            }

            String quorumPart = "";
            if (entry.quorumRequired > 0) {
                String qColor = entry.quorumMet ? "§a" : "§c";
                quorumPart = " §8| quorum: " + qColor + entry.totalVotes + "/" + entry.quorumRequired + "§7";
            }

            String line =
                    " §e#" + entry.decreeId +
                            " §7[" + statusColor + entry.finalStatus + "§7]" +
                            " §r" + title +
                            " §8– " + dateStr +
                            " §7(Y:" + entry.votesYes +
                            " N:" + entry.votesNo +
                            " A:" + entry.votesAbstain + ")" +
                            quorumPart;

            src.sendMessage(Text.literal(line));
        }

        if (totalPages > 1) {
            src.sendMessage(Text.literal(
                    "§7Use §e/decrees history <page>§7 to view other pages."
            ));
        }

        return 1;
    }
}
