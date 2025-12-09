package com.spacewaltz.decrees.decree;

import java.util.HashMap;
import java.util.Map;

public class Decree {
    public int id;
    public String title;
    public String description;
    public String category;
    public Long expiresAt;

    public DecreeStatus status;
    public String createdBySeatId;
    public long createdAt;

    // NEW: when voting was opened (can be null)
    public Long votingOpenedAt;

    public Map<String, VoteChoice> votes = new HashMap<>();
}
