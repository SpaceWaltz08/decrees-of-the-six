# Decrees of the Six

A Fabric server-side governance, voting, lightweight economy, and guild system designed for council-style roleplay servers.  
The Hexarchate (or any council you define) can create, debate, and vote on decrees in-game, with automatic quorum/majority handling, a season history log, an optional multi-tier currency (Gold/Silver/Copper by default), and player guilds with shared treasuries.

**Current version:** 0.2.1 (Minecraft 1.21.1, Fabric)

---

## Features

- Council seats defined via JSON (`council.json`).
- **Named councils** with a configurable `councilName` used in decree-related messages.
- **Council ceremony** command to convene or re-convene the council with a server-wide broadcast, configurable chime, and fireworks.
- **Configurable council ceremony sound** via `ceremonySound` in `council.json` (e.g. `decrees_of_the_six:council_chime`).
- Decrees with lifecycle: **DRAFT → VOTING → ENACTED/REJECTED/CANCELLED**.
- Configurable voting rules (quorum, duration, majority mode, ties).
- Global toggles: enable/disable the whole decree system or restrict it to ops.
- Seat tools for assigning council members.
- **Join reminder** for council members with pending votes.
- **One-vote-remaining ping** when only one seat is left to vote on a decree.
- **Paginated decree lists & in-game history** via `/decrees decree list [page]` and `/decrees history [page]`.
- **Status-filtered decree lists** via `/decrees decree list status <draft|voting|enacted|rejected|cancelled> [page]`.
- **“My decrees” & active lists with pagination** via `/decrees decree list my [page]` and `/decrees decree list active [page]`.
- **Seat statistics & status helpers** via `/decrees stats ...` and `/decrees status [id]`.
- **Clean chat formatting & tab completion** for decree IDs, seat IDs, and categories.
- **Clickable vote hints** (buttons `[Yes] [No] [Abstain]`) for council members when a decree is opened.
- Text log of final decrees for season history.
- **Optional guild system**:
  - Player-created guilds with ranks (`Leader / Officer / Veteran / Member / Recruit`),
  - Per-guild customizable rank titles,
  - Each guild has its own Treasury account in the same economy,
  - Vox Imperion tools for inspecting and moderating guilds.
- **Optional Hexarchate Panel UI (G-key)** (if the client also loads the mod) with:
  - A **Ledger tab** for player balance and transactions,
  - A **Guild tab** showing guild name, rank, member counts, and guild Treasury balance, plus quick actions (deposit / leave / accept invites).
- All interaction done in-game via `/decrees`, `/money`, `/economy`, `/guild`, and `/vox guild` commands.

---

## Economy / Money System

- Optional **single currency with three denominations** (default: Gold, Silver, Copper Scales).
- Configurable names and conversion ratios via `economy_config.json`.
- Persistent JSON-backed store of accounts and transactions (per-player account + global Treasury account + guild Treasuries).
- **Player commands** (`/money`) for checking balances, paying other players, and viewing your own transaction log.
- **Admin commands** (`/moneyadmin`) for granting / seizing money and managing the Treasury.
- **Economy meta commands** (`/economy`) for help and live reloading of economy config.
- Amounts are always shown in `G/S/C` form (e.g. `2G 4S 30C Scales`).
- **G-key “Hexarchate Panel” UI** (client-side optional):
  - **Ledger tab**:
    - Player name and balance in G/S/C,
    - Recent transactions formatted in G/S/C.
  - **Guild tab**:
    - Guild name and your guild rank/title,
    - Leader and member counts per rank,
    - Guild Treasury balance in G/S/C,
    - Pending invite count,
    - Quick buttons to deposit, leave, or accept invites (front-end to `/guild` commands).

---

## Guilds & Vox Imperion

The guild system is fully server-side and uses the same economy backend. Guilds are stored persistently in a world data file managed by the mod (not meant to be edited manually).

### Guild Basics

- A player can be a member of **at most one guild** at a time.
- Each guild has:
  - A unique internal ID and a **name**,
  - A **Leader** (player UUID),
  - Members with one of 5 ranks:
    - `Leader`, `Officer`, `Veteran`, `Member`, `Recruit`,
  - A **Treasury account** in the economy system,
  - Optional settings: MOTD/description, open-join flag, and max members.
- Each guild can customize the **display titles** for ranks:
  - e.g. `Leader = "High Chancellor"`, `Officer = "First Blade"`, etc.

### Guild Ranks & Titles

Internal permission roles:

- `Leader` – full control.
- `Officer` – strong management powers.
- `Veteran` – minor management powers.
- `Member` – normal access, no management.
- `Recruit` – probationary / newcomer.

Each role can have a **guild-specific title**, set by the guild leader, for flavour/RP. Internally, permissions are based on the roles; titles are just display.

---

## Requirements

- Minecraft: **1.21.1** (adjust if you built for a different version).
- **Fabric Loader** (matching your server version).
- **Fabric API**.

This is primarily a **server-side** mod. Clients do **not** need to install it to use `/decrees`, `/money`, or `/guild` commands.  
Installing the mod on the **client** is optional but enables the G-key “Hexarchate Panel” screen.

---

## Installation (Server Owners)

1. Build or download the mod JAR (usually in `build/libs/`).
2. Place the JAR into your server's `mods/` folder.
3. Start the server once to generate default config files.
4. Edit the config files under:

       config/decrees_of_the_six/council.json
       config/decrees_of_the_six/voting_rules.json
       config/decrees_of_the_six/economy_config.json

5. Use `/decrees reload` and `/economy reload` in-game (as an op) after editing configs, or restart the server.

Guild and economy data stores (accounts, transactions, guilds) are managed automatically by the mod and saved on server shutdown. They should not be edited by hand.

---

## Configuration

### 1. `council.json`

Defines global flags, the council name, ceremony sound, and council seats.

Example:

    {
      "councilName": "Hexarchate of Salmon",
      "decreesEnabled": true,
      "opsOnly": false,
      "ceremonySound": "decrees_of_the_six:council_chime",
      "seats": [
        {
          "id": "overseer_regent",
          "displayName": "Overseer-Regent"
        }
      ]
    }

**Fields:**

- `councilName` (string, optional)  
  - Friendly display name of your council (e.g. `"Hexarchate of Salmon"`).  
  - Used in decree-related messages and flavour text.  
  - Can be set manually or via `/decrees council create <name>`.

- `decreesEnabled` (boolean)  
  - `true` → all decree features work normally.  
  - `false` → decree creation, opening, voting, etc. are disabled (players get a message).

- `opsOnly` (boolean)  
  - `false` → only council seats can create/open/vote (normal mode).  
  - `true` → only ops can create/open/delete/vote on decrees (safety mode / emergency override).

- `ceremonySound` (string, optional)  
  - Resource location of the sound event used for the council ceremony broadcast.  
  - Example: `"decrees_of_the_six:council_chime"` for a sound defined in your `sounds.json`.  
  - If missing or invalid, the mod falls back to a vanilla toast sound.

- `seats` (array) – each seat:
  - `id` – internal ID (e.g. `"overseer_regent"`). Used in saves & logs.  
  - `displayName` – pretty name shown in chat (e.g. `"Overseer-Regent"`).  
  - `holderUuid` (optional) – UUID of the player currently holding that seat.

You typically assign seat holders in-game using `/decrees seat set` instead of editing `holderUuid` by hand.

> `/decrees council create <name>` will:
> - Set `councilName` to `<name>`,
> - Force `decreesEnabled = true`,
> - Force `opsOnly = false`.

---

### 2. `voting_rules.json`

Controls how votes are evaluated.

Example:

    {
      "minQuorumPercent": 60,
      "votingDurationMinutes": 30,
      "majorityMode": "SIMPLE",
      "tiesPass": false
    }

**Fields:**

- `minQuorumPercent`  
  Minimum % of active seats that must vote for the decree to be valid.  
  Example: `60` with 5 active seats → at least 3 votes needed.

- `votingDurationMinutes`  
  How long voting stays open once a decree is opened.  
  `0` or less → no time limit (will resolve when everyone votes or via force).

- `majorityMode`  
  - `"SIMPLE"` → more YES than NO and at least half + 1 of the votes.  
  - `"TWO_THIRDS"` → at least 2/3 of the votes must be YES.

- `tiesPass`  
  - `true` → YES = NO counts as **ENACTED**.  
  - `false` → YES = NO counts as **REJECTED**.

---

### 3. Decree History Log

Finalised decrees (status becomes **ENACTED**, **REJECTED**, or **CANCELLED**) are appended to a history log, e.g.:

    config/decrees_of_the_six/decrees_history.log

The log typically includes:

- Timestamp  
- Decree ID and title  
- Final status  
- Who forced it (if done via `/decrees decree force`)  
- A compact summary of votes  

This is meant for season records / lore.

---

### 4. `economy_config.json`

Controls how the currency behaves and how amounts are displayed.

Example:

    {
      "enabled": true,
      "currencyName": "Scales",
      "goldName": "Gold",
      "silverName": "Silver",
      "copperName": "Copper",
      "copperPerSilver": 10,
      "copperPerGold": 100
    }

**Fields (high level):**

- `enabled` (boolean)  
  - If `false`, the money system is effectively disabled (commands will refuse to run).

- `currencyName` (string)  
  - Base display name, e.g. `"Scales"`, `"Crowns"`, `"Marks"`.

- `goldName`, `silverName`, `copperName` (strings)  
  - Display names for each denomination.  
  - Used in chat and UI.

- `copperPerSilver`, `copperPerGold` (ints)  
  - Conversion rates to the smallest unit (copper).  
  - Example above → `1 Silver = 10 Copper`, `1 Gold = 100 Copper`.

Economy data (accounts + transactions + guild Treasuries) is stored in a separate JSON file (e.g. `economy_store.json`) next to your other Decrees config. It is not meant to be edited by hand.

---

## How Council Seats Work

Seats are defined in `council.json` under `"seats"`.

- A player can hold **at most one seat**.
- Assigning a new seat will clear any previous one.

Seats are used to:

1. Determine who may create/open/delete decrees.
2. Determine who may vote (each seat = one vote).
3. Show “your vote” in `/decrees decree list active`.
4. Drive join reminders and “one vote left” pings.

### Managing Seats (Ops Only)

All seat management commands are op-only:

- `/decrees seat list`  
  Shows all seats, their display names, and current holder (player name or `<empty>`).

- `/decrees seat set <seat_id> <player>`  
  Assigns a player to a seat.  
  If the player already holds a different seat, that old seat is cleared automatically.

- `/decrees seat clear <seat_id>`  
  Removes the holder from a seat (seat becomes empty).

---

## Decree Lifecycle (Council Workflow)

Typical flow for a council session:

1. **Create a decree (DRAFT)**  
   A council member runs:

       /decrees decree create <title>

   This creates a new decree in **DRAFT** status, linked to their seat.

2. **Edit details (optional but recommended)**  

       /decrees decree edit title <id> <new_title>
       /decrees decree edit description <id> <text>
       /decrees decree edit category <id> <category>
       /decrees decree edit expiry <id> none
       /decrees decree edit expiry <id> <days>

3. **Open for voting (VOTING)**  

       /decrees decree open <id>

   - Starts the voting timer (`votingDurationMinutes`) if configured.  
   - Notifies council members, including clickable `[Yes] [No] [Abstain]` buttons in chat.

4. **Council votes**  
   Each council seat holder uses:

       /decrees vote <id> yes
       /decrees vote <id> no
       /decrees vote <id> abstain

   or clicks the vote buttons to prefill the vote command.

5. **Automatic resolution**  

   When:

   - Quorum is reached **and**
   - All active seats have voted **or** voting time has expired,

   the system automatically sets the decree to **ENACTED**, **REJECTED**, or (if forced) **CANCELLED** according to the rules in `voting_rules.json`, then logs the change and notifies the council.

6. **Review results / history**

   - `/decrees decree results <id>` – detailed counts, quorum info, timer state.  
   - History log in `decrees_history.log` for permanent record.  
   - `/decrees history [page]` for an in-game, paginated history view.

---

## Council Ceremony & Reminders

### Council Ceremony

- `/decrees council create <name>` (op only)  

This command:

- Sets the council name (`councilName`) to `<name>`.
- Enables the decree system (`decreesEnabled = true`).
- Switches back to council mode (`opsOnly = false`).
- Broadcasts a global message announcing that the council has convened or re-convened.
- Plays the configured **ceremony sound** (`ceremonySound` in `council.json`) to all online players.
- Launches multiple fireworks at the executor’s position.

Use this once per season when the council is founded, or again if you want to ceremonially “re-convene” it after changes.

### Join Reminder & One-Vote-Remaining Ping

- When a **council member** joins, they get a reminder if they have pending votes:  
  - Shows how many decrees in **VOTING** still lack their vote.  
  - Lists up to 5 decree IDs (e.g. `#3, #7, #12...`).

- When **only one active seat** is left to vote on a decree, the server broadcasts a message indicating which seat is still pending.

---

## Commands Overview

### 1. Root

- `/decrees help`  
  Show a short command overview.

- `/decrees reload` (op only)  
  Reload `council.json` and `voting_rules.json` from disk.

- `/decrees status`  
  Show voting status for all decrees currently in **VOTING**.

- `/decrees status <id>`  
  Show detailed voting status for a specific decree.

- `/decrees history [page]`  
  Paginated history of completed decrees.

- `/decrees config decreesEnabled on|off` (op only)  
  Enable or disable the decrees system globally.

- `/decrees config opsOnly on|off` (op only)  
  Toggle ops-only mode.

- `/decrees config show` (op only)  
  Show current system status, including council name and your seat (if any).

- `/decrees stats seats`  
  Show per-seat statistics for created decrees and votes.

- `/decrees stats me`  
  Show statistics for your own seat.

- `/decrees stats seat <seat_id>`  
  Show statistics for a specific seat.

---

### 2. Council Ceremony

- `/decrees council create <name>` (op only)  
  Convene or re-convene the council, set the council name, enable decrees, and trigger the ceremony broadcast + configurable sound + fireworks.

---

### 3. Seats (Ops Only)

- `/decrees seat list`  
- `/decrees seat set <seat_id> <player>`  
- `/decrees seat clear <seat_id>`

---

### 4. Decree Management

- `/decrees decree list [page]`  
  List all decrees (paginated).

- `/decrees decree list my [page]`  
  List decrees created by your seat (council members only), with pagination.

- `/decrees decree list active [page]`  
  List all decrees currently in **VOTING**, including your own vote if you hold a seat, with pagination.

- `/decrees decree list status <draft|voting|enacted|rejected|cancelled> [page]`  
  List decrees filtered by status, with pagination.

- `/decrees decree list category <category>`  
  List decrees in a given category.

- `/decrees decree info <id>`  
  Show full info (title, description, category, expiry, creator, votes).

- `/decrees decree results <id>`  
  Show detailed vote results, quorum status, majority mode, and timer state.

- `/decrees decree create <title>`  
  Create a new decree in **DRAFT** (council only, or ops if `opsOnly` is `true`).

- `/decrees decree open <id>`  
  Open a **DRAFT** decree for voting.

- `/decrees decree edit title <id> <new_title>`  
- `/decrees decree edit description <id> <new_description>`  
- `/decrees decree edit category <id> <new_category>`  
- `/decrees decree edit expiry <id> none`  
- `/decrees decree edit expiry <id> <days>`

- `/decrees decree delete <id>`  
  Delete a decree (council only / ops depending on config).

- `/decrees decree force <id> enacted|rejected|cancelled` (op only)  
  Force the final status of a decree (escape hatch if rules misbehave or get stuck).

---

### 5. Voting

- `/decrees vote <id> yes|no|abstain`  
  Cast your seat’s vote on a decree currently in **VOTING**.  
  (Can also be filled in via the clickable `[Yes] [No] [Abstain]` hints.)

---

### 6. Economy & Money

**Player-facing commands:**

- `/money`  
  Show your own balance in `G/S/C` form.

- `/money balance <player>`  
  Show another player’s balance.

- `/money pay <player> <amount>`  
  Pay another player.  
  - `<amount>` supports:
    - Raw copper integer (e.g. `150`)
    - Denominated strings such as `1G 5S 20C`, `2g`, `10s`, `50c` (case-insensitive).

- `/money log`  
  Show your recent transactions.

**Admin / treasury commands:**

- `/moneyadmin grant <player> <amount>`  
  Mint money directly to a player.

- `/moneyadmin seize <player> <amount>`  
  Remove money from a player.

- `/moneyadmin treasury deposit <amount>`  
  Move money from the executor to the global Treasury.

- `/moneyadmin treasury withdraw <amount>`  
  Move money from the global Treasury to the executor.

- `/moneyadmin log`  
  Show recent transactions across the whole system (for audit).

**Economy meta:**

- `/economy help`  
  Overview of all money and treasury commands, plus a short explanation of denominations.

- `/economy reload` (op only)  
  Reload `economy_config.json` from disk.

---

### 7. Guilds & Vox Imperion

**Player guild commands:**

- `/guild create <name>`  
  Create a new guild (one guild per player).  
  You become the **Leader** and a guild Treasury account is created.

- `/guild invite <player>`  
- `/guild accept`  
- `/guild deny`  
  Invite other players to your guild and let them accept or deny.

- `/guild leave`  
  Leave your current guild.  
  - Leaders must transfer leadership or disband instead of leaving.

- `/guild kick <player>`  
  Remove a player from the guild.  
  - Leader and Officers may kick lower-rank members according to rank rules.

- `/guild promote <player>`  
- `/guild demote <player>`  
  Change a member’s rank within `Leader / Officer / Veteran / Member / Recruit`.  
  (Leader can adjust all non-leader ranks; Officers can adjust Veteran/Member/Recruit.)

- `/guild setmotd <text>`  
  Set a guild description / MOTD for info screens.

- `/guild setopen <true|false>`  
  Mark the guild as open or closed for future open-join features (stored now, used later).

- `/guild setmax <number>`  
  Optional member cap (stored now, used later).

- `/guild settitle <leader|officer|veteran|member|recruit> <title>`  
  Set guild-specific display titles for ranks (e.g. `"High Chancellor"`, `"First Blade"`).

- `/guild balance`  
  Show the guild Treasury balance in G/S/C.

- `/guild deposit <G> <S> <C>`  
  Deposit money from your personal account into the guild Treasury.

- `/guild withdraw <G> <S> <C>`  
  **Leader-only**: withdraw money from the guild Treasury to your personal account.

**Admin / Vox Imperion commands:**

- `/vox guild info <name>`  
  Inspect any guild:
  - Leader,
  - Member counts by rank,
  - Treasury balance,
  - Creation date (if tracked).

- `/vox guild setleader <guild> <player>`  
  Force-transfer leadership to another player.

- `/vox guild disband <guild>`  
  Force disband a guild.  
  Remaining guild funds are redirected to the Council Treasury (configurable pattern in code).

- `/vox guild rename <guild> <newName>`  
  Emergency rename for trolling / violations.

Vox commands are bound to the **Vox Imperion** council seat via the council permission system (with an op fallback).

---

## Permissions Summary

**Ops:**

- Always allowed to use:
  - `/decrees reload`
  - `/decrees council create <name>`
  - `/decrees seat ...`
  - `/decrees decree force ...`
  - `/decrees config ...`
  - `/economy reload`
  - All `/moneyadmin ...` commands
  - All `/vox guild ...` commands
- When `opsOnly = true`, ops can also create/open/delete/vote on decrees even without a seat.

**Council members** (players who hold a seat):

- Can create, edit, open, delete their decrees  
  (assuming `decreesEnabled = true` and `opsOnly = false`).
- Can vote on any decree in **VOTING**.

**Guild members:**

- May use `/guild` commands according to their rank:
  - Everyone: `/guild balance`, `/guild deposit`, `/guild leave`, basic info.
  - Veterans/Officers: extra invite powers.
  - Leader: full management + `/guild withdraw`.

**Regular players:**

- Can typically view decree lists and info (if you keep those commands open).
- Can use `/money`, `/money balance`, `/money pay`, `/money log` if the economy is enabled.
- Can create and join guilds via `/guild` commands (subject to your server rules).
- Cannot create, open, delete, or vote on decrees unless:
  - they are given a seat, or
  - you switch to `opsOnly` and make them ops (not recommended).

> If `decreesEnabled` is `false`, mutating decree commands are blocked even for ops  
> (except `/decrees reload` and `/decrees decree force`).  
> If `economy_config.enabled` is `false`, all money and guild Treasury commands will refuse to run.

---

## Versioning & Changelog

This mod uses **semantic versioning**.  
See `CHANGELOG.md` for a detailed list of changes per version.

---

## License

Decrees of the Six is licensed under the **MIT License**.  
See `LICENSE` for full details.
