package com.spacewaltz.decrees.economy;

/**
 * Configuration for the core economy system.
 *
 * Serialized as config/decrees_of_the_six/economy_config.json.
 */
public class EconomyConfigData {

    /**
     * Base currency name, shown after the denominations.
     * Example: "Scales" => "3 Gold, 4 Silver, 2 Copper Scales".
     */
    public String currencyName = "Scales";

    /**
     * How many silver coins equal one gold coin.
     * Default: 10 silver = 1 gold.
     */
    public int silverPerGold = 10;

    /**
     * How many copper coins equal one silver coin.
     * Default: 10 copper = 1 silver.
     */
    public int copperPerSilver = 10;

    /**
     * Starting balance for a new player, expressed in copper units.
     * You can give every player some initial money if desired.
     */
    public int startingBalanceCopper = 0;

    /**
     * Global toggle for the economy system.
     * If disabled, /money and /ledger commands will refuse to run.
     */
    public boolean enabled = true;
}