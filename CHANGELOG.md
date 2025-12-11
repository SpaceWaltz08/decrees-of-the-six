# Changelog

All notable changes to this project will be documented in this file.

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [v0.2.1] – Guilds, Treasury & Hexarchate Panel

### New: Player Guilds (Phase 2 foundations)
- Added fully persistent **Guilds** as first-class entities:
  - Unique guild ID, name, leader, members and 5 ranks: `Leader / Officer / Veteran / Member / Recruit`.
  - Each guild has its own **Treasury account** in the economy system.
- Core guild commands:
  - `/guild create <name>` – found a new guild (one guild per player).
  - `/guild invite <player>` / `/guild accept` / `/guild deny` – invite-based join flow.
  - `/guild leave` – members can leave; leaders must transfer or disband.
  - `/guild kick <player>` – Leader and Officers can remove lower-rank members.
  - `/guild promote <player>` / `/guild demote <player>` – rank management within the 5-step ladder.
- Guild settings & flavour:
  - `/guild setmotd <text>` – set a guild description / MOTD.
  - `/guild setopen <true|false>` – prepare for future open-join behaviour.
  - `/guild setmax <number>` – optional member cap (stored now, enforced later).
  - `/guild settitle <leader|officer|veteran|member|recruit> <title>` – **per-guild custom rank titles** (e.g. “Archon”, “First Blade”, etc.).

### New: Guild Treasury + Economy Integration
- Every guild now has a dedicated **SYSTEM Treasury account** created at guild creation.
- Economy-backed commands:
  - `/guild balance` – shows the guild Treasury balance (Gold / Silver / Copper).
  - `/guild deposit <G> <S> <C>` – transfer from your personal account into the guild Treasury.
  - `/guild withdraw <G> <S> <C>` – **Leader-only** withdrawal from Treasury back to their personal account.
- All guild money flows use the existing Phase 1 economy backend (`EconomyStore`, `EconomyService`, `TransactionType`).

### New: Vox Imperion Guild Tools
- The **Vox Imperion** council seat (or server ops) gains admin-side guild controls:
  - `/vox guild info <name>` – inspect any guild: leader, member breakdown, and Treasury balance.
  - `/vox guild setleader <guild> <player>` – force-transfer guild leadership.
  - `/vox guild disband <guild>` – forcibly disband a guild; remaining funds are redirected to the **Council Treasury**.
  - `/vox guild rename <guild> <newName>` – emergency rename for trolling / violations.
- Vox commands are bound to the **Vox Imperion** seat via the existing council permission system (plus fallback for ops).

### New: Hexarchate Panel – Guild Tab (G-key UI)
- The in-game **Hexarchate Panel** (opened with the G key) now has **two tabs**:
  - **Ledger** – existing player economy overview.
  - **Guild** – new tab for guild status.
- Guild tab displays:
  - Guild name and **your custom rank title**.
  - Leader name (resolved from UUID when possible).
  - Member counts by rank: total members + Officers / Veterans / Members / Recruits.
  - Guild Treasury balance formatted as G/S/C.
  - Pending invite count if you are not yet in a guild.
- Guild tab actions:
  - **Deposit** – opens chat prefilled with `/guild deposit ` to send funds to the Treasury.
  - **Leave Guild** – sends `/guild leave` for quick exit.
  - **Invites** – shows `Invites (X)` when you have pending invites and opens chat with `/guild accept`.

### Networking & Persistence
- Added guild snapshot networking for the UI:
  - `GuildSnapshotRequestC2SPayload` (client → server) requests a fresh guild snapshot.
  - `GuildSnapshotS2CPayload` (server → client) sends a compact summary of the player’s guild + Treasury.
  - `GuildNetworking` (server) and `GuildClientNetworking` + `ClientGuildState` (client) mirror the existing economy snapshot pattern.
- `GuildStore` is loaded on server start and saved on clean shutdown alongside `EconomyStore`, ensuring guilds and their treasuries persist between restarts.
- Ensured a player can effectively be **in only one guild at a time**; helper logic keeps membership maps and the player→guild mapping consistent, even under Vox overrides.

### Notes / Limitations (by design for 0.2.1)
- No weekly dues, taxes, or faction politics yet – this release is focused on **solid foundations**:
  - guild identity + ranks,
  - shared money via Treasury,
  - clean admin controls (Vox Imperion),
  - and a first UI surface for players.
- Open-join behaviour (`openJoin`) and more advanced guild management / Vox-only UI hooks are reserved for future milestones.


## [0.2.0] – Economy Foundations (Phase 1)

### New – Core Currency & Accounts
- Added a **server-side global currency system** with three denominations:
  - Gold, Silver, Copper (backed by a single internal “copper” value).
  - Currency **name is configurable** (e.g. “Scales”) while the G/S/C structure is fixed.
- Introduced **persistent accounts**:
  - Each player automatically gets an account on first join (balance starts at 0).
  - Added a dedicated **Treasury account** for server-side operations.
- All balances and transactions are saved as part of the mod’s economy data and are written on world save / clean shutdown.

### New – Transaction & Ledger Model
- Every movement of money is now stored as a **typed transaction**:
  - `PLAYER_PAYMENT`, `ADMIN_GRANT`, `ADMIN_SEIZE`, plus Treasury operations.
- Added helper rules:
  - No negative balances allowed.
  - Zero or negative transfer amounts are rejected with clear error messages.
  - Transfers to offline players are supported (by UUID).
  - Self-payments are blocked as invalid.
- Introduced internal helpers to convert between **copper ↔ Gold/Silver/Copper** for both storage and display.

### New – Player Commands
- **`/money`**  
  - `/money` or `/money balance` shows **your balance** in compact G/S/C format, e.g.  
    `Balance: 1G 4S 50C Scales`.
- **`/money pay <player> <amount>`**  
  - Pays another player from your account.
  - `<amount>` accepts:
    - Plain copper: `150` (150C).
    - Coin strings: `10g`, `5s`, `20c`, or combinations like `1g 4s 50c`.
  - Command validates:
    - Target exists.
    - Amount is > 0.
    - Sender has enough funds.
  - Both sender and receiver get coloured chat messages showing **full G/S/C breakdown**.

### New – Admin / Operator Commands
> Replaces the old `/ledger` namespace to avoid conflicts with other mods.

- **`/moneyadmin grant <player> <amount>`**  
  - Mints currency directly into a player’s account.  
  - Uses the same G/S/C amount syntax as `/money pay` (`1g 4s 50c`, `250`, etc.).
  - Logged as an `ADMIN_GRANT` transaction.
- **`/moneyadmin seize <player> <amount>`**  
  - Removes money from a player’s account (to Treasury or burn, depending on config / rule).  
  - Same G/S/C amount syntax.  
  - Logged as `ADMIN_SEIZE`.
- **`/moneyadmin treasury`**  
  - Shows the **Treasury account balance** in G/S/C.
- **`/moneyadmin log [count]`**  
  - Shows the latest N transactions (default ~10–20), each line with:
    - Timestamp.
    - Amount in Gold/Silver/Copper (e.g. `+1G 4S 50C Scales`).
    - From → To.
    - Transaction type tag (payment / grant / seizure / treasury op).
- **`/economy help`**  
  - Lists all economy-related commands and basic usage.
- **`/economy reload`**  
  - Reloads the economy config (currency name, conversion rates, etc.) without restarting the server.

### New – G-Key Economy Panel (Client UI)
- Bound a new **G-key panel** (client-side) that talks to the server via Fabric networking:
  - Opens/closes with **G**.
  - **Header**:
    - Player name (or seat label).
    - Current balance in **G/S/C** with the configured currency name.
  - **Ledger tab**:
    - Title: “Recent Transactions”.
    - Displays the last 5–10 transactions relevant to that player, each showing:
      - Signed amount in G/S/C (e.g. `+1G 4S 50C`).
      - Counterparty (“from System”, “to Treasury”, other player names, etc.).
      - Transaction type in brackets (`(payment)`, `(grant)`, `(seizure)`).
- Visuals:
  - Removed the blurred world background; the panel now uses a **solid dark rect** for readability while still letting the world be faintly visible around it.
  - All text colours are tuned so they remain readable even at dusk/night in-game.

### Internal / Technical
- Added dedicated **economy networking payloads** for snapshot requests:
  - Client sends a “snapshot request” when the G-panel opens.
  - Server responds with balance + recent transactions as a compact payload.
- Centralised all economy logic into:
  - `EconomyAccount`, `EconomyStore`, `EconomyTransaction`, `EconomyService`, `EconomyConfig`, `EconomyNetworking`, and client-side `ClientEconomyState`.
- Safe-guarded network registration to avoid duplicate payload registrations during dev reloads.

---

This version is the **Phase 1 foundation** of the economy: a single configurable currency, proper accounts, transaction logging, admin tools, and a first in-game UI. No decree / council features were removed; this update is fully additive.


## [0.1.3] – Ceremony & UX polish

- Added **configurable council ceremony sound** via `ceremonySound` in `council.json`.  
  - Supports custom sound events like `decrees_of_the_six:council_chime` (e.g. ElevenLabs chime bundled as `council_chime.ogg`).
  - Plays to all online players when the council is created or reconvened, without chat spam.
- Standardized decree messaging through `Messenger` with a consistent `[Hexarchate]`-style prefix.
- Added clickable vote hints when a decree enters voting:
  - Council members now see `Cast your vote: [Yes] [No] [Abstain]` buttons, which prefill `/decrees decree vote <id> <yes/no/abstain>`.
- Improved listing & browsing:
  - `/decrees decree list status <draft|voting|enacted|rejected|cancelled> [page]` to filter by status.
  - `/decrees decree list active [page]` and `/decrees decree list my [page]` now support pagination.
- Updated `/decrees help` to reflect all current commands and subcommands.

## [0.1.2] - 2025-12-09

### Added
- **Council activation ceremony**
  - `/decrees council create <name>` now:
    - Sets the council name and binds the decree system to it.
    - Enables decrees and switches back to council-mode (not ops-only).
    - Broadcasts a realm-wide announcement, plays a toast sound to all players, and launches fireworks at the executor’s position.

- **Config & mode controls**
  - `/decrees config decreesEnabled on|off` – globally enable/disable the decrees system.
  - `/decrees config opsOnly on|off` – restrict decree creation/editing/voting to operators.
  - `/decrees config show` – show current system status, your seat (if any), and active decrees.

- **Seat administration**
  - `/decrees seat list` – list all defined council seats and their current holders.
  - `/decrees seat set <seat_id> <player>` – assign a player to a seat (one seat per player enforced).
  - `/decrees seat clear <seat_id>` – clear the holder of a seat.

- **Decree history & pagination**
  - `/decrees decree list [page]` – paginated list of all decrees with a compact one-line summary:
    - `[#7] [VOTING] On Guild Levies – opened by Overseer-Regent – Y:3 N:1 A:0`
  - `/decrees history [page]` – paginated view of completed decrees for season history.

- **Decree statistics**
  - `/decrees stats seats` – per-seat overview of created decrees and voting behaviour.
  - `/decrees stats me` – stats for the caller’s own seat.
  - `/decrees stats seat <seat_id>` – stats for a specific seat.

### Changed
- **Unified messaging / UX**
  - All command output now goes through a `Messenger` helper for consistent prefixing and styling.
  - Status labels are color-coded everywhere:
    - `DRAFT`, `VOTING`, `ENACTED`, `REJECTED`, `CANCELLED`.
  - Key broadcasts use clear, compact lines like:
    - `§6[Hexarchate] §eDecree #7 (On Guild Levies) is now in §e[VOTING]§e. Cast your vote with §b/decrees vote 7 <yes/no/abstain>.`

- **Command behaviour**
  - `/decrees decree open <id>`:
    - Sets `votingOpenedAt` and `votingClosesAt` based on `voting_rules.json`.
    - Uses `DecreeStore.setStatus(..., VOTING, "opened for voting by <name>")` so history is logged consistently.
  - `/decrees decree delete <id>`:
    - Now uses a 2-step confirmation flow per source (`delete` → `delete <id> confirm`), tracked per-sender.
  - `/decrees vote <id> yes|no|abstain`:
    - Recomputes quorum/majority after each vote.
    - Automatically finalises a decree when:
      - All active seats have voted, or
      - The voting period has expired.
    - Broadcasts final state and notifies council seats.

- **Auto-close logic**
  - `tickAutoClose(...)` now:
    - Respects per-decree `votingClosesAt` (with a fallback to duration from `votingOpenedAt`).
    - Applies the same quorum/majority/tie rules as `/decrees vote`.
    - Finalises the decree via `DecreeStore.setStatus(...)` and broadcasts the result.

### UX & Tab Completion
- Added Brigadier suggestion providers for:
  - Decree IDs on all `<id>` arguments.
  - Seat IDs for `/decrees seat ...` and seat-based stats.
  - Existing categories for `/decrees decree list category <category>`.
- Introduced more compact list formatting and short guidance hints (e.g. “Use `/decrees decree list 2` for the next page.”).

### Safety
- Mutating commands now respect global flags:
  - If the system is disabled or in `opsOnly` mode, non-eligible users get clear error messages.
- Destructive operations (delete, force) have extra checks and error feedback for illegal or redundant state changes.


## [0.1.1] – Council Awakens

- Added `/decrees council create <name>` to ceremonially convene the council:
  - Enables the decrees system and switches to council mode.
  - Broadcasts a global message with the chosen council name.
  - Launches fireworks at the executor’s position.
- Added council name support in prefixes (e.g. `[Hexarchate • Decrees]` instead of plain `[Decrees]`).
- Improved join reminder:
  - Now shows per-seat pending decrees and up to 5 decree IDs.
- Minor code cleanup and internal polish.


## [0.1.0] - 2025-12-09

### Added
- Initial public alpha release of **Decrees of the Six**.
- `/decrees` root command with subcommands for council and decree management.
- Council seat system defined in `config/decrees/council.json`:
  - Each seat has an `id`, `displayName`, and optional `holderUuid`.
- Global council flags in `council.json`:
  - `decreesEnabled` – master switch to enable/disable the decree system.
  - `opsOnly` – when true, only server operators can create/open/delete/vote on decrees.

- Decree lifecycle:
  - Create decrees with `/decrees decree create <title>`.
  - Inspect decrees with `/decrees decree info <id>`.
  - List all / own / active decrees:
    - `/decrees decree list`
    - `/decrees decree list my`
    - `/decrees decree list active`
  - Edit fields via `/decrees decree edit title|description|category|expiry`.
  - Open decrees for voting with `/decrees decree open <id>`.
  - Delete decrees with `/decrees decree delete <id>` (council only).

- Voting system:
  - `/decrees vote <id> yes|no|abstain` for council seats.
  - Voting rules configurable in `config/decrees/voting_rules.json`:
    - `minQuorumPercent`
    - `votingDurationMinutes`
    - `majorityMode` (`SIMPLE` or `TWO_THIRDS`)
    - `tiesPass` (true/false)
  - Automatic resolution to `ENACTED` or `REJECTED` when:
    - Quorum is reached **and**
    - Either all active seats have voted or the voting duration has expired.

- Operator safety tools:
  - `/decrees decree force <id> enacted|rejected` to override decree status (ops only).
  - `/decrees reload` to reload `council.json` and `voting_rules.json` live.

- Council utilities:
  - `/decrees seat list` – list all seats and their current holders.
  - `/decrees seat set <seat_id> <player>` – assign a player to a seat (ops only).
  - `/decrees seat clear <seat_id>` – clear the holder of a seat (ops only).
  - `/decrees decree results <id>` – detailed results: yes/no/abstain, quorum status, timer.
  - `/decrees decree list active` – shows all decrees currently in `VOTING`, including your own vote if you hold a seat.

- Basic decree history logging:
  - Finalised decrees (`ENACTED` / `REJECTED`) are written to a plain-text history log via `DecreeHistoryLogger`.
  - Intended for season/roleplay records (e.g. `config/decrees/decrees_history.log`).

### Changed
- N/A – initial release.

### Fixed
- N/A – initial release.
