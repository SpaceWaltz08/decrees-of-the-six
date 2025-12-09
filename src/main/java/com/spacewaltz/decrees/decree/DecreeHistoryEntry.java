package com.spacewaltz.decrees.decree;

import java.time.Instant;

public class DecreeHistoryEntry {

    /** Numeric id of the decree at the time it was closed. */
    public int decreeId;

    /** Final status: ENACTED / REJECTED / CANCELLED. */
    public DecreeStatus finalStatus;

    /** Title of the decree when it was closed (may be null for old entries). */
    public String title;

    /** Raw vote tallies. */
    public int votesYes;
    public int votesNo;
    public int votesAbstain;

    /** Total votes actually cast (YES + NO + ABSTAIN). */
    public int totalVotes;

    /** How many votes were required for quorum at closure (0 = no quorum rule). */
    public int quorumRequired;

    /** Whether quorum was considered met at closure. */
    public boolean quorumMet;

    /** Preferred field name used by newer versions for the closure timestamp. */
    public long closedAtEpochMillis;

    /**
     * Legacy field used by older builds (named "closedAtMillis" in JSON).
     * Kept for Gson backwards-compatibility; new code writes to closedAtEpochMillis.
     */
    public Long closedAtMillis;

    public DecreeHistoryEntry() {
        // for Gson
    }

    /**
     * Unified accessor for the closure instant, tolerant of both old and new field names.
     */
    public Instant getClosedAtInstant() {
        long millis;
        if (closedAtEpochMillis != 0L) {
            millis = closedAtEpochMillis;
        } else if (closedAtMillis != null) {
            millis = closedAtMillis;
        } else {
            millis = 0L;
        }
        return Instant.ofEpochMilli(millis);
    }

    /** Older name; kept in case anything still calls it. */
    public Instant closedAtInstant() {
        return getClosedAtInstant();
    }
}
