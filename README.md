# Voltworks Industrial

An industrial tech mod for Minecraft **26.3** (Fabric). Generate power, run it through copper cables, and process ores with powered machines — up to a nuclear reactor.

![Test world](docs/images/test-world.png)

## Features

**Power** (measured in J)
- **Generator** — burns furnace fuel, 10 J/t (one coal = 4,000 J). **Solar Panel** — 1 J/t in daylight. **Thermal Generator** — 5 J/t per adjacent lava source.
- **Reactor Chamber** — 3×3 grid of Fuel Rods (Refined Uranium + lead) and Heat Vents; needs a redstone signal; adjacent rods give more power *and* heat; pauses when its buffer is full; melts down at 100% heat. A comparator reads its heat. Lead Blocks resist blasts (40, vs 6 for iron): two layers of them contain a meltdown. Server admins can change meltdowns with `/gamerule voltworks:reactor_meltdown destructive|contained|off` (default `destructive`; `contained` hurts nearby entities but breaks no blocks; `off` shuts the reactor down at 100% heat until it cools to 90%).
- **Copper Cable** carries power. **Batteries**: Small 40,000 J (128 J/t) · Medium 100,000 J (512 J/t) · Large 1,000,000 J (2,048 J/t) · Huge 10,000,000 J (8,192 J/t). Power goes to machines first, then batteries.

**Machines**
- Tier 1: **Crusher** (ore → 2 dust), **Electric Furnace**, **Alloy Smelter** (iron + 2 coal → steel).
- Tier 2: **Industrial Crusher** (3 dust per ore, 4× speed), **Induction Furnace** (2 lanes, 4× speed).
- **Uranium Refiner** (tier 2) — uranium can't be smelted or crushed; the refiner enriches Raw Uranium (or uranium ore) into **Refined Uranium** for fuel rods (16 J/t, 300 ticks each), and reprocesses a spent fuel rod into one Refined Uranium.
- **Upgrades** (3 slots per machine): Speed, Capacity, Ejector (auto-outputs into adjacent inventories/machines).

**Logistics**
- **Item Pipes** — clear glass pipes linking chests, hoppers and machines; you can watch the items travel through them. Pipes pull finished items out of machines into chests automatically (machine outputs never go into other machines this way, so nothing loops). Start a line at a chest and its first pipe pulls items out of it (Extract, striped collar) as long as that line doesn't already reach another inventory, so later chests on the line are destinations; items go to the nearest inventory with room. Sneak + right-click the arm with the wrench to switch Extract on or off. An arm cut off with the wrench shows a red-barred cap you can click to reconnect.
  - **Item Pipe** (tin) 4 items/s · **Fast Item Pipe** (steel, red rims) 16 items/s · **Express Item Pipe** (gold, gold rims) 64 items/s and near-instant travel. A line runs at its slowest pipe's rate, so upgrade the whole line.
  - **Filter Pipe** (purple) — right-click to set up to 9 items and Allow / Deny. Only matching items pass through it, so a chest behind one only receives those.
- **Wrench** — rotate blocks, set each machine side to None / Input / Output / Both, right-click a pipe arm to connect / disconnect it, sneak + right-click a pipe arm on an inventory (or the face of the inventory it plugs into) to toggle Extract, and sneak + right-click other Voltworks blocks (not ores) to pick them up.
- **Voltworks Guide** — in-game guide (book + copper ingot): a contents page and short topic pages with pictures of example builds and item icons (hover for names). Text lives in `tools/guide.txt`; addons add pages with `GuideScreen.addPages(...)`.
- Machines show their charge on a front gauge.

**Computer Storage**
- **Silicon** (Alloy Smelter: 2 sand or red sand + coal, or quartz + coal) → **Silicon Chips**.
- **Hard Drives** — Small 1K, Medium 4K, Large 16K, Huge 64K items, up to 63 kinds each. Contents stay on the drive when you take it out.
- **Drive Rack** — 8 drives; needs power (1 J/t + 1 per drive). **Data Cable** links racks, terminals and crafters (touching works too). Expand by adding drives, bigger drives or more racks.
- **Storage Terminal** — search and browse everything stored; click to take, shift-click to your inventory, click holding an item (or shift-click from your inventory) to store.
- **Storage Interface** — connects item pipes to storage: an Extract arm (sneak + wrench) pulls items out — put a Filter Pipe there to choose which, e.g. Raw Tin to a crusher — and plain arms put items in, so a second pipe brings the crusher's dust back.
- **Computer Crafter** — set a 3×3 pattern from the storage list and craft x1 / x8 / x64 straight from storage; results go back in.

**Tools** — Electric Drill, Chainsaw (fells whole trees; sneak to cut one log), Advanced Drill (sneak + right-click for 3×3 mining). Run on energy, recharge in the **Charger**.

**Steel tools** — Steel Sword, Pickaxe, Axe, Shovel, Hoe and Spear: between iron and diamond (iron's mining level, 750 durability, faster and harder-hitting than iron). Vanilla tool recipes with Steel Ingots; repaired with steel.

**Ores** — Tin, Lead (raw ores, dusts, ingots, storage blocks) and Uranium (raw ore, deep underground below Y 0, refined in the Uranium Refiner); `c:` convention tags.

## Building and running

Requires JDK 25 (the path is set in `gradle.properties` via `org.gradle.java.home` — change it if your JDK lives elsewhere).

```bash
./gradlew build            # jar in build/libs/
./gradlew runClient        # dev client with the mod
./gradlew runClientGameTest  # automated in-world test (see below)
```

To play: put `build/libs/voltworks-<version>.jar` and Fabric API in your `mods` folder.

## Automated test

`src/gametest/.../VoltworksClientGameTest.java` launches the game, creates a normal world (fixed seed), builds every machine setup, runs 700 ticks, and checks the results: machine outputs, power flow, battery charging, ore generation, loot tables, tool tiers, 3×3 drilling, upgrades, item pipes (tiers, filters, items in flight), the wrench, a safe reactor, an unpowered reactor, an idle reactor that saves fuel and a deliberate meltdown, a meltdown inside two layers of lead, plus a contained meltdown and an emergency shutdown with the meltdown gamerule changed. It also saves screenshots to `build/run/clientGameTest/screenshots/`.

## Assets

Textures, models, recipes, loot tables, tags, worldgen and translations are generated by `tools/GenAssets.java`:

```bash
java tools/GenAssets.java src/main/resources
java tools/GenIcon.java .
```

The guide's pictures are photos of small example builds that the game test sets up (`GuideScenes.java`). After changing a scene, run the test, then copy the new shots into the mod:

```bash
java tools/GuideImages.java build/run/clientGameTest/screenshots src/main/resources
```

In `tools/guide.txt` a page starts with its title; `@image name` adds a picture and `@items id id ...` adds item icons.

All art is original: ores sit on procedurally generated stone and deepslate. (You *can* pass the Minecraft client jar as a second argument to draw ores over vanilla stone instead, but released builds don't, so no Mojang textures ship in the jar.) `GenIcon` draws the mod icon and the 400×400 CurseForge logo.

![Tools](docs/images/tools-preview.png)

## Publishing

See [docs/curseforge.md](docs/curseforge.md) for the CurseForge project settings, upload fields, changelog and page description.

## License

MIT — see [LICENSE](LICENSE).
