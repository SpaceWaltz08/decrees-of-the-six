package com.spacewaltz.decrees;

/**
 * Core toggles for the Decrees system.
 *
 * This will be serialized as config/decrees_of_the_six/decrees_config.json.
 */
public class DecreesConfigData {

    /**
     * Global toggle.
     * If false, all /decrees commands (except /decrees reload) will refuse to run.
     */
    public boolean enabled = true;

    /**
     * When true, only operators (permission level >= 3) may use /decrees commands.
     * Console is always treated as allowed.
     */
    public boolean opsOnly = false;
}
