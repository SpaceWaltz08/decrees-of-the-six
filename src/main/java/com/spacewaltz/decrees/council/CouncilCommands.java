package com.spacewaltz.decrees.council;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.spacewaltz.decrees.decree.Decree;
import com.spacewaltz.decrees.decree.DecreeHistoryLogger;
import com.spacewaltz.decrees.decree.DecreeStatus;
import com.spacewaltz.decrees.decree.DecreeStore;
import com.spacewaltz.decrees.decree.VoteChoice;
import com.spacewaltz.decrees.decree.VotingRulesConfig;
import com.spacewaltz.decrees.decree.VotingRulesData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CouncilCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("decrees");

        // ---------- /decrees help ----------
        var helpCmd = CommandManager.literal("help")
                .executes(ctx -> showHelp(ctx.getSource()));

        // ---------- /decrees reload (ops only) ----------
        var reloadCmd = CommandManager.literal("reload")
                .requires(src -> src.hasPermissionLevel(3))
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    CouncilConfig.load();
                    VotingRulesConfig.load();
                    src.sendMessage(Text.literal("§a[Decrees] Reloaded council.json and voting_rules.json."));
                    return 1;
                });

        // ---------- SEAT ADMIN (/decrees seat ...) ----------
        var seatCmd = CommandManager.literal("seat")
                .requires(src -> src.hasPermissionLevel(3)) // ops only
                .then(CommandManager.literal("list")
                        .executes(ctx -> listSeats(ctx.getSource()))
                )
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("seat_id", StringArgumentType.string())
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            String seatId = StringArgumentType.getString(ctx, "seat_id");
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                            return setSeat(ctx.getSource(), seatId, player);
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("seat_id", StringArgumentType.string())
                                .executes(ctx -> {
                                    String seatId = StringArgumentType.getString(ctx, "seat_id");
                                    return clearSeat(ctx.getSource(), seatId);
                                })
                        )
                );

        // ---------- DECREE EDIT SUBCOMMAND (/decrees decree edit ...) ----------
        var editCmd = CommandManager.literal("edit")
                .then(CommandManager.literal("title")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .then(CommandManager.argument("new_title", StringArgumentType.greedyString())
                                        .executes(ctx -> editTitle(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                StringArgumentType.getString(ctx, "new_title")
                                        ))
                                )
                        )
                )
                .then(CommandManager.literal("description")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .then(CommandManager.argument("new_description", StringArgumentType.greedyString())
                                        .executes(ctx -> editDescription(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                StringArgumentType.getString(ctx, "new_description")
                                        ))
                                )
                        )
                )
                .then(CommandManager.literal("category")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .then(CommandManager.argument("new_category", StringArgumentType.greedyString())
                                        .executes(ctx -> editCategory(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                StringArgumentType.getString(ctx, "new_category")
                                        ))
                                )
                        )
                )
                .then(CommandManager.literal("expiry")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .then(CommandManager.literal("none")
                                        .executes(ctx -> editExpiryClear(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id")
                                        ))
                                )
                                .then(CommandManager.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> editExpirySet(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                IntegerArgumentType.getInteger(ctx, "days")
                                        ))
                                )
                        )
                );

        // ---------- DECREE MGMT (/decrees decree ...) ----------
        var decreeCmd = CommandManager.literal("decree")
                .then(CommandManager.literal("list")
                        .executes(ctx -> listDecrees(ctx.getSource()))
                        .then(CommandManager.literal("my")
                                .executes(ctx -> listMyDecrees(ctx.getSource()))
                        )
                        .then(CommandManager.literal("active")
                                .executes(ctx -> listActiveDecrees(ctx.getSource()))
                        )
                )
                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .executes(ctx -> decreeInfo(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id")))
                        )
                )
                .then(CommandManager.literal("results")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .executes(ctx -> decreeResults(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id")))
                        )
                )
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("title", StringArgumentType.greedyString())
                                .executes(ctx -> createDecree(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "title")))
                        )
                )
                .then(CommandManager.literal("open")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .executes(ctx -> openDecree(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id")))
                        )
                )
                .then(CommandManager.literal("force")
                        .requires(src -> src.hasPermissionLevel(3)) // ops only
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .then(CommandManager.literal("enacted")
                                        .executes(ctx -> forceDecreeStatus(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                DecreeStatus.ENACTED
                                        ))
                                )
                                .then(CommandManager.literal("rejected")
                                        .executes(ctx -> forceDecreeStatus(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                DecreeStatus.REJECTED
                                        ))
                                )
                        )
                )
                .then(editCmd)
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .suggests(CouncilCommands::suggestDecreeIds)
                                .executes(ctx -> deleteDecree(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id")
                                ))
                        )
                );

        // ---------- VOTING (/decrees vote ...) ----------
        var voteCmd = CommandManager.literal("vote")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .suggests(CouncilCommands::suggestDecreeIds)
                        .then(CommandManager.literal("yes")
                                .executes(ctx -> vote(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id"),
                                        VoteChoice.YES))
                        )
                        .then(CommandManager.literal("no")
                                .executes(ctx -> vote(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id"),
                                        VoteChoice.NO))
                        )
                        .then(CommandManager.literal("abstain")
                                .executes(ctx -> vote(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id"),
                                        VoteChoice.ABSTAIN))
                        )
                );

        // Attach subcommands to /decrees root
        root.then(helpCmd);
        root.then(reloadCmd);
        root.then(seatCmd);
        root.then(decreeCmd);
        root.then(voteCmd);

        dispatcher.register(root);
    }

    // -------- /decrees help --------

    private static int showHelp(ServerCommandSource src) {
        src.sendMessage(Text.literal("§6[Decrees] Command overview:"));
        src.sendMessage(Text.literal("§e/decrees help §7- Show this help."));
        src.sendMessage(Text.literal("§e/decrees reload §7- Reload council & voting configs (ops only)."));

        src.sendMessage(Text.literal("§e/decrees seat list §7- List all council seats (ops only)."));
        src.sendMessage(Text.literal("§e/decrees seat set <seat_id> <player> §7- Assign a player to a seat (ops only)."));
        src.sendMessage(Text.literal("§e/decrees seat clear <seat_id> §7- Clear the holder of a seat (ops only)."));

        src.sendMessage(Text.literal("§e/decrees decree create <title> §7- Create a new decree (council only)."));
        src.sendMessage(Text.literal("§e/decrees decree list §7- List all decrees."));
        src.sendMessage(Text.literal("§e/decrees decree list my §7- List decrees created by your seat."));
        src.sendMessage(Text.literal("§e/decrees decree list active §7- Show decrees currently in VOTING."));
        src.sendMessage(Text.literal("§e/decrees decree info <id> §7- Show full info for a decree."));
        src.sendMessage(Text.literal("§e/decrees decree results <id> §7- Show detailed vote results."));
        src.sendMessage(Text.literal("§e/decrees decree open <id> §7- Open a decree for voting (council only)."));
        src.sendMessage(Text.literal("§e/decrees decree force <id> enacted|rejected §7- Force final status (ops only)."));
        src.sendMessage(Text.literal("§e/decrees decree edit title|description|category|expiry ... §7- Edit decree fields."));
        src.sendMessage(Text.literal("§e/decrees decree delete <id> §7- Delete a decree (council only)."));

        src.sendMessage(Text.literal("§e/decrees vote <id> yes|no|abstain §7- Cast your seat's vote."));

        src.sendMessage(Text.literal("§8Note: Some commands require you to hold a council seat or be an operator."));
        return 1;
    }


    // -------- SUGGESTIONS (tab completion for decree IDs) --------

    private static CompletableFuture<Suggestions> suggestDecreeIds(CommandContext<ServerCommandSource> context,
                                                                   SuggestionsBuilder builder) {
        var ids = DecreeStore.get().decrees.stream()
                .map(d -> Integer.toString(d.id))
                .toList();
        return CommandSource.suggestMatching(ids, builder);
    }

    // -------- SEAT METHODS --------

    private static int listSeats(ServerCommandSource src) {
        CouncilConfigData data = CouncilConfig.get();

        if (data.seats.isEmpty()) {
            src.sendMessage(Text.literal("§7[Decrees] No seats are defined in council.json."));
            return 1;
        }

        src.sendMessage(Text.literal("§6[Decrees] Council seats:"));
        for (SeatDefinition seat : data.seats) {
            String holderStr = "§8<empty>";
            if (seat.holderUuid != null) {
                var server = src.getServer();
                var userCache = server.getUserCache();

                if (userCache != null) {
                    var profileOpt = userCache.getByUuid(seat.holderUuid);
                    if (profileOpt.isPresent()) {
                        holderStr = "§a" + profileOpt.get().getName();
                    } else {
                        holderStr = "§a" + seat.holderUuid.toString();
                    }
                } else {
                    holderStr = "§a" + seat.holderUuid.toString();
                }
            }
            src.sendMessage(Text.literal(" §e- " + seat.id + " §7(" + seat.displayName + ")§r: " + holderStr));
        }

        return 1;
    }

    private static int setSeat(ServerCommandSource src, String seatId, ServerPlayerEntity player) {
        SeatDefinition seat = CouncilConfig.findSeat(seatId);
        if (seat == null) {
            src.sendMessage(Text.literal("§c[Decrees] Unknown seat id: " + seatId));
            return 0;
        }

        UUID playerUuid = player.getUuid();

        // Ensure one seat per player
        SeatDefinition existing = CouncilConfig.findSeatByHolder(playerUuid);
        if (existing != null && existing != seat) {
            existing.holderUuid = null;
        }

        seat.holderUuid = playerUuid;
        CouncilConfig.save();

        src.sendMessage(Text.literal("§a[Decrees] Seat §e" + seat.id + " §7(" + seat.displayName + ")§a is now held by §b" + player.getName().getString() + "§a."));
        return 1;
    }

    private static int clearSeat(ServerCommandSource src, String seatId) {
        SeatDefinition seat = CouncilConfig.findSeat(seatId);
        if (seat == null) {
            src.sendMessage(Text.literal("§c[Decrees] Unknown seat id: " + seatId));
            return 0;
        }

        seat.holderUuid = null;
        CouncilConfig.save();

        src.sendMessage(Text.literal("§a[Decrees] Seat §e" + seat.id + " §7(" + seat.displayName + ")§a has been cleared."));
        return 1;
    }

    // -------- DECREE METHODS --------

    private static int listActiveDecrees(ServerCommandSource src) {
        var data = DecreeStore.get();

        var active = data.decrees.stream()
                .filter(d -> d.status == DecreeStatus.VOTING)
                .toList();

        if (active.isEmpty()) {
            src.sendMessage(Text.literal("§7[Decrees] There are no decrees currently in §eVOTING§7."));
            return 1;
        }

        SeatDefinition callerSeat = null;
        if (src.getEntity() instanceof ServerPlayerEntity player) {
            callerSeat = CouncilUtil.getSeatFor(player);
        }

        src.sendMessage(Text.literal("§6[Decrees] Active decrees in §eVOTING§6:"));

        for (Decree d : active) {
            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : d.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }

            String baseLine = " §e#" + d.id + " §7[" + d.status + "] §r" + d.title +
                    " §8(Yes " + yes + ", No " + no + ", Abstain " + abstain + ")";

            if (callerSeat != null) {
                VoteChoice myVote = d.votes.get(callerSeat.id);
                String myVoteText;
                if (myVote == null) {
                    myVoteText = "§cPENDING";
                } else if (myVote == VoteChoice.YES) {
                    myVoteText = "§aYES";
                } else if (myVote == VoteChoice.NO) {
                    myVoteText = "§cNO";
                } else {
                    myVoteText = "§7ABSTAIN";
                }

                src.sendMessage(Text.literal(baseLine + " §7| Your vote: " + myVoteText));
            } else {
                src.sendMessage(Text.literal(baseLine));
            }
        }

        return 1;
    }

    private static int listDecrees(ServerCommandSource src) {
        var data = DecreeStore.get();

        if (data.decrees.isEmpty()) {
            src.sendMessage(Text.literal("§7[Decrees] There are currently no decrees."));
            return 1;
        }

        src.sendMessage(Text.literal("§6[Decrees] Decrees:"));
        for (Decree d : data.decrees) {
            src.sendMessage(Text.literal(" §e#" + d.id + " §7[" + d.status + "] §r" + d.title));
        }
        return 1;
    }

    private static int listMyDecrees(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[Decrees] Only players can list their own decrees."));
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            src.sendError(Text.literal("§c[Decrees] Only council members have personal decrees."));
            return 0;
        }

        var data = DecreeStore.get();
        boolean any = false;

        src.sendMessage(Text.literal("§6[Decrees] Decrees created by §e" + seat.displayName + "§6:"));
        for (Decree d : data.decrees) {
            if (seat.id.equals(d.createdBySeatId)) {
                any = true;
                src.sendMessage(Text.literal(" §e#" + d.id + " §7[" + d.status + "] §r" + d.title));
            }
        }

        if (!any) {
            src.sendMessage(Text.literal("§7[Decrees] Your seat has not created any decrees yet."));
        }

        return 1;
    }

    private static int decreeInfo(ServerCommandSource src, int id) {
        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        src.sendMessage(Text.literal("§6[Decrees] Decree #" + d.id));
        src.sendMessage(Text.literal(" §7Title: §r" + d.title));

        String desc = (d.description == null || d.description.isEmpty())
                ? "§8<none>"
                : d.description;
        src.sendMessage(Text.literal(" §7Description: §r" + desc));

        String cat = (d.category == null || d.category.isEmpty())
                ? "§8<none>"
                : d.category;
        src.sendMessage(Text.literal(" §7Category: §b" + cat));

        if (d.expiresAt == null || d.expiresAt <= 0) {
            src.sendMessage(Text.literal(" §7Expiry: §8none"));
        } else {
            long now = System.currentTimeMillis();
            long diff = d.expiresAt - now;
            if (diff <= 0) {
                src.sendMessage(Text.literal(" §7Expiry: §cEXPIRED (by time)"));
            } else {
                long days = diff / 86_400_000L;
                if (days < 1) {
                    src.sendMessage(Text.literal(" §7Expiry: §ein less than 1 day"));
                } else {
                    src.sendMessage(Text.literal(" §7Expiry: §ein approx " + days + " day(s)"));
                }
            }
        }

        src.sendMessage(Text.literal(" §7Status: §e" + d.status));
        src.sendMessage(Text.literal(" §7Created by seat: §b" + d.createdBySeatId));

        if (d.votes.isEmpty()) {
            src.sendMessage(Text.literal(" §7Votes: §8none yet"));
        } else {
            src.sendMessage(Text.literal(" §7Votes:"));
            d.votes.forEach((seatId, vote) ->
                    src.sendMessage(Text.literal("  §e- " + seatId + "§7: §b" + vote))
            );
        }

        return 1;
    }

    private static int decreeResults(ServerCommandSource src, int id) {
        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        int totalActiveSeats = (int) CouncilConfig.get().seats.stream()
                .filter(s -> s.holderUuid != null)
                .count();

        int yes = 0;
        int no = 0;
        int abstain = 0;
        for (VoteChoice v : d.votes.values()) {
            if (v == VoteChoice.YES) yes++;
            else if (v == VoteChoice.NO) no++;
            else if (v == VoteChoice.ABSTAIN) abstain++;
        }
        int votesCast = d.votes.size();

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

        boolean hasQuorum = votesCast >= minVotesRequired;

        boolean timeExpired = false;
        long elapsedMinutes = 0;
        if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
            long now = System.currentTimeMillis();
            long diff = now - d.votingOpenedAt;
            elapsedMinutes = diff / 60_000L;
            if (diff >= rules.votingDurationMinutes * 60_000L) {
                timeExpired = true;
            }
        }

        String mode = rules.majorityMode == null ? "SIMPLE" : rules.majorityMode.toUpperCase();

        src.sendMessage(Text.literal("§6[Decrees] Results for decree §e#" + d.id + "§6: §r" + d.title));
        src.sendMessage(Text.literal(" §7Status: §e" + d.status));
        src.sendMessage(Text.literal(" §7Active seats: §e" + totalActiveSeats));
        src.sendMessage(Text.literal(" §7Votes: §aYes " + yes + "§7, §cNo " + no + "§7, §8Abstain " + abstain + "§7, Total " + votesCast));

        if (minVotesRequired > 0) {
            src.sendMessage(Text.literal(" §7Quorum: §e" + votesCast + "/" + minVotesRequired +
                    (hasQuorum ? " §a(REACHED)" : " §c(NOT reached)")));
        } else {
            src.sendMessage(Text.literal(" §7Quorum: §8none required"));
        }

        src.sendMessage(Text.literal(" §7Majority mode: §e" + mode +
                "§7, Ties: " + (rules.tiesPass ? "§apass" : "§cfail")));

        if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
            src.sendMessage(Text.literal(" §7Time: §e" + elapsedMinutes + "/" + rules.votingDurationMinutes +
                    " min§7 → " + (timeExpired ? "§cEXPIRED" : "§aONGOING")));
        }

        if (!d.votes.isEmpty()) {
            src.sendMessage(Text.literal(" §7Per seat:"));
            d.votes.forEach((seatId, vote) ->
                    src.sendMessage(Text.literal("  §e- " + seatId + "§7: §b" + vote))
            );
        }

        return 1;
    }

    // -------- COUNCIL CHECK / HELPERS --------

    // Global toggles from council.json (decreesEnabled / opsOnly)
    private static boolean checkGlobalFlags(ServerCommandSource src) {
        CouncilConfigData cfg = CouncilConfig.get();
        if (cfg == null) {
            // No config loaded → behave as enabled for backwards compat
            return true;
        }

        if (!cfg.decreesEnabled) {
            src.sendError(Text.literal("§c[Decrees] The decree system is currently disabled in council.json."));
            return false;
        }

        if (cfg.opsOnly && !src.hasPermissionLevel(3)) {
            src.sendError(Text.literal("§c[Decrees] Decrees are currently restricted to server operators."));
            return false;
        }

        return true;
    }

    private static boolean ensureCouncil(ServerCommandSource src, String action) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[Decrees] Only players can " + action + "."));
            return false;
        }
        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            src.sendError(Text.literal("§c[Decrees] Only council members can " + action + "."));
            return false;
        }
        return true;
    }

    private static void notifyCouncilVotingOpened(ServerCommandSource src, Decree d) {
        var pm = src.getServer().getPlayerManager();

        for (ServerPlayerEntity p : pm.getPlayerList()) {
            if (CouncilUtil.getSeatFor(p) == null) continue;

            p.sendMessage(
                    Text.literal("§6[Decrees] §e#" + d.id + "§6 is now in §eVOTING§6: §r" + d.title),
                    false
            );
        }
    }

    private static void notifyCouncilDecreeFinal(ServerCommandSource src, Decree d) {
        var pm = src.getServer().getPlayerManager();

        for (ServerPlayerEntity p : pm.getPlayerList()) {
            if (CouncilUtil.getSeatFor(p) == null) continue;

            if (d.status == DecreeStatus.ENACTED) {
                p.sendMessage(
                        Text.literal("§a[Decrees] Decree §e#" + d.id + "§a has been §lENACTED§r§a."),
                        false
                );
            } else if (d.status == DecreeStatus.REJECTED) {
                p.sendMessage(
                        Text.literal("§c[Decrees] Decree §e#" + d.id + "§c has been §lREJECTED§r§c."),
                        false
                );
            }
        }
    }

    // -------- CREATE / OPEN / DELETE / EDIT --------

    private static int createDecree(ServerCommandSource src, String title) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[Decrees] Only players can create decrees."));
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            src.sendError(Text.literal("§c[Decrees] Only council members can create decrees."));
            return 0;
        }

        Decree decree = DecreeStore.createDecree(title, seat.id);
        src.sendMessage(Text.literal("§a[Decrees] Created decree §e#" + decree.id + "§a with title: §r" + decree.title));
        return 1;
    }

    private static int openDecree(ServerCommandSource src, int id) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "open decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        if (d.status != DecreeStatus.DRAFT) {
            src.sendMessage(Text.literal("§c[Decrees] Only decrees in DRAFT status can be opened for voting."));
            return 0;
        }

        d.status = DecreeStatus.VOTING;
        d.votingOpenedAt = System.currentTimeMillis();
        DecreeStore.save();

        int totalActiveSeats = (int) CouncilConfig.get().seats.stream()
                .filter(s -> s.holderUuid != null)
                .count();

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        String durationText = rules.votingDurationMinutes <= 0
                ? "no time limit"
                : (rules.votingDurationMinutes + " min");

        src.sendMessage(Text.literal("§a[Decrees] Decree §e#" + d.id + "§a is now open for voting. (" +
                "Active seats: §e" + totalActiveSeats + "§a)"));
        src.sendMessage(Text.literal("§7[Decrees] Rules: Majority §e" + rules.majorityMode +
                "§7, Quorum §e" + rules.minQuorumPercent + "%§7, Duration §e" + durationText +
                "§7, Ties " + (rules.tiesPass ? "§apass" : "§cfail")));

        src.getServer().getPlayerManager().broadcast(
                Text.literal("§6[Decrees] Voting opened on decree §e#" + d.id + "§6: " + d.title),
                false
        );

        notifyCouncilVotingOpened(src, d);

        return 1;
    }

    private static int deleteDecree(ServerCommandSource src, int id) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "delete decrees")) {
            return 0;
        }

        var data = DecreeStore.get();
        boolean removed = data.decrees.removeIf(d -> d.id == id);
        if (!removed) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Deleted decree §e#" + id + "§a."));
        return 1;
    }

    private static int forceDecreeStatus(ServerCommandSource src, int id, DecreeStatus newStatus) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!src.hasPermissionLevel(3)) {
            src.sendError(Text.literal("§c[Decrees] Only operators can force decree status."));
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        DecreeStatus before = d.status;
        d.status = newStatus;
        DecreeStore.save();

        // Log to history
        DecreeHistoryLogger.logStatusChange(
                d,
                newStatus,
                "FORCED_BY_OP:" + src.getName()
        );

        src.getServer().getPlayerManager().broadcast(
                Text.literal("§6[Decrees] Decree §e#" + d.id + "§6 (" + d.title + ") was §eFORCED " + newStatus + "§6 by §e" + src.getName() + "§6."),
                false
        );

        // Notify council members with the usual finalisation message
        if (newStatus == DecreeStatus.ENACTED || newStatus == DecreeStatus.REJECTED) {
            notifyCouncilDecreeFinal(src, d);
        }

        src.sendMessage(Text.literal(
                "§a[Decrees] Forced decree §e#" + d.id + "§a from §e" + before + "§a to §e" + newStatus + "§a."
        ));

        return 1;
    }

    private static int editTitle(ServerCommandSource src, int id, String newTitle) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        d.title = newTitle;
        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Updated title of decree §e#" + id + "§a."));
        return 1;
    }

    private static int editDescription(ServerCommandSource src, int id, String newDescription) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        d.description = newDescription;
        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Updated description of decree §e#" + id + "§a."));
        return 1;
    }

    private static int editCategory(ServerCommandSource src, int id, String newCategory) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        d.category = newCategory;
        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Updated category of decree §e#" + id + "§a."));
        return 1;
    }

    private static int editExpiryClear(ServerCommandSource src, int id) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        d.expiresAt = null;
        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Cleared expiry for decree §e#" + id + "§a."));
        return 1;
    }

    private static int editExpirySet(ServerCommandSource src, int id, int days) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        long now = System.currentTimeMillis();
        long millis = days * 86_400_000L;
        d.expiresAt = now + millis;

        DecreeStore.save();
        src.sendMessage(Text.literal("§a[Decrees] Set expiry of decree §e#" + id + "§a to about " + days + " day(s) from now."));
        return 1;
    }

    // -------- VOTING --------

    private static int vote(ServerCommandSource src, int id, VoteChoice choice) {
        if (!checkGlobalFlags(src)) {
            return 0;
        }

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[Decrees] Only players can vote."));
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            src.sendError(Text.literal("§c[Decrees] Only council members can vote."));
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            src.sendMessage(Text.literal("§c[Decrees] No decree with id #" + id + "."));
            return 0;
        }

        if (d.status != DecreeStatus.VOTING) {
            src.sendMessage(Text.literal("§c[Decrees] Decree #" + id + " is not open for voting."));
            return 0;
        }

        d.votes.put(seat.id, choice);

        int totalActiveSeats = (int) CouncilConfig.get().seats.stream()
                .filter(s -> s.holderUuid != null)
                .count();

        int yes = 0;
        int no = 0;
        int abstain = 0;
        for (VoteChoice c : d.votes.values()) {
            if (c == VoteChoice.YES) yes++;
            else if (c == VoteChoice.NO) no++;
            else if (c == VoteChoice.ABSTAIN) abstain++;
        }
        int votesCast = d.votes.size();

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

        boolean hasQuorum = votesCast >= minVotesRequired;

        boolean timeExpired = false;
        if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
            long limitMillis = rules.votingDurationMinutes * 60_000L;
            long now = System.currentTimeMillis();
            if (now - d.votingOpenedAt >= limitMillis) {
                timeExpired = true;
            }
        }

        boolean allVoted = totalActiveSeats > 0 && votesCast == totalActiveSeats;

        DecreeStatus before = d.status;

        // --- automatic finalisation logic ---
        if (hasQuorum && (allVoted || timeExpired)) {
            int requiredYes;
            if ("TWO_THIRDS".equalsIgnoreCase(rules.majorityMode)) {
                requiredYes = (int) Math.ceil(votesCast * 2.0 / 3.0);
            } else {
                requiredYes = (votesCast / 2) + 1;
            }

            if (votesCast == 0) {
                d.status = DecreeStatus.REJECTED;
            } else if (yes > no && yes >= requiredYes) {
                d.status = DecreeStatus.ENACTED;
            } else if (yes == no) {
                d.status = rules.tiesPass ? DecreeStatus.ENACTED : DecreeStatus.REJECTED;
            } else {
                d.status = DecreeStatus.REJECTED;
            }
        } else if (timeExpired && !hasQuorum) {
            d.status = DecreeStatus.REJECTED;
        }

        DecreeStatus after = d.status;
        DecreeStore.save();

        // Log automatic finalisation via voting
        if (before != after &&
                (after == DecreeStatus.ENACTED || after == DecreeStatus.REJECTED)) {
            DecreeHistoryLogger.logStatusChange(
                    d,
                    after,
                    "VOTE_FINAL"
            );
        }

        String quorumText;
        if (minVotesRequired > 0) {
            quorumText = " §7Quorum: §e" + votesCast + "/" + minVotesRequired +
                    (hasQuorum ? " §a(REACHED)" : " §c(NOT reached)");
        } else {
            quorumText = " §7Quorum: §8none required";
        }

        String extraStatus = "";
        if (before != after && after != DecreeStatus.VOTING) {
            extraStatus = " §7[Final: §e" + after + "§7]";
        }

        String msg = "§a[Decrees] " + seat.displayName + " voted §e" + choice +
                "§a on decree §e#" + d.id + "§a. (Yes: " + yes + ", No: " + no +
                ", Abstain: " + abstain + ", Votes: " + votesCast + "/" + totalActiveSeats + ")" +
                quorumText + extraStatus;

        src.sendMessage(Text.literal(msg));

        if (before != after && after != DecreeStatus.VOTING) {
            src.getServer().getPlayerManager().broadcast(
                    Text.literal("§6[Decrees] Decree §e#" + d.id + "§6 (" + d.title + ") is now §e" + after + "§6."),
                    false
            );
            notifyCouncilDecreeFinal(src, d);
        }

        return 1;
    } // <- closes vote(...)
}     // <- closes CouncilCommands
