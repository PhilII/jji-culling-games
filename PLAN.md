# Culling Games mod — master plan

This file is the source of truth for the build. Every session starts by reading it and updating the status table.

- **Server**: Jujutsu Incident (JJI), Forge 1.20.1 (47.4.10), Java 17, ServerMiner.
- **Mod**: id `cullinggames`, one jar for client and server.
- **Owner**: PhilL. Design input: Red (scope), BigMann (features).
- **Repo location**: `~/dev/culling-games` (moved 16 Oct out of `Desktop/CODE/Fujiwara`, which is iCloud-optimized: slow reads and once a file truncated to 0 bytes during checkout). Keep it outside Desktop/Documents.
- **Design source**: `~/Desktop/CODE/Fujiwara/drafts/culling-games/spec-v1.md` (outside this repo, kept local).

## Status

| Step | Name | Status | Branch | Checkpoint tag |
|---|---|---|---|---|
| 0 | Repo + project setup | done | `setup/forge-mdk` | `v0.0-setup` |
| 1 | Event skeleton | done | `step1/event-core` | `v0.1-event-core` |
| 2 | Colonies + barrier | done | `step2/colonies` | `v0.2-colonies` |
| 3 | Points + kill log | done | `step3/points` | `v0.3-points` |
| 4 | Timer + penalty + event lives | done | `step4/penalty` | `v0.4-penalty` |
| 5 | Kogane + shop | done | `step5/kogane-shop` | `v0.5-kogane-shop` |
| 6 | Client HUD + screens | done | `step6/client` | `v0.6-client` |
| 7 | Polish + release | in progress (release waits for Red) | `step7/release` | `v1.0.0` |

## Decided

- **Event**: admin starts and stops it. Not always on.
- **Free-for-all**: no parties, teams or factions protect anyone inside colonies.
- **Penalty** (BigMann's idea): if your points don't change within the timer (default 19 min), the admin-chosen penalty applies per event: CT sealed until your points change again (default), or lose an event life. Any sealed CT is restored when the event ends.
- **Lives**: event deaths do not cost real server lives. Admins set event lives.
- **Kogane shop**: admins stock it. Points convert to Lightman's money, never the other way.
- **Art**: placeholders first (Kogane, HUD, sounds).
- **How the CT seal works** (tested 16 Oct): the seal drains cursed energy to 0 every tick and gives the saved amount back on unseal. Jujutsu Craft's CT fields are never written. Reason: JJC treats CT 0 as "re-roll a random CT on respawn" and CT -1 as "non-sorcerer" (and can grant advancements on respawn), and Alm ownership / ReBalanced key off the CT, so changing it risks losing unique CTs.
- **Integration**: Jujutsu Craft, ReBalanced and Lightman's Currency are soft dependencies, read at runtime. We never copy their code (all are "All Rights Reserved").

## The seven steps

### Step 1 — Event skeleton
Goal: an event an admin can start, stop and inspect, that survives a restart.
- `/cg start`, `/cg stop`, `/cg pause`, `/cg resume`, `/cg status` (op level 2+).
- Event state in world `SavedData`: phase (IDLE, RUNNING, PAUSED, ENDED), start time, participants.
- Server config (TOML) with every tunable value (timer, points, lives, warnings).
- Join/leave handling: who is a participant, late joiners.
- **Done when**: start → restart server → status still RUNNING; stop returns to IDLE cleanly.

### Step 2 — Colonies + barrier
Goal: players are placed in colonies and cannot walk out during the event.
- `/cg colony add <name> <center> <radius>` (and box by two corners), `remove`, `list`, `tp`.
- Colonies stored in SavedData, per dimension.
- On start: spread participants across colonies (even split, random spot inside, safe ground).
- Barrier: if a participant is outside their colony during RUNNING, push back in (checked a few times per second, not every tick for every player; server has 4 GB RAM).
- Entering/leaving message per colony.
- Later option: import City Districts polygons.
- **Done when**: 2+ colonies, start spreads players, nobody can walk or pearl out, admins are exempt.

### Step 3 — Points + kill log
Goal: kills give points; points are private; every kill is logged.
- Player kill = 5 points, weaker target (non-player / lower value) = 1. Values in config.
- Points per participant in SavedData. A player only sees their own points.
- Kill log: killer, victim, time, position, points. Admin command to view; buyable access comes in step 5.
- Admin: `/cg points <player> get|set|add|remove`.
- Every point change resets that player's penalty timer (used in step 4).
- **Done when**: kills give the right points, log is correct after restart, players can't see others' points.

### Step 4 — Timer + penalty + event lives
Goal: inactivity is punished as decided, and everything is restored afterwards.
- Per-player timer: time since last point change. Default 19 min, configurable.
- Kogane warnings at 5 min and 1 min left (chat/title for now).
- Penalty mode per event (admin picks at start): `SEAL_CT` (default) or `LOSE_LIFE`.
- **CT seal**: save the player's CT state from all three places (Jujutsu Craft `jujutsucraft:player_variables` → `PlayerCurseTechnique`/`PlayerCurseTechnique2`, ReBalanced overrides/ct2, Alm ownership), set CT to "none", sync to client. Unseal when points change again or the event ends.
- **First task in this step**: test on a local server which value means "no CT" and that restore is exact. Do not build the seal before this test passes.
- Event lives: admin sets the count; at 0 → spectator mode until the event ends.
- Event end: unseal everyone, restore gamemode, teleport out.
- **Done when**: seal/unseal round-trips with no data loss, including across a server restart mid-seal.

### Step 5 — Kogane + shop
Goal: Kogane runs the event and sells things for points.
- Kogane as placeholder: chat prefix, titles, bossbar (event time left, own points).
- Shop GUI: admins add items with point prices (`/cg shop add|remove|list`).
- Buyables: leaderboard view, point transfer, kill-log access, add a rule (100 points).
- Rules: a fixed menu of rules the code can enforce. Starter list drafted for Red to adjust.
- Points → Lightman's money conversion (soft dependency; disabled if the mod is missing).
- Warning before the event ends so players spend their points.
- **Done when**: shop buys work, transfers can't duplicate points, conversion is one-way.

### Step 6 — Client HUD + screens
Goal: players see what matters without commands.
- HUD: own points, timer, current colony.
- Shop screen and rule menu screen.
- Network sync server → client (server stays authoritative; client can't cheat).
- Placeholder textures and sounds.
- **Done when**: HUD updates live, works on a dedicated server with several clients.

### Step 7 — Polish + release
Goal: a jar the server can run in a real event.
- End rewards (if Red wants them).
- Remaining admin commands, help text, permissions review.
- Performance check with spark on the dev server.
- Test with the full JJI modpack, then upload to ServerMiner (ask PhilL before upload).
- Later: publish on CurseForge when the team is happy.
- **Done when**: one full test event runs start to finish on the real modpack.

## Full-modpack test server
- `~/fujiwara-local-server` (outside iCloud on purpose: Desktop/Documents files are iCloud placeholders and very slow to read). Full FTP mirror of the server incl. the map, Forge 47.4.10 installed, port 25567, RCON 25576, offline mode, `server-ip` cleared, our jar in `mods/`.
- Start: `cd ~/fujiwara-local-server && ./run.sh nogui` (JDK 17 on PATH).
- 16 Oct: all 96 mods + ours load on the real map; colony/start/stop work; spark median 5.8 ms/tick idle.

## Open questions (do not block work)
- Red: are the 4 starter rules right (reveal_points, double_points, longer_timer, free_transfers)? Prices (leaderboard 10, transfer 10, kill log 20, rule 100) and coin per point are config.
- Do Jujutsu Craft shikigami/summons count as `OwnableEntity`? If not, their kills don't credit the owner yet. Check in the real modpack.
- Which JJC entities are "weak targets"? Default: every hostile mob. Extra ids can go in `weakTargetsExtra`.
- Red: final OK on scope; Kogane bossbar; end rewards; which rules can be bought.
- Is JujutsuAlm still on the server? (configs exist, no jar found)
- Which CT value means "no CT" (step 4 test).

## Workflow and checkpoints
- `main` is always buildable. Never commit directly to `main` after setup.
- One branch per step (see table). Small commits with clear messages.
- Push after every meaningful commit. Open a PR when a step (or a clear part of it) works. Merge to `main` when it builds and is tested.
- After each merged step: tag the merge commit (`v0.N-name`) and push the tag. These tags are the checkpoints to go back to.
- Update the status table in this file in the same PR that finishes a step.
- Never commit secrets, server files, world data or other mods' jars.

## Build
- JDK 17 (Homebrew): `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- `./gradlew build` → `build/libs/cullinggames-<version>.jar`
- If a build hangs on downloads, add `-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=120000`.

## Testing with Jujutsu Craft
- Copy the server's jars into `libs/` (git-ignored): `JujutsuCraft-ver51-forge-1.20.1.jar`, `geckolib-forge-1.20.1-4.7.2.jar`, `player-animation-lib-forge-1.0.2-rc1+1.20.jar` from `~/fujiwara-local-server/mods/` (fast, outside iCloud) or `~/Desktop/CODE/Fujiwara/server-copy/mods/`.
- `build.gradle` deobfuscates every jar in `libs/` onto the dev runtime only. The mod never compiles against them.
- Without `libs/`, the build and tests still run; JJC-specific checks are skipped.
- Lightman's Currency can't be loaded in the dev test server (it loads a client class on the dedicated server). Cash-out is tested for the "coin item missing" path only; test real coins on the server.

## Local test server
- `run/` is git-ignored. EULA accepted by PhilL for local testing (16 Oct). RCON on 127.0.0.1:25575, password in `run/server.properties`.
- `./gradlew runServer`, then send commands over RCON. Console commands run as permission level 4.
- `./gradlew runClient -PjoinLocal` starts a dev client (user "Dev") that joins the local server. Add `-PselfTest=<shop id>` to make it buy that item through the real packet path ~10 s after joining. Run `execute as Dev run cg shop` over RCON to open the shop screen.

## Automated tests
- Run `./scripts/gametest.sh` before every PR. It runs `./gradlew runGameTestServer` and checks the log for "All N required tests passed". Gradle alone exits 0 even when the test server crashes on startup.
- Tests use the `cullinggames:empty` structure template. Don't use `GameTestHelper.makeMockServerPlayerInLevel()` (crashes on Forge 1.20.1); use the EmbeddedChannel test player in `ColonyGameTests`.
- Tests that touch global state (event, colonies) go in their own `batch` so they never run at the same time.
