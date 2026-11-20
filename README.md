# Wonder Realms

A Fabric mod for Minecraft 26.3 (needs Fabric API and GeckoLib 5.5.6 or newer) with three adventures in one:

- **Tiny Worlds**: shrink to the size of a bug, and shrink your enemies too.
- **Dragons**: win an egg, raise a dragon, armor it, and fly it.
- **Sky Islands & Airships**: explore floating islands of five kinds, raid pillager sky outposts, and fight battles
  between airships.

## What's new in 1.2

- **Elemental sky islands**: volcanic Ember islands, snowy Frost islands, Storm islands crowned with lightning rods,
  and lush Verdant islands. Each roost's dragon matches its island.
- **The Verdant Dragon**: a nature dragon whose breath poisons foes and makes plants burst into growth.
- **Dragon armor** in iron, gold, diamond and netherite, which shows on your dragon.
- **The Dragon Whistle** calls your dragons to you.
- **Two riders**: a friend can ride behind you.
- **Airship cannons and dyed balloons**. Pillager ships fire back.
- **The Shrink Ray** shrinks mobs to bug size.
- **The Pixel Chisel** lets you carve pixels at full size.
- **12 new advancements.**

## Tiny Worlds

**Shrinking Draught** (glass bottle + brown mushroom + amethyst shard): drink it to shrink to a tenth of your size
for five minutes. While you're tiny:

- **Climb walls** by walking into them.
- **Falls barely hurt.**
- **Carve blocks pixel by pixel.** Mining stone, dirt, wood and most other plain blocks chips away one 1/8-block
  "pixel" at a time, so you can sculpt them. The block's drop comes once you've carved half of it away.
- **Find dewdrops.** Breaking grass and ferns sometimes turns one up. Drinking one gives a moment of regeneration.
- **Bugs become bosses.** Spiders, silverfish, bees and endermites hit twice as hard. Beat one and it drops a hoard:
  string, spider eyes, cobwebs, sometimes emeralds or another Shrinking Draught.
- **Meet the Pixel Miners**, tiny folk who live in the grass and the stone. Give one a dewdrop and it pays you in
  nuggets, redstone, or now and then a Diamond or Emerald Pixel. Nine pixels craft into a whole gem.

**Growth Draught** (glass bottle + red mushroom + bone meal) brings you back to full size early.

**Shrink Ray**: aim it at a mob and use it to shrink that mob to bug size for 30 seconds. Shrunken mobs only do a
quarter of their usual damage to anything full-sized, so a shrunken creeper's blast barely stings. Like you, they
can climb walls while they're tiny. It doesn't work on players or bosses. Craft it as follows:

```
          amethyst shard
iron ingot  Shrinking Draught  iron ingot
          iron ingot
```

**Pixel Chisel** (iron ingot + stick + amethyst shard): hold it to carve blocks pixel by pixel at full size.

## Dragons

Dragons live only on **sky islands**, and they come in four wild kinds:

| Dragon | Breath | Food |
| --- | --- | --- |
| **Ember** | Fire: sets things alight, melts snow and ice, lights campfires and candles | Meat |
| **Frost** | Ice: slows and freezes, turns water into ice you can walk on, cools lava | Meat or fish |
| **Storm** | Lightning: crackles, and sometimes calls down a real bolt | Meat |
| **Verdant** | Spores: poisons foes, makes plants grow, and turns bare dirt to grass | Meat, berries or apples |

Fire breath never sets your blocks alight, and storm lightning never starts fires.

There's a fifth, secret kind: feed the End's **Dragon Egg** chorus fruit and it may hatch an **Ender Dragon**,
whose breath lifts targets off their feet.

**Getting an egg.** Wild dragons guard roosts on sky islands. Defeat one and it drops its egg. A tamed, grown
dragon also lays an egg every two or three in-game days.

**Hatching.** Place the egg and wait. It cracks over a few minutes, then hatches a dragon that bonds with the
nearest player.

**Taming a wild dragon.** Feed it its favourite food. Wild adults take patience and will fight back. Hatchlings and
freed dragons warm to you quickly.

**Raising.** Hatchlings grow to full size over two in-game days, and feeding them speeds that up. They get bigger
and tougher as they grow, from 20 to 80 health. What your dragon kills counts as your kill, experience and all.

**Commands.** Sneak and use an empty hand on your dragon to make it sit or follow. Tamed dragons defend you, and
follow you into the air when you're gliding, on an airship, or on another dragon.

**Dragon Whistle** (bone + iron nugget + string): use it to call every dragon of yours within earshot. They stop
sitting and fly to you. Sneak and use it to tell them all to sit.

**Dragon armor**: use it on your dragon to armor it. Using another piece swaps the old one out, and shears take
armor off (and after that, the saddle). The armor shows on your dragon.

| Armor | Recipe | Armor points |
| --- | --- | --- |
| Iron | iron ingots around leather (see below) | 6 |
| Gold | gold ingots around leather | 8 |
| Diamond | diamonds around leather | 12, plus toughness |
| Netherite | upgrade diamond dragon armor at a smithing table | 16, plus toughness and knockback resistance |

```
ingot  .       ingot
ingot  leather ingot
ingot  ingot   ingot
```

**Riding.** Put a saddle on a grown dragon, then use it with an empty hand to climb on. Once you're in the saddle, a
friend can climb on behind you.

| Action | Control |
| --- | --- |
| Take off | Jump, or look up while moving forward |
| Steer | Look where you want to go |
| Climb | Hold jump |
| Dive for speed | Look down |
| Land | Come down to the ground looking level or lower |
| Breathe | **V** (rebindable under Controls, "Dragon Breath / Airship Cannon") |

## Sky Islands

Floating islands drift between y 180 and 230 in every overworld biome, roughly a hundred blocks apart. Each has a
rocky underside full of ore that's richest toward the tip, and small rocks floating nearby. They come in five
themes:

- **Meadow**: rolling grass, oak, birch and azalea, flowers and fireflies, ponds, and glow berries hanging beneath.
- **Ember**: blackstone and basalt with patches of magma, smoking vents, basalt columns, lava pools, charred trees,
  and gilded blackstone in the rock.
- **Frost**: snowfields, ice spikes, frozen ponds, snow-laden spruce, and icicles beneath.
- **Storm**: windswept rock and grass, and stone spires crowned with lightning rods that draw the storm's strikes.
  Chains, lanterns and copper rods hang beneath.
- **Verdant**: moss, flowering azalea, pink petals, and spore blossoms and glow berries thick beneath.

**How to find one.** Look up! Some islands have a spring that pours a waterfall all the way to the ground, and Ember
islands send up tall columns of smoke. Fireflies and glow berries give them away at night.

There are three kinds of island:

- **Wild islands** hide a ruined shrine with a treasure chest: gold, gems, enchanted books, Tiny Worlds draughts,
  sometimes a saddle, a whistle, a chisel, a Shrink Ray, or even an airship.
- **Dragon roosts** have a nest made from what's on the island, a dragon's hoard (always with a saddle, sometimes
  dragon armor), and the wild dragon that guards it. Its kind matches the island's theme.
- **Pillager sky outposts** have a dark oak watchtower with a lookout chest, a dock with a pillager airship, a
  garrison, and pillagers riding dragons instead of ravagers.

**Conquering an outpost.** Once every pillager within about 48 blocks is gone (including the dragon riders and the
airship crew), their dragons are free. Freed dragons stop fighting, fly back to the island, and are easy to tame.

## Airships

Craft an airship as follows:

```
wool    wool     wool
string  furnace  string
planks  boat     planks
```

To use one:

- **Place it** on the ground and climb aboard.
- **Fuel it** with coal or charcoal (80 s), blaze rods (2 min) or coal blocks (10 min) by using them on the ship.
- **Fly it** with WASD to steer, jump to climb, and sprint to descend.
- **Out of fuel**, it drifts slowly and sinks. Left empty, it hovers where you parked it.
- **Seats**: up to four players (the first aboard pilots).
- **Cargo hold**: sneak and use the ship with an empty hand to open 27 slots of storage.
- **Balloon color**: use any dye on the ship.
- **Picking it up**: punch it a few times quickly. Stray arrows and crossbow bolts bounce off.

**Airship Cannon**: use one on your ship to mount it at the bow, then press **V** at the helm to fire where you're
looking. Each shot burns ten seconds of fuel. Cannonballs hurt whatever they hit (and anyone right beside it) but
never break blocks. Four hits sink a ship, and planks patch the holes. Craft the cannon as follows:

```
iron ingot  iron ingot  iron ingot
gunpowder   dispenser   gunpowder
```

Pillagers fly airships too, under dark balloons, and every one carries a cannon. Ground outposts launch one now and
then when a player is near, and sky outposts keep one docked. Shoot the crew off and the ship, and the plunder in
its hold, are yours to take. Or sink it with your own cannon.

## Advancements

The **Wonder Realms** tab tracks these:

- **Tiny Worlds**: shrinking, dewdrops, trading, carving and gem-crafting with pixels, defeating a bug while tiny,
  shrinking mobs (and a creeper), and sculpting at full size with a chisel.
- **Dragons**: winning an egg, hatching, taming, riding, flying above y 256, freeing an outpost's dragon, hatching
  the Ender Dragon, armoring your dragon (and in netherite), calling it with a whistle, making plants grow with
  Verdant breath, riding with a friend, and taming every kind of dragon.
- **Sky**: boarding an airship, capturing a pillager airship, dyeing a balloon, firing a cannon, and sinking a
  pillager ship.

## For developers

To build from this folder:

```
./gradlew build
```

The jar lands in `build/libs/`.

The build compiles against GeckoLib 5.5.7 for 26.3, looking for the jar at
`../mods/geckolib-fabric-26.3-5.5.7.jar`. If yours is somewhere else, point the build at it:

```
./gradlew build -Pgeckolib_jar=/path/to/geckolib-fabric-26.3-5.5.7.jar
```

Models, animations, textures, item models, recipes, loot tables, advancements and text are all generated by scripts
in `tools/`. Run them from this folder in this order:

```
python3 tools/generate_assets.py
python3 tools/generate_miner_assets.py
python3 tools/generate_airship_assets.py
python3 tools/generate_dragon_assets.py
python3 tools/generate_gear_assets.py
python3 tools/generate_data.py
```
