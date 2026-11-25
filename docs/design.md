# RNGambling design

Decisions made so far, and the plan. Minecraft 26.1.2, NeoForge 26.1.2.126, Java 25.

## Lucky blocks

- Breaking one plays a random **effect** from the **effect catalog** (`docs/effect-catalog.csv`,
  coded in `rngambling.outcome.Catalog`), each with a tier, a luck band and a weight.
- Only a **real survival (or adventure) player** sets one off. Creative players, spectators,
  machines' fake players (quarries, block breakers, mining turtles) and explosions just remove
  the block. It never drops itself.
- **Luck**, from -100 to +100, is stored in each block, and picks the effect in two steps
  (`Outcomes.pick`): first good, neutral or bad (a smooth S-curve in luck, never below 6% either
  way, so nothing is ever certain; neutral likeliest near 0), then the tier within that group by
  closeness to each tier's home luck (Catastrophic -85, Very Bad -52, Bad -22, Good +25,
  Very Good +60, Legendary +88); then the effect within the tier by the catalog's weight, at
  full weight inside its own band and a quarter outside. Wildcards are drawn instead 8% of the
  time when the luck is inside their band. Shares (logged by the `luck_tilts_the_odds` test):

  | Luck | Good | Neutral | Bad | Likeliest tier |
  |---|---|---|---|---|
  | -100 | 7% | 3% | 90% | Catastrophic 68% |
  | -50 | 13% | 7% | 80% | Very Bad 38% |
  | 0 | 34% | 33% | 34% | Neutral 33% |
  | +50 | 80% | 7% | 13% | Very Good 37% |
  | +100 | 90% | 3% | 7% | Legendary 61% |

- **The catalog** (234 effects): the whole of docs/effect-catalog.csv: the "Vanilla only"
  effects, all 15 Sandbox rooms and Boss Bounty (below), the "easy custom" effects and the
  Dragon Hatchling (below), plus additions since: **Wear and Tear** (VBD-30), and for the casino
  **Token Toss** (GOD-30, 8-24 tokens), **Token Jackpot** (VGD-24, 2-4 Piles of Tokens) and
  **Casino Invitation** (GOD-31, a casino room in the Sandbox), and **Lucky Sense** (GOD-32).
  A **second batch** of 39 (`MoreEffects`, all vanilla parts) added variety where most breaks
  land: 9 Bad, 9 Neutral, 9 Good, and 4 Very Bad, 4 Very Good, 2 Catastrophic, 2 Legendary
  (rows CAT-23..24, VBD-31..34, BAD-26..34, NEU-25..33, GOD-33..41, VGD-25..28, LEG-20..21).
  None of them changes blocks, and items are only moved or changed, never deleted.
- **Past the limits:** the best gear goes beyond vanilla's enchantment levels: Netherite Armor
  Set (Protection VI, Unbreaking V), God Sword (Sharpness VII, Looting IV, Fire Aspect III,
  Mending), Perfect Pickaxe (Efficiency VII, Fortune IV, Unbreaking V, Mending), Elytra &
  Rockets (Unbreaking V), Enchanted Bow (Power VI, Punch II, Infinity, Flame), and Genie's Wish's
  sword, pickaxe, elytra, trident (Impaling VII) and mace (Density VII, Wind Burst II).
- **Grief:** explosions and fire happen in the world. Effects that build or place blocks (or
  would tear a lot up, like a Wither) go to the Sandbox. Effects that dig (Trapdoor Drop: a 2x2 shaft
  under where the player stands, so it can't miss a player between blocks; The Crater) only dig natural ground (stone, dirt, sand, gravel...), never what players built.
  Effects that put blocks into empty space and take them away again (Melting Ice Prison,
  Jukebox) and chain effects (more lucky blocks, only into empty space) stay in the world.
- **Chains:** effects that place lucky blocks (Echo Blocks, Bonus Lucky Blocks, Lucky Fountain,
  Lucky Piñata, Lucky Block Respawn) mark them one deeper; a block 2 deep can't chain again.
- **Server-wide effects** (Nightfall, Weather Roulette, Time Skip) happen in single player only,
  unless `global_effects_in_multiplayer` is on. `max_explosion_power` (6) caps effects'
  explosions; `max_bonus_hearts` (10) caps Heart Container.
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
    copies, 24% darker (`MimicTint.SHADE`), including a copied block's own tint such as grass
    colour. Dark blocks, whose texture averages below 0.36 brightness (`MimicLook.DARK_BELOW`:
    deepslate 0.32, basalt, blackstone, obsidian, mud, dark planks; stone is 0.49), are drawn
    lighter instead, by a faint white overlay (`block/mimic_lighten`, 14% opaque: about as
    noticeable on deepslate as 24% darkening is on stone; both doubled after survival play,
    when grass-topped disguises were too hard to spot), since
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
  | Overworld | spread around 0 (±15; was ±30, which made the far tiers too common: about 1 in 25 blocks each end, now about 1 in 60) | config: surface 8, cave 8 | |
  | Mushroom Fields | spread around +20 (±25) | as Overworld | |
  | Pale Garden | spread around +15 (±25) | as Overworld | |
  | Nether | extremes ±50-100, half good | caves 6 | 31 blocks, none within ±49, 19 good |
  | End (outer islands) | extremes +50-100 only | surface 6 | 4 blocks (mostly void), +50 to +93 |
  | Woodland Mansion | spread around +30 (±25) | 4 of its chunks | 2-8 inside at rates 5-3 |
  | Ancient City | extremes ±70-100, half good | 20 of its chunks | 11 inside |

- **Themed effects** (done, `rngambling.outcome.Themes`): the catalog depends on the luck zone
  where a lucky block is **broken** (not where it came from); its own luck still sets how good
  or bad the draw is likely to be. A theme swaps some catalog effects (keeping their tier, band
  and weight) and adds its own:

  | Zone | Swapped | Added |
  |---|---|---|
  | Nether | Zombie Horde -> Blaze Horde, Cave Spider Pack -> magma cubes, Witch Coven -> wither skeletons, Iron Haul -> gold blocks, Food Basket -> porkchops | netherite scrap, quartz and glowstone |
  | End | Zombie Horde -> endermites, Fused Creeper -> shulkers, Orbital Launch -> levitation, Single Lightning Bolt -> blindness, Food Basket -> chorus fruit | shulker shells, dragon's breath |
  | Ancient City | Zombie Horde -> a sculk shriek and Darkness | echo shards |
  | Mushroom Fields | Barnyard -> mooshrooms, Food Basket -> mushroom stew | |
- **Mimic Wand** (creative only, done): right-click a block to disguise it, with luck rolled by
  the luck zone there; right-click a disguised one to see what it copies, its luck and its
  zone; sneak + right-click to turn it back.

### Custom effects

In `rngambling.outcome.CustomEffects` (first versions, to tune in playtesting):

| Effect | What happens |
|---|---|
| Hot Potato (VBD-20) | a lit Hot Potato in the inventory; in 5 s it explodes (power 3) wherever it is: at the player holding it, or where it lies. Throw it! (Stored in a chest it waits, and goes off when taken out.) |
| Wear and Tear (VBD-30) | one random item with durability (armour and off hand too) drops to its last point. If nothing can wear: health and hunger to one, Hunger I for a minute; if that last hunger runs out with no food in the inventory, the player starves. Eating (or 5 minutes) ends it |
| Tax Collector (BAD-25) | a pillager takes a quarter (rounded up) of the player's emeralds and gold and runs; kill him to get it back, or after a minute he's gone with it |
| Snow Golem Escort (NEU-15), Iron Golem Bodyguard (GOD-13) | two snow golems for 5 minutes / an iron golem (player-made, never turns on them) for 10, following the player; they stay afterwards |
| Disco Floor (NEU-16) | the floor around the player (plain full blocks open to the air) flashes stained glass to a beat for 15 s, then is put back exactly, even if the server stops first |
| Llama Spit Party (NEU-17) | four llamas surround the player and spit (harmlessly) for ten seconds, then vanish |
| Message in a Bottle (NEU-20) | a treasure map to the nearest buried treasure; where there's none (Nether, End), the message is smudged |
| Mob Shuffle (NEU-21) | wild mobs within 16 blocks swap for another of their kind (hostile for hostile, animal for animal); never pets, named or leashed mobs, villagers, golems or bosses |
| Ghost Block (NEU-24) | the lucky block comes back: hit, it vanishes and reappears, three times; then it breaks into a fresh roll |
| Ore Vision (GOD-17) | ores within 16 blocks (up to 300) glow through walls for a minute, coloured by ore |
| Lucky Sense (GOD-32) | lucky blocks within 64 blocks (up to 100, disguised ones too) glow through walls for two minutes, coloured by luck (red bad, white even, green good); found through loaded chunks' block entities |
| Mystery Item (GOD-29), Mystery Treasure (LEG-19) | one / three random items that can be crafted or found (recipes, loot tables, natural blocks' drops; any mod's), less `#rngambling:lucky_blacklist`; rebuilt after /reload (about 1,230 items in vanilla) |
| Villager Friends (VGD-19) | a master librarian (Mending, Unbreaking III, Efficiency V, Protection IV books) and a master toolsmith (diamonds, enchanted diamond tools and chestplate), trades fixed |
| Genie's Wish (LEG-09) | a window with three of twelve Legendary items; click one. Closing it grants one at random |
| Phoenix Blessing (LEG-11) | saved with the player: the next death is cancelled; they rise at half health in a burst of fire that sets nearby monsters alight |
| Dragon Hatchling (LEG-16) | a tame dragon pet (a wolf underneath: follows, sits, defends, heals on meat): 50 health, armour, fire-immune, its bite sets things alight; can't breed or fly. Own model and texture |
| Double or Nothing (WLD-02) | [Double] / [Walk away] in chat (`/rngambling double accept|walk`): roll again at twice the luck, or take 2 to 4 Casino Tokens (also after a minute's silence) |

Temporary entities (Ore Vision's glows, the llamas, the Tax Collector) are removed if found
after a restart. Tests: `custom_effects`; `runClientShots -PshotsWorld=effects` pictures Ore
Vision, the Disco Floor, the llamas, the Dragon Hatchling, Genie's Wish and the Hot Potato.

### Dying in the Sandbox

What a player loses dying in the Sandbox (their drops, less items a room handed out, and their
experience) isn't scattered over the Sandbox: it's kept with them (saved, so a restart doesn't
lose it) and given back when they respawn (`SandboxDeaths`).

### Announcements and testing effects

Every effect says in the player's chat what it is: `[Lucky Block] Name: one-liner`, coloured by
tier (the one-liners, some snarky, are the `outcome.rngambling.<id>.quip` lines in
`lang/en_us.json`; scares and fake-outs (Fake Explosion, Fake Death Message, Ghast Scream, Silent Creeper) are
announced only after they've played out, so nothing spoils them). The name also shows above the hotbar.

Operators can set off effects to test them. The target is a player by name, or whoever runs
the command when no name is given:

- `/rngambling effect play <effect> [player] [luck]`: one effect, catalog or theme (tab completes
  the ids). Luck defaults to the middle of the effect's band. Rooms need the Sandbox.
- `/rngambling effect roll [player] [luck]`: a real draw, as a lucky block of that luck (default
  0) broken there would make, themed by the luck zone.
- `/rngambling effect list [tier]`: the effect ids, by tier.

The "lucky block" is taken to be two blocks in front of the player (if that's open), so drops
and placed blocks land beside them.

## The Sandbox

Effects that build or place blocks, or would tear a lot up (`Outcome.sandbox()`: Anvil Rain,
Obsidian Cage, Spider Web Trap, Silverfish Infestation, Lava Shower, Diamond Pillar; later the
Wither fights and structure rooms) are played out in the **Sandbox** dimension instead (`rngambling:sandbox`): an endless plane of bedrock
under the End's sky. Each visit gets its own arena on the plane, 4,096 blocks from any other,
so players never meet there, even if several get a Sandbox outcome at once (one visit per
player at a time; a Sandbox outcome while already there plays out where they stand). Arena
slots are kept with the world, so they aren't reused after a restart, and the ground around
an arena is cleared before each visit. Everything else happens
where the block was broken. `config/rngambling-common.toml` `sandbox_enabled = false` turns it
off (outcomes then happen in place, as they also do if the dimension is missing).

- **Coming back:** once nothing is going on within 64 blocks of the arena (no mobs, TNT, falling blocks,
  lightning or projectiles) and at least 5 seconds (or the effect's own hold, e.g. the Obsidian
  Cage's 30) have passed, or after 90 seconds at most (or the hold plus 10),
  the player is brought back to **exactly** where they broke the block (dimension, position,
  facing). Items and experience left there come back with them.
- **Safeguards:** the return point is kept three ways: in the running visit, on the player,
  and with the world (`SandboxRecords`, which survives restarts). A **full copy of the
  player's inventory** (armour and off-hand too) is saved with the world before they go, and
  kept afterwards. If they die there their drops go to their return point. If they log
  out or the server stops mid-visit, they're brought back when they next log in.
- **Admin commands** (operators): `/rngambling sandbox list` (every visit on record: in the
  Sandbox now or back, the return point, the inventory copy's size, when);
  `/rngambling sandbox return <player>` (brings them back exactly, ending the visit);
  `/rngambling sandbox restore <player>` (replaces their inventory with the saved copy; it
  replaces, never adds, so it can't duplicate items).
- **Testing:** the game test server leaves out datapack dimensions, so game tests cover the
  fallback and the records; `runClientShots -PshotsWorld=sandbox` checks the real thing in a
  real world (logs `SANDBOX CHECK ok/FAILED`).

### Sandbox rooms

Built in code (`rngambling.outcome.Rooms`), fresh for each visit on the Sandbox's plane; first
versions, to be tuned in playtesting. A room ends when it's finished (its **way out**, a purple
portal you walk into, or a challenge met), when its time runs out, or if the player dies or
leaves; then the usual return applies. Room blocks can't be broken, and nothing can be placed,
unless the room allows it (no cutting through the maze or bridging the parkour course). Blocks
a room hands out are taken away at the end. Falling off a sky room is failure: home at once.

| Room | What it is | Ends |
|---|---|---|
| Wither Arena (CAT-01) | sealed obsidian box, a Wither charging up | Wither dead (5 min max) |
| Empowered Wither (CAT-20) | ruined stone-brick ring; Wither with double health and +6 armour, 2 wither skeletons every 20 s | 30 s after it dies, with an End City and a bastion chest (6 min max) |
| Warden's Hollow (CAT-03) | an 80-long dark deepslate and sculk cavern, Darkness; shriekers that can summon a Warden along it, sculk sensors (sneak past), and sound traps that go off however carefully you move: bone piles that rattle and dark pressure plates that click, each setting off the nearest shrieker. The player starts at Warden warning level 2, so two shrieks bring the Warden | the way out at the far end (5 min) |
| Void Platform (CAT-07) | 9x9 platform 100 up; a 3-2-1 countdown, then a block darkens and falls every 0.3 s, every fourth one the block under the player (standing still fails); all gone by about 25 s. 8 cobblestone to save yourself | a way out appears 30 s after the countdown; falling fails |
| Rising Lava Tomb (CAT-08) | sealed stone-brick box; on arrival a title ("The lava is coming!") and a 15-second countdown bar (the last five seconds big on screen), then lava rises a layer every 5 s; cracked-brick decoys, and one weak pair (two high, at the floor) that breaks | the way out down a tunnel behind the weak pair |
| Elder Guardian Monument (CAT-14) | flooded prismarine chamber, 2 Elder Guardians | the way out at the far wall (3 min) |
| Nether Fortress Ambush (CAT-17) | 40-long nether brick corridor, blazes and wither skeletons | the way out at the end (3 min) |
| Blind Hedge Maze (VBD-15) | a fresh random 8x8-cell hedge maze each time, Blindness, 4 zombies | the way out in the far corner (4 min) |
| Mob Arena Waves (VBD-16) | walled arena, three waves (zombies and skeletons; spiders and a creeper; vindicators, witches, husks, strays) | 10 s after the last wave, with iron, emeralds and golden apples (5 min) |
| Skyblock Strand (VBD-17) | 3x3 island 90 up, the exit island 18 away; 24 cobblestone | the way out; falling fails |
| Drowned Pit (VBD-24) | 18-deep flooded pit, 4 Drowned with tridents | the way out on a ledge at the top (2 min) |
| Treasure Vault (VGD-09) | gold-block room, 6 loot chests; only the chests can be broken (true of every room unless it says otherwise), so the gold stays put | 40 s timer bar (or the way out) |
| Speedrun Course (VGD-10) | 14 jumps in the sky against the clock, an End City chest at the end | the way out, with a medal by time on top of the chest; falling fails, and you keep nothing |
| Puzzle Vault (VGD-11) | 5 levers, 5 lamps: each lever flips its lamp and its neighbours'; light them all and the vault door opens to an End City chest | the way out in the vault (4 min) |
| Treasure Island (LEG-06) | sand island with palms and a moat, 4 treasure chests; anything can be broken | 3 min timer bar |
| Casino Invitation (GOD-31) | a casino hall: four slot machines, a roulette and a blackjack table, a Casino Dealer to trade with; 8 tokens on the house; nothing in it can be broken or taken | the way out just outside the front door, whenever (10 min max); winnings come home |
| Boss Bounty (LEG-07) | an offer in chat, [Accept] / [Decline] (`/rngambling bounty accept|decline`; a minute's silence declines). Accepted: an Illager Warlord (evoker on a ravager) and 3 vindicators; won, 2 netherite ingots and two Legendary rolls. Declined: a Good roll | 15 s after the fight (5 min max) |

Rooms are left out of the draw when the Sandbox is off or missing. Tests: `rooms_build` builds
every room in the test world (the maze always solvable, the tomb's weak pair the only way out,
the puzzle always solvable); `runClientShots -PshotsWorld=rooms` enters each in the real
Sandbox, pictures it and leaves by its way out (`-PshotsRooms=Dropper,IceRun` for only some,
each also pictured from above).

### Skill rooms (Neutral)

Challenges that score you instead of rewarding or punishing you (`SkillRooms`): each ends in a
**medal**, and the medal is the prize. Gold: a Blessed Lucky Block, 2 Lucky Blocks and 16 Casino
Tokens. Silver: 2 Lucky Blocks and 8 tokens. Bronze: a Lucky Block and 4 tokens. No medal: nothing,
and nothing lost. Falls and ender pearls don't hurt in them. Timed courses start the clock when
you leave the start and show it over the hotbar, with the medal times worked out from the
course's length (first guesses, to tune in playtesting). All NEU-36 to NEU-43, luck -20 to 20,
weight 3. Game test `skill_rooms`.

| Room | What it is | Medal |
|---|---|---|
| Dropper (NEU-36) | a 100-block shaft; 5 floors across it, one opening each (3x3 narrowing to 2x2), each within 2 blocks of the last; water at the bottom; the glass under you goes after 3-2-1 | the water, gold; landing on a floor ends it: 3 passed, silver; 2, bronze |
| Ice Run (NEU-37) | 3-wide packed ice, then blue ice, in the sky; sideways turns and 2-3 block gaps | by time at the way out; falling, none |
| Bounce House (NEU-38) | 8 honey platforms, each 2 lower; a slime pad 4 below each gap to bounce up from | by time; falling, none |
| Crumbling Path (NEU-39) | 4 winding stone-brick paths that crack when stepped on and drop 0.6 s later, with 2-4 easy quartz jumps between | by time; falling, none |
| Ender Pearl Hop (NEU-40) | 6 islands 13-17 apart, 9 pearls (3 spare) | at the way out: 3 left, gold; 1-2, silver; 0, bronze |
| Tower Climb (NEU-41) | up the inside of a 50-high tower: jumps along the walls, ladders, vines, scaffolding; a fall costs only the climb | the way out at the top, gold; at 3 minutes, by height: 2/3, silver; 1/3, bronze |
| Block Party (NEU-42) | 16x16 floor of coloured 2x2 tiles; a colour is called, the rest drop away when the countdown ends (4 s down to 1.2 s) | rounds survived of 10: 10 gold, 7 silver, 4 bronze |
| Simon Says (NEU-43) | 4 coloured pads with buttons; repeat the lit sequence, one longer each round; a wrong button or 10 s idle ends it | sequences repeated: 8 gold, 5 silver, 3 bronze |

### Higher or Lower and Creeper Field (done)

Two games that aren't from a real casino, each its own block (craftable with a Pile of Tokens in
the middle, in the Casino Invitation room, and in village casinos like the others).
Bets 5 to 100: pots are whole tokens, rounded to the nearest, and from 5 up rounding matters little.

- **Pots, not multipliers:** the screen shows tokens throughout ("Higher: 25", "Next block:
  19", "Cash out 16"). A pot is the bet times the fair odds of everything won so far, rounded
  to the nearest token, and never less than before. They're fair games: whatever the plan, what
  comes back is about what was bet (within 4% at a bet of 100, 12% at 5; game test
  `casino_games`). Luck multiplies every pot (x1.10 at I, x1.25 at II; Bad Luck x0.90, x0.75),
  but not a pot that a sure guess can't lose (higher on an ace). Pots stop at 10,000 tokens (data
  slots are 16-bit), paid at once.
- **Higher or Lower** (`HigherLower`, `HigherLowerMenu`): a card is turned up (endless deck,
  aces low); guess Higher or Lower, ties win. Each button shows the pot if it's right. Skip
  changes the card for free; Cash out any time; a wrong guess loses the pot. Closing the screen
  cashes out.
- **Creeper Field** (`CreeperField`, `CreeperFieldMenu`): a 5 x 5 field of grass hides 1-20
  creepers (chosen before starting; placed when the game starts). Dig a block: an emerald and a
  bigger pot, or a creeper and the pot is lost. Cash out any time; clearing every safe block
  pays at once. The field is shown at the end. Closing the screen cashes out.
- **Luck** tilts both like the other games: Higher or Lower re-draws a losing card, Creeper Field
  moves the creeper you dug elsewhere (Bad Luck the other way), at the same chances. Luck II at
  Higher or Lower over three guesses comes to about 119%.

## The Lucky Journal, announcements and lucky loot

- **Lucky Journal** (`Journal`, `client.JournalScreen`, item `lucky_journal`: book + Casino
  Token, shapeless): every effect there is (catalog and zone variants, 239), by tier with a
  found/total count. An effect is written in the first time it happens to the player (a surprise
  once it's revealed; admin `effect play` counts too), with a chat note "New in your Lucky
  Journal (n of 239)". Written-in effects show their luck range, where they happen (zone, Sandbox),
  the chat line and a description of what they do (`outcome.rngambling.<id>.desc`, generated
  from the effect guide); the rest are "???". Kept through death; synced to the player's game.
  Admin: `/rngambling journal reveal|reset [player]`.
- **Variety** (`Variety`, config `variety_strength`, 0-5, default 1; 0 is off): each player's tally of how often
  and how recently each effect has happened to them. Within a tier (never across tiers: which
  tier comes up is luck alone), an effect's weight is multiplied by 1 / (1 + 5 x recent + 0.6 x
  times), where recent adds 0.85^(draws since) for its last four occurrences; at least 0.1, so
  nothing is ever blocked, and effects never had keep full weight. Right after an effect, about
  0.15 of its weight; 15 draws later about 0.5; long after, 0.63 for once, 0.25 for five times.
  Simulated over 10 players x 300 draws (game test `variety`): repeats within 10 draws fell from
  264 to 114, and different effects in the first 100 draws rose from about 70 to 78. Lucky block
  rolls, Coin Flip, Double or Nothing and The Last Gamble's reward all count and are weighted;
  admin `effect play` doesn't. The journal shows how many times each effect has happened.
- **Server-wide announcements:** a Legendary or Catastrophic pull is told to everyone else on
  the server ("Steve pulled a Legendary: God Sword!"). Config `announce_big_pulls` (default on).
- **The dealer's luck potions** (a chance: each level's trade set picks 2 of 3, or 1 of 2):
  level 2, 12 tokens for a Potion of Luck; level 3, 10 tokens for a Splash Potion of Bad Luck;
  level 4, 2 Piles of Tokens for a Potion of Luck II.
- **Lucky loot in chests** (NeoForge global loot modifiers adding `rngambling:chests/lucky_loot`
  or `lucky_treasure`): one extra draw in ordinary chests (dungeon, mineshaft, desert and jungle
  temples, stronghold corridor/crossing, shipwreck treasure, outpost, ruined portal, big ocean
  ruin, igloo, Nether fortress, trial chamber reward), about 2 in 3 something: 2-8 tokens, a
  Pile, Potion of Luck (or II), lucky blocks (plain, Safe, Extreme), a Four-Leaf Clover. Two
  richer draws in treasure chests (bastion treasure, End city, Ancient City, mansion, buried
  treasure, stronghold library), up to Blessed lucky blocks and the Golden Horseshoe. In a test,
  300 dungeon chests gave 112 with tokens, 37 luck potions and 58 lucky blocks.

## Luck and Bad Luck (the vanilla effects)

The vanilla **Luck** and **Bad Luck** effects (`PlayerLuck`) give a player a luck level: Luck's
level minus Bad Luck's, each counted up to II, so -2 to +2.
- **Lucky blocks:** each level adds 10 to the luck of a block the player breaks (Luck I +10,
  Luck II +20; Bad Luck I -10, II -20), before the effect is drawn. The block's own stored luck
  doesn't change (the Luck Glasses still show it).
- **The casino** (`CasinoLuck`): every game is fair without luck, and every game comes to the
  same figures with it: about 110% of what is bet back at Luck I, 125% at Luck II, 90% at Bad
  Luck I and 75% at Bad Luck II (game test `casino_luck`). Slots and roulette re-spin a losing
  spin with Luck, a winning one with Bad Luck, as often as that game's exact odds need for the
  edge (slots: 17% / 43% of losses at Luck I / II; red: 21% / 51%; the same of wins with Bad
  Luck). Blackjack draws a card twice and keeps the
  better one for the player (13.5% of cards at I, 33.5% at II; the worse with Bad Luck, 14% /
  35%, found by simulation). Higher or Lower and Creeper Field pots grow or shrink by the edge.
- **Getting it:** effects Lucky Streak (GOD-42, Luck I) and Fortune's Favor (VGD-29, Luck II),
  Broken Mirror (BAD-35, Bad Luck I) and Black Cat (VBD-35, Bad Luck II, with a black cat), all
  5 minutes. **Brewing:** Awkward Potion + Pile of Tokens = Potion of Luck (vanilla's, Luck I,
  5:00); + glowstone = Luck II (3:00); Fermented Spider Eye turns Luck into Bad Luck (I 5:00,
  II 3:00), and glowstone strengthens Bad Luck. Splash and lingering versions brew as usual, so
  Bad Luck can be thrown at other players.

## Fate: omens, insurance and the Re-roll Ticket

Effects that play with the draw itself (`Fortunes`), kept per player in `Fate` (saved, through
death). Game test `fate`.
- **Fortune Teller** (NEU-34) draws a tier now, by the odds of the block that gave it, and tells
  the player; their **next lucky block** draws only from that tier ("As foretold: Good.").
- **Jinxed** (BAD-36): the same, but always Bad (7 in 10) or Very Bad (3 in 10), whatever the
  next block's luck ("The jinx strikes: Bad."). The newest omen replaces an older one. A tier
  with nothing open where the block is (e.g. rooms with no Sandbox) is dropped and the block
  draws as usual.
- **Insurance Policy** (GOD-43): the next bad effect (Bad, Very Bad or Catastrophic) drawn for
  the player never happens ("Insurance paid out: X never happened."); it isn't recorded in the
  Journal. One policy at a time. It covers lucky-block draws and their redraws (Coin Flip, The
  Last Gamble), never the admin effect command, and it does cover a jinx.
- **Re-roll Ticket** (GOD-44), an item: held in the **off hand** when breaking a lucky block, it's
  used up and the block draws two different effects (within an omen's tier, if any), shown as
  named cards in a one-row window (`RerollMenu`). Click one; closing the window plays one of the
  two at random. Insurance still applies to the chosen one.
- **Pet Rock** (NEU-35), an item: a rock with a random name, like and dislike. Using it pets it.

## The casino

- **Casino machines can be found or crafted**, each recipe with a Pile of Tokens (9 tokens) in the
  middle: Slot Machine (iron ingots, glass pane, redstone, an iron block), Roulette Table (red
  and black wool, gold ingots, planks), Blackjack Table (green wool, paper, planks). Each drops
  itself when broken and works wherever it's placed, so a player can take the games home (game test
  `machines_drop_themselves`). Every machine is a Casino Dealer job site.
- **Casino buildings** (done): each village type's vanilla Fletcher House with its fletching
  table swapped for a casino game, in five variants (Higher or Lower and Creeper Field too): Slot Machine (facing into the room),
  Roulette Table and Blackjack Table (`tools/make_structures.py` makes them from the vanilla
  files; `CasinoHouses` adds them to the village house pools as the server starts). How often is
  `village_casino_percent` in `rngambling-common.toml`: the casinos' share of each house pool's
  weight, 6 by default (what it was before it was configurable; in a survey, 9 of 19 vanilla
  villages had a casino, some two); 15 puts one in nearly every village. With
  `casinos_in_modded_villages` (on by default), other mods' village house pools
  (`<mod>:village/<type>/house` or `houses`) get them too, in the nearest vanilla style by the
  type's name (mesa to desert, christmas to snowy, mountain to taiga, anything else plains);
  types up trees, underground or underwater are skipped. Their roads join buildings by the same
  `minecraft:building_entrance` jigsaw. With ChoiceTheorem's Overhauled Village that's 20 of
  its 22 village types (all but jungle_tree and underground). The log lists every pool.
- **Slot Machine** (done): right-click to play. Bet 1 to 100 tokens (taken from the
  inventory; see Betting below), spin, and three reels stop one by one (`SlotMachineScreen`). All results are
  decided on the server (`SlotMachine`, `SlotMachineMenu`); the win is paid as the last reel
  stops, or at once if the screen is closed. It's a fair game: it pays back **99.99%** of
  tokens bet (the `slot_machine` game test checks the exact figure and a 300,000-spin
  simulation). About 35% of spins win and another 7% hand the bet back, so losing streaks stay
  short (it used to be 1 win in 4 at 92%). Reel weights (out of 24) and pay table:

  | Symbol | On a reel | Three pay (x bet, the bet included) | Two pay |
  |---|---|---|---|
  | Cherry | 8 | 3 | 2 |
  | Lemon | 6 | 5 | nothing (sour) |
  | Bell | 4 | 10 | 1 (the bet back) |
  | BAR | 3 | 25 | 2 |
  | 7 | 2 | 80 | 3 |
  | Lucky block | 1 | 15, plus a Lucky Block | 3 |

  Mined with a pickaxe.
- **Roulette Table** (done): European roulette, one zero and 1-36. Bet 1 to 100 on red,
  black, green (the zero, 36 to 1), odd, even, 1-18, 19-36, a dozen (pays 2 to 1) or a single
  number (36 to 1); even bets pay 1 to 1. The wheel is shown as a strip of its pockets in wheel order, slowing to a
  stop under a marker. Made fair: on zero every other bet is handed back, and green and single
  numbers pay 36 to 1, not 35, so every bet returns exactly what is bet (`roulette` game test).
  Mined with an axe.
- **Blackjack Table** (done): against the dealer, from an endlessly shuffled deck. Deal, then
  Hit, Stand or Double (first two cards only); no splitting, but five cards without going bust
  win at once (a Five-Card Charlie). The dealer stands on every 17 and
  plays out one card at a time; blackjack pays 3 to 2; bets are 2 to 100 in steps of 2 so that comes to
  whole tokens. The dealer's face-down card isn't sent to the player's game until it's
  turned over. Closing the screen mid-hand stands. Played well (basic strategy) it comes out
  even, within about 0.1% (a million simulated hands in the `blackjack` game test; the rules
  above were picked by simulation, from 98.7% under a soft-17 dealer with no Charlie); played
  carelessly, less. Mined with an axe.
- **Betting** (all three games): « and » change the bet by 10, − and + by one (2 at
  blackjack), between the least bet and 100 (`Bets`). The bet is taken, and the result decided,
  the moment Spin or Deal is pressed: closing the screen can't take it back (a pending win is
  paid at once; a blackjack hand mid-play stands). Results read "Paid X tokens (+profit)": what
  comes back, the bet included. The player's own inventory is kept up to date while a game is
  open (the `tables` pictures check it).
- **Casino Dealer** (done): a villager profession whose job site is any casino machine (the
  `rngambling:casino_machine` point of interest). Black vest, white shirt, red bow tie, green
  visor. Trades (data files: `data/rngambling/villager_trade/dealer/<level>/`, and
  `trade_set/dealer/level_<n>.json`), two per level:

  | Level | Trades |
  |---|---|
  | Novice | 1 emerald for 2 tokens; 8 tokens for a Lucky Block (-100 to +100) |
  | Apprentice | 12 tokens for a Safe Lucky Block (-50 to +50); 16 tokens for a Four-Leaf Clover |
  | Journeyman | 32 tokens for an Extreme Lucky Block (50 to 100, good or bad); 24 tokens for a Golden Horseshoe |
  | Expert | 4 Piles of Tokens for a Lucky Seven (the only trade: tokens never go back to emeralds) |
  | Master | 16 Piles of Tokens (144 tokens) for a Blessed Lucky Block (+80 to +100); 2 piles for 3 Lucky Blocks |

  **Pile of Tokens**: 9 tokens crafted together (3 x 3), and back into 9, for the dearest trades.

- **Luck charms** (done): one use each, on any lucky block (bought or disguised): Four-Leaf
  Clover +10, Golden Horseshoe +25, Lucky Seven +50, up to +100. Survival players are only
  told the block "feels luckier"; a charm isn't used on a block already at +100.

## Plan

1. ~~Bought lucky block, luck, 23 outcomes, the break rules.~~ Done.
2. ~~Luck Glasses~~ Done: creative-only headwear (only from the creative tab); while worn, lucky
   blocks within 128 blocks show their luck through walls, red to white to green (drawn on
   the HUD, since the game doesn't draw underground spaces the player can't see).
3. ~~The disguised block, the Mimic Wand and world generation.~~ Done.
4. ~~Casino tokens, the Casino Dealer and its trades, the luck charms, and the casino
   buildings in villages.~~ Done.
5. ~~Playing the Slot Machine.~~ Done.
6. ~~Roulette and blackjack.~~ Done.
7. ~~Themed outcomes per luck zone.~~ Done.
8. ~~The whole effect catalog: the Sandbox and its rooms, the custom effects, the Dragon
   Hatchling.~~ Done. Next: survival playtesting, then tuning.

No house edge: every game pays back what is bet, without luck (blackjack played well).

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
- `-PshotsWorld=effects`: the custom effects with something to see (Ore Vision, Disco Floor,
  Llama Spit Party, the Dragon Hatchling, Genie's Wish, the Hot Potato).
- `-PshotsWorld=rooms`: every Sandbox room entered, pictured and left by its way out.
- `-PshotsWorld=sandbox`: Anvil Rain played out in the Sandbox, the exact return, and the admin
  commands, in a real world.
- Casino buildings: `tools/make_structures.py` (see its header for extracting the vanilla
  houses it starts from).
- Textures: `tools/make_textures.py`, run in Docker (see its header).
- Add an outcome: one line in `Catalog.ALL` (or a theme in `Themes.BY_ZONE`), its row in
  docs/effect-catalog.csv, and its message in `lang/en_us.json`.
