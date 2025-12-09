package com.spacewaltz.decrees.council;

import java.util.ArrayList;
import java.util.List;

public class CouncilConfigData {

    /**
     * Master toggle for the whole decree system.
     * If false, you can later short-circuit commands like /decrees decree/vote.
     * Maps to: "decreesEnabled" in council.json
     */
    public boolean decreesEnabled = true;

    /**
     * If true, only ops (permission level ≥ 3) are allowed to use decree/vote
     * mechanics, regardless of seats. Seat config is still loaded for later use.
     * Maps to: "opsOnly" in council.json
     */
    public boolean opsOnly = false;

    /**
     * Display name of the council, used in chat prefixes like [The Hexarchate].
     * Maps to: "councilName" in council.json
     */
    public String councilName = "";

    /**
     * List of council seats as defined in council.json → "seats": [ ... ]
     */
    public List<SeatDefinition> seats = new ArrayList<>();
}
