package com.spacewaltz.decrees.guilds;

import java.util.HashMap;
import java.util.Map;

/**
 * Single guild record.
 */
public class Guild {

    /**
     * Internal numeric id.
     */
    public int id;

    /**
     * Guild display name (unique).
     */
    public String name;

    /**
     * Optional short tag (3–5 letters, unique).
     */
    public String tag;

    /**
     * Creation time in epoch millis.
     */
    public long createdAt;

    /**
     * UUID string of the leader.
     */
    public String leaderUuid;

    /**
     * Guild description / MOTD.
     */
    public String motd;

    /**
     * Custom display titles per role (guild-specific).
     * If null or blank, defaults will be used.
     */
    public String leaderTitle;
    public String officerTitle;
    public String veteranTitle;
    public String memberTitle;
    public String recruitTitle;


    /**
     * Whether anyone can join without an invite.
     */
    public boolean openJoin;

    /**
     * Maximum number of members.
     * 0 means "no limit" in Phase 2.
     */
    public int maxMembers;

    /**
     * Treasury account id in the economy system.
     */
    public String treasuryAccountId;

    /**
     * Members and their roles.
     * Key = player UUID string, value = role.
     */
    public Map<String, GuildRole> members = new HashMap<>();
}
