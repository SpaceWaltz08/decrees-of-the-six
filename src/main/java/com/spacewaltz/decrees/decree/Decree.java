package com.spacewaltz.decrees.decree;

import java.util.HashMap;
import java.util.Map;

public class Decree {

    public int id;
    public DecreeStatus status = DecreeStatus.DRAFT;

    public String title;
    public String description;
    public String category;

    // Which council seat created this decree
    public String createdBySeatId;

    // When the decree enters VOTING, we set when the voting period will end (epoch millis).
    public Long votingClosesAt;

    // Optional content expiry (not the voting timeout)
    public Long expiresAt;

    // When voting was opened (epoch millis)
    public Long votingOpenedAt;

    /**
     * Absolute time (epoch millis) when voting should automatically close.
     * Null = no automatic timeout.
     */
    public Long openUntilMillis;

    // seatId -> vote
    public Map<String, VoteChoice> votes = new HashMap<>();

    public Decree() {
        // for Gson
    }
}
