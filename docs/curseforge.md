# CurseForge release kit (Voltworks Industrial)

Everything needed to create the Voltworks project and upload the first file. Copy the fields below into the CurseForge author console.

## Project settings

| Field | Value |
|---|---|
| Name | Voltworks Industrial |
| Summary (one line) | Industrial tech: power networks, ore crushers, electric furnaces, power tools and a nuclear reactor. |
| Game / type | Minecraft / Mods |
| Main category | Technology |
| Extra categories | Energy, Ore Processing |
| License | MIT License |
| Logo | `docs/images/logo.png` (400×400) |
| Screenshots | `docs/images/test-world.png`, `crusher-gui.png`, `reactor-gui.png`, `tools-preview.png`, `ore-preview.png` |
| Source link | Your GitHub repo URL, if you publish one (optional) |

## First file upload

| Field | Value |
|---|---|
| File | `build/libs/voltworks-0.1.0.jar` (not the `-sources` jar) |
| Display name | Voltworks Industrial 0.1.0 |
| Release type | Beta |
| Game version | 26.3 |
| Mod loader | Fabric |
| Java version | Java 25 |
| Environment | Client and Server |
| Related project | **Fabric API** — Required Dependency |
| Changelog | See below |

### Changelog for 0.1.0

```
First public release.
- Power: Generator, Solar Panel, Thermal Generator, Reactor Chamber; Copper Cable; Small, Medium, Large and Huge Batteries
- Machines: Crusher, Electric Furnace, Alloy Smelter, Industrial Crusher, Induction Furnace, Uranium Refiner
- Machine upgrades: Speed, Capacity, Ejector
- Power tools: Electric Drill, Chainsaw (tree felling), Advanced Drill (3x3 mode), Charger
- Logistics: glass Item Pipes in three speeds (watch the items travel), Filter Pipe, Wrench
- Computer storage: Hard Drives (1K-64K), Drive Rack, Data Cable, searchable Storage Terminal, Computer Crafter
- New ores: Tin, Lead, Uranium
```

## Project description (paste into the description editor, Markdown mode)

```markdown
# Voltworks Industrial

**Build a power grid, automate your ore processing, and run a nuclear reactor — without blowing up your base. (Probably.)**

Voltworks Industrial is a tech mod for Fabric. Generate power, carry it through copper cables, and feed it to machines that double (or triple) your ore output.

## ⚡ Power
- **Generator** — burns any furnace fuel for 10 J/t (one coal = 4,000 J).
- **Solar Panel** — free power in daylight.
- **Thermal Generator** — sits next to lava source blocks: 5 J/t per source, up to 25 J/t.
- **Reactor Chamber** — the big one. See below.
- **Copper Cable** connects everything; **Batteries** come in four sizes, from 25,000 J to 10,000,000 J. Machines get power first, batteries get the surplus.

## 🏭 Machines
| Machine | What it does |
|---|---|
| Crusher | Ore or raw ore → 2 dust (smelt dust into ingots: double output) |
| Electric Furnace | Smelts anything, 2× furnace speed |
| Alloy Smelter | Iron + 2 coal → **Steel**. Also makes netherite. |
| Industrial Crusher | 3 dust per ore, 4× faster |
| Induction Furnace | Two independent lanes, each 4× furnace speed |

Every machine has **3 upgrade slots**:
- **Speed Upgrade** — 30% faster each (uses more power)
- **Capacity Upgrade** — +4,000 J internal storage each
- **Ejector Upgrade** — pushes finished items into the next machine or chest. Put a Crusher next to an Electric Furnace and ore turns into ingots by itself.

## ☢️ Nuclear reactor
The Reactor Chamber has a 3×3 grid for **Fuel Rods** and **Heat Vents**.
- Each rod pulses once, **plus once for every rod beside it** — clusters make far more power… and far more heat.
- Heat Vents cool more when placed next to rods; water around the reactor helps too.
- It only runs with a **redstone signal**, and a **comparator** reads its heat — build an automatic shutdown!
- Let it reach 100% heat and it **melts down**. Spectacularly.

## ⛏️ Power tools
- **Electric Drill** — pickaxe + shovel, faster than diamond.
- **Chainsaw** — cuts wood and leaves, and fells a whole tree from one cut (sneak to cut a single log).
- **Advanced Drill** — diamond tier with a **3×3 mining mode** (sneak + right-click).
- Tools never break: recharge them in the **Charger**.

## 🪨 New ores
**Tin** (mid-depth), **Lead** (deep) and **Uranium** (very deep, faintly glowing). All work with the Crusher for double output, and use common `c:` tags for compatibility with other mods.

## Requirements
- Minecraft 26.3
- Fabric Loader
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)

Modpacks: yes, go ahead! Voltworks Industrial is MIT licensed.
```

## Before you click upload

- [ ] `./gradlew build` and grab the fresh jar from `build/libs/`
- [x] Name: "Voltworks" was taken on CurseForge, so the project is "Voltworks Industrial"
- [ ] Test the jar once in a normal Minecraft install (Fabric Loader + Fabric API) to be sure it loads outside the dev environment
