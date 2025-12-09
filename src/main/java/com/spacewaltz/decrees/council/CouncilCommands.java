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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * All /decrees commands and council logic.
 */
public class CouncilCommands {

    // How many decrees per page for /decrees decree list [page] and history
    private static final int DECREES_PER_PAGE = 8;

    // -------- PREFIX HELPER (delegates to Messenger) --------
    private static String getPrefix() {
        return Messenger.prefix();
    }

    // Pending delete confirmations: source name -> decree id
    private static final Map<String, Integer> PENDING_DELETES = new LinkedHashMap<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("decrees");

        // ---------- /decrees help ----------
        var helpCmd = CommandManager.literal("help")
                .executes(ctx -> showHelp(ctx.getSource()));

        // ---------- /decrees history [page] (completed decrees) ----------
        var historyCmd = CommandManager.literal("history")
                .executes(ctx -> history(ctx.getSource(), 1))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> history(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "page")
                        ))
                );

        // ---------- /decrees reload (ops only) ----------
        var reloadCmd = CommandManager.literal("reload")
                .requires(src -> src.hasPermissionLevel(3))
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    CouncilConfig.load();
                    VotingRulesConfig.load();
                    Messenger.info(src, "§aReloaded council.json and voting_rules.json.");
                    return 1;
                });

        // ---------- /decrees status [id] (voting status / reminders) ----------
        var statusCmd = CommandManager.literal("status")
                .executes(ctx -> statusAll(ctx.getSource()))
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                        .suggests(CouncilCommands::suggestDecreeIds)
                        .executes(ctx -> statusOne(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "id")
                        ))
                );

        // ---------- /decrees config (live toggles, ops only) ----------
        var configCmd = CommandManager.literal("config")
                .requires(src -> src.hasPermissionLevel(3))
                .then(CommandManager.literal("decreesEnabled")
                        .then(CommandManager.literal("on")
                                .executes(ctx -> setDecreesEnabled(ctx.getSource(), true))
                        )
                        .then(CommandManager.literal("off")
                                .executes(ctx -> setDecreesEnabled(ctx.getSource(), false))
                        )
                )
                .then(CommandManager.literal("opsOnly")
                        .then(CommandManager.literal("on")
                                .executes(ctx -> setOpsOnly(ctx.getSource(), true))
                        )
                        .then(CommandManager.literal("off")
                                .executes(ctx -> setOpsOnly(ctx.getSource(), false))
                        )
                )
                .then(CommandManager.literal("show")
                        .executes(ctx -> showStatus(ctx.getSource()))
                );

        // ---------- /decrees council create <name> (ceremonial activation, ops only) ----------
        var councilCmd = CommandManager.literal("council")
                .requires(src -> src.hasPermissionLevel(3))
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> createCouncil(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")
                                ))
                        )
                );

        // ---------- /decrees stats ----------
        var statsCmd = CommandManager.literal("stats")
                .then(CommandManager.literal("seats")
                        .executes(ctx -> showAllSeatStats(ctx.getSource()))
                )
                .then(CommandManager.literal("me")
                        .executes(ctx -> showMySeatStats(ctx.getSource()))
                )
                .then(CommandManager.literal("seat")
                        .then(CommandManager.argument("seat_id", StringArgumentType.string())
                                .suggests(CouncilCommands::suggestSeatIds)
                                .executes(ctx -> showSeatStatsById(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "seat_id")
                                ))
                        )
                );

        // ---------- SEAT ADMIN (/decrees seat ...) ----------
        var seatCmd = CommandManager.literal("seat")
                .requires(src -> src.hasPermissionLevel(3)) // ops only
                .then(CommandManager.literal("list")
                        .executes(ctx -> listSeats(ctx.getSource()))
                )
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("seat_id", StringArgumentType.string())
                                .suggests(CouncilCommands::suggestSeatIds)
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
                                .suggests(CouncilCommands::suggestSeatIds)
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
                                        .suggests(CouncilCommands::suggestCategories)
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
                        // /decrees decree list
                        .executes(ctx -> listDecrees(ctx.getSource(), 1))
                        // /decrees decree list <page>
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> listDecrees(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")
                                ))
                        )
                        // /decrees decree list my
                        .then(CommandManager.literal("my")
                                .executes(ctx -> listMyDecrees(ctx.getSource()))
                        )
                        // /decrees decree list active [page]
                        .then(CommandManager.literal("active")
                                .executes(ctx -> listActiveDecrees(ctx.getSource(), 1))
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> listActiveDecrees(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "page")
                                        ))
                                )
                        )
                        // /decrees decree list category <category>
                        .then(CommandManager.literal("category")
                                .then(CommandManager.argument("category", StringArgumentType.greedyString())
                                        .suggests(CouncilCommands::suggestCategories)
                                        .executes(ctx -> listDecreesByCategory(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "category")
                                        ))
                                )
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
                                .then(CommandManager.literal("cancelled")
                                        .executes(ctx -> forceDecreeStatus(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                DecreeStatus.CANCELLED
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
                                        IntegerArgumentType.getInteger(ctx, "id"),
                                        false
                                ))
                                .then(CommandManager.literal("confirm")
                                        .executes(ctx -> deleteDecree(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "id"),
                                                true
                                        ))
                                )
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
        root.then(statusCmd);
        root.then(historyCmd);
        root.then(configCmd);
        root.then(councilCmd);
        root.then(statsCmd);
        root.then(seatCmd);
        root.then(decreeCmd);
        root.then(voteCmd);

        dispatcher.register(root);
    }

    // -------- /decrees help --------

    private static int showHelp(ServerCommandSource src) {
        Messenger.info(src, "§eCommand overview:");
        Messenger.line(src, "§e/decrees help §7- Show this help.");
        Messenger.line(src, "§e/decrees status [id] §7- Show voting status and which seats still haven't voted.");
        Messenger.line(src, "§e/decrees history [page] §7- View the season history of completed decrees.");
        Messenger.line(src, "§e/decrees council create <name> §7- Ceremonially convene the council and enable decrees (ops only).");
        Messenger.line(src, "§e/decrees stats seats §7- Overview of decree stats per seat.");
        Messenger.line(src, "§e/decrees stats me §7- Stats for your own seat.");
        Messenger.line(src, "§e/decrees stats seat <seat_id> §7- Stats for a specific seat.");
        Messenger.line(src, "§e/decrees reload §7- Reload council & voting configs (ops only).");
        Messenger.line(src, "§e/decrees config decreesEnabled on|off §7- Enable/disable decrees (ops only).");
        Messenger.line(src, "§e/decrees config opsOnly on|off §7- Toggle ops-only mode (ops only).");
        Messenger.line(src, "§e/decrees config show §7- Show system status and active decrees.");

        Messenger.line(src, "§e/decrees seat list §7- List all council seats (ops only).");
        Messenger.line(src, "§e/decrees seat set <seat_id> <player> §7- Assign a player to a seat (ops only).");
        Messenger.line(src, "§e/decrees seat clear <seat_id> §7- Clear the holder of a seat (ops only).");

        Messenger.line(src, "§e/decrees decree create <title> §7- Create a new decree (council only).");
        Messenger.line(src, "§e/decrees decree list [page] §7- List decrees with pagination.");
        Messenger.line(src, "§e/decrees decree list my §7- List decrees created by your seat.");
        Messenger.line(src, "§e/decrees decree list active [page] §7- Show decrees currently in VOTING.");
        Messenger.line(src, "§e/decrees decree list category <category> §7- List decrees by category.");
        Messenger.line(src, "§e/decrees decree info <id> §7- Show full info for a decree.");
        Messenger.line(src, "§e/decrees decree results <id> §7- Show detailed vote results.");
        Messenger.line(src, "§e/decrees decree open <id> §7- Open a decree for voting (council only).");
        Messenger.line(src, "§e/decrees decree force <id> enacted|rejected|cancelled §7- Force final status (ops only).");
        Messenger.line(src, "§e/decrees decree edit title|description|category|expiry ... §7- Edit decree fields.");
        Messenger.line(src, "§e/decrees decree delete <id> [confirm] §7- Delete a §eDRAFT§7 decree with 2-step confirmation (council only).");

        Messenger.line(src, "§e/decrees vote <id> yes|no|abstain §7- Cast your seat's vote.");

        Messenger.line(src, "§8Note: Some commands require you to hold a council seat or be an operator.");
        return 1;
    }

    // -------- SUGGESTIONS (tab completion) --------

    private static CompletableFuture<Suggestions> suggestDecreeIds(CommandContext<ServerCommandSource> context,
                                                                   SuggestionsBuilder builder) {
        var ids = DecreeStore.get().decrees.stream()
                .map(d -> Integer.toString(d.id))
                .toList();
        return CommandSource.suggestMatching(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestSeatIds(CommandContext<ServerCommandSource> context,
                                                                 SuggestionsBuilder builder) {
        var ids = CouncilConfig.get().seats.stream()
                .map(seat -> seat.id)
                .toList();
        return CommandSource.suggestMatching(ids, builder);
    }

    private static CompletableFuture<Suggestions> suggestCategories(CommandContext<ServerCommandSource> context,
                                                                    SuggestionsBuilder builder) {
        var cats = DecreeStore.get().decrees.stream()
                .map(d -> d.category)
                .filter(cat -> cat != null && !cat.isBlank())
                .distinct()
                .toList();
        return CommandSource.suggestMatching(cats, builder);
    }

    // -------- SEAT METHODS --------

    private static int listSeats(ServerCommandSource src) {
        CouncilConfigData data = CouncilConfig.get();

        if (data.seats.isEmpty()) {
            Messenger.info(src, "§7No seats are defined in council.json.");
            return 1;
        }

        Messenger.info(src, "§6Council seats:");
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
            Messenger.line(src, " §e- " + seat.id + " §7(" + seat.displayName + ")§r: " + holderStr);
        }

        return 1;
    }

    private static int setSeat(ServerCommandSource src, String seatId, ServerPlayerEntity player) {
        SeatDefinition seat = CouncilConfig.findSeat(seatId);
        if (seat == null) {
            Messenger.error(src, "Unknown seat id: " + seatId);
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

        Messenger.info(src, "§aSeat §e" + seat.id + " §7(" + seat.displayName + ")§a is now held by §b" + player.getName().getString() + "§a.");
        return 1;
    }

    private static int clearSeat(ServerCommandSource src, String seatId) {
        SeatDefinition seat = CouncilConfig.findSeat(seatId);
        if (seat == null) {
            Messenger.error(src, "Unknown seat id: " + seatId);
            return 0;
        }

        seat.holderUuid = null;
        CouncilConfig.save();

        Messenger.info(src, "§aSeat §e" + seat.id + " §7(" + seat.displayName + ")§a has been cleared.");
        return 1;
    }

    // -------- DECREE LISTING / INFO --------

    private static int listActiveDecrees(ServerCommandSource src) {
        return listActiveDecrees(src, 1);
    }

    private static int listActiveDecrees(ServerCommandSource src, int page) {
        var data = DecreeStore.get();

        List<Decree> active = data.decrees.stream()
                .filter(d -> d.status == DecreeStatus.VOTING)
                .toList();

        if (active.isEmpty()) {
            Messenger.info(src, "§7There are no decrees currently in §eVOTING§7.");
            return 1;
        }

        SeatDefinition callerSeat = null;
        if (src.getEntity() instanceof ServerPlayerEntity player) {
            callerSeat = CouncilUtil.getSeatFor(player);
        }

        int total = active.size();
        int totalPages = (int) Math.ceil(total / (double) DECREES_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * DECREES_PER_PAGE;
        int endIndex = Math.min(startIndex + DECREES_PER_PAGE, total);

        Messenger.info(src, "§6Active decrees in " + Messenger.colorStatus(DecreeStatus.VOTING) +
                "§6 §7(page " + page + "/" + totalPages + "):");

        for (int i = startIndex; i < endIndex; i++) {
            Decree d = active.get(i);

            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : d.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }

            String baseLine = " §e#" + d.id + " " + Messenger.colorStatus(d.status) + "§r " + d.title +
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

                Messenger.line(src, baseLine + " §7| Your vote: " + myVoteText);
            } else {
                Messenger.line(src, baseLine);
            }
        }

        if (totalPages > 1) {
            if (page < totalPages) {
                Messenger.line(src, "§7Use §e/decrees decree list active " + (page + 1) + "§7 for the next page.");
            } else {
                Messenger.line(src, "§7You are on the last page of active decrees.");
            }
        }

        return 1;
    }

    private static int listDecrees(ServerCommandSource src) {
        return listDecrees(src, 1);
    }

    private static int listDecrees(ServerCommandSource src, int page) {
        var data = DecreeStore.get();

        if (data.decrees.isEmpty()) {
            Messenger.info(src, "§7There are currently no decrees.");
            return 1;
        }

        int total = data.decrees.size();
        int totalPages = (int) Math.ceil(total / (double) DECREES_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * DECREES_PER_PAGE;
        int endIndex = Math.min(startIndex + DECREES_PER_PAGE, total);

        Messenger.info(src, "§6Decrees §7(page " + page + "/" + totalPages + "):");

        for (int i = startIndex; i < endIndex; i++) {
            Decree d = data.decrees.get(i);
            String statusColored = Messenger.colorStatus(d.status);

            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : d.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }

            String creatorName = null;
            if (d.createdBySeatId != null && !d.createdBySeatId.isBlank()) {
                SeatDefinition seat = CouncilConfig.findSeat(d.createdBySeatId);
                if (seat != null) {
                    creatorName = seat.displayName;
                } else {
                    creatorName = d.createdBySeatId;
                }
            }

            StringBuilder line = new StringBuilder();
            line.append(" §e[#").append(d.id).append("] ")
                    .append(statusColored).append("§r ")
                    .append(d.title);

            if (creatorName != null) {
                line.append(" §7– opened by §e").append(creatorName);
            }

            if (!d.votes.isEmpty()) {
                line.append(" §7– Y:").append(yes)
                        .append(" N:").append(no)
                        .append(" A:").append(abstain);
            }

            Messenger.line(src, line.toString());
        }

        if (totalPages > 1) {
            if (page < totalPages) {
                Messenger.line(src, "§7Use §e/decrees decree list " + (page + 1) + "§7 for the next page.");
            } else {
                Messenger.line(src, "§7You are on the last page.");
            }
        }

        return 1;
    }

    private static int listMyDecrees(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            Messenger.error(src, "Only players can list their own decrees.");
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            Messenger.error(src, "Only council members have personal decrees.");
            return 0;
        }

        var data = DecreeStore.get();
        boolean any = false;

        Messenger.info(src, "§6Decrees created by §e" + seat.displayName + "§6:");
        for (Decree d : data.decrees) {
            if (seat.id.equals(d.createdBySeatId)) {
                any = true;

                int yes = 0;
                int no = 0;
                int abstain = 0;
                for (VoteChoice v : d.votes.values()) {
                    if (v == VoteChoice.YES) yes++;
                    else if (v == VoteChoice.NO) no++;
                    else if (v == VoteChoice.ABSTAIN) abstain++;
                }

                String statusColored = Messenger.colorStatus(d.status);
                String line = " §e#" + d.id + " " + statusColored + "§r " + d.title +
                        " §8(Yes " + yes + ", No " + no + ", Abstain " + abstain + ")";
                Messenger.line(src, line);
            }
        }

        if (!any) {
            Messenger.info(src, "§7Your seat has not created any decrees yet.");
        }

        return 1;
    }

    private static int listDecreesByCategory(ServerCommandSource src, String category) {
        var data = DecreeStore.get();

        var filtered = data.decrees.stream()
                .filter(d -> d.category != null && d.category.equalsIgnoreCase(category))
                .toList();

        if (filtered.isEmpty()) {
            Messenger.info(src, "§7No decrees found in category §b" + category + "§7.");
            return 1;
        }

        Messenger.info(src, "§6Decrees in category §b" + category + "§6:");
        for (Decree d : filtered) {
            String statusColored = Messenger.colorStatus(d.status);
            Messenger.line(src, " §e#" + d.id + " " + statusColored + "§r " + d.title);
        }

        return 1;
    }

    // -------- HISTORY LISTING (/decrees history [page]) --------

    private static int history(ServerCommandSource src, int page) {
        var data = DecreeStore.get();

        List<Decree> finished = new ArrayList<>();
        for (Decree d : data.decrees) {
            if (d.status == DecreeStatus.ENACTED
                    || d.status == DecreeStatus.REJECTED
                    || d.status == DecreeStatus.CANCELLED) {
                finished.add(d);
            }
        }

        if (finished.isEmpty()) {
            Messenger.info(src, "§7No completed decrees yet this season.");
            return 1;
        }

        // Newest first by id
        finished.sort((a, b) -> Integer.compare(b.id, a.id));

        int total = finished.size();
        int totalPages = (int) Math.ceil(total / (double) DECREES_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * DECREES_PER_PAGE;
        int endIndex = Math.min(startIndex + DECREES_PER_PAGE, total);

        Messenger.info(src, "§6Decree history §7(page " + page + "/" + totalPages + "):");

        for (int i = startIndex; i < endIndex; i++) {
            Decree d = finished.get(i);

            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : d.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }

            String proposerName = null;
            if (d.createdBySeatId != null && !d.createdBySeatId.isBlank()) {
                SeatDefinition seat = CouncilConfig.findSeat(d.createdBySeatId);
                if (seat != null) {
                    proposerName = seat.displayName;
                } else {
                    proposerName = d.createdBySeatId;
                }
            }

            StringBuilder line = new StringBuilder();
            line.append(" §e[#").append(d.id).append("] ")
                    .append(Messenger.colorStatus(d.status)).append("§r ")
                    .append(d.title);

            if (proposerName != null) {
                line.append(" §7– proposed by §e").append(proposerName);
            }

            if (!d.votes.isEmpty()) {
                line.append(" §7– Y:").append(yes)
                        .append(" N:").append(no)
                        .append(" A:").append(abstain);
            }

            Messenger.line(src, line.toString());
        }

        if (totalPages > 1) {
            if (page < totalPages) {
                Messenger.line(src, "§7Use §e/decrees history " + (page + 1) + "§7 for the next page.");
            } else {
                Messenger.line(src, "§7You are on the last page of history.");
            }
        }

        return 1;
    }

    private static int decreeInfo(ServerCommandSource src, int id) {
        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        Messenger.info(src, "§6Decree #" + d.id);
        Messenger.line(src, " §7Title: §r" + d.title);

        String desc = (d.description == null || d.description.isEmpty())
                ? "§8<none>"
                : d.description;
        Messenger.line(src, " §7Description: §r" + desc);

        String cat = (d.category == null || d.category.isEmpty())
                ? "§8<none>"
                : d.category;
        Messenger.line(src, " §7Category: §b" + cat);

        if (d.expiresAt == null || d.expiresAt <= 0) {
            Messenger.line(src, " §7Expiry: §8none");
        } else {
            long now = System.currentTimeMillis();
            long diff = d.expiresAt - now;
            if (diff <= 0) {
                Messenger.line(src, " §7Expiry: §cEXPIRED (by time)");
            } else {
                long days = diff / 86_400_000L;
                if (days < 1) {
                    Messenger.line(src, " §7Expiry: §ein less than 1 day");
                } else {
                    Messenger.line(src, " §7Expiry: §ein approx " + days + " day(s)");
                }
            }
        }

        Messenger.line(src, " §7Status: " + Messenger.colorStatus(d.status));
        Messenger.line(src, " §7Created by seat: §b" + d.createdBySeatId);

        if (d.votes.isEmpty()) {
            Messenger.line(src, " §7Votes: §8none yet");
        } else {
            Messenger.line(src, " §7Votes:");
            d.votes.forEach((seatId, vote) ->
                    Messenger.line(src, "  §e- " + seatId + "§7: §b" + vote)
            );
        }

        return 1;
    }

    private static int decreeResults(ServerCommandSource src, int id) {
        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
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

        Messenger.info(src, "§6Results for decree §e#" + d.id + "§6: §r" + d.title);
        Messenger.line(src, " §7Status: " + Messenger.colorStatus(d.status));
        Messenger.line(src, " §7Active seats: §e" + totalActiveSeats);
        Messenger.line(src, " §7Votes: §aYes " + yes + "§7, §cNo " + no + "§7, §8Abstain " + abstain + "§7, Total " + votesCast);

        if (minVotesRequired > 0) {
            Messenger.line(src, " §7Quorum: §e" + votesCast + "/" + minVotesRequired +
                    (hasQuorum ? " §a(REACHED)" : " §c(NOT reached)"));
        } else {
            Messenger.line(src, " §7Quorum: §8none required");
        }

        Messenger.line(src, " §7Majority mode: §e" + mode +
                "§7, Ties: " + (rules.tiesPass ? "§apass" : "§cfail"));

        if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
            Messenger.line(src, " §7Time: §e" + elapsedMinutes + "/" + rules.votingDurationMinutes +
                    " min§7 → " + (timeExpired ? "§cEXPIRED" : "§aONGOING"));
        }

        if (!d.votes.isEmpty()) {
            Messenger.line(src, " §7Per seat:");
            d.votes.forEach((seatId, vote) ->
                    Messenger.line(src, "  §e- " + seatId + "§7: §b" + vote)
            );
        }

        return 1;
    }

    // -------- VOTING STATUS / REMINDERS (/decrees status [id]) --------

    private static int statusAll(ServerCommandSource src) {
        var store = DecreeStore.get();

        var activeVoting = store.decrees.stream()
                .filter(d -> d.status == DecreeStatus.VOTING)
                .toList();

        if (activeVoting.isEmpty()) {
            Messenger.info(src, "§7There are no decrees currently in §eVOTING§7.");
            return 1;
        }

        CouncilConfigData cfg = CouncilConfig.get();
        var activeSeats = cfg.seats.stream()
                .filter(s -> s.holderUuid != null)
                .toList();

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        Messenger.info(src, "§6Voting status for active decrees:");

        for (Decree d : activeVoting) {
            sendStatusLines(src, d, activeSeats, rules);
        }

        return 1;
    }

    private static int statusOne(ServerCommandSource src, int id) {
        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        CouncilConfigData cfg = CouncilConfig.get();
        var activeSeats = cfg.seats.stream()
                .filter(s -> s.holderUuid != null)
                .toList();

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        Messenger.info(src, "§6Voting status for decree §e#" + d.id + "§6: §r" + d.title);
        sendStatusLines(src, d, activeSeats, rules);
        return 1;
    }

    private static void sendStatusLines(ServerCommandSource src,
                                        Decree d,
                                        List<SeatDefinition> activeSeats,
                                        VotingRulesData rules) {

        int totalActiveSeats = activeSeats.size();

        int yes = 0;
        int no = 0;
        int abstain = 0;
        for (VoteChoice v : d.votes.values()) {
            if (v == VoteChoice.YES) yes++;
            else if (v == VoteChoice.NO) no++;
            else if (v == VoteChoice.ABSTAIN) abstain++;
        }
        int votesCast = d.votes.size();

        // Quorum
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

        // Missing seats
        List<String> missingNames = new ArrayList<>();
        for (SeatDefinition seat : activeSeats) {
            if (!d.votes.containsKey(seat.id)) {
                missingNames.add(seat.displayName);
            }
        }

        String quorumText;
        if (minVotesRequired > 0) {
            quorumText = "§7Quorum: §e" + votesCast + "/" + minVotesRequired +
                    (hasQuorum ? " §a(REACHED)" : " §c(NOT reached)");
        } else {
            quorumText = "§7Quorum: §8none required";
        }

        String summaryLine =
                " §e#" + d.id +
                        " " + Messenger.colorStatus(d.status) + "§r " + d.title +
                        " §8(Yes " + yes + ", No " + no + ", Abstain " + abstain +
                        ", Votes " + votesCast + "/" + totalActiveSeats + ")";

        Messenger.line(src, summaryLine);
        Messenger.line(src, "  " + quorumText);

        String missingText;
        if (missingNames.isEmpty()) {
            missingText = "§aAll active seats have voted.";
        } else {
            missingText = "§eMissing votes from: §c" +
                    String.join("§7, §c", missingNames);
        }

        Messenger.line(src, "  " + missingText);
    }

    // -------- GLOBAL FLAG CHECKS & STATUS --------

    private static boolean checkMutatingAllowed(ServerCommandSource src, String action) {
        CouncilConfigData cfg = CouncilConfig.get();

        if (!cfg.decreesEnabled) {
            Messenger.error(src, "The decrees system is currently DISABLED. You cannot " + action + " right now.");
            return false;
        }

        if (cfg.opsOnly && !src.hasPermissionLevel(3)) {
            Messenger.error(src, "Only operators may " + action + " while opsOnly mode is enabled.");
            return false;
        }

        return true;
    }

    private static boolean ensureCouncil(ServerCommandSource src, String action) {
        CouncilConfigData cfg = CouncilConfig.get();

        // In ops-only mode, ops may bypass seat requirement.
        if (cfg.opsOnly && src.hasPermissionLevel(3)) {
            return true;
        }

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            Messenger.error(src, "Only players can " + action + ".");
            return false;
        }
        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            Messenger.error(src, "Only council members can " + action + ".");
            return false;
        }
        return true;
    }

    private static int showStatus(ServerCommandSource src) {
        CouncilConfigData config = CouncilConfig.get();

        Messenger.info(src, "§6Status overview:");

        if (!config.decreesEnabled) {
            Messenger.line(src, "§cDecrees system is currently DISABLED (\"decreesEnabled\": false).");
        } else {
            Messenger.line(src, "§aDecrees system is ENABLED.");
        }

        if (config.opsOnly) {
            Messenger.line(src, "§7Mode: §eopsOnly§7 – only operators may create/open/edit/vote on decrees.");
        } else {
            Messenger.line(src, "§7Mode: §ecouncil§7 – council seats control decrees.");
        }

        if (src.getEntity() instanceof ServerPlayerEntity player) {
            SeatDefinition seat = CouncilUtil.getSeatFor(player);
            if (seat == null) {
                Messenger.line(src, "§7Your seat: §cYou do not currently hold a council seat.");
            } else {
                Messenger.line(src, "§7Your seat: §e" + seat.id + " §7(" + seat.displayName + ")");
            }
        } else {
            Messenger.line(src, "§7Source: console / command block (no seat).");
        }

        Messenger.line(src, "§7---");
        listActiveDecrees(src);

        return 1;
    }

    private static int setDecreesEnabled(ServerCommandSource src, boolean value) {
        CouncilConfigData cfg = CouncilConfig.get();
        cfg.decreesEnabled = value;
        CouncilConfig.save();

        if (value) {
            Messenger.info(src, "§aDecrees system has been ENABLED. Mutating commands are now allowed again.");
        } else {
            Messenger.info(src, "§cDecrees system has been DISABLED. Mutating commands are blocked (except reload & force).");
        }
        return 1;
    }

    private static int setOpsOnly(ServerCommandSource src, boolean value) {
        CouncilConfigData cfg = CouncilConfig.get();
        cfg.opsOnly = value;
        CouncilConfig.save();

        if (value) {
            Messenger.info(src, "§eopsOnly mode ENABLED. Only operators may create/open/edit/vote on decrees.");
        } else {
            Messenger.info(src, "§aopsOnly mode DISABLED. Council seats control decrees again.");
        }
        return 1;
    }

    // -------- COUNCIL CREATION CEREMONY --------

    private static int createCouncil(ServerCommandSource src, String name) {
        if (!src.hasPermissionLevel(3)) {
            Messenger.error(src, "Only operators can create or convene the council.");
            return 0;
        }

        String councilName = name.trim();
        if (councilName.isEmpty()) {
            councilName = "the Council";
        }

        CouncilConfigData cfg = CouncilConfig.get();
        boolean wasEnabled = cfg.decreesEnabled;

        // Store the name, turn the system on & into council mode
        cfg.councilName = councilName;
        cfg.decreesEnabled = true;
        cfg.opsOnly = false;
        CouncilConfig.save();

        String verb = wasEnabled ? "has been re-convened" : "has been convened";
        String subtitle = wasEnabled
                ? "Decrees remain active and are now bound to this council."
                : "Decrees are now active and bound to this council.";

        var server = src.getServer();
        var pm = server.getPlayerManager();

        // Big broadcast line
        pm.broadcast(
                Text.literal(getPrefix() + " §l" + councilName + "§r §6" + verb + "§6. " + subtitle),
                false
        );

        // Play a simple celebration sound for every player
        pm.getPlayerList().forEach(player -> {
            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        });

        // Fireworks at the executor's location if it's a player
        if (src.getEntity() instanceof ServerPlayerEntity opPlayer) {
            spawnCelebrationFireworks(opPlayer);
            Messenger.info(src, "§aCouncil ceremony completed. Fireworks launched at your position.");
        } else {
            Messenger.info(src, "§aCouncil ceremony completed. (Run this as a player to get fireworks.)");
        }

        return 1;
    }

    private static void spawnCelebrationFireworks(ServerPlayerEntity player) {
        var world = player.getWorld();
        if (world.isClient()) {
            return;
        }

        for (int i = 0; i < 5; i++) {
            ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, 1);

            double offsetX = (world.random.nextDouble() - 0.5) * 4.0;
            double offsetZ = (world.random.nextDouble() - 0.5) * 4.0;

            FireworkRocketEntity rocket = new FireworkRocketEntity(
                    world,
                    player.getX() + offsetX,
                    player.getY() + 1.0,
                    player.getZ() + offsetZ,
                    stack
            );

            world.spawnEntity(rocket);
        }
    }

    // -------- STATS (REPUTATION-LIKE) --------

    private static class SeatStats {
        final String id;
        final String displayName;
        int createdTotal;
        int createdEnacted;
        int createdRejected;
        int createdOther;
        int votesYes;
        int votesNo;
        int votesAbstain;

        SeatStats(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
    }

    private static Map<String, SeatStats> buildSeatStats() {
        Map<String, SeatStats> map = new LinkedHashMap<>();

        // Start with declared seats so order is predictable
        for (SeatDefinition seat : CouncilConfig.get().seats) {
            map.put(seat.id, new SeatStats(seat.id, seat.displayName));
        }

        var data = DecreeStore.get();

        // Created-by stats
        for (Decree d : data.decrees) {
            if (d.createdBySeatId != null && !d.createdBySeatId.isBlank()) {
                SeatStats stats = map.computeIfAbsent(
                        d.createdBySeatId,
                        id -> new SeatStats(id, id)
                );
                stats.createdTotal++;
                if (d.status == DecreeStatus.ENACTED) {
                    stats.createdEnacted++;
                } else if (d.status == DecreeStatus.REJECTED) {
                    stats.createdRejected++;
                } else {
                    stats.createdOther++;
                }
            }

            // Voting stats
            for (Map.Entry<String, VoteChoice> entry : d.votes.entrySet()) {
                String seatId = entry.getKey();
                VoteChoice v = entry.getValue();
                SeatStats stats = map.computeIfAbsent(
                        seatId,
                        id -> new SeatStats(id, id)
                );

                if (v == VoteChoice.YES) stats.votesYes++;
                else if (v == VoteChoice.NO) stats.votesNo++;
                else if (v == VoteChoice.ABSTAIN) stats.votesAbstain++;
            }
        }

        return map;
    }

    private static int showAllSeatStats(ServerCommandSource src) {
        Map<String, SeatStats> statsMap = buildSeatStats();

        if (statsMap.isEmpty()) {
            Messenger.info(src, "§7No seat stats available yet.");
            return 1;
        }

        Messenger.info(src, "§6Seat statistics:");
        for (SeatStats s : statsMap.values()) {
            String createdPart = "Created " + s.createdTotal;
            if (s.createdTotal > 0) {
                createdPart += " (" +
                        s.createdEnacted + "§a enacted§r, " +
                        s.createdRejected + "§c rejected§r, " +
                        s.createdOther + " other)";
            }

            String votesPart = "Votes: " +
                    s.votesYes + "§a yes§r, " +
                    s.votesNo + "§c no§r, " +
                    s.votesAbstain + " abstain";

            Messenger.line(src, " §e" + s.displayName + "§7 (" + s.id + ")§r → " + createdPart + "; " + votesPart);
        }

        return 1;
    }

    private static int showMySeatStats(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            Messenger.error(src, "Only players can view their own seat stats.");
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            Messenger.error(src, "You do not currently hold a council seat.");
            return 0;
        }

        return showSeatStatsInternal(src, seat.id);
    }

    private static int showSeatStatsById(ServerCommandSource src, String seatId) {
        return showSeatStatsInternal(src, seatId);
    }

    private static int showSeatStatsInternal(ServerCommandSource src, String seatId) {
        Map<String, SeatStats> statsMap = buildSeatStats();
        SeatStats s = statsMap.get(seatId);

        if (s == null) {
            Messenger.error(src, "No stats found for seat id: " + seatId);
            return 0;
        }

        Messenger.info(src, "§6Stats for seat §e" + s.displayName + "§7 (" + s.id + ")");

        Messenger.line(src, " §7Decrees created: §e" + s.createdTotal);
        if (s.createdTotal > 0) {
            int enactedPct = (int) Math.round((s.createdEnacted * 100.0) / s.createdTotal);
            int rejectedPct = (int) Math.round((s.createdRejected * 100.0) / s.createdTotal);
            Messenger.line(src, "  §7Enacted: §a" + s.createdEnacted + " (" + enactedPct + "%)");
            Messenger.line(src, "  §7Rejected: §c" + s.createdRejected + " (" + rejectedPct + "%)");
            Messenger.line(src, "  §7Other status (Draft/Voting/Expired): §e" + s.createdOther);
        } else {
            Messenger.line(src, "  §8No decrees created yet.");
        }

        int totalVotes = s.votesYes + s.votesNo + s.votesAbstain;
        Messenger.line(src, " §7Votes cast: §e" + totalVotes);
        if (totalVotes > 0) {
            Messenger.line(src, "  §7Yes: §a" + s.votesYes);
            Messenger.line(src, "  §7No: §c" + s.votesNo);
            Messenger.line(src, "  §7Abstain: §e" + s.votesAbstain);
        } else {
            Messenger.line(src, "  §8No votes cast yet.");
        }

        return 1;
    }

    private static void notifyCouncilVotingOpened(ServerCommandSource src, Decree d) {
        var pm = src.getServer().getPlayerManager();

        String msg = Messenger.prefix() +
                " §eDecree #" + d.id + "§6 is now in " +
                Messenger.colorStatus(DecreeStatus.VOTING) +
                "§6: §r" + d.title;

        for (ServerPlayerEntity p : pm.getPlayerList()) {
            if (CouncilUtil.getSeatFor(p) == null) continue;
            p.sendMessage(Text.literal(msg), false);
        }
    }

    private static void notifyCouncilDecreeFinal(ServerCommandSource src, Decree d) {
        var pm = src.getServer().getPlayerManager();

        for (ServerPlayerEntity p : pm.getPlayerList()) {
            if (CouncilUtil.getSeatFor(p) == null) continue;

            if (d.status == DecreeStatus.ENACTED) {
                p.sendMessage(
                        Text.literal(Messenger.prefix() + " §aDecree §e#" + d.id + "§a has been §lENACTED§r§a."),
                        false
                );
            } else if (d.status == DecreeStatus.REJECTED) {
                p.sendMessage(
                        Text.literal(Messenger.prefix() + " §cDecree §e#" + d.id + "§c has been §lREJECTED§r§c."),
                        false
                );
            } else if (d.status == DecreeStatus.CANCELLED) {
                p.sendMessage(
                        Text.literal(Messenger.prefix() + " §eDecree §e#" + d.id + "§e has been §lCANCELLED§r§e."),
                        false
                );
            }
        }
    }

    // -------- CREATE / OPEN / DELETE / EDIT / FORCE --------

    private static int createDecree(ServerCommandSource src, String title) {
        if (!checkMutatingAllowed(src, "create decrees")) {
            return 0;
        }

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            Messenger.error(src, "Only players can create decrees.");
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            Messenger.error(src, "Only council members can create decrees.");
            return 0;
        }

        Decree decree = DecreeStore.createDecree(title, seat.id);
        Messenger.info(src, "§aCreated decree §e#" + decree.id + "§a with title: §r" + decree.title);
        return 1;
    }

    private static int openDecree(ServerCommandSource src, int id) {
        if (!checkMutatingAllowed(src, "open decrees for voting")) {
            return 0;
        }

        if (!ensureCouncil(src, "open decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        if (d.status != DecreeStatus.DRAFT) {
            Messenger.error(src, "Only decrees in §eDRAFT§c can be opened for voting.");
            return 0;
        }

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        long now = System.currentTimeMillis();
        d.votingOpenedAt = now;

        if (rules.votingDurationMinutes > 0) {
            long durationMillis = rules.votingDurationMinutes * 60_000L;
            d.votingClosesAt = now + durationMillis;
        } else {
            d.votingClosesAt = null; // no automatic timeout
        }

        boolean changed = DecreeStore.setStatus(
                d,
                DecreeStatus.VOTING,
                "opened for voting by " + src.getName()
        );
        if (!changed) {
            Messenger.error(src, "Illegal or redundant state change for decree #" + id + ".");
            return 0;
        }

        int totalActiveSeats = (int) CouncilConfig.get().seats.stream()
                .filter(s -> s.holderUuid != null)
                .count();

        String durationText = (rules.votingDurationMinutes <= 0)
                ? "no time limit"
                : (rules.votingDurationMinutes + " min");

        Messenger.info(src, "§aDecree §e#" + d.id + "§a is now open for voting. (Active seats: §e" + totalActiveSeats + "§a)");
        Messenger.line(src, "§7Rules: Majority §e" + rules.majorityMode +
                "§7, Quorum §e" + rules.minQuorumPercent + "%§7, Duration §e" + durationText +
                "§7, Ties " + (rules.tiesPass ? "§apass" : "§cfail"));

        String broadcast = Messenger.prefix() +
                " §eDecree #" + d.id + " (" + d.title + ") is now in " +
                Messenger.colorStatus(DecreeStatus.VOTING) +
                "§e. Cast your vote with §b/decrees vote " + d.id + " <yes/no/abstain>.";

        src.getServer().getPlayerManager().broadcast(
                Text.literal(broadcast),
                false
        );

        notifyCouncilVotingOpened(src, d);

        return 1;
    }

    private static int deleteDecree(ServerCommandSource src, int id, boolean confirm) {
        if (!checkMutatingAllowed(src, "delete decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "delete decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        if (d.status != DecreeStatus.DRAFT) {
            Messenger.error(src, "Only decrees in §eDRAFT§c can be deleted.");
            Messenger.info(src, "§7Use §e/decrees decree force " + id + " cancelled§7 if you wish to cancel a non-draft decree.");
            return 0;
        }

        String key = src.getName();

        // First call: warn and store pending confirmation
        if (!confirm) {
            PENDING_DELETES.put(key, id);

            Messenger.info(src,
                    "§eYou are about to §cpermanently delete§e decree §e#" + id + "§e (DRAFT).");
            Messenger.info(src,
                    "§7Run §e/decrees decree delete " + id + " confirm §7to confirm.");
            return 1;
        }

        // Second call: must match pending entry
        Integer pending = PENDING_DELETES.get(key);
        if (pending == null || pending != id) {
            Messenger.error(src,
                    "No pending delete confirmation for decree §e#" + id +
                            "§c. Run §e/decrees decree delete " + id + "§c first.");
            return 0;
        }

        PENDING_DELETES.remove(key);

        var data = DecreeStore.get();
        boolean removed = data.decrees.removeIf(x -> x.id == id);
        if (!removed) {
            Messenger.error(src, "No decree with id #" + id + " (it may have already been deleted).");
            return 0;
        }

        DecreeStore.save();
        Messenger.info(src, "§aDeleted decree §e#" + id + "§a (DRAFT).");
        return 1;
    }

    private static int forceDecreeStatus(ServerCommandSource src, int id, DecreeStatus newStatus) {
        // Force bypasses decreesEnabled/opsOnly, but still requires op level.
        if (!src.hasPermissionLevel(3)) {
            Messenger.error(src, "Only operators can force decree status.");
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        DecreeStatus before = d.status;

        boolean changed = DecreeStore.setStatus(
                d,
                newStatus,
                "forced by " + src.getName()
        );
        if (!changed) {
            Messenger.error(src,
                    "Illegal or redundant state change for decree §e#" + id + "§c.");
            return 0;
        }

        String broadcast = Messenger.prefix() +
                " §6Decree §e#" + d.id + "§6 (" + d.title + ") was §eFORCED " +
                Messenger.colorStatus(newStatus) + "§6 by §e" + src.getName() + "§6.";
        src.getServer().getPlayerManager().broadcast(
                Text.literal(broadcast),
                false
        );

        // Notify council members with the usual finalisation message
        if (newStatus == DecreeStatus.ENACTED || newStatus == DecreeStatus.REJECTED) {
            notifyCouncilDecreeFinal(src, d);
        }

        Messenger.info(src,
                "§aForced decree §e#" + d.id + "§a from " +
                        Messenger.colorStatus(before) + "§a to " +
                        Messenger.colorStatus(newStatus) + "§a.");

        return 1;
    }

    private static int editTitle(ServerCommandSource src, int id, String newTitle) {
        if (!checkMutatingAllowed(src, "edit decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        d.title = newTitle;
        DecreeStore.save();
        Messenger.info(src, "§aUpdated title of decree §e#" + id + "§a.");
        return 1;
    }

    private static int editDescription(ServerCommandSource src, int id, String newDescription) {
        if (!checkMutatingAllowed(src, "edit decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        d.description = newDescription;
        DecreeStore.save();
        Messenger.info(src, "§aUpdated description of decree §e#" + id + "§a.");
        return 1;
    }

    private static int editCategory(ServerCommandSource src, int id, String newCategory) {
        if (!checkMutatingAllowed(src, "edit decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        d.category = newCategory;
        DecreeStore.save();
        Messenger.info(src, "§aUpdated category of decree §e#" + id + "§a.");
        return 1;
    }

    private static int editExpiryClear(ServerCommandSource src, int id) {
        if (!checkMutatingAllowed(src, "edit decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        d.expiresAt = null;
        DecreeStore.save();
        Messenger.info(src, "§aCleared expiry for decree §e#" + id + "§a.");
        return 1;
    }

    private static int editExpirySet(ServerCommandSource src, int id, int days) {
        if (!checkMutatingAllowed(src, "edit decrees")) {
            return 0;
        }

        if (!ensureCouncil(src, "edit decrees")) {
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        long now = System.currentTimeMillis();
        long millis = days * 86_400_000L;
        d.expiresAt = now + millis;

        DecreeStore.save();
        Messenger.info(src, "§aSet expiry of decree §e#" + id + "§a to about " + days + " day(s) from now.");
        return 1;
    }

    // -------- VOTING (with 1-vote-left broadcast) --------

    private static int vote(ServerCommandSource src, int id, VoteChoice choice) {
        if (!checkMutatingAllowed(src, "vote on decrees")) {
            return 0;
        }

        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            Messenger.error(src, "Only players can vote.");
            return 0;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null) {
            Messenger.error(src, "Only council members can vote.");
            return 0;
        }

        Decree d = DecreeStore.find(id);
        if (d == null) {
            Messenger.error(src, "No decree with id #" + id + ".");
            return 0;
        }

        if (d.status != DecreeStatus.VOTING) {
            Messenger.error(src, "Decree #" + id + " is not open for voting.");
            return 0;
        }

        // Record vote
        d.votes.put(seat.id, choice);

        CouncilConfigData cfg = CouncilConfig.get();
        var activeSeats = cfg.seats.stream()
                .filter(s -> s.holderUuid != null)
                .toList();
        int totalActiveSeats = activeSeats.size();

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

        // Quorum
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

        // Timing (prefer per-decree votingClosesAt, fallback to old logic)
        boolean timeExpired = false;
        long now = System.currentTimeMillis();

        if (d.votingClosesAt != null) {
            if (now >= d.votingClosesAt) {
                timeExpired = true;
            }
        } else if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
            long limitMillis = rules.votingDurationMinutes * 60_000L;
            if (now - d.votingOpenedAt >= limitMillis) {
                timeExpired = true;
            }
        }

        boolean allVoted = totalActiveSeats > 0 && votesCast == totalActiveSeats;

        // Missing seats BEFORE possible finalisation
        List<SeatDefinition> missingSeats = new ArrayList<>();
        for (SeatDefinition sDef : activeSeats) {
            if (!d.votes.containsKey(sDef.id)) {
                missingSeats.add(sDef);
            }
        }

        DecreeStatus before = d.status;
        DecreeStatus targetStatus = before;

        // --- automatic finalisation logic ---
        if (hasQuorum && (allVoted || timeExpired)) {
            int requiredYes;
            if ("TWO_THIRDS".equalsIgnoreCase(rules.majorityMode)) {
                requiredYes = (int) Math.ceil(votesCast * 2.0 / 3.0);
            } else {
                requiredYes = (votesCast / 2) + 1;
            }

            if (votesCast == 0) {
                targetStatus = DecreeStatus.REJECTED;
            } else if (yes > no && yes >= requiredYes) {
                targetStatus = DecreeStatus.ENACTED;
            } else if (yes == no) {
                targetStatus = rules.tiesPass ? DecreeStatus.ENACTED : DecreeStatus.REJECTED;
            } else {
                targetStatus = DecreeStatus.REJECTED;
            }
        } else if (timeExpired && !hasQuorum) {
            // You can later swap this to CANCELLED if you want timeout ≠ rejection
            targetStatus = DecreeStatus.REJECTED;
        }

        if (targetStatus != before) {
            String reason;
            if (timeExpired && !hasQuorum) {
                reason = "automatic resolution: time expired without quorum";
            } else if (timeExpired) {
                reason = "automatic resolution: time expired";
            } else {
                reason = "automatic resolution: all votes received";
            }
            DecreeStore.setStatus(d, targetStatus, reason);
        }

        DecreeStatus after = d.status;

        String quorumText;
        if (minVotesRequired > 0) {
            quorumText = " §7Quorum: §e" + votesCast + "/" + minVotesRequired +
                    (hasQuorum ? " §a(REACHED)" : " §c(NOT reached)");
        } else {
            quorumText = " §7Quorum: §8none required";
        }

        String extraStatus = "";
        if (before != after && after != DecreeStatus.VOTING) {
            extraStatus = " §7[Final: " + Messenger.colorStatus(after) + "§7]";
        }

        String msg = "§a" + seat.displayName + " voted §e" + choice +
                "§a on decree §e#" + d.id + "§a. (Yes: " + yes + ", No: " + no +
                ", Abstain: " + abstain + ", Votes: " + votesCast + "/" + totalActiveSeats + ")" +
                quorumText + extraStatus;

        Messenger.info(src, msg);

        // Finalisation broadcast
        if (before != after && after != DecreeStatus.VOTING) {
            String broadcast = Messenger.prefix() +
                    " §6Decree §e#" + d.id + "§6 (" + d.title + ") is now " +
                    Messenger.colorStatus(after) + "§6.";
            src.getServer().getPlayerManager().broadcast(
                    Text.literal(broadcast),
                    false
            );
            notifyCouncilDecreeFinal(src, d);
        } else {
            // No finalisation yet → if only ONE vote remaining, ping the council.
            if (after == DecreeStatus.VOTING && missingSeats.size() == 1) {
                SeatDefinition missing = missingSeats.get(0);
                String broadcast = Messenger.prefix() +
                        " §6Only one vote remaining on decree §e#" + d.id +
                        "§6 (" + d.title + "). Awaiting: §e" + missing.displayName + "§6.";
                src.getServer().getPlayerManager().broadcast(
                        Text.literal(broadcast),
                        false
                );
            }
        }

        return 1;
    }

    // -------- AUTO-CLOSE EXPIRED VOTES (SERVER TICK HOOK) --------

    /**
     * Called each server tick from DecreesOfTheSix.onInitialize → END_SERVER_TICK.
     * Closes decrees whose voting period has expired, using the same logic as in vote().
     */
    public static void tickAutoClose(MinecraftServer server) {
        var store = DecreeStore.get();
        if (store.decrees.isEmpty()) {
            return;
        }

        VotingRulesData rules = VotingRulesConfig.get();
        if (rules == null) {
            rules = new VotingRulesData();
        }

        long now = System.currentTimeMillis();

        CouncilConfigData cfg = CouncilConfig.get();
        var activeSeats = cfg.seats.stream()
                .filter(s -> s.holderUuid != null)
                .toList();
        int totalActiveSeats = activeSeats.size();

        for (Decree d : store.decrees) {
            if (d.status != DecreeStatus.VOTING) {
                continue;
            }

            // Determine when this decree should close.
            Long closesAt = d.votingClosesAt;
            if (closesAt == null) {
                // Fallback for older decrees that don't have votingClosesAt set.
                if (rules.votingDurationMinutes > 0 && d.votingOpenedAt != null) {
                    long limitMillis = rules.votingDurationMinutes * 60_000L;
                    closesAt = d.votingOpenedAt + limitMillis;
                } else {
                    continue; // no closing time → nothing to auto-close
                }
            }

            if (now < closesAt) {
                continue; // not yet expired
            }

            // Voting period expired → compute result (same logic as in vote(...))
            int yes = 0;
            int no = 0;
            int abstain = 0;
            for (VoteChoice v : d.votes.values()) {
                if (v == VoteChoice.YES) yes++;
                else if (v == VoteChoice.NO) no++;
                else if (v == VoteChoice.ABSTAIN) abstain++;
            }
            int votesCast = d.votes.size();

            // Quorum
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

            DecreeStatus before = d.status;
            DecreeStatus after;

            if (!hasQuorum) {
                // For now: timeout without quorum → REJECTED
                after = DecreeStatus.REJECTED;
            } else {
                int requiredYes;
                if ("TWO_THIRDS".equalsIgnoreCase(rules.majorityMode)) {
                    requiredYes = (int) Math.ceil(votesCast * 2.0 / 3.0);
                } else {
                    requiredYes = (votesCast / 2) + 1;
                }

                if (votesCast == 0) {
                    after = DecreeStatus.REJECTED;
                } else if (yes > no && yes >= requiredYes) {
                    after = DecreeStatus.ENACTED;
                } else if (yes == no) {
                    after = rules.tiesPass ? DecreeStatus.ENACTED : DecreeStatus.REJECTED;
                } else {
                    after = DecreeStatus.REJECTED;
                }
            }

            if (after == before) {
                continue;
            }

            boolean changed = DecreeStore.setStatus(
                    d,
                    after,
                    hasQuorum
                            ? "automatic resolution: voting period expired"
                            : "automatic resolution: voting period expired without quorum"
            );

            if (!changed) {
                continue; // illegal/redundant transition, ignore
            }

            String broadcast = Messenger.prefix() +
                    " §6Decree §e#" + d.id + "§6 (" + d.title +
                    ") is now " + Messenger.colorStatus(after) +
                    "§6 (voting period expired).";
            server.getPlayerManager().broadcast(
                    Text.literal(broadcast),
                    false
            );

            // Notify council members for final states
            if (after == DecreeStatus.ENACTED || after == DecreeStatus.REJECTED) {
                notifyCouncilDecreeFinal(server.getCommandSource(), d);
            }
        }
    }

}
