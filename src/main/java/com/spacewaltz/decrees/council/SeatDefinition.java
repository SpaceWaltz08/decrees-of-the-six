package com.spacewaltz.decrees.council;

import java.util.UUID;

/**
 * Single council seat entry in council.json
 */
public class SeatDefinition {

    /** Unique stable ID (e.g. "overseer_regent"). */
    public String id;

    /** Optional – for compatibility with the earlier spec. */
    public String title;

    /** Display name used in chat (e.g. "Overseer-Regent"). */
    public String displayName;

    /** Optional – original spec used "holder". */
    public String holder;

    /** Last known player name holding this seat (for human readability). */
    public String holderName;

    /** UUID of current holder (authoritative binding). */
    public UUID holderUuid;
}
