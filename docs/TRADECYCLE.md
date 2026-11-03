# Librarian trades (`@trades`, MC 26.3)

Index the librarians around you, see their books through walls, and reroll novice librarians until every
wanted enchanted book is locked in at an acceptable (optionally the best possible) price.

Only the 26.3 build has it. On other Minecraft lines `@trades` exists but scan/cycle/overlay report that
they need 26.3.

## Commands

| command | what it does |
|---|---|
| `@trades scan [radius]` | Walks to every loaded librarian within `radius` (default 32), opens its trade screen, reads all offers, closes it, and stores the result. Indexed villagers in that radius that were not found become `MISSING` (they are kept). |
| `@trades list` | Compact table: status, UUID prefix, book + base price (`BEST` if at the best price), position, distance. |
| `@trades owned` | Every book offered by a `LOCKED` librarian. |
| `@trades missing` | Wanted books not owned yet. |
| `@trades forget <uuid-prefix\|all>` | Drop villagers from the index. |
| `@trades want add <enchant> [minLevel] [maxPrice\|best]` | Add/replace a want. `minLevel` defaults to the enchantment's max level, `maxPrice` to 64. `best` sets maxPrice to the vanilla minimum for that level. |
| `@trades want remove <enchant>` / `want list` / `want clear` | Manage the list. |
| `@trades want all [best]` | One want per enchantment in the game's `#minecraft:tradeable` tag (read from the live registry), at its max level; `best` = lowest possible price. |
| `@trades tolerance <n>` | Extra emeralds accepted over the best price for `best` wants (default 0). |
| `@trades upgrade on\|off` | Default for upgrade mode (see below). |
| `@trades cycle [radius] [upgrade]` | Reroll + auto-lock (see below). `@stop` stops it and prints the summary. |
| `@trades overlay` / `@trades gui` / `@trades reload` | Toggle overlay, open the index screen, reread `tradecycle.json`. |

Enchantments accept short names (`mending`, `fire_aspect`, `"Fire-Aspect"`) or full ids (`minecraft:mending`).

## Keybinds (Controls → TenorClef)

- `O` toggles the 3D overlay: outline box per indexed villager (green = LOCKED with a wanted book, blue = LOCKED
  other, yellow = REROLLABLE, grey = UNKNOWN/MISSING), label with books (`Mending I – 12 em`, `BEST`) and lock
  state, and a thin line to the linked lectern. Drawn through walls within `overlayDistance` (default 96).
  Labels are rebuilt only when the index or config changes.
- `P` opens the index screen: owned / wanted / missing summary on top, table sortable by enchantment, price,
  distance or status (click the sort buttons, scroll the list).

## Status

- `LOCKED`: the trade screen showed XP > 0 or level > novice. Trades can no longer change; its lectern is never broken.
  LOCKED is sticky in the index.
- `REROLLABLE`: novice with 0 XP.
- `UNKNOWN`: not scanned yet, or the last read failed.
- `MISSING`: indexed, but not found on the last scan of its area.

"Owned" books are all book offers of LOCKED villagers. A want is satisfied when a LOCKED villager sells that
enchantment at `minLevel` or higher for at most its max price (plus tolerance for `best` wants).

## Prices

From the 26.3 data and code (`villager_trade/librarian/*/…enchanted_book.json`, `EnchantRandomlyFunction`,
`VillagerTrade`, `TradeCost`): base cost = `2 + random(0 .. 4 + 10·L) + 3·L` emeralds, doubled for enchantments in
`#minecraft:double_trade_price` (= `#minecraft:treasure`: Mending, Frost Walker, Soul Speed, Swift Sneak, Wind Burst,
curses), clamped to 64. So the best price is `(2 + 3·L)`, or twice that for treasure: Mending I = 10,
Sharpness V = 17, Efficiency V = 17, Unbreaking III = 11, Protection IV = 14. Matching uses the offer's base price;
reputation and demand discounts are shown in the index but ignored for matching.

## Cycle

For each REROLLABLE (or not yet read) librarian in range, nearest first:

1. Open the trade screen and read the offers (also refreshes level/XP).
2. If an offer matches a wanted book that is not owned yet: buy it once (needs the emeralds + a book; if they are
   missing the cycle stops and says what is needed), re-open to confirm XP > 0, mark the villager LOCKED.
   That book is then owned, so no other villager is locked for it.
3. Otherwise: live re-check (entity is a novice librarian, index REROLLABLE with level 1 / XP 0 read moments ago,
   lectern is not linked to a LOCKED villager and no LOCKED librarian stands closer to it), equip the best axe,
   break the lectern, pick up the drop, place it back, wait for the villager to lose and regain the librarian
   profession (`professionTimeoutSec`), and go to 1.

Pathing is forbidden from breaking any other lectern while the cycle runs. If the villager keeps its profession
after the break, that lectern was not its job site: it is put back and the villager is skipped.

Upgrade mode (`upgrade`, off by default): if a wanted book is owned but above the best price (+ tolerance), a
REROLLABLE villager may still lock a strictly cheaper copy. The existing LOCKED villager is never touched.

Stops when: all wanted books are owned, no REROLLABLE librarians are left in range, no lectern / axe / emeralds /
books, the best axe is at or below `axeMinDurability`, the player took damage, or every villager hit `attemptCap`
(default 500 per villager per run). One chat line per attempt; a summary at the end (newly locked books with price
and position, attempts per villager, still missing).

## Files

- `config/tenorclef/tradecycle.json`: `wanted`, `bestTolerance`, `upgrade`, `attemptCap`, `minDelayMs`/`maxDelayMs`
  (random pause between actions), `axeMinDurability`, `professionTimeoutSec`, `overlayDistance`, `lecternSearchRadius`.
- `config/tenorclef/trades/<server>/<dimension>.json`: the index (per server address or `singleplayer_<world>`,
  per dimension). Loaded on join, saved after every change; position-only updates are written at most every 10 s.

## Limitations

- The client cannot see a villager's job site. The linked lectern is the nearest lectern within
  `lecternSearchRadius` not already linked to another indexed villager; the cycle verifies it by watching the
  profession drop after the break.
- Villager XP is only known from the trade screen, so status comes from the last screen read.
- Vanilla librarian books carry one enchantment; if a modded trade has several, the highest level one is indexed.
- Gizmo overlays may not show with some shader packs.
