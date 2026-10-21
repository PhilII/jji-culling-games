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

Name ideas from the anime and manga: **Tokyo No. 1**, **Tokyo No. 2**, **Sendai**, **Sakurajima** (all confirmed). Unconfirmed but used by fans: Lake Gosho (Iwate), Aomori, Aichi, Kyoto, Osaka, Hiroshima. Names are one word in the command, e.g. `tokyo_no_1` or `Sendai`.

## 3. Stock Kogane's shop (optional)
Hold the item (with the stack size you want to sell) and run:
```
/cg shop add 50
```
That sells the held stack for 50 points. `/cg shop` lists items with their numbers; `/cg shop remove <id>` removes one (later items move up a number).

Always in the shop, priced in the config:
- **Services**: leaderboard, transfer, kill log (100 points each, unlocked for the rest of the event).
- **Rules** (100 points, apply to everyone): reveal_points, double_points, longer_timer (+10 min), free_transfers.
- **Cash-out**: points to Lightman's coins, 5 points per copper coin, at most 10 coins per player per event (`pointsPerCoin`, `maxCoinsPerEvent`). One way only. Works during the event and in the reward shop after it (Coins tab, or `/cg cashout <coins>`).

The event shop and rules only work **while the event runs**. Leftover points go to the reward shop after the event (section 6) and are cleared when the next event starts.

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
If a participant's points don't change within the timer (default 19 min of their own online time; Kogane warns at 5 and 1 min left). The timer stands still while a player is logged out, so a weekend event can run overnight without sealing everyone who went to bed:
- `seal_ct`: their cursed energy is drained to 0 until they score again. The CT itself is never changed, so unique CTs are safe.
- `lose_life`: they lose an event life.

At 0 event lives a player becomes a spectator. Event deaths never cost real server lives.

### Red's rules (0.13.0)
All on by default; every number is a setting (see 5).
- **Tracker**: every participant carries Kogane's Tracker, a compass pointing to the nearest other participant (updates every 2 s; spins when nobody is in the same dimension). It can't be dropped or kept: it vanishes when tossed or on death, comes back, and is taken at the end.
- **Kill bonus**: a participant kill gives the killer +500 cursed energy (up to their max) and 45 s of Jujutsu Craft's Zone effect (`jujutsucraft:zone`, the Black Flash state).
- **Top 3 revealed**: every 10 min of event time Kogane tells everyone which colony the 3 players with the most points are in (points stay hidden).
- **Hunted**: a participant with 0 points and no kill or assist glows for 30 s once per Minecraft day (20 min of event time). A kill or an assist (damage on a player within 15 s before someone else kills them) ends it; dropping back to 0 points makes them hunted again.
- **Heal / cursed energy**: in the shop (Services tab, or `/cg buy heal` / `/cg buy ce`) a full heal costs 1 point per missing heart and a full cursed energy refill 1 point per 200 missing, +25% per level of fatigue. During a battle (hit by or hitting a participant in the last 15 s) one purchase per battle; outside battle a 10 s wait.

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
| `pointsPerCoin` | 5 | points per coin when cashing out |
| `maxCoinsPerEvent` | 10 | coins per player per event (0 = no limit) |
| `rewardShopOpen` | true | reward shop open after the event |
| `maxFragmentsPerEvent` | 2 | weapon fragments per player per event |
| `specialGradePrice` / `fragmentPrice` | 220 / 60 | prices of the default rewards (until set with `/cg reward set`) |
| `trackerCompass` / `trackerUpdateSeconds` | true / 2 | tracker compass on/off, seconds between updates |
| `killBonusCe` / `killBonusCeOverMax` | 500 / false | cursed energy per kill (0 = off); allow going above max |
| `killZoneSeconds` / `killZoneLevel` / `killZoneEffect` | 45 / 1 / `jujutsucraft:zone` | Zone after a kill (0 s = off) |
| `killBonusWeakKills` | false | mob kills also give the CE and Zone bonus |
| `revealTopCount` / `revealIntervalMinutes` | 3 / 10 | top players whose colony is announced, how often (0 = off) |
| `huntedEnabled` / `huntedGlowSeconds` / `huntedIntervalMinutes` | true / 30 / 20 | hunted glow, its length, how often |
| `assistSeconds` | 15 | damage this recent before a kill counts as an assist |
| `recoveryEnabled` | true | heal and CE refill in the shop |
| `healPointsPerHeart` / `cePerPoint` / `fatigueCostPercent` | 1 / 200 / 25 | recovery prices |
| `battleSeconds` / `recoveriesPerBattle` / `recoveryCooldownSeconds` | 15 / 1 / 10 | what a battle is, purchases per battle, wait outside battle |

## 6. Reward shop (after the event)
When an event ends, every participant keeps the points they had left. Until the next event starts, they can spend them in **Kogane's reward shop**: press **K**, or type `/cg rewards`. Starting the next event wipes leftover points.

Default rewards (added automatically when Jujutsu Craft is installed):
- **Special Grade promotion** (220 pts, `specialGradePrice`): grants `jujutsucraft:sorcerer_grade_special`. Needs Grade 1, once per player, refused without charge if already Special Grade.
- **Weapon fragments** (60 pts each, `fragmentPrice`) for the 7 Special Grade tools. A full set gives the real weapon: Inverted Spear of Heaven 6, Playful Cloud 4, Split Soul Katana 5, Black Rope 4, Kamutoke 6, Hiten 5, Sword of Extermination 6. Fragments are stored per player (not items, so they can't be traded or faked) and carry over between events. A player can buy at most 2 fragments per event (`maxFragmentsPerEvent`).

Add your own:
```
/cg reward add item apples 15                      # hold the stack the reward gives
/cg reward add command speed_boost 40 effect give %player% minecraft:speed 600 1
/cg reward set speed_boost name Speed boost (10 min)
/cg reward set apples perplayer 1                  # max per player per event
/cg reward set speed_boost stock 5                 # max sold per event
/cg reward set speed_boost once true               # once per player, ever
/cg reward set speed_boost requires jujutsucraft:sorcerer_grade_2
/cg reward set speed_boost icon                    # hold an item to use as the icon
/cg reward list | remove <id> | defaults
```
`%player%` is replaced with the buyer's name. If a command fails, the player is not charged. A command reward can give a weapon with effects, e.g. `give %player% minecraft:netherite_sword{Enchantments:[{id:"sharpness",lvl:6}]}`.

If a player's inventory is full, Kogane keeps the reward and hands it over when there is room (or at next login). Nothing is dropped on the ground. Check or fix fragments with `/cg reward fragments <player> [weapon] [count]`. Close the shop with `/cg config rewardShopOpen false`.

## Checklist for a first event
1. Jar in `mods/`, server restarted.
2. Two or more colonies, checked with `/cg colony tp`.
3. Shop stocked (optional), settings checked with `/cg config`.
4. Admins in creative, players in survival.
5. `/cg start`, watch `/cg status`, end with `/cg stop 10`.

Problems or ideas: post in #general on the dev Discord.
