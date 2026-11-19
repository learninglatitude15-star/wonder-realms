# Wonder Realms

A Fabric mod for Minecraft 26.3 (needs Fabric API and GeckoLib 5.5.6 or newer) with three adventures in one:

- **Tiny Worlds**: shrink to the size of a bug.
- **Dragons**: win an egg, raise a dragon, and fly it.
- **Sky Islands & Airships**: explore floating islands, raid pillager sky outposts, and sail the skies.

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

## Dragons

Dragons live only on **sky islands**, and they come in three wild kinds:

- **Ember** breathes fire. It sets things alight, melts snow and ice, and lights campfires and candles.
- **Frost** breathes ice. It slows and freezes targets, turns water into ice you can walk on, and cools lava.
- **Storm** breathes lightning. It crackles, and sometimes calls down a real bolt.

There's a fourth, secret kind: feed the End's **Dragon Egg** chorus fruit and it may hatch an **Ender Dragon**,
whose breath lifts targets off their feet.

**Getting an egg.** Wild dragons guard roosts on sky islands. Defeat one and it drops its egg. A tamed, grown
dragon also lays an egg every two or three in-game days.

**Hatching.** Place the egg and wait. It cracks over a few minutes, then hatches a dragon that bonds with the
nearest player.

**Taming a wild dragon.** Feed it meat (Frost dragons also eat fish; Ender dragons eat chorus fruit). Wild adults
take patience and will fight back. Hatchlings and freed dragons warm to you quickly.

**Raising.** Hatchlings grow to full size over two in-game days, and feeding them speeds that up. They get bigger
and tougher as they grow, from 20 to 80 health.

**Commands.** Sneak and use an empty hand on your dragon to make it sit or follow. Tamed dragons defend you, and
follow you into the air when you're gliding, on an airship, or on another dragon.

**Riding.** Put a saddle on a grown dragon, then use it with an empty hand to climb on.

| Action | Control |
| --- | --- |
| Take off | Jump, or look up while moving forward |
| Steer | Look where you want to go |
| Climb | Hold jump |
| Dive for speed | Look down |
| Land | Come down to the ground looking level or lower |
| Breathe | **V** (rebindable under Controls, "Dragon Breath") |

## Sky Islands

Floating islands drift between y 180 and 230 in every overworld biome, roughly a hundred blocks apart. Each one has
rolling meadows, trees and flowers, a rocky underside full of ore that's richest toward the tip, glow berries and
roots trailing beneath, and small rocks floating nearby. Some have ponds.

**How to find one.** Look up! Some islands have a spring that pours a waterfall all the way to the ground. Fireflies
over the meadows and glow berries underneath give them away at night.

There are three kinds of island:

- **Wild islands** hide a ruined shrine with a treasure chest: gold, gems, enchanted books, Tiny Worlds draughts,
  sometimes a saddle or even an airship.
- **Dragon roosts** have a hay nest strewn with bones, a dragon's hoard (always with a saddle), and the wild dragon
  that guards it.
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
- **Picking it up**: punch it a few times quickly. Stray arrows and crossbow bolts bounce off.

Pillagers fly airships too. Ground outposts launch one now and then when a player is near, and sky outposts keep one
docked. Shoot the crew off, and the ship and the plunder in its hold are yours to take.

## Advancements

The **Wonder Realms** tab tracks these:

- **Tiny Worlds**: shrinking, dewdrops, trading, carving and gem-crafting with pixels, and defeating a bug while tiny.
- **Dragons**: winning an egg, hatching, taming, riding, flying above y 256, freeing an outpost's dragon, and
  hatching the Ender Dragon.
- **Sky**: boarding an airship, and capturing a pillager airship.

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

Models, animations, textures, item models, loot tables, advancements and text are all generated by scripts in
`tools/`. Run them from this folder in this order:

```
python3 tools/generate_assets.py
python3 tools/generate_miner_assets.py
python3 tools/generate_airship_assets.py
python3 tools/generate_dragon_assets.py
python3 tools/generate_data.py
```
