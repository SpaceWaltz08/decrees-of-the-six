package com.spacewaltz.decrees.guilds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spacewaltz.decrees.DecreesOfTheSix;
import com.spacewaltz.decrees.economy.EconomyAccount;
import com.spacewaltz.decrees.economy.EconomyService;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent store for all guild data.
 *
 * Serialized as config/decrees_of_the_six/guilds.json.
 */
public class GuildStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(DecreesOfTheSix.MOD_ID);

    private static final Path STORE_PATH = CONFIG_DIR.resolve("guilds.json");

    // Singleton instance
    private static GuildStore INSTANCE = new GuildStore();

    /**
     * Next numeric guild id.
     */
    public int nextGuildId = 1;

    /**
     * All guilds.
     */
    public List<Guild> guilds = new ArrayList<>();

    /**
     * Player UUID string -> guild id.
     */
    public Map<String, Integer> playerGuild = new HashMap<>();

    /**
     * Pending invites: target player UUID string -> guild id.
     * For Phase 2 we allow at most one pending invite per player.
     */
    public Map<String, Integer> pendingInvites = new HashMap<>();

    public static GuildStore get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (!Files.exists(STORE_PATH)) {
                INSTANCE = new GuildStore();
                save();
                return;
            }

            String json = Files.readString(STORE_PATH, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                INSTANCE = new GuildStore();
                return;
            }

            GuildStore loaded = GSON.fromJson(json, GuildStore.class);
            if (loaded == null) {
                INSTANCE = new GuildStore();
            } else {
                INSTANCE = loaded;
            }
        } catch (IOException e) {
            DecreesOfTheSix.LOGGER.error("Failed to load guilds from " + STORE_PATH, e);
            INSTANCE = new GuildStore();
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
            DecreesOfTheSix.LOGGER.error("Failed to save guilds to " + STORE_PATH, e);
        }
    }

    // ---------------------------------------------------------------------
    // Lookup helpers
    // ---------------------------------------------------------------------

    public static Guild findById(int id) {
        for (Guild g : get().guilds) {
            if (g.id == id) {
                return g;
            }
        }
        return null;
    }

    public static Guild findByName(String name) {
        if (name == null) return null;
        String needle = name.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return null;

        for (Guild g : get().guilds) {
            if (g.name != null && g.name.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return g;
            }
        }
        return null;
    }

    public static Guild getGuildForPlayer(UUID playerUuid) {
        if (playerUuid == null) return null;
        String key = playerUuid.toString();
        Integer id = get().playerGuild.get(key);
        if (id == null) return null;
        return findById(id);
    }

    public static Integer getGuildIdForPlayer(UUID playerUuid) {
        if (playerUuid == null) return null;
        return get().playerGuild.get(playerUuid.toString());
    }

    public static void setGuildForPlayer(UUID playerUuid, Integer guildId) {
        if (playerUuid == null) return;
        GuildStore store = get();
        String key = playerUuid.toString();
        if (guildId == null) {
            store.playerGuild.remove(key);
        } else {
            store.playerGuild.put(key, guildId);
        }
        save();
    }

    // ---------------------------------------------------------------------
    // Guild lifecycle
    // ---------------------------------------------------------------------

    public static Guild createGuild(String name, String tag, UUID leaderUuid) {
        GuildStore store = get();

        Guild guild = new Guild();
        guild.id = store.nextGuildId++;
        guild.name = name;
        guild.tag = tag;
        guild.createdAt = System.currentTimeMillis();
        guild.leaderUuid = leaderUuid != null ? leaderUuid.toString() : null;
        guild.motd = "";
        guild.openJoin = false;
        guild.maxMembers = 0; // 0 = no limit (Phase 2)
        // Default titles for ranks (customizable by the leader later).
        guild.leaderTitle = "Leader";
        guild.officerTitle = "Officer";
        guild.veteranTitle = "Veteran";
        guild.memberTitle = "Member";
        guild.recruitTitle = "Recruit";


        // Create treasury account using the economy system.
        String ownerToken = "GUILD:" + guild.id;
        EconomyAccount treasury = EconomyService.createSystemAccount(ownerToken);
        guild.treasuryAccountId = treasury != null ? treasury.id : null;

        // Leader membership
        if (leaderUuid != null) {
            String key = leaderUuid.toString();
            guild.members.put(key, GuildRole.LEADER);
            store.playerGuild.put(key, guild.id);
        }

        store.guilds.add(guild);
        save();
        return guild;
    }

    public static void removeMember(Guild guild, UUID playerUuid) {
        if (guild == null || playerUuid == null) return;
        String key = playerUuid.toString();

        guild.members.remove(key);

        GuildStore store = get();
        Integer existing = store.playerGuild.get(key);
        if (existing != null && existing == guild.id) {
            store.playerGuild.remove(key);
        }
        save();
    }

    public static int getPendingInviteCount(UUID playerUuid) {
        if (playerUuid == null) return 0;
        GuildStore store = get();
        return store.pendingInvites.containsKey(playerUuid.toString()) ? 1 : 0;
    }

    public static void addMember(Guild guild, UUID playerUuid, GuildRole role) {
        if (guild == null || playerUuid == null) return;
        String key = playerUuid.toString();

        if (role == null) {
            role = GuildRole.MEMBER;
        }

        guild.members.put(key, role);
        get().playerGuild.put(key, guild.id);
        save();
    }

    public static void disbandGuild(Guild guild) {
        if (guild == null) return;
        GuildStore store = get();

        // Clear all members' guild references
        for (String memberUuid : new ArrayList<>(guild.members.keySet())) {
            store.playerGuild.remove(memberUuid);
        }

        // Remove pending invites pointing to this guild
        store.pendingInvites.values().removeIf(id -> id != null && id == guild.id);

        // Treasury handling is done by the caller (admin command),
        // so we only drop the record here.
        store.guilds.removeIf(g -> g.id == guild.id);

        save();
    }

    // ---------------------------------------------------------------------
    // Invites
    // ---------------------------------------------------------------------

    public static void addInvite(UUID targetUuid, Guild guild) {
        if (targetUuid == null || guild == null) return;
        GuildStore store = get();
        store.pendingInvites.put(targetUuid.toString(), guild.id);
        save();
    }

    public static Guild consumeInvite(UUID targetUuid) {
        if (targetUuid == null) return null;
        GuildStore store = get();
        String key = targetUuid.toString();
        Integer guildId = store.pendingInvites.remove(key);
        if (guildId == null) {
            return null;
        }
        save();
        return findById(guildId);
    }
}
