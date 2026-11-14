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

- Survival players can't see a block's luck. Creative tooltips show it, and so will the
  creative-only glasses.
- Two kinds:
  - **Bought** (done): a plain casino-styled texture, sold by the casino villager. Its luck is set
    by the trade, so higher trade levels sell luckier blocks.
  - **Disguised** (`mimic_lucky_block`, done; world generation planned): draws the block it
    copies, 12% darker (`MimicTint.SHADE`), including a copied block's own tint such as grass
    colour. It also sounds, mines and resists explosions like the copied block, and creative
    middle-click gives the copied block. Only plain, full, solid, opaque blocks without a block
    entity can be copied. Luck is rolled clustered around 0 (`Luck.rollNatural`, a normal
    curve with spread 30, so beyond ±60 is about 1 in 20).
    World generation will place them only where at least one side faces air (cave walls,
    cliffs, the surface), so every one can be spotted: roughly 1 in 4-8 chunks, half surface
    and half caves, in a config.
- **Mimic Wand** (creative only, done): right-click a block to disguise it; right-click a
  disguised one to see what it copies and its luck; sneak + right-click to turn it back.

## Plan

1. ~~Bought lucky block, luck, 23 outcomes, the break rules.~~ Done.
2. Luck-seeing glasses: creative-only helmet; lucky blocks within ~32 blocks show their luck
   through walls, red to white to green.
3. ~~The disguised block and the Mimic Wand.~~ Done. Still to do: its world generation.
4. Casino tokens (done as an item), the casino villager (the machines are its job sites) and its
   trades: tokens for lucky blocks, then one-use luck boosters.
5. Slot machine and the casino hut in villages. Machines can't be crafted.
6. Roulette, then blackjack. All game logic runs on the server.

Open: what happens when a casino machine is broken; the machines' house edge.

## Working on it

- Build: `./gradlew build` (JAVA_HOME = JDK 25). Jar: `build/libs/`.
- Tests: `./gradlew runGameTestServer` (the exit code is the number of failures).
- Pictures: `./gradlew runClientShots` opens the game, builds a test wall (real / disguised /
  real block per column), saves pictures to `runs/shots/screenshots` and quits.
- Textures: `tools/make_textures.py`, run in Docker (see its header).
- Add an outcome: one line in `Outcomes.ALL` and its message in `lang/en_us.json`.
