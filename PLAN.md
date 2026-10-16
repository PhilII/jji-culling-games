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
| 7 | Polish + release (7b: reward shop) | in progress (0.12.0 pre-release on GitHub: JJK art, sounds, visible barriers, reward shop; Red decides about the server) | `step7/release` | `v1.0.0` |

## Decided

- **Event**: admin starts and stops it. Not always on.
- **Free-for-all**: no parties, teams or factions protect anyone inside colonies.
- **Penalty** (BigMann's idea): if your points don't change within the timer (default 19 min), the admin-chosen penalty applies per event: CT sealed until your points change again (default), or lose an event life. Any sealed CT is restored when the event ends.
- **Lives**: event deaths do not cost real server lives. Admins set event lives.
- **Kogane shop**: admins stock it. Points convert to Lightman's money, never the other way.
- **Art**: JJK look (PhilL, 16 Oct). Images made in ChatGPT on a flat magenta background; originals in `~/culling-games-assets/originals/` (outside the repo), turned into textures by `scripts/process_art.py`. 0.8.0 has: Kogane sprite (3 expressions), HUD icons, shop as Kogane with its green-screen menu open, 死滅回游 title card at start/end, talisman banner "Rule 9 added" (bought rules continue from the original 8), barrier veil icon, mod logo. 0.9.0 adds 12 custom sounds (original, synthesized by Codex from scratch, free to redistribute; see `docs/sound-credits.txt`), played client-side only so players without the mod still join and hear vanilla cues. 0.10.0 makes the barriers visible (PhilL, 16 Oct, from an anime reference: the barrier is pure black): each colony edge is drawn on the client as a black curtain from the bottom of the world to the build limit while an event runs.
- **Shop service prices** (final ruling relayed by PhilL, 16 Oct; BigMann's idea): leaderboard, transfers and kill log cost 100 points each, like rules.
- **Kill reward** (PhilL, 16 Oct): Kogane gives 5 and the killer takes 5 from the victim by default (victim loses them, never below 0, their timer is not reset). Mode FLAT / PERCENT / NONE and amounts are config values, changeable in the file or in game with `/cg config`.
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
- Rules: a fixed menu of rules the code can enforce. Red confirmed the 4 rules (15 Oct).
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
- Admin guide for Red: `ADMIN_GUIDE.md` (install, colonies, shop, running an event, settings). Done 16 Oct.
- Performance check with spark on the dev server.
- Test with the full JJI modpack, then publish the jar as a GitHub release. **We never upload to ServerMiner**: Red downloads the jar and decides whether and when it goes on the server.
- Later: publish on CurseForge when the team is happy.
- **Done when**: one full test event runs start to finish on the real modpack.

### Step 7b — Reward shop (end-of-event rewards)
Red (16 Oct): leftover points are spent in a shop after the event, e.g. a Special Grade grade-up, pieces of Special Grade weapons and weapon effects. Research by ChatGPT (17 Oct, files in `~/culling-games-assets/research/`); JJC facts checked against the local v51 jar. PhilL approved the plan on 17 Oct: build 1-4 first, then 5-7.

1. **Points survive the event.** At event end the points freeze into a reward balance per player, spendable until the next event starts. Unspent points expire then. No scoring or transfers after the end. Eliminated and offline players can still spend their own balance.
2. **Kogane's reward shop** (same green-screen design), open after the event. Two reward types, both added by admins:
   - item: the held stack, like the event shop;
   - command: a command run as the server for the buyer (`%player%` placeholder), e.g. a grade-up or a weapon with NBT. Lets Red add weapon effects himself.
3. **Default rewards with verified IDs** (JJC v51):
   - Special Grade grade-up: `/advancement grant %player% only jujutsucraft:sorcerer_grade_special`. Requires `jujutsucraft:sorcerer_grade_1`; once per player; refused (no charge) if already Special Grade.
   - The 7 Special Grade tools (tag `cursed_tool_grade0`): `inverted_spear_of_heaven`, `playful_cloud`, `split_soul_katana`, `black_rope`, `supreme_martial_solution` (Kamutoke), `hiten`, `sword_of_extermination`.
   - Weapon fragments: per-weapon fragment counts stored by the mod against the player's UUID (not physical items, so they can't be forged or traded). Full set redeems the real weapon: Playful Cloud 4, Split Soul Katana 5, Inverted Spear 6 (others configurable). Fragments persist across events.
4. **Limits**: stock per event, max per player per event, once per player ever. If the inventory is full the reward waits (pending) until there is room; rare rewards never drop on the ground. Every purchase is logged.
5. **Prices** (starting values, configurable in file and `/cg config`): supplies 1-25, mid 30-60, fragments 50-90, premium 200-240. Estimate: a normal player ends with 20-70 points, a top player 100-250. Tune after the first real event.
6. **Cash-out**: 5 points = 1 copper coin, max 10 copper per player per event (both configurable). Lightman coin IDs: `lightmanscurrency:coin_copper/iron/gold/emerald/diamond/netherite` (default 10 of one tier = 1 of the next).
7. **JJK flavour**: show the 8 canon rules (paraphrased) in the rules menu, bought rules continue from 9; Kogane lines from the research in announcements; admin guide suggests colony names (Tokyo No. 1, Tokyo No. 2, Sendai, Sakurajima verified; others marked unverified).

**Status 17 Oct:** 5-7 built in 0.12.0 (default prices follow `specialGradePrice`/`fragmentPrice`; cash-out 5 pts/coin, max 10 coins/player/event, also in the reward shop's Coins tab; original 8 rules in `/cg rules` and the Rules tab; Kogane lines in announcements; colony names in the admin guide). Fixed: popups were hidden behind a full chat. 1-4 built in 0.11.0 (reward shop, defaults, fragments, limits, pending delivery; GameTest `rewardShop`; Special Grade promotion checked in game: JJC sets PlayerLevel 20). Next: 5-7.

Not covered yet: which weapon effects JJC supports (no answer; Red can add them as command rewards). Prices are estimates until a real event.

## Full-modpack test server
- `~/fujiwara-local-server` (outside iCloud on purpose: Desktop/Documents files are iCloud placeholders and very slow to read). Full FTP mirror of the server incl. the map, Forge 47.4.10 installed, port 25567, RCON 25576, offline mode, `server-ip` cleared, our jar in `mods/`.
- Start: `cd ~/fujiwara-local-server && ./run.sh nogui` (JDK 17 on PATH).
- 16 Oct: all 96 mods + ours load on the real map; colony/start/stop work; spark median 5.8 ms/tick idle.

## Open questions (do not block work)
- Red: final OK on scope. End rewards answered 16 Oct (see step 7b); full reward list and prices still his call.
- Which JJC entities are "weak targets"? Default: every hostile mob except summons. Extra ids can go in `weakTargetsExtra`.
- Resolved 16 Oct (Red): JujutsuAlm is out of the pack (not ReBalanced, which stays). No Alm handling needed.
- Resolved 16 Oct: JJC summons carry `OWNER_UUID` (not `OwnableEntity`); handled. The seal drains CE instead of changing the CT, so no "no CT" value is needed.

## Workflow and checkpoints
- `main` is always buildable. Never commit directly to `main` after setup.
- One branch per step (see table). Small commits with clear messages.
- Push after every meaningful commit. Open a PR when a step (or a clear part of it) works. Merge to `main` when it builds and is tested.
- After each merged step: tag the merge commit (`v0.N-name`) and push the tag. These tags are the checkpoints to go back to.
- Update the status table in this file in the same PR that finishes a step.
- Never commit secrets, server files, world data or other mods' jars.
- Releases are GitHub releases with the jar attached. Nothing is ever uploaded to the game server from here (FTP is download-only).

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
