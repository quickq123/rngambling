# RNGambling design

Decisions made so far, and the plan. Minecraft 26.1.2, NeoForge 26.1.2.126, Java 25.

## Lucky blocks

- Breaking one runs a random **outcome**, good or bad (`rngambling.outcome.Outcomes`).
- Only a **real survival (or adventure) player** sets one off. Creative players, spectators,
  machines' fake players (quarries, block breakers, mining turtles) and explosions just remove
  the block. It never drops itself.
- **Luck**, from -100 to +100, is stored in each block. Each outcome has a weight (how often at
  0 luck) and a rating from -10 to +10; luck multiplies the weight by
  `exp(1.6 * luck/100 * rating/10)`. Bad outcomes never vanish, even at +100.
  Current share of good / bad outcomes (logged by the `luck_tilts_the_odds` game test):

  | Luck | Good | Bad |
  |---|---|---|
  | -100 | 26% | 71% |
  | 0 | 58% | 38% |
  | +100 | 84% | 13% |

- Survival players can't see a placed block's luck; the creative-only Luck Glasses show it.
- Two kinds:
  - **Bought** (done): a plain casino-styled block, sold by the Casino Dealer. **Its luck is
    rolled when a player places it**, from the item's range, never from the luck zone, so
    carrying blocks somewhere favourable gains nothing. The item variants (all place the same
    block; their icons differ): Lucky Block, any luck -100 to +100, each equally likely;
    Safe, -50 to +50; Extreme, 50 to 100 good or bad; Blessed, +80 to +100. Blocks moved by
    other mods' block-transport tools keep their saved luck. The creative tab's fixed-luck
    test blocks keep their luck only when a creative player places them.
  - **Disguised** (`mimic_lucky_block`, done): draws the block it
    copies, 12% darker (`MimicTint.SHADE`), including a copied block's own tint such as grass
    colour. Dark blocks, whose texture averages below 0.36 brightness (`MimicLook.DARK_BELOW`:
    deepslate 0.32, basalt, blackstone, obsidian, mud, dark planks; stone is 0.49), are drawn
    lighter instead, by a faint white overlay (`block/mimic_lighten`, 7% opaque: about as
    noticeable on deepslate as 12% darkening is on stone), since
    a tint can only darken and darkening a dark texture barely shows. It also sounds, mines and resists explosions like the copied block, and creative
    middle-click gives the copied block. Only plain, full, solid, opaque blocks without a block
    entity can be copied. Its luck is rolled by the luck zone it's in (below), by world
    generation and the Mimic Wand alike.
    **World generation** (done): always with a side open to air, so every one can be
    spotted. Surface: the top ground block (grass, sand, gravel...), never water. Caves: a
    floor, ceiling or wall block next to roofed air (Overworld y -56 to 56; Nether 32 to 118).
    Structures: a wall, floor or pillar of a structure, next to its rooms. Overworld rates
    are in `config/rngambling-common.toml` (`surface_one_in_chunks`, `cave_one_in_chunks`,
    both 1 in 8; 0 turns one off). Not every try finds a spot (oceans, solid rock), so a
    test world had 23 in 169 chunks, 1 per 7 chunks (10 surface, 13 cave).
- **Luck zones** (done): datapack files, `data/<namespace>/rngambling/luck_zone/*.json`
  (format in `LuckZone`), each setting how luck is rolled and how often blocks appear for a
  dimension, biomes or structures. The most specific match wins: structure, then biome, then
  dimension. Shipped zones, with what a survey of 169 chunks found:

  | Zone | Luck | Rate (1 in N chunks) | Surveyed |
  |---|---|---|---|
  | Overworld | spread around 0 (±30) | config: surface 8, cave 8 | 18 blocks, -57 to +82 |
  | Mushroom Fields | spread around +20 (±25) | as Overworld | |
  | Pale Garden | spread around +15 (±25) | as Overworld | |
  | Nether | extremes ±50-100, half good | caves 6 | 31 blocks, none within ±49, 19 good |
  | End (outer islands) | extremes +50-100 only | surface 6 | 4 blocks (mostly void), +50 to +93 |
  | Woodland Mansion | spread around +30 (±25) | 4 of its chunks | 2-8 inside at rates 5-3 |
  | Ancient City | extremes ±70-100, half good | 20 of its chunks | 11 inside |

- **Themed outcomes** (done, `rngambling.outcome.Themes`): the outcome list depends on the
  luck zone where a lucky block is **broken** (not where it came from); its own luck still
  sets how good or bad the draw is likely to be. A theme swaps some ordinary outcomes and
  adds its own; zones without one use the ordinary 23:

  | Zone | Swapped | Added |
  |---|---|---|
  | Nether | zombies -> blazes, silverfish -> magma cubes, creeper -> wither skeletons, lightning -> ring of fire, chickens -> striders, ingots -> gold blocks, feast -> porkchops | ghast (bad), netherite scrap, quartz and glowstone |
  | End | zombies -> endermites, creeper -> angry endermen, silverfish -> a shulker, launch -> levitation, lightning -> blindness and slowness, feast -> chorus fruit | ender pearls, shulker shells, elytra (very rare) |
  | Ancient City | zombies -> a sculk shriek and Darkness | echo shards |
  | Mushroom Fields | chickens -> mooshrooms, feast -> mushroom stew | |
- **Mimic Wand** (creative only, done): right-click a block to disguise it, with luck rolled by
  the luck zone there; right-click a disguised one to see what it copies, its luck and its
  zone; sneak + right-click to turn it back.

## The casino

- **Casino machines can't be crafted: they have to be found.** Each drops itself when broken
  and works wherever it's placed, so a player can take the games home (game test
  `machines_drop_themselves`). Every machine is a Casino Dealer job site.
- **Casino buildings** (done): each village type's vanilla Fletcher House with its fletching
  table swapped for a casino game, in three variants: Slot Machine (facing into the room),
  Roulette Table and Blackjack Table (`tools/make_structures.py` makes them from the vanilla
  files; `CasinoHouses` adds them to the village house pools, together at three times the
  Fletcher House's weight). In a survey, 9 of 19 villages had a casino (some two).
- **Slot Machine** (done): right-click to play. Bet 1, 5 or 10 tokens (taken from the
  inventory), spin, and three reels stop one by one (`SlotMachineScreen`). All results are
  decided on the server (`SlotMachine`, `SlotMachineMenu`); the win is paid as the last reel
  stops, or at once if the screen is closed. Like a real casino it's weighted slightly
  against the player: it pays back **91.6%** of tokens bet (an 8.4% house edge; the
  `slot_machine` game test checks the exact figure is 90-94%, and a 300,000-spin simulation
  gave 91.9%). About 1 spin in 4 wins something. Reel weights (out of 24) and pay table:

  | Symbol | On a reel | Three pay (x bet) |
  |---|---|---|
  | Cherry | 7 | 8 (any two cherries: 2) |
  | Lemon | 6 | 10 |
  | Bell | 4 | 18 |
  | BAR | 3 | 30 |
  | 7 | 2 | 80 |
  | Lucky block | 2 | 20, plus a Lucky Block |

  Mined with a pickaxe.
- **Roulette Table** (done): European roulette, one zero and 1-36. Bet 1, 5 or 10 on red,
  black, odd, even, 1-18, 19-36, a dozen (pays 2 to 1) or a single number (35 to 1); even
  bets pay 1 to 1. The wheel is shown as a strip of its pockets in wheel order, slowing to a
  stop under a marker. The usual casino odds make every bet return exactly 36/37: a
  **2.7%** house edge, as in a real casino (`roulette` game test). Mined with an axe.
- **Blackjack Table** (done): against the dealer, from an endlessly shuffled deck. Deal, then
  Hit, Stand or Double (first two cards only); no splitting. The dealer hits a soft 17 and
  plays out one card at a time; blackjack pays 3 to 2; bets are 2, 6 or 10 so that comes to
  whole tokens. The dealer's face-down card isn't sent to the player's game until it's
  turned over. Closing the screen mid-hand stands. Played perfectly (basic strategy) the
  house keeps **1.2%** of the starting bet (a million simulated hands in the `blackjack`
  game test); played carelessly, more. Mined with an axe.
- **Casino Dealer** (done): a villager profession whose job site is any casino machine (the
  `rngambling:casino_machine` point of interest). Black vest, white shirt, red bow tie, green
  visor. Trades (data files: `data/rngambling/villager_trade/dealer/<level>/`, and
  `trade_set/dealer/level_<n>.json`), two per level:

  | Level | Trades |
  |---|---|
  | Novice | 1 emerald for 3 tokens; 8 tokens for a Lucky Block (-100 to +100) |
  | Apprentice | 12 tokens for a Safe Lucky Block (-50 to +50); 16 tokens for a Four-Leaf Clover |
  | Journeyman | 16 tokens for an Extreme Lucky Block (50 to 100, good or bad); 24 tokens for a Golden Horseshoe |
  | Expert | 6 tokens for an emerald; 4 Piles of Tokens for a Lucky Seven |
  | Master | 16 Piles of Tokens (144 tokens) for a Blessed Lucky Block (+80 to +100); 2 piles for 3 Lucky Blocks |

  **Pile of Tokens**: 9 tokens crafted together (3 x 3), and back into 9, for the dearest trades.

- **Luck charms** (done): one use each, on any lucky block (bought or disguised): Four-Leaf
  Clover +10, Golden Horseshoe +25, Lucky Seven +50, up to +100. Survival players are only
  told the block "feels luckier"; a charm isn't used on a block already at +100.

## Plan

1. ~~Bought lucky block, luck, 23 outcomes, the break rules.~~ Done.
2. ~~Luck Glasses~~ Done: creative-only headwear; while worn by a creative player, lucky
   blocks within 32 blocks show their luck through walls, red to white to green (drawn on
   the HUD, since the game doesn't draw underground spaces the player can't see).
3. ~~The disguised block, the Mimic Wand and world generation.~~ Done.
4. ~~Casino tokens, the Casino Dealer and its trades, the luck charms, and the casino
   buildings in villages.~~ Done.
5. ~~Playing the Slot Machine.~~ Done.
6. ~~Roulette and blackjack.~~ Done.
7. ~~Themed outcomes per luck zone.~~ Done.

House edges: slots 8.4%, roulette 2.7%, blackjack about 1.2% with perfect play.

## Working on it

- Build: `./gradlew build` (JAVA_HOME = JDK 25). Jar: `build/libs/`.
- Tests: `./gradlew runGameTestServer` (the exit code is the number of failures).
- Pictures: `./gradlew runClientShots` opens the game, builds a test wall (real / disguised /
  real block per column), saves pictures to `runs/shots/screenshots` and quits.
  `-PshotsWorld=normal` makes a normal world instead, logs every generated lucky block in
  the chunks around the player, and pictures one on the surface and one in a cave.
  `-PshotsWorld=zones` surveys each luck zone: the Overworld, the Nether, the End, then the
  nearest Ancient City and Woodland Mansion (`-PstructuresOnly` for just those two).
  `-PshotsWorld=tables` places and plays the roulette and blackjack tables.
  `-PshotsWorld=casino` places the plains slots casino with a villager, checks it becomes a Casino
  Dealer, and pictures it and its trades. `-PshotsWorld=villages` counts the casinos in the
  nearest villages (from their layouts, without generating terrain).
- Casino buildings: `tools/make_structures.py` (see its header for extracting the vanilla
  houses it starts from).
- Textures: `tools/make_textures.py`, run in Docker (see its header).
- Add an outcome: one line in `Outcomes.ALL` (or a theme in `Themes.BY_ZONE`) and its message
  in `lang/en_us.json`.
