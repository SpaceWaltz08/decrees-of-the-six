# Changelog

All notable changes to this project will be documented in this file.

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
