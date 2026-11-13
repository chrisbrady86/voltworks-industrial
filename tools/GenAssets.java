import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Generates Voltworks textures + JSON assets/data.
 * Usage: java GenAssets.java <src/main/resources> [minecraft client jar]
 * If the client jar is given, ore textures are drawn on top of vanilla stone/deepslate (read at generation
 * time, never copied into the repo as-is); otherwise a procedural stone is used.
 */
public class GenAssets {
	static Path res;
	static final String NS = "voltworks";
	static BufferedImage stoneBase, deepslateBase;

	public static void main(String[] args) throws Exception {
		res = Paths.get(args[0]);
		if (args.length > 1) {
			try (var zip = new java.util.zip.ZipFile(args[1])) {
				stoneBase = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/stone.png")));
				deepslateBase = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/deepslate.png")));
			}
		}
		textures();
		models();
		data();
		ores();
		tools();
		upgrades();
		nuclear();
		System.out.println("done");
	}

	// ---------------- nuclear ----------------
	static void nuclear() throws Exception {
		// Fuel rod: lead tube with a glowing green core; depleted = dark core
		for (boolean spent : new boolean[]{false, true}) {
			png("item/" + (spent ? "depleted_" : "") + "fuel_rod", (x, y, r) -> {
				if (x < 5 || x > 10 || y < 1 || y > 14) return 0;
				if (x == 5 || x == 10 || y == 1 || y == 14) return rgb(28, 28, 32);
				if (y == 2 || y == 13) return rgb(150, 150, 170);                 // end caps
				if (x >= 7 && x <= 8) return spent ? rgb(50, 60, 45) : rgb(150 + r.nextInt(60), 255, 90);
				return shade(rgb(104, 100, 140), x == 6 ? 25 : 0);                // lead casing
			});
		}
		// Heat vent: steel frame with fan blades
		png("item/heat_vent", (x, y, r) -> {
			if (x < 1 || x > 14 || y < 1 || y > 14) return 0;
			if (x == 1 || x == 14 || y == 1 || y == 14) return rgb(28, 28, 32);
			if (x == 2 || x == 13 || y == 2 || y == 13) return rgb(150, 156, 168);
			double dx = x - 7.5, dy = y - 7.5, d = Math.hypot(dx, dy);
			if (d < 1.6) return rgb(90, 90, 96);
			double a = Math.atan2(dy, dx) + d * 0.35;
			return (Math.sin(a * 4) > 0.2 && d < 5.6) ? rgb(185, 190, 200) : rgb(40, 44, 52);
		});
		for (String item : List.of("fuel_rod", "depleted_fuel_rod", "heat_vent")) {
			write("assets/voltworks/models/item/" + item + ".json",
					"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/" + item + "\" } }");
			itemDef(item, "voltworks:item/" + item);
		}

		// Reactor chamber: thick porthole showing the core, hazard-striped casing
		for (boolean on : new boolean[]{false, true}) {
			png("block/reactor_chamber_front" + (on ? "_on" : ""), (x, y, r) -> {
				double d = Math.hypot(x - 7.5, y - 7.0);
				if (d < 3.6) return on ? rgb(120 + r.nextInt(60), 255, 110 + r.nextInt(40)) : rgb(30, 50, 34);
				if (d < 4.9) return rgb(104, 100, 140);
				if (y >= 13 && y <= 14 && x >= 1 && x <= 14) return ((x + y) / 2 % 2 == 0) ? rgb(240, 200, 30) : rgb(25, 25, 25);
				return casing2(x, y, r);
			});
		}
		machine("reactor_chamber", true);

		shaped("fuel_rod", 1, new String[]{"LUL", "LUL", "LUL"}, "L", "voltworks:lead_ingot", "U", "voltworks:uranium_ingot");
		shaped("heat_vent", 1, new String[]{"SBS", "BCB", "SBS"}, "S", "voltworks:steel_ingot", "B", "minecraft:iron_bars",
				"C", "minecraft:copper_ingot");
		shaped("reactor_chamber", 1, new String[]{"LCL", "CAC", "LCL"}, "L", "voltworks:lead_block", "C", "voltworks:circuit",
				"A", "voltworks:advanced_machine_frame");
	}

	// ---------------- machine upgrades ----------------
	/** Upgrade card: dark circuit board with gold contacts along the bottom and an icon in the middle. */
	static Painter upgradeCard(Painter icon) {
		return (x, y, r) -> {
			if (x < 2 || x > 13 || y < 1 || y > 14) return 0;
			if (x == 2 || x == 13 || y == 1 || y == 14) return rgb(28, 28, 32);
			if (y == 13 && x % 2 == 1) return rgb(230, 180, 60);                  // edge connector
			int ic = icon.at(x, y, r);
			if (ic != 0) return ic;
			return shade(rgb(40, 70, 60), (x + y) % 4 == 0 ? 10 : 0);
		};
	}

	static void upgrades() throws Exception {
		// Speed: two green chevrons pointing right
		png("item/speed_upgrade", upgradeCard((x, y, r) -> {
			for (int c : new int[]{5, 9}) {
				int d = Math.abs(y - 7);
				if (d <= 3 && (x == c + 2 - d || x == c + 3 - d)) return rgb(90, 230, 90);
			}
			return 0;
		}));
		// Capacity: a red battery with charge bars
		png("item/capacity_upgrade", upgradeCard((x, y, r) -> {
			if (y == 3 && (x == 7 || x == 8)) return rgb(200, 200, 205);
			if (inRect(x, y, 5, 4, 10, 11)) {
				if (x == 5 || x == 10 || y == 4 || y == 11) return rgb(200, 200, 205);
				return y % 2 == 0 ? rgb(230, 70, 60) : rgb(120, 40, 35);
			}
			return 0;
		}));
		// Ejector: a yellow arrow coming out of a box
		png("item/ejector_upgrade", upgradeCard((x, y, r) -> {
			if (inRect(x, y, 4, 6, 7, 10) && (x == 4 || y == 6 || y == 10)) return rgb(200, 200, 205);
			if (y == 8 && x >= 6 && x <= 11) return rgb(250, 210, 60);
			if (Math.abs(y - 8) == 1 && x == 10 || Math.abs(y - 8) == 2 && x == 9) return rgb(250, 210, 60);
			return 0;
		}));
		for (String item : List.of("speed_upgrade", "capacity_upgrade", "ejector_upgrade")) {
			write("assets/voltworks/models/item/" + item + ".json",
					"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/" + item + "\" } }");
			itemDef(item, "voltworks:item/" + item);
		}
		// All use a circuit + tin; the centre ingredient picks the upgrade.
		shapedN("speed_upgrade", 2, new String[]{"TRT", "CXC"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:gold_ingot");
		shapedN("capacity_upgrade", 2, new String[]{"TRT", "CXC"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "voltworks:power_cell");
		shapedN("ejector_upgrade", 2, new String[]{"TRT", "CXC"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:hopper");
	}

	static void shapedN(String result, int count, String[] pattern, String... keys) throws Exception {
		shaped(result, count, pattern, keys);
	}

	// ---------------- power tools & charger ----------------
	/** A thick line segment layer for drawing tool sprites; radius tapers from r0 to r1. */
	record Seg(double x0, double y0, double x1, double y1, double r0, double r1, Painter color) {
		double dist(int px, int py, double[] tOut) {
			double dx = x1 - x0, dy = y1 - y0;
			double t = Math.max(0, Math.min(1, ((px + 0.5 - x0) * dx + (py + 0.5 - y0) * dy) / (dx * dx + dy * dy)));
			tOut[0] = t;
			double cx = x0 + t * dx, cy = y0 + t * dy;
			return Math.hypot(px + 0.5 - cx, py + 0.5 - cy);
		}
	}

	/** Draws segments (later ones on top), then a dark 1px outline so the sprite reads well in hand. */
	static void toolPng(String path, Seg... segs) throws Exception {
		int[][] px = new int[16][16];
		Random r = new Random(path.hashCode());
		double[] t = new double[1];
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			for (Seg s : segs) {
				double d = s.dist(x, y, t);
				if (d <= s.r0() + (s.r1() - s.r0()) * t[0]) px[y][x] = s.color().at(x, y, r);
			}
		}
		png(path, (x, y, rr) -> {
			if (px[y][x] != 0) return px[y][x];
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + d[0], ny = y + d[1];
				if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[ny][nx] != 0) return rgb(28, 28, 32);
			}
			return 0;
		});
	}

	static Painter flat(int c) { return (x, y, r) -> shade(c, (x + y) % 5 == 0 ? 12 : 0); }
	static Painter spiral(int c) { return (x, y, r) -> (x + y) % 3 == 0 ? shade(c, -45) : shade(c, (x - y) % 2 == 0 ? 25 : 0); }

	static void tools() throws Exception {
		int grip = rgb(55, 55, 60);
		toolPng("item/electric_drill",
				new Seg(2, 14, 6, 10, 1.6, 1.6, flat(grip)),
				new Seg(5, 11, 9, 7, 2.6, 2.4, flat(rgb(230, 180, 40))),
				new Seg(9, 7, 15, 1, 1.5, 0.4, spiral(rgb(190, 195, 205))));
		toolPng("item/advanced_drill",
				new Seg(2, 14, 6, 10, 1.6, 1.6, flat(grip)),
				new Seg(5, 11, 9, 7, 2.8, 2.6, flat(rgb(64, 96, 170))),
				new Seg(7, 9, 8, 8, 0.9, 0.9, flat(rgb(240, 170, 50))),
				new Seg(9, 7, 15, 1, 1.7, 0.4, spiral(rgb(110, 230, 220))));
		toolPng("item/chainsaw",
				new Seg(2, 14, 5, 11, 1.5, 1.5, flat(grip)),
				new Seg(4, 12, 8, 8, 2.8, 2.6, flat(rgb(225, 105, 35))),
				new Seg(8, 8, 14.5, 1.5, 1.6, 1.4, (x, y, r) -> ((x + y) % 2 == 0) ? rgb(70, 70, 76) : rgb(185, 190, 198)));
		png("item/power_cell", (x, y, r) -> {
			if (y == 2 && x >= 7 && x <= 8) return rgb(200, 120, 70);              // terminal
			if (y < 3 || y > 14 || x < 5 || x > 10) return 0;
			if (x == 5 || x == 10 || y == 3 || y == 14) return rgb(28, 28, 32);     // outline
			if (y >= 6 && y <= 9) return (x == 7 || x == 8) && y != 6 && y != 9 ? rgb(250, 220, 60) : rgb(230, 230, 235); // label + bolt
			return shade(rgb(70, 75, 85), x == 6 ? 30 : 0);                         // lead casing
		});
		for (String item : List.of("electric_drill", "advanced_drill", "chainsaw")) {
			write("assets/voltworks/models/item/" + item + ".json",
					"{ \"parent\": \"minecraft:item/handheld\", \"textures\": { \"layer0\": \"voltworks:item/" + item + "\" } }");
			itemDef(item, "voltworks:item/" + item);
		}
		write("assets/voltworks/models/item/power_cell.json",
				"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/power_cell\" } }");
		itemDef("power_cell", "voltworks:item/power_cell");

		// Charger: casing with a lightning bolt that lights up while charging
		for (boolean on : new boolean[]{false, true}) {
			png("block/charger_front" + (on ? "_on" : ""), (x, y, r) -> {
				boolean bolt = (y >= 3 && y <= 7 && x == 10 - (y - 3)) || (y >= 3 && y <= 7 && x == 11 - (y - 3))
						|| (y == 8 && x >= 6 && x <= 10) || (y >= 9 && y <= 12 && (x == 9 - (y - 9) || x == 10 - (y - 9)));
				if (bolt) return on ? rgb(255, 235, 90) : rgb(120, 110, 60);
				if (inRect(x, y, 3, 2, 12, 13)) return rgb(36, 40, 48);
				return casing(x, y, r);
			});
		}
		machine("charger", true);
		selfLoot("charger");

		shaped("power_cell", 1, new String[]{" C ", "LRL", "LRL"}, "C", "voltworks:copper_cable", "L", "voltworks:lead_ingot",
				"R", "minecraft:redstone");
		shaped("electric_drill", 1, new String[]{" I ", "IRI", "IBI"}, "I", "minecraft:iron_ingot", "R", "minecraft:redstone",
				"B", "voltworks:power_cell");
		shaped("chainsaw", 1, new String[]{" II", "IRI", "BI "}, "I", "minecraft:iron_ingot", "R", "minecraft:redstone",
				"B", "voltworks:power_cell");
		shaped("advanced_drill", 1, new String[]{" D ", "DXD", "CSC"}, "D", "minecraft:diamond", "X", "voltworks:electric_drill",
				"C", "voltworks:circuit", "S", "voltworks:steel_ingot");
		shaped("charger", 1, new String[]{"CBC", "RMR", "CCC"}, "C", "minecraft:copper_ingot", "B", "voltworks:power_cell",
				"R", "minecraft:redstone", "M", "voltworks:machine_frame");
	}

	// ---------------- ores & metals ----------------
	record Metal(String name, int color, String toolTag, int veinSize, int veinsPerChunk, int minY, int maxY) {
	}

	static final List<Metal> METALS = List.of(
			// Tin: common, mid-depth, stone pickaxe. Lead: rarer, deep, iron pickaxe.
			new Metal("tin", rgb(214, 222, 230), "needs_stone_tool", 9, 12, -16, 96),
			new Metal("lead", rgb(104, 100, 140), "needs_iron_tool", 8, 7, -64, 40),
			// Uranium: rare and deep, iron pickaxe, faintly glowing.
			new Metal("uranium", rgb(120, 210, 70), "needs_iron_tool", 4, 4, -64, -8));
	static final List<String> ORE_BLOCKS = List.of("tin_ore", "deepslate_tin_ore", "lead_ore", "deepslate_lead_ore", "uranium_ore", "deepslate_uranium_ore");

	static int baseStone(int x, int y, Random r, boolean deep) {
		BufferedImage base = deep ? deepslateBase : stoneBase;
		if (base != null) return base.getRGB(x, y);
		return deep ? originalDeepslate(x, y) : originalStone(x, y);
	}

	/** Smooth tileable value noise (period 16) so the stone wraps seamlessly between blocks. */
	static double noise(int x, int y, int cell, long seed) {
		int n = 16 / cell;
		double fx = (double) x / cell, fy = (double) y / cell;
		int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
		double tx = fx - x0, ty = fy - y0;
		tx = tx * tx * (3 - 2 * tx);
		ty = ty * ty * (3 - 2 * ty);
		double a = hash(Math.floorMod(x0, n), Math.floorMod(y0, n), seed), b = hash(Math.floorMod(x0 + 1, n), Math.floorMod(y0, n), seed);
		double c = hash(Math.floorMod(x0, n), Math.floorMod(y0 + 1, n), seed), d = hash(Math.floorMod(x0 + 1, n), Math.floorMod(y0 + 1, n), seed);
		return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
	}

	static double hash(int x, int y, long seed) {
		long h = seed ^ (x * 374761393L) ^ (y * 668265263L);
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
	}

	/** Original stone: mottled grey from two octaves of noise plus fine grain. */
	static int originalStone(int x, int y) {
		double v = 0.6 * noise(x, y, 8, 11) + 0.3 * noise(x, y, 4, 12) + 0.1 * noise(x, y, 2, 13);
		int d = (int) Math.round((v - 0.5) * 44) + (hash(x, y, 14) > 0.85 ? -10 : 0);
		return shade(rgb(124, 124, 126), d);
	}

	/** Original deepslate: darker, bluish, with horizontal strata. */
	static int originalDeepslate(int x, int y) {
		double strata = noise(x * 2 % 16, y, 2, 21);
		double v = 0.5 * noise(x, y, 8, 22) + 0.5 * strata;
		int d = (int) Math.round((v - 0.5) * 36) + (y % 4 == 0 ? -6 : 0) + (hash(x, y, 23) > 0.9 ? 8 : 0);
		return shade(rgb(74, 74, 82), d);
	}

	/** Ore speckles: a few 2-3 pixel clusters with a highlight and a shadow. */
	static boolean[][] oreMask(long seed) {
		boolean[][] m = new boolean[16][16];
		Random r = new Random(seed);
		int[][] centers = {{3, 3}, {11, 2}, {7, 7}, {2, 11}, {12, 10}, {7, 13}};
		for (int[] c : centers) {
			int cx = c[0] + r.nextInt(2), cy = c[1] + r.nextInt(2);
			m[cy][cx] = true;
			if (cx + 1 < 16) m[cy][cx + 1] = true;
			if (cy + 1 < 16) m[cy + 1][cx] = true;
			if (r.nextBoolean() && cx + 1 < 16 && cy + 1 < 16) m[cy + 1][cx + 1] = true;
		}
		return m;
	}

	static void ores() throws Exception {
		for (Metal m : METALS) {
			String n = m.name();
			boolean[][] mask = oreMask(n.hashCode());
			for (boolean deep : new boolean[]{false, true}) {
				String block = (deep ? "deepslate_" : "") + n + "_ore";
				png("block/" + block, (x, y, r) -> {
					if (mask[y][x]) {
						boolean top = y == 0 || !mask[y - 1][x];
						return shade(m.color(), top ? 25 : (x < 15 && !mask[y][x + 1] ? -30 : 0));
					}
					return baseStone(x, y, r, deep);
				});
				cubeAll(block);
			}
			// Storage block: riveted plates in the metal colour
			png("block/" + n + "_block", (x, y, r) -> casing(x, y, r, m.color(), shade(m.color(), 40)));
			cubeAll(n + "_block");
			// Raw ore lump, ingot, dust
			png("item/raw_" + n, (x, y, r) -> {
				double dx = (x - 7.5) / 6.0, dy = (y - 8.5) / 5.5;
				double d = dx * dx + dy * dy + Math.sin(x * 1.7 + y) * 0.12;
				if (d > 1) return 0;
				return shade(m.color(), (d > 0.75 ? -45 : 0) + (y < 7 ? 15 : 0) + r.nextInt(25) - 18);
			});
			ingot("item/" + n + "_ingot", m.color());
			dust("item/" + n + "_dust", m.color());
			for (String item : List.of("raw_" + n, n + "_ingot", n + "_dust")) {
				write("assets/voltworks/models/item/" + item + ".json",
						"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/" + item + "\" } }");
				itemDef(item, "voltworks:item/" + item);
			}

			// Loot: silk touch drops the ore, otherwise raw ore with Fortune
			for (String ore : List.of(n + "_ore", "deepslate_" + n + "_ore")) {
				write("data/voltworks/loot_table/blocks/" + ore + ".json", """
					{ "type": "minecraft:block", "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:alternatives", "children": [
					  { "type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": "voltworks:%s" },
					  { "type": "minecraft:item", "name": "voltworks:raw_%s", "modifier": [
					    { "type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops" },
					    { "type": "minecraft:explosion_decay" } ] } ] } ] } ],
					  "random_sequence": "voltworks:blocks/%s" }
					""".formatted(ore, n, ore));
			}
			selfLoot(n + "_block");

			// Smelting: raw ore, ore blocks and dust all give an ingot
			for (String in : List.of("raw_" + n, n + "_ore", "deepslate_" + n + "_ore", n + "_dust")) {
				for (String type : List.of("smelting", "blasting")) {
					write("data/voltworks/recipe/" + n + "_ingot_from_" + type + "_" + in + ".json", """
						{ "type": "minecraft:%s", "cookingtime": %d, "experience": 0.7,
						  "ingredient": "voltworks:%s", "result": { "id": "voltworks:%s_ingot" } }
						""".formatted(type, type.equals("smelting") ? 200 : 100, in, n));
				}
			}
			storageRecipes(n + "_ingot", n + "_block");

			// Worldgen: configured feature (vein) + placed feature (how many, how deep)
			write("data/voltworks/worldgen/feature/ore_" + n + ".json", """
				{ "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": %d, "targets": [
				  { "state": "voltworks:%s_ore", "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables" } },
				  { "state": "voltworks:deepslate_%s_ore", "target": { "predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables" } } ] }
				""".formatted(m.veinSize(), n, n));
			write("data/voltworks/worldgen/placed_feature/ore_" + n + ".json", """
				{ "feature": "voltworks:ore_%s", "placement": [
				  { "type": "minecraft:count", "count": %d },
				  { "type": "minecraft:in_square" },
				  { "type": "minecraft:height_range", "height": { "type": "minecraft:trapezoid",
				    "min_inclusive": { "absolute": %d }, "max_inclusive": { "absolute": %d } } },
				  { "type": "minecraft:biome" } ] }
				""".formatted(n, m.veinsPerChunk(), m.minY(), m.maxY()));

			// Convention tags so other mods recognise the materials
			tag("item", "c", "ingots/" + n, "voltworks:" + n + "_ingot");
			tag("item", "c", "raw_materials/" + n, "voltworks:raw_" + n);
			tag("item", "c", "dusts/" + n, "voltworks:" + n + "_dust");
			tag("block", "c", "ores/" + n, "voltworks:" + n + "_ore", "voltworks:deepslate_" + n + "_ore");
			tag("item", "c", "ores/" + n, "voltworks:" + n + "_ore", "voltworks:deepslate_" + n + "_ore");
			tag("block", "c", "storage_blocks/" + n, "voltworks:" + n + "_block");
			tag("item", "c", "storage_blocks/" + n, "voltworks:" + n + "_block");
		}

		// Steel storage block
		cubeAllWithTexture("steel_block", (x, y, r) -> casing(x, y, r, rgb(110, 116, 128), rgb(170, 175, 185)));
		selfLoot("steel_block");
		storageRecipes("steel_ingot", "steel_block");
		tag("item", "c", "ingots/steel", "voltworks:steel_ingot");
		tag("block", "c", "storage_blocks/steel", "voltworks:steel_block");

		// Mining tiers
		tag("block", "minecraft", "needs_stone_tool", "voltworks:tin_ore", "voltworks:deepslate_tin_ore", "voltworks:tin_block");
		tag("block", "minecraft", "needs_iron_tool", "voltworks:lead_ore", "voltworks:deepslate_lead_ore", "voltworks:lead_block",
				"voltworks:uranium_ore", "voltworks:deepslate_uranium_ore", "voltworks:uranium_block",
				"voltworks:steel_block");
	}

	static void cubeAll(String block) throws Exception {
		write("assets/voltworks/models/block/" + block + ".json",
				"{ \"parent\": \"minecraft:block/cube_all\", \"textures\": { \"all\": \"voltworks:block/" + block + "\" } }");
		write("assets/voltworks/blockstates/" + block + ".json",
				"{ \"variants\": { \"\": { \"model\": \"voltworks:block/" + block + "\" } } }");
		itemDef(block, "voltworks:block/" + block);
	}

	static void cubeAllWithTexture(String block, Painter p) throws Exception {
		png("block/" + block, p);
		cubeAll(block);
	}

	static void selfLoot(String b) throws Exception {
		write("data/voltworks/loot_table/blocks/" + b + ".json", """
			{ "type": "minecraft:block", "pools": [ { "rolls": 1,
			  "condition": { "type": "minecraft:survives_explosion" },
			  "entries": [ { "type": "minecraft:item", "name": "voltworks:%s" } ] } ],
			  "random_sequence": "voltworks:blocks/%s" }
			""".formatted(b, b));
	}

	static void storageRecipes(String ingot, String block) throws Exception {
		write("data/voltworks/recipe/" + block + ".json", """
			{ "type": "minecraft:crafting_shaped", "pattern": ["###", "###", "###"],
			  "key": { "#": "voltworks:%s" }, "result": { "id": "voltworks:%s" } }
			""".formatted(ingot, block));
		write("data/voltworks/recipe/" + ingot + "_from_" + block + ".json", """
			{ "type": "minecraft:crafting_shapeless", "ingredients": ["voltworks:%s"],
			  "result": { "id": "voltworks:%s", "count": 9 } }
			""".formatted(block, ingot));
	}

	static void tag(String kind, String ns, String path, String... values) throws Exception {
		List<String> v = new ArrayList<>();
		for (String s : values) v.add(q(s));
		write("data/" + ns + "/tags/" + kind + "/" + path + ".json", "{ \"replace\": false, \"values\": [" + String.join(", ", v) + "] }");
	}

	// ---------------- textures ----------------
	interface Painter { int at(int x, int y, Random r); }

	static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
	static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
	static int shade(int c, int d) { return rgb(((c >> 16) & 255) + d, ((c >> 8) & 255) + d, (c & 255) + d); }

	static void png(String path, Painter p) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(path.hashCode());
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, p.at(x, y, r));
		File f = res.resolve("assets/" + NS + "/textures/" + path + ".png").toFile();
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}

	static final int STEEL = rgb(132, 138, 146);

	/** Steel casing: bevelled border, rivets in corners, light noise. */
	static int casing(int x, int y, Random r) {
		return casing(x, y, r, STEEL, shade(STEEL, 55));
	}

	static int casing(int x, int y, Random r, int base, int rivet) {
		if (x == 0 || y == 0) return shade(base, 40);
		if (x == 15 || y == 15) return shade(base, -50);
		if (x == 1 || y == 1 || x == 14 || y == 14) return shade(base, -12);
		if ((x == 2 || x == 13) && (y == 2 || y == 13)) return rivet;
		return shade(base, r.nextInt(9) - 4);
	}

	/** Tier 2 casing: dark blue steel with amber rivets and a hazard stripe along the bottom. */
	static final int ADV = rgb(64, 78, 98);
	static int casing2(int x, int y, Random r) {
		if (y == 14 && x > 0 && x < 15) return ((x / 2) % 2 == 0) ? rgb(230, 180, 40) : rgb(30, 30, 30);
		return casing(x, y, r, ADV, rgb(240, 170, 50));
	}

	/** Brick-trimmed casing for the alloy smelter. */
	static int brickCasing(int x, int y, Random r) {
		if (y >= 12 && y <= 14 && x >= 1 && x <= 14) {
			boolean mortar = y == 12 || ((y == 13) ? x % 4 == 0 : (x + 2) % 4 == 0);
			return mortar ? rgb(170, 160, 150) : rgb(150 + r.nextInt(20), 70, 55);
		}
		return casing(x, y, r);
	}

	static Painter crusherFront(boolean on, Painter casing) {
		return (x, y, r) -> {
			if (inRect(x, y, 3, 3, 12, 12)) {
				boolean tooth = ((x + (y < 8 ? 0 : 1)) % 2 == 0) && (y == 7 || y == 8);
				if (tooth) return on ? rgb(220, 220, 225) : rgb(170, 170, 175);
				if (y < 7 || y > 8) return (x + y) % 3 == 0 ? rgb(80, 82, 86) : rgb(105, 108, 112);
				return rgb(20, 20, 22);
			}
			return casing.at(x, y, r);
		};
	}

	static void ingot(String path, int color) throws Exception {
		png(path, (x, y, r) -> {
			boolean top = y >= 5 && y <= 7 && x >= 4 + (7 - y) && x <= 11 - (7 - y) + 2;
			boolean body = y >= 8 && y <= 11 && x >= 2 && x <= 13;
			if (top) return shade(color, 45);
			if (body) return y == 11 || x == 13 ? shade(color, -40) : shade(color, r.nextInt(7) - 3);
			return 0;
		});
	}

	static boolean inRect(int x, int y, int x0, int y0, int x1, int y1) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }

	static void textures() throws Exception {
		png("block/machine_side", GenAssets::casing);
		png("block/machine_top", (x, y, r) -> inRect(x, y, 4, 4, 11, 11) && y % 2 == 0 ? rgb(40, 42, 46) : casing(x, y, r));
		png("block/machine_frame", (x, y, r) -> {
			boolean beam = x <= 2 || y <= 2 || x >= 13 || y >= 13 || Math.abs(x - y) <= 1 || Math.abs(x + y - 15) <= 1;
			return beam ? casing(x, y, r) : rgb(54, 56, 60);
		});
		for (boolean on : new boolean[]{false, true}) {
			String s = on ? "_on" : "";
			// Generator: firebox
			png("block/generator_front" + s, (x, y, r) -> {
				if (inRect(x, y, 4, 7, 11, 12)) return on ? rgb(255, 140 + r.nextInt(80), 30) : rgb(30, 30, 32);
				if (inRect(x, y, 3, 6, 12, 13)) return rgb(60, 62, 66);
				if (inRect(x, y, 4, 3, 11, 3)) return rgb(70, 72, 76);
				return casing(x, y, r);
			});
			// Crusher: two toothed rollers (tier 2 uses the blue casing)
			png("block/crusher_front" + s, crusherFront(on, GenAssets::casing));
			png("block/industrial_crusher_front" + s, crusherFront(on, GenAssets::casing2));
			// Induction furnace: two stacked coil windows, one per lane
			png("block/induction_furnace_front" + s, (x, y, r) -> {
				for (int wy : new int[]{2, 8}) {
					if (inRect(x, y, 4, wy + 1, 11, wy + 4)) {
						if (x % 2 == 0) return on ? rgb(255, 120 + r.nextInt(60), 40) : rgb(120, 70, 50);
						return on ? rgb(140, 50, 30) : rgb(30, 34, 42);
					}
					if (inRect(x, y, 3, wy, 12, wy + 5)) return rgb(40, 48, 60);
				}
				return casing2(x, y, r);
			});
			// Alloy smelter: crucible mouth over a brick base
			png("block/alloy_smelter_front" + s, (x, y, r) -> {
				if (inRect(x, y, 4, 4, 11, 9)) {
					if (y >= 7) return on ? rgb(255, 170 + r.nextInt(50), 60) : rgb(70, 50, 45);
					return rgb(25, 25, 28);
				}
				if (inRect(x, y, 3, 3, 12, 10)) return rgb(60, 62, 66);
				return brickCasing(x, y, r);
			});
			// Thermal generator: heat-exchanger grille
			png("block/thermal_generator_front" + s, (x, y, r) -> {
				if (inRect(x, y, 3, 3, 12, 12)) {
					if (y % 3 == 0) return rgb(50, 58, 70);
					return on ? rgb(230, 90 + r.nextInt(70) + y * 4, 20) : rgb(70, 40, 35);
				}
				return casing2(x, y, r);
			});
			// Electric furnace: glass window with heating coil
			png("block/electric_furnace_front" + s, (x, y, r) -> {
				if (inRect(x, y, 4, 4, 11, 11)) {
					if (y == 6 || y == 9) return on ? rgb(255, 90 + r.nextInt(40), 40) : rgb(110, 60, 50);
					return on ? rgb(120, 50, 30) : rgb(36, 38, 44);
				}
				if (inRect(x, y, 3, 3, 12, 12)) return rgb(60, 62, 66);
				if (y == 13 && (x == 5 || x == 10)) return on ? rgb(80, 255, 80) : rgb(40, 90, 40);
				return casing(x, y, r);
			});
		}
		png("block/battery_box_front", (x, y, r) -> {
			if (inRect(x, y, 5, 3, 10, 12)) {
				if (x == 5 || x == 10 || y == 3 || y == 12) return rgb(40, 40, 44);
				return y >= 7 ? rgb(70, 200, 90) : rgb(30, 60, 36);
			}
			return casing(x, y, r);
		});
		png("block/battery_box_top", (x, y, r) -> {
			if (inRect(x, y, 3, 6, 5, 8) || inRect(x, y, 11, 6, 11, 8) || inRect(x, y, 10, 7, 12, 7)) return rgb(200, 40, 40);
			if (inRect(x, y, 3, 7, 5, 7)) return rgb(40, 40, 40);
			return casing(x, y, r);
		});
		png("block/solar_panel_top", (x, y, r) -> {
			if (x == 0 || y == 0 || x == 15 || y == 15) return shade(STEEL, 20);
			if (x % 5 == 0 || y % 5 == 0) return rgb(170, 180, 195);
			return rgb(28 + (x + y) % 3 * 4, 48 + r.nextInt(6), 110 + (x * 3 % 20));
		});
		png("block/copper_cable", (x, y, r) -> {
			int base = rgb(196, 110, 70);
			if (y % 4 == 0) return shade(base, 30);
			if (y % 4 == 3) return shade(base, -35);
			return shade(base, r.nextInt(13) - 6);
		});
		dust("item/iron_dust", rgb(205, 205, 210));
		dust("item/gold_dust", rgb(250, 210, 60));
		dust("item/copper_dust", rgb(225, 125, 80));

		// Tier 2
		png("block/advanced_machine_side", GenAssets::casing2);
		png("block/advanced_machine_top", (x, y, r) -> inRect(x, y, 4, 4, 11, 11) && (x + y) % 2 == 0 ? rgb(30, 34, 42) : casing(x, y, r, ADV, rgb(240, 170, 50)));
		png("block/advanced_machine_frame", (x, y, r) -> {
			boolean beam = x <= 2 || y <= 2 || x >= 13 || y >= 13 || Math.abs(x - y) <= 1 || Math.abs(x + y - 15) <= 1;
			if (inRect(x, y, 6, 6, 9, 9)) return rgb(60, 170, 90); // circuit core
			return beam ? casing(x, y, r, ADV, rgb(240, 170, 50)) : rgb(30, 34, 40);
		});
		ingot("item/steel_ingot", rgb(110, 116, 128));
		png("item/circuit", (x, y, r) -> {
			if (!inRect(x, y, 1, 3, 14, 12)) return 0;
			if (x == 1 || x == 14 || y == 3 || y == 12) return rgb(20, 90, 45);
			if (inRect(x, y, 6, 6, 9, 9)) return rgb(30, 30, 34); // chip
			if (y == 5 && x >= 2 && x <= 7 || x == 11 && y >= 5 && y <= 10 || y == 10 && x >= 4 && x <= 11) return rgb(230, 150, 70); // traces
			if ((x == 3 || x == 12) && (y == 8)) return rgb(200, 200, 205);
			return rgb(40, 140 + r.nextInt(12), 70);
		});
	}

	static void dust(String path, int color) throws Exception {
		png(path, (x, y, r) -> {
			// mound shape: a half ellipse sitting on the bottom
			double dx = (x - 7.5) / 6.5, dy = (y - 14.0) / 8.0;
			if (y > 14 || dx * dx + dy * dy > 1) return 0;
			int d = (int) (-dy * 18) + r.nextInt(31) - 15;
			if (dx * dx + dy * dy > 0.8) d -= 40;
			return shade(color, d);
		});
	}

	// ---------------- JSON helpers ----------------
	static void write(String path, String json) throws Exception {
		Path p = res.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, json.strip() + "\n");
	}

	static String q(String s) { return "\"" + s + "\""; }

	// ---------------- models / blockstates ----------------
	static final String[] FACINGS = {"north", "east", "south", "west"};
	static final int[] ROT = {0, 90, 180, 270};

	static void models() throws Exception {
		machine("generator", true);
		machine("crusher", true);
		machine("electric_furnace", true);
		machine("battery_box", false);
		machine("alloy_smelter", true);
		machine("industrial_crusher", true);
		machine("induction_furnace", true);
		machine("thermal_generator", true);

		write("assets/voltworks/models/block/advanced_machine_frame.json", """
			{ "parent": "minecraft:block/cube_all", "textures": { "all": "voltworks:block/advanced_machine_frame" } }
			""");
		write("assets/voltworks/blockstates/advanced_machine_frame.json", """
			{ "variants": { "": { "model": "voltworks:block/advanced_machine_frame" } } }
			""");
		itemDef("advanced_machine_frame", "voltworks:block/advanced_machine_frame");

		// Solar panel: same model regardless of facing / lit
		write("assets/voltworks/models/block/solar_panel.json", """
			{ "parent": "minecraft:block/cube_bottom_top",
			  "textures": { "top": "voltworks:block/solar_panel_top", "side": "voltworks:block/machine_side", "bottom": "voltworks:block/machine_top" } }
			""");
		facingStates("solar_panel", lit -> "solar_panel", false);
		itemDef("solar_panel", "voltworks:block/solar_panel");

		write("assets/voltworks/models/block/machine_frame.json", """
			{ "parent": "minecraft:block/cube_all", "textures": { "all": "voltworks:block/machine_frame" } }
			""");
		write("assets/voltworks/blockstates/machine_frame.json", """
			{ "variants": { "": { "model": "voltworks:block/machine_frame" } } }
			""");
		itemDef("machine_frame", "voltworks:block/machine_frame");

		// Cable: core + one arm per connected side (multipart)
		write("assets/voltworks/models/block/copper_cable_core.json", """
			{ "ambientocclusion": false,
			  "textures": { "cable": "voltworks:block/copper_cable", "particle": "voltworks:block/copper_cable" },
			  "elements": [ { "from": [5,5,5], "to": [11,11,11], "faces": {
			    "north": {"uv":[5,5,11,11],"texture":"#cable"}, "south": {"uv":[5,5,11,11],"texture":"#cable"},
			    "east": {"uv":[5,5,11,11],"texture":"#cable"}, "west": {"uv":[5,5,11,11],"texture":"#cable"},
			    "up": {"uv":[5,5,11,11],"texture":"#cable"}, "down": {"uv":[5,5,11,11],"texture":"#cable"} } } ] }
			""");
		write("assets/voltworks/models/block/copper_cable_side.json", """
			{ "ambientocclusion": false,
			  "textures": { "cable": "voltworks:block/copper_cable", "particle": "voltworks:block/copper_cable" },
			  "elements": [ { "from": [5,5,0], "to": [11,11,5], "faces": {
			    "north": {"uv":[5,5,11,11],"texture":"#cable"},
			    "east": {"uv":[0,5,5,11],"texture":"#cable"}, "west": {"uv":[0,5,5,11],"texture":"#cable"},
			    "up": {"uv":[5,0,11,5],"texture":"#cable"}, "down": {"uv":[5,0,11,5],"texture":"#cable"} } } ] }
			""");
		write("assets/voltworks/models/block/copper_cable_inventory.json", """
			{ "parent": "minecraft:block/block",
			  "textures": { "cable": "voltworks:block/copper_cable", "particle": "voltworks:block/copper_cable" },
			  "elements": [ { "from": [0,5,5], "to": [16,11,11], "faces": {
			    "north": {"uv":[0,5,16,11],"texture":"#cable"}, "south": {"uv":[0,5,16,11],"texture":"#cable"},
			    "up": {"uv":[0,5,16,11],"texture":"#cable"}, "down": {"uv":[0,5,16,11],"texture":"#cable"},
			    "east": {"uv":[5,5,11,11],"texture":"#cable"}, "west": {"uv":[5,5,11,11],"texture":"#cable"} } } ] }
			""");
		write("assets/voltworks/blockstates/copper_cable.json", """
			{ "multipart": [
			  { "apply": { "model": "voltworks:block/copper_cable_core" } },
			  { "when": { "north": "true" }, "apply": { "model": "voltworks:block/copper_cable_side" } },
			  { "when": { "east": "true" },  "apply": { "model": "voltworks:block/copper_cable_side", "y": 90 } },
			  { "when": { "south": "true" }, "apply": { "model": "voltworks:block/copper_cable_side", "y": 180 } },
			  { "when": { "west": "true" },  "apply": { "model": "voltworks:block/copper_cable_side", "y": 270 } },
			  { "when": { "up": "true" },    "apply": { "model": "voltworks:block/copper_cable_side", "x": 270 } },
			  { "when": { "down": "true" },  "apply": { "model": "voltworks:block/copper_cable_side", "x": 90 } }
			] }
			""");
		itemDef("copper_cable", "voltworks:block/copper_cable_inventory");

		for (String dust : List.of("iron_dust", "gold_dust", "copper_dust", "steel_ingot", "circuit")) {
			write("assets/voltworks/models/item/" + dust + ".json",
					"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/" + dust + "\" } }");
			itemDef(dust, "voltworks:item/" + dust);
		}

		write("assets/voltworks/lang/en_us.json", """
			{
			  "itemGroup.voltworks": "Voltworks Industrial",
			  "block.voltworks.generator": "Generator",
			  "block.voltworks.crusher": "Crusher",
			  "block.voltworks.electric_furnace": "Electric Furnace",
			  "block.voltworks.battery_box": "Battery Box",
			  "block.voltworks.solar_panel": "Solar Panel",
			  "block.voltworks.copper_cable": "Copper Cable",
			  "block.voltworks.machine_frame": "Machine Frame",
			  "item.voltworks.iron_dust": "Iron Dust",
			  "item.voltworks.gold_dust": "Gold Dust",
			  "item.voltworks.copper_dust": "Copper Dust",
			  "gui.voltworks.energy": "%s / %s J",
			  "message.voltworks.battery": "Battery: %s / %s J",
			  "message.voltworks.solar.on": "Solar Panel: generating 1 J/t",
			  "message.voltworks.solar.off": "Solar Panel: idle (needs daylight and open sky)",
			  "block.voltworks.advanced_machine_frame": "Advanced Machine Frame",
			  "block.voltworks.alloy_smelter": "Alloy Smelter",
			  "block.voltworks.industrial_crusher": "Industrial Crusher",
			  "block.voltworks.induction_furnace": "Induction Furnace",
			  "block.voltworks.thermal_generator": "Thermal Generator",
			  "item.voltworks.steel_ingot": "Steel Ingot",
			  "item.voltworks.circuit": "Circuit",
			  "message.voltworks.thermal.on": "Thermal Generator: %s J/t from %s lava source(s)",
			  "message.voltworks.thermal.off": "Thermal Generator: idle (place lava source blocks next to it)",
			  "block.voltworks.tin_ore": "Tin Ore",
			  "block.voltworks.deepslate_tin_ore": "Deepslate Tin Ore",
			  "block.voltworks.lead_ore": "Lead Ore",
			  "block.voltworks.deepslate_lead_ore": "Deepslate Lead Ore",
			  "block.voltworks.tin_block": "Block of Tin",
			  "block.voltworks.lead_block": "Block of Lead",
			  "block.voltworks.steel_block": "Block of Steel",
			  "item.voltworks.raw_tin": "Raw Tin",
			  "item.voltworks.tin_ingot": "Tin Ingot",
			  "item.voltworks.tin_dust": "Tin Dust",
			  "item.voltworks.raw_lead": "Raw Lead",
			  "item.voltworks.lead_ingot": "Lead Ingot",
			  "item.voltworks.lead_dust": "Lead Dust",
			  "block.voltworks.charger": "Charger",
			  "item.voltworks.power_cell": "Power Cell",
			  "item.voltworks.electric_drill": "Electric Drill",
			  "item.voltworks.chainsaw": "Chainsaw",
			  "item.voltworks.advanced_drill": "Advanced Drill",
			  "tooltip.voltworks.energy": "Energy: %s / %s J",
			  "tooltip.voltworks.per_block": "Uses %s J per block",
			  "tooltip.voltworks.mode.single": "Mode: single block",
			  "tooltip.voltworks.mode.wide": "Mode: 3x3",
			  "tooltip.voltworks.mode.hint": "Sneak + right-click to switch mode",
			  "message.voltworks.drill.wide": "Advanced Drill: 3x3 mode",
			  "message.voltworks.drill.single": "Advanced Drill: single block mode",
			  "item.voltworks.speed_upgrade": "Speed Upgrade",
			  "item.voltworks.capacity_upgrade": "Capacity Upgrade",
			  "item.voltworks.ejector_upgrade": "Ejector Upgrade",
			  "tooltip.voltworks.upgrade.speed": "Each: 30% faster, uses 60% more power per tick",
			  "tooltip.voltworks.upgrade.capacity": "Each: +4,000 J internal storage",
			  "tooltip.voltworks.upgrade.ejector": "Pushes finished items into adjacent inventories and machines",
			  "tooltip.voltworks.upgrade.hint": "Place in a machine's upgrade slots (up to 4 per slot)",
			  "block.voltworks.uranium_ore": "Uranium Ore",
			  "block.voltworks.deepslate_uranium_ore": "Deepslate Uranium Ore",
			  "block.voltworks.uranium_block": "Block of Uranium",
			  "item.voltworks.raw_uranium": "Raw Uranium",
			  "item.voltworks.uranium_ingot": "Uranium Ingot",
			  "item.voltworks.uranium_dust": "Uranium Dust",
			  "block.voltworks.reactor_chamber": "Reactor Chamber",
			  "item.voltworks.fuel_rod": "Fuel Rod",
			  "item.voltworks.depleted_fuel_rod": "Depleted Fuel Rod",
			  "item.voltworks.heat_vent": "Heat Vent",
			  "tooltip.voltworks.fuel": "Fuel: %s%%",
			  "tooltip.voltworks.fuel_rod": "Each rod beside another rod pulses more: more power, much more heat",
			  "tooltip.voltworks.depleted_fuel_rod": "Crush it to recover the lead",
			  "tooltip.voltworks.heat_vent": "Cools the reactor; works better next to fuel rods",
			  "gui.voltworks.heat": "Heat: %s / %s",
			  "message.voltworks.reactor": "Reactor: %s J/t, heat %s%%"
			}
			""");
	}

	interface LitModel { String name(boolean lit); }

	static void machine(String name, boolean hasOnTexture) throws Exception {
		for (boolean lit : new boolean[]{false, true}) {
			if (lit && !hasOnTexture) continue;
			String suffix = lit ? "_on" : "";
			boolean tier2 = TIER2.contains(name);
			String top = name.equals("battery_box") ? "voltworks:block/battery_box_top"
					: tier2 ? "voltworks:block/advanced_machine_top" : "voltworks:block/machine_top";
			String side = tier2 ? "voltworks:block/advanced_machine_side" : "voltworks:block/machine_side";
			write("assets/voltworks/models/block/" + name + suffix + ".json",
					"{ \"parent\": \"minecraft:block/orientable\", \"textures\": { \"front\": \"voltworks:block/" + name + "_front" + suffix
							+ "\", \"side\": \"" + side + "\", \"top\": \"" + top + "\" } }");
		}
		facingStates(name, lit -> lit && hasOnTexture ? name + "_on" : name, true);
		itemDef(name, "voltworks:block/" + name);
	}

	static void facingStates(String name, LitModel model, boolean rotate) throws Exception {
		StringBuilder sb = new StringBuilder("{ \"variants\": {\n");
		List<String> entries = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			for (boolean lit : new boolean[]{false, true}) {
				String rot = rotate && ROT[i] != 0 ? ", \"y\": " + ROT[i] : "";
				entries.add("  " + q("facing=" + FACINGS[i] + ",lit=" + lit) + ": { \"model\": " + q("voltworks:block/" + model.name(lit)) + rot + " }");
			}
		}
		sb.append(String.join(",\n", entries)).append("\n} }");
		write("assets/voltworks/blockstates/" + name + ".json", sb.toString());
	}

	static void itemDef(String name, String model) throws Exception {
		write("assets/voltworks/items/" + name + ".json", "{ \"model\": { \"type\": \"minecraft:model\", \"model\": " + q(model) + " } }");
	}

	// ---------------- data: loot, tags, recipes ----------------
	static final Set<String> TIER2 = Set.of("industrial_crusher", "induction_furnace", "thermal_generator", "reactor_chamber");
	static final List<String> BLOCKS = List.of("generator", "crusher", "electric_furnace", "battery_box", "solar_panel", "copper_cable", "machine_frame",
			"advanced_machine_frame", "alloy_smelter", "industrial_crusher", "induction_furnace", "thermal_generator", "charger", "reactor_chamber");

	static void data() throws Exception {
		for (String b : BLOCKS) {
			write("data/voltworks/loot_table/blocks/" + b + ".json", """
				{ "type": "minecraft:block", "pools": [ { "rolls": 1,
				  "condition": { "type": "minecraft:survives_explosion" },
				  "entries": [ { "type": "minecraft:item", "name": "voltworks:%s" } ] } ],
				  "random_sequence": "voltworks:blocks/%s" }
				""".formatted(b, b));
		}
		List<String> ids = new ArrayList<>();
		for (String b : BLOCKS) ids.add(q("voltworks:" + b));
		for (String b : ORE_BLOCKS) ids.add(q("voltworks:" + b));
		for (String b : List.of("tin_block", "lead_block", "steel_block", "uranium_block")) ids.add(q("voltworks:" + b));
		write("data/minecraft/tags/block/mineable/pickaxe.json", "{ \"replace\": false, \"values\": [" + String.join(", ", ids) + "] }");

		shaped("machine_frame", 1, new String[]{"III", "IRI", "III"}, "I", "minecraft:iron_ingot", "R", "minecraft:redstone");
		shaped("copper_cable", 6, new String[]{"CCC"}, "C", "minecraft:copper_ingot");
		shaped("generator", 1, new String[]{"F", "M"}, "F", "minecraft:furnace", "M", "voltworks:machine_frame");
		shaped("crusher", 1, new String[]{"FFF", "CMC", "CRC"}, "F", "minecraft:flint", "C", "minecraft:cobblestone",
				"M", "voltworks:machine_frame", "R", "minecraft:redstone");
		shaped("electric_furnace", 1, new String[]{" R ", "CFC", "CMC"}, "R", "minecraft:redstone", "C", "minecraft:copper_ingot",
				"F", "minecraft:furnace", "M", "voltworks:machine_frame");
		shaped("battery_box", 1, new String[]{"LRL", "RMR", "LRL"}, "L", "voltworks:lead_ingot", "R", "minecraft:redstone",
				"M", "voltworks:machine_frame");
		shaped("solar_panel", 1, new String[]{"GGG", "CDC", "CMC"}, "G", "minecraft:glass", "C", "minecraft:copper_ingot",
				"D", "minecraft:daylight_detector", "M", "voltworks:machine_frame");

		// Tier 2: the Alloy Smelter (tier 1 parts) makes steel, which unlocks circuits and advanced frames.
		shaped("alloy_smelter", 1, new String[]{"BBB", "FMF", "BRB"}, "B", "minecraft:bricks", "F", "minecraft:furnace",
				"M", "voltworks:machine_frame", "R", "minecraft:redstone");
		shapedItem("circuit", 2, new String[]{"CTC", "RSR", "CTC"}, "C", "voltworks:copper_cable", "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"S", "voltworks:steel_ingot");
		shaped("advanced_machine_frame", 1, new String[]{"SCS", "CMC", "SCS"}, "S", "voltworks:steel_ingot", "C", "voltworks:circuit",
				"M", "voltworks:machine_frame");
		shaped("industrial_crusher", 1, new String[]{" C ", "XAX", " C "}, "C", "voltworks:circuit", "X", "voltworks:crusher",
				"A", "voltworks:advanced_machine_frame");
		shaped("induction_furnace", 1, new String[]{" C ", "EAE", " C "}, "C", "voltworks:circuit", "E", "voltworks:electric_furnace",
				"A", "voltworks:advanced_machine_frame");
		shaped("thermal_generator", 1, new String[]{"SMS", "CAC", "SMS"}, "S", "voltworks:steel_ingot", "M", "minecraft:magma_block",
				"C", "voltworks:circuit", "A", "voltworks:advanced_machine_frame");

		for (String metal : List.of("iron", "gold", "copper")) {
			for (String type : List.of("smelting", "blasting")) {
				write("data/voltworks/recipe/" + metal + "_ingot_from_" + type + "_dust.json", """
					{ "type": "minecraft:%s", "cookingtime": %d, "experience": 0.5,
					  "ingredient": "voltworks:%s_dust", "result": { "id": "minecraft:%s_ingot" } }
					""".formatted(type, type.equals("smelting") ? 200 : 100, metal, metal));
			}
		}
	}

	static void shapedItem(String result, int count, String[] pattern, String... keys) throws Exception {
		shaped(result, count, pattern, keys);
	}

	static void shaped(String result, int count, String[] pattern, String... keys) throws Exception {
		List<String> k = new ArrayList<>();
		for (int i = 0; i < keys.length; i += 2) k.add(q(keys[i]) + ": " + q(keys[i + 1]));
		List<String> p = new ArrayList<>();
		for (String row : pattern) p.add(q(row));
		write("data/voltworks/recipe/" + result + ".json",
				"{ \"type\": \"minecraft:crafting_shaped\",\n  \"pattern\": [" + String.join(", ", p) + "],\n  \"key\": { "
						+ String.join(", ", k) + " },\n  \"result\": { \"id\": \"voltworks:" + result + "\", \"count\": " + count + " } }");
	}
}
