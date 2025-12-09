package com.spacewaltz.decrees.council;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO backing council.json
 */
public class CouncilConfigData {

    /** Human hint – stays in the JSON as "_comment". */
    public String _comment =
            "Configure the council for Decrees of the Six. " +
                    "Edit 'councilName', add seats, and set 'decreesEnabled' to true once ready.";

    /** Extra comment for seats section. */
    public String _commentSeats =
            "Each seat needs an 'id' and 'displayName'. " +
                    "holderName + holderUuid are optional; they are usually managed in-game via /decrees seat set.";

    /** Optional comment for ceremonySound. */
    public String _commentCeremonySound =
            "ceremonySound is the sound event played when the council is convened, " +
                    "e.g. 'decrees_of_the_six:council_chime'.";

    /** Display name used in prefixes like [Hexarchate]. */
    public String councilName = "The Council";

    /** Master toggle – if false, mutating commands refuse to run. */
    public boolean decreesEnabled = false;

    /** When true, only admins (permissions / ops) can mutate & vote. */
    public boolean opsOnly = false;

    /** Sound event ID used for the council ceremony (see ceremonySound in council.json). */
    public String ceremonySound = "minecraft:ui.toast.challenge_complete";

    /** Declared council seats. */
    public List<SeatDefinition> seats = new ArrayList<>();
}
