package com.spacewaltz.decrees.council;

import java.util.UUID;

public class SeatDefinition {
    public String id;           // e.g. "overseer_regent"
    public String displayName;  // e.g. "Overseer-Regent"
    public UUID holderUuid;     // null if empty

    public SeatDefinition() {
        // Needed for Gson
    }

    public SeatDefinition(String id, String displayName, UUID holderUuid) {
        this.id = id;
        this.displayName = displayName;
        this.holderUuid = holderUuid;
    }
}
