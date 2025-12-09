# Changelog

All notable changes to this project will be documented in this file.

This project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
