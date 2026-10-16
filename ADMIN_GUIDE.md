# Culling Games: admin guide

For server admins (op level 2+). Players only need `/cg help` and the **K** key.

## 1. Install
1. Download `cullinggames-<version>.jar` from [Releases](https://github.com/PhilII/jji-culling-games/releases).
2. Put it in the server's `mods/` folder. Players should add the same jar to their client for the HUD and shop screen. Players without it can still join; they get a bossbar and use chat commands.
3. Restart the server. The config file appears at `world/serverconfig/cullinggames-server.toml`.

Jujutsu Craft and Lightman's Currency are optional. Without Jujutsu Craft the CT seal does nothing; without Lightman's Currency cash-out is off.

## 2. Set up colonies (once)
Colonies are the areas players are spread into and can't leave. Make at least two.

Stand in the dimension you want, then:
```
/cg colony add shibuya circle 100 -200 80
/cg colony add sendai box 400 400 520 560
```
- `circle <x z> <radius>`: centre and radius (3–5000).
- `box <x1 z1> <x2 z2>`: two corners, at least 5×5.
- Colonies cover the full height. Name = one word.
- `/cg colony list`, `/cg colony tp <name>`, `/cg colony remove <name>` (only when no event runs).

Colonies are saved with the world and survive restarts.

## 3. Stock Kogane's shop (optional)
Hold the item (with the stack size you want to sell) and run:
```
/cg shop add 50
```
That sells the held stack for 50 points. `/cg shop` lists items with their numbers; `/cg shop remove <id>` removes one (later items move up a number).

Always in the shop, priced in the config:
- **Services**: leaderboard, transfer, kill log (100 points each, unlocked for the rest of the event).
- **Rules** (100 points, apply to everyone): reveal_points, double_points, longer_timer (+10 min), free_transfers.
- **Cash-out**: points to Lightman's coins (1 copper per point by default). One way only.

The shop, rules and cash-out only work **while the event runs**. Points are cleared when the next event starts, so warn players to spend them.

## 4. Run an event
```
/cg start                     # default penalty and lives from the config
/cg start seal_ct 3           # CT seal penalty, 3 event lives
/cg start lose_life 2         # inactivity costs a life, 2 lives
```
On start, everyone online in **survival or adventure** joins and is spread across the colonies. Admins in **creative or spectator** are left out and are not held by the barrier, so switch to creative before starting if you only want to watch.

During the event:
| Command | What it does |
|---|---|
| `/cg status` | phase, running time, participants, penalty, sealed, out, active rules |
| `/cg pause` / `/cg resume` | freeze/unfreeze the timers; the barrier stays up and the shop closes |
| `/cg participant add\|remove <player>` | add a late player or take someone out |
| `/cg points get\|set\|add\|remove <player> [n]` | fix points |
| `/cg killlog [count]` | recent kills: killer, victim, time, place (kill proof) |
| `/cg stop 10` | end in 10 minutes; Kogane warns everyone to spend points |
| `/cg stop` | end now |

Players who log in mid-event join automatically (`allowLateJoin`).

### Penalty
If a participant's points don't change within the timer (default 19 min; Kogane warns at 5 and 1 min left):
- `seal_ct`: their cursed energy is drained to 0 until they score again. The CT itself is never changed, so unique CTs are safe.
- `lose_life`: they lose an event life.

At 0 event lives a player becomes a spectator. Event deaths never cost real server lives.

### When it ends
Every seal is lifted, spectators get their old game mode back, and participants are sent to world spawn (`teleportToSpawnOnEnd`). The event and kill log survive server restarts at any point.

## 5. Settings
Change in game (saved to the file right away):
```
/cg config                       # list every setting
/cg config timerMinutes          # show one with its description
/cg config killStealMode PERCENT
```

| Key | Default | Meaning |
|---|---|---|
| `allowLateJoin` | true | late joiners become participants |
| `teleportToSpawnOnEnd` | true | send participants to spawn at the end |
| `timerMinutes` | 19 | minutes without a point change before the penalty |
| `firstWarningMinutes` / `finalWarningMinutes` | 5 / 1 | Kogane's warnings |
| `defaultPenalty` | SEAL_CT | used when `/cg start` names none |
| `eventLives` | 3 | lives per participant |
| `playerKill` | 5 | points Kogane gives for a player kill |
| `killStealMode` | FLAT | also take points from the victim: FLAT, PERCENT or NONE |
| `killStealFlat` / `killStealPercent` | 5 / 25 | amount taken in each mode (victim never below 0) |
| `weakKill` | 1 | points for a hostile mob |
| `weakTargetsAllHostile` | true | every hostile mob counts; summons never do |
| `weakTargetsExtra` | [] | extra entity ids that count as weak targets |
| `ruleCost` | 100 | price of a rule |
| `leaderboardPrice` / `transferPrice` / `killLogPrice` | 100 | service prices |
| `longerTimerMinutes` | 10 | extra minutes from the longer_timer rule |
| `moneyItem` | `lightmanscurrency:coin_copper` | cash-out item; empty turns cash-out off |
| `moneyPerPoint` | 1 | items per point |

## Checklist for a first event
1. Jar in `mods/`, server restarted.
2. Two or more colonies, checked with `/cg colony tp`.
3. Shop stocked (optional), settings checked with `/cg config`.
4. Admins in creative, players in survival.
5. `/cg start`, watch `/cg status`, end with `/cg stop 10`.

Problems or ideas: post in #general on the dev Discord.
