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
		gauges();
		logistics();
		refining();
		computers();
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

		shaped("fuel_rod", 1, new String[]{"LUL", "LUL", "LUL"}, "L", "voltworks:lead_ingot", "U", "voltworks:refined_uranium");
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
		shapedN("speed_upgrade", 1, new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "minecraft:gold_ingot");
		shapedN("capacity_upgrade", 1, new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
				"C", "voltworks:circuit", "X", "voltworks:power_cell");
		shapedN("ejector_upgrade", 1, new String[]{"TCT", "RXR"}, "T", "voltworks:tin_ingot", "R", "minecraft:redstone",
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
		int d = (int) Math.round((v - 0.5) * 56) + (hash(x, y, 14) > 0.82 ? -14 : 0) + (hash(x, y, 15) > 0.9 ? 12 : 0);
		return shade(rgb(124, 124, 126), d);
	}

	/** Original deepslate: darker, bluish, with horizontal strata. */
	static int originalDeepslate(int x, int y) {
		double strata = noise(x * 2 % 16, y, 2, 21);
		double v = 0.5 * noise(x, y, 8, 22) + 0.5 * strata;
		int d = (int) Math.round((v - 0.5) * 36) + (y % 4 == 0 ? -6 : 0) + (hash(x, y, 23) > 0.9 ? 8 : 0);
		return shade(rgb(74, 74, 82), d);
	}

	/**
	 * Tone a colour: t in [-1, 1]. Negative darkens toward black, positive lightens toward white,
	 * so light metals (tin) and dark ones (lead) both get a full shading ramp.
	 */
	static int tone(int c, double t) {
		int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
		if (t >= 0) {
			return rgb((int) (r + (255 - r) * t * 0.75), (int) (g + (255 - g) * t * 0.75), (int) (b + (255 - b) * t * 0.75));
		}
		double f = 1 + t * 0.8;
		return rgb((int) (r * f), (int) (g * f), (int) (b * f));
	}

	/** Snap a tone to pixel-art bands so shading reads as flat steps, like vanilla items. */
	static double band(double t) {
		return Math.round(t * 5) / 5.0;
	}

	/**
	 * Ore block in the vanilla style: six chunky mineral clusters set into plain stone. Like vanilla ores,
	 * each cluster is brightest in its middle and darkens toward its edges, with a little speckle.
	 */
	/** Hand-drawn mineral clusters. h highlight, l light, m mid, d dark edge, '.' = stone. */
	static final String[][] ORE_STAMPS = {
			{"..dmd.", ".dlhmd", "dmllm.", ".ddm.."},
			{".dm...", "dlhmd.", ".mlhmd", "..dmd."},
			{"dmd.", "mhld", "dlmm", ".dd."},
			{"..dd", ".dlm", "dhlm", "mmd."},
	};

	static void oreBlock(String path, int color, boolean deep, long seed) throws Exception {
		Random rnd = new Random(seed);
		char[][] px = new char[16][16];
		int[][] spots = {{1, 1}, {9, 1}, {5, 6}, {0, 10}, {9, 10}};
		for (int[] s : spots) {
			String[] stamp = ORE_STAMPS[rnd.nextInt(ORE_STAMPS.length)];
			boolean flip = rnd.nextBoolean();
			int ox = s[0] + rnd.nextInt(2), oy = s[1] + rnd.nextInt(2);
			for (int sy = 0; sy < stamp.length; sy++) for (int sx = 0; sx < stamp[sy].length(); sx++) {
				char c = stamp[sy].charAt(flip ? stamp[sy].length() - 1 - sx : sx);
				int x = ox + sx, y = oy + sy;
				if (c != '.' && x < 16 && y < 16) px[y][x] = c;
			}
		}
		// Light metals (tin) are pulled down a little so the ore still stands out against grey stone.
		int lum = (((color >> 16) & 255) + ((color >> 8) & 255) + (color & 255)) / 3;
		double shift = lum > 170 ? -0.25 : 0;
		png(path, (x, y, r) -> {
			double t;
			switch (px[y][x]) {
				case 'h' -> t = 0.55;
				case 'l' -> t = 0.25;
				case 'm' -> t = 0.0;
				case 'd' -> t = -0.4;
				default -> { return baseStone(x, y, r, deep); }
			}
			return tone(color, t + shift);
		});
	}

	/** Raw ore in the vanilla style: a lumpy two-lobed rock lit from the top-left, with a dark outline. */
	static void rawLump(String path, int color) throws Exception {
		double[][] blobs = {{6.5, 6.0, 6.0, 4.4}, {11.0, 10.0, 3.6, 3.2}, {4.0, 9.5, 3.4, 2.6}}; // cx, cy, rx, ry
		double[][] t = new double[16][16];
		boolean[][] in = new boolean[16][16];
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			double best = -1;
			for (double[] b : blobs) {
				double dx = (x + 0.5 - b[0]) / b[2], dy = (y + 0.5 - b[1]) / b[3];
				double d = dx * dx + dy * dy;
				if (d <= 1) {
					double light = -(dx * 0.5 + dy * 0.85);       // lit from the top-left
					double edge = d > 0.65 ? -0.25 : 0;              // darker toward the rim
					double val = light * 0.6 + edge + 0.05;
					if (val > best || !in[y][x]) best = Math.max(best, val);
					in[y][x] = true;
				}
			}
			t[y][x] = best;
		}
		png(path, (x, y, r) -> {
			if (in[y][x]) {
				double v = t[y][x] + (hash(x, y, 5) - 0.5) * 0.25; // a little grain
				return tone(color, band(Math.max(-0.7, Math.min(0.75, v))));
			}
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + d[0], ny = y + d[1];
				if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && in[ny][nx]) return tone(mul(color, 0.55), -0.6);
			}
			return 0;
		});
	}

	static void ores() throws Exception {
		for (Metal m : METALS) {
			String n = m.name();
			for (boolean deep : new boolean[]{false, true}) {
				String block = (deep ? "deepslate_" : "") + n + "_ore";
				oreBlock("block/" + block, m.color(), deep, n.hashCode());
				cubeAll(block);
			}
			// Storage block: riveted plates in the metal colour
			png("block/" + n + "_block", (x, y, r) -> casing(x, y, r, m.color(), shade(m.color(), 40)));
			cubeAll(n + "_block");
			// Raw ore lump, ingot, dust. Uranium is never smelted or crushed: the Uranium Refiner turns it into
			// Refined Uranium (see refining()), which is what its storage block holds.
			boolean refined = n.equals("uranium");
			rawLump("item/raw_" + n, m.color());
			if (!refined) {
				ingot("item/" + n + "_ingot", m.color());
				dust("item/" + n + "_dust", m.color());
			}
			for (String item : refined ? List.of("raw_" + n) : List.of("raw_" + n, n + "_ingot", n + "_dust")) {
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
			for (String in : refined ? List.<String>of() : List.of("raw_" + n, n + "_ore", "deepslate_" + n + "_ore", n + "_dust")) {
				for (String type : List.of("smelting", "blasting")) {
					write("data/voltworks/recipe/" + n + "_ingot_from_" + type + "_" + in + ".json", """
						{ "type": "minecraft:%s", "cookingtime": %d, "experience": 0.7,
						  "ingredient": "voltworks:%s", "result": { "id": "voltworks:%s_ingot" } }
						""".formatted(type, type.equals("smelting") ? 200 : 100, in, n));
				}
			}
			storageRecipes(refined ? "refined_" + n : n + "_ingot", n + "_block");

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
			if (!refined) {
				tag("item", "c", "ingots/" + n, "voltworks:" + n + "_ingot");
				tag("item", "c", "dusts/" + n, "voltworks:" + n + "_dust");
			}
			tag("item", "c", "raw_materials/" + n, "voltworks:raw_" + n);
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

	/**
	 * Ingot in the vanilla style: a chunky bar tilted up to the right, seen from above, with a bright
	 * top face, a ridge highlight where it meets the darker front face, a shaded left end and an outline.
	 * Drawn by hand as a template; each letter is a tone so every metal gets the same shading.
	 *   L light outline   D dark outline (lower-right)   H ridge highlight   T top face   l top face, shaded
	 *   E lit end face (right)   e shaded end face (left)   F front face   f front face, lighter
	 */
	static final String[] INGOT = {
			"................",
			"................",
			"..........LL....",
			".......LLLllL...",
			"....LLLTTTTTTL..",
			".LLLlTTTTTTTTlD.",
			"LHTTTTTTTTTTHHTD",
			"LlHTTTTTTHHHTFED",
			"LlTHTTHHHTfFFFED",
			"LlTlHHTfFFFFfEED",
			"LeTlTfFFFFfEEDD.",
			".LelTfFFffDDD...",
			"..LelffDDD......",
			"...LLDD.........",
			"................",
			"................",
	};

	static void ingot(String path, int color) throws Exception {
		Map<Character, Double> tones = Map.of(
				'H', 0.45, 'T', 0.2, 'l', 0.05, 'E', 0.05, 'e', -0.2,
				'f', -0.2, 'F', -0.32, 'L', -0.5, 'D', -0.85);
		png(path, (x, y, r) -> {
			char c = INGOT[y].charAt(x);
			return c == '.' ? 0 : tone(color, tones.get(c));
		});
	}

	static int mul(int c, double f) {
		return rgb((int) Math.round(((c >> 16) & 255) * f), (int) Math.round(((c >> 8) & 255) * f), (int) Math.round((c & 255) * f));
	}

	static boolean inPoly(double px, double py, double[][] poly) {
		boolean in = false;
		for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			double xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
			if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
		}
		return in;
	}

	static double distToLine(double px, double py, double[] a, double[] b) {
		double dx = b[0] - a[0], dy = b[1] - a[1];
		return Math.abs(dy * px - dx * py + b[0] * a[1] - b[1] * a[0]) / Math.hypot(dx, dy);
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
		// Batteries: an empty meter window (the live charge level is a gauge model drawn over it), a stripe in the
		// battery's colour under it, and +/- terminals of that colour on top. Large and Huge use the tier 2 casing.
		for (var b : BATTERY_COLORS.entrySet()) {
			String name = b.getKey();
			int color = b.getValue();
			boolean big = TIER2.contains(name);
			png("block/" + name + "_front", (x, y, r) -> {
				if (inRect(x, y, 5, 3, 10, 12)) {
					if (x == 5 || x == 10 || y == 3 || y == 12) return rgb(40, 40, 44);
					return rgb(24, 40, 28);
				}
				if (y == 14 && x >= 3 && x <= 12) return shade(color, x % 2 == 0 ? 0 : -25);
				return big ? casing2(x, y, r) : casing(x, y, r);
			});
			png("block/" + name + "_top", (x, y, r) -> {
				if (inRect(x, y, 3, 6, 5, 8) || inRect(x, y, 11, 6, 11, 8) || inRect(x, y, 10, 7, 12, 7)) return color;
				if (inRect(x, y, 3, 7, 5, 7)) return rgb(40, 40, 40);
				return big ? casing2(x, y, r) : casing(x, y, r);
			});
		}
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

	/**
	 * Dust in the vanilla style: a pointed heap (narrow peak, wide middle, rounded base) of speckled
	 * grains in four tones, outlined lighter on the upper-left and darker on the lower-right.
	 */
	static void dust(String path, int color) throws Exception {
		// Heap silhouette, rows 3..13 as {left x, right x}: a small peak, three broad rows, a rounded base.
		int[][] rows = {{7, 8}, {6, 9}, {5, 10}, {4, 11}, {3, 12}, {2, 13}, {2, 13}, {2, 13}, {3, 12}, {4, 11}, {6, 9}};
		boolean[][] in = new boolean[16][16];
		for (int i = 0; i < rows.length; i++) for (int x = rows[i][0]; x <= rows[i][1]; x++) in[3 + i][x] = true;
		long seed = path.hashCode();
		png(path, (x, y, r) -> {
			if (!in[y][x]) return 0;
			// The heap's own edge pixels form the outline: lighter up-left, darker down-right.
			boolean edgeUpLeft = !in[y - 1][x] || !in[y][x - 1];
			boolean edgeDownRight = !in[y + 1][x] || !in[y][x + 1];
			if (edgeDownRight && (x >= 8 || y >= 11)) return tone(color, -0.7);
			if (edgeUpLeft || edgeDownRight) return tone(color, -0.55);
			// Grains in four tones; darker grains gather toward the lower-right.
			double h = hash(x, y, seed) - (x + y > 18 ? 0.15 : 0);
			double t = h > 0.72 ? 0.35 : h > 0.42 ? 0.15 : h > 0.18 ? 0.0 : -0.3;
			return tone(color, t);
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
		for (String battery : BATTERY_COLORS.keySet()) machine(battery, false);
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
			  "block.voltworks.battery_box": "Medium Battery",
			  "block.voltworks.small_battery": "Small Battery",
			  "block.voltworks.large_battery": "Large Battery",
			  "block.voltworks.huge_battery": "Huge Battery",
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
			  "tooltip.voltworks.upgrade.hint": "Place in a machine's upgrade slots (one per slot)",
			  "block.voltworks.uranium_ore": "Uranium Ore",
			  "block.voltworks.deepslate_uranium_ore": "Deepslate Uranium Ore",
			  "block.voltworks.uranium_block": "Block of Refined Uranium",
			  "item.voltworks.refined_uranium": "Refined Uranium",
			  "tooltip.voltworks.refined_uranium": "Enriched in the Uranium Refiner; Fuel Rods are made from it",
			  "block.voltworks.uranium_refiner": "Uranium Refiner",
			  "item.voltworks.silicon": "Silicon",
			  "item.voltworks.silicon_chip": "Silicon Chip",
			  "item.voltworks.small_hard_drive": "Small Hard Drive (1K)",
			  "item.voltworks.medium_hard_drive": "Medium Hard Drive (4K)",
			  "item.voltworks.large_hard_drive": "Large Hard Drive (16K)",
			  "item.voltworks.huge_hard_drive": "Huge Hard Drive (64K)",
			  "tooltip.voltworks.drive.usage": "Stored: %s / %s items",
			  "tooltip.voltworks.drive.types": "Types: %s / %s",
			  "block.voltworks.data_cable": "Data Cable",
			  "block.voltworks.drive_rack": "Drive Rack",
			  "block.voltworks.storage_terminal": "Storage Terminal",
			  "block.voltworks.computer_crafter": "Computer Crafter",
			  "gui.voltworks.search": "Search...",
			  "gui.voltworks.storage.offline": "Offline: no powered Drive Rack",
			  "gui.voltworks.storage.usage": "%s / %s items, %s types",
			  "gui.voltworks.storage.count": "Stored: %s",
			  "gui.voltworks.crafter.craft": "x%s",
			  "gui.voltworks.rack.online": "Online",
			  "gui.voltworks.rack.offline": "No power: drives offline",
			  "item.voltworks.raw_uranium": "Raw Uranium",
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
			String top = BATTERY_COLORS.containsKey(name) ? "voltworks:block/" + name + "_top"
					: tier2 ? "voltworks:block/advanced_machine_top" : "voltworks:block/machine_top";
			String side = tier2 ? "voltworks:block/advanced_machine_side" : "voltworks:block/machine_side";
			write("assets/voltworks/models/block/" + name + suffix + ".json",
					"{ \"parent\": \"minecraft:block/orientable\", \"textures\": { \"front\": \"voltworks:block/" + name + "_front" + suffix
							+ "\", \"side\": \"" + side + "\", \"top\": \"" + top + "\" } }");
		}
		// Energy gauge on the front: the Battery Box's big meter, a slim gauge on other machines.
		// The Thermal Generator stores nothing, so it has none.
		String gauge = BATTERY_COLORS.containsKey(name) ? "battery_gauge" : Set.of("thermal_generator", "storage_terminal", "computer_crafter").contains(name) ? null : "gauge";
		if (gauge == null) {
			facingStates(name, lit -> lit && hasOnTexture ? name + "_on" : name, true);
		} else {
			gaugeStates(name, lit -> lit && hasOnTexture ? name + "_on" : name, gauge);
		}
		itemDef(name, "voltworks:block/" + name);
	}

	/** Multipart blockstate: the machine model per facing/lit, plus the gauge model for the current charge level. */
	static void gaugeStates(String name, LitModel model, String gauge) throws Exception {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String rot = ROT[i] != 0 ? ", \"y\": " + ROT[i] : "";
			for (boolean lit : new boolean[]{false, true}) {
				parts.add("  { \"when\": { \"facing\": " + q(FACINGS[i]) + ", \"lit\": " + q(String.valueOf(lit)) + " }, \"apply\": { \"model\": "
						+ q("voltworks:block/" + model.name(lit)) + rot + " } }");
			}
			for (int c = 0; c <= GAUGE_LEVELS; c++) {
				parts.add("  { \"when\": { \"facing\": " + q(FACINGS[i]) + ", \"charge\": " + q(String.valueOf(c)) + " }, \"apply\": { \"model\": "
						+ q("voltworks:block/" + gauge + "_" + c) + rot + " } }");
			}
		}
		write("assets/voltworks/blockstates/" + name + ".json", "{ \"multipart\": [\n" + String.join(",\n", parts) + "\n] }");
	}

	static final int GAUGE_LEVELS = 6;

	/**
	 * Gauge models and textures, one per charge level. The gauge is a thin raised panel on the machine's
	 * front (north) face; its glass shows the fill level, its rim uses a dark frame texture.
	 */
	static void gauges() throws Exception {
		png("block/gauge_frame", (x, y, r) -> rgb(34, 36, 42));
		for (int c = 0; c <= GAUGE_LEVELS; c++) {
			int level = c;
			// Slim machine gauge: 2x10 glass, fills from the bottom in blue
			int filled = Math.round(level * 10f / GAUGE_LEVELS);
			png("block/gauge_" + c, (x, y, r) -> {
				if (x > 1 || y > 9) return rgb(34, 36, 42);
				boolean full = y >= 10 - filled;
				if (!full) return rgb(22, 26, 34);
				boolean topOfFill = y == 10 - filled;
				return topOfFill ? rgb(150, 230, 255) : (x == 0 ? rgb(90, 200, 255) : rgb(50, 150, 230));
			});
			gaugeModel("gauge_" + c, new double[]{13, 3, -0.4, 15, 13, 0}, 2, 10);
			// Battery Box meter: 4x8 glass, fills from the bottom in green
			int bFilled = Math.round(level * 8f / GAUGE_LEVELS);
			png("block/battery_gauge_" + c, (x, y, r) -> {
				if (x > 3 || y > 7) return rgb(34, 36, 42);
				if (y < 8 - bFilled) return rgb(24, 40, 28);
				boolean topOfFill = y == 8 - bFilled;
				return topOfFill ? rgb(170, 255, 170) : (x == 0 ? rgb(110, 230, 120) : rgb(60, 190, 80));
			});
			gaugeModel("battery_gauge_" + c, new double[]{6, 4, -0.4, 10, 12, 0}, 4, 8);
		}
	}

	static void gaugeModel(String name, double[] box, int w, int h) throws Exception {
		String from = "[" + box[0] + ", " + box[1] + ", " + box[2] + "]", to = "[" + box[3] + ", " + box[4] + ", " + box[5] + "]";
		write("assets/voltworks/models/block/" + name + ".json", """
			{ "textures": { "glass": "voltworks:block/%s", "frame": "voltworks:block/gauge_frame", "particle": "voltworks:block/gauge_frame" },
			  "elements": [ { "from": %s, "to": %s, "faces": {
			    "north": { "uv": [0, 0, %d, %d], "texture": "#glass" },
			    "east":  { "uv": [0, 0, 1, 1], "texture": "#frame" },
			    "west":  { "uv": [0, 0, 1, 1], "texture": "#frame" },
			    "up":    { "uv": [0, 0, 1, 1], "texture": "#frame" },
			    "down":  { "uv": [0, 0, 1, 1], "texture": "#frame" } } } ] }
			""".formatted(name, from, to, w, h));
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
	static final Set<String> TIER2 = Set.of("industrial_crusher", "induction_furnace", "thermal_generator", "reactor_chamber",
			"large_battery", "huge_battery", "uranium_refiner");
	/** Batteries and their colour (top terminals, front stripe). battery_box is the Medium Battery. */
	static final Map<String, Integer> BATTERY_COLORS = new LinkedHashMap<>(Map.of());
	static {
		BATTERY_COLORS.put("small_battery", rgb(60, 190, 70));
		BATTERY_COLORS.put("battery_box", rgb(200, 40, 40));
		BATTERY_COLORS.put("large_battery", rgb(50, 110, 220));
		BATTERY_COLORS.put("huge_battery", rgb(160, 70, 210));
	}
	static final List<String> BLOCKS = List.of("generator", "crusher", "electric_furnace", "battery_box", "solar_panel", "copper_cable", "machine_frame",
			"advanced_machine_frame", "alloy_smelter", "industrial_crusher", "induction_furnace", "thermal_generator", "charger", "reactor_chamber", "item_pipe",
			"fast_item_pipe", "express_item_pipe", "filter_pipe", "small_battery", "large_battery", "huge_battery", "uranium_refiner",
			"data_cable", "drive_rack", "storage_terminal", "computer_crafter");

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
		shaped("small_battery", 1, new String[]{"LCL", "R R"}, "L", "voltworks:lead_ingot", "R", "minecraft:redstone",
				"C", "minecraft:copper_ingot");
		shaped("large_battery", 1, new String[]{"BCB", "SAS", "BCB"}, "B", "voltworks:battery_box", "C", "voltworks:circuit",
				"S", "voltworks:steel_ingot", "A", "voltworks:advanced_machine_frame");
		shaped("huge_battery", 1, new String[]{"BCB", "LAL", "BCB"}, "B", "voltworks:large_battery", "C", "voltworks:circuit",
				"L", "voltworks:lead_block", "A", "voltworks:advanced_machine_frame");
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

	// ---------------- logistics: item pipe, wrench, guide ----------------
	static void logistics() throws Exception {
		pipes();

		// Wrench: open-ended spanner, steel head with a slot, red grip
		boolean[][] mask = new boolean[16][16];
		int[][] col = new int[16][16];
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
			double px = x + 0.5, py = y + 0.5;
			double hx = px - 11.2, hy = py - 4.8;                      // head centre
			boolean head = Math.hypot(hx, hy) < 3.6;
			double along = (hx - hy) / Math.sqrt(2), across = (hx + hy) / Math.sqrt(2);
			boolean slot = along > -0.6 && Math.abs(across) < 1.25;     // slot opens toward the upper right
			double t = Math.max(0, Math.min(1, ((px - 3) * 6.5 + (py - 13) * -6.5) / (2 * 6.5 * 6.5)));
			boolean shaft = Math.hypot(px - (3 + 6.5 * t), py - (13 - 6.5 * t)) < 1.35;
			if ((head && !slot) || shaft) {
				mask[y][x] = true;
				boolean grip = shaft && !head && px + (16 - py) < 10.5;
				col[y][x] = grip ? shade(rgb(190, 60, 45), (x + y) % 2 == 0 ? 10 : -10) : shade(rgb(185, 190, 200), x - y > 4 ? 20 : -5);
			}
		}
		outlined("item/wrench", mask, col);
		write("assets/voltworks/models/item/wrench.json",
				"{ \"parent\": \"minecraft:item/handheld\", \"textures\": { \"layer0\": \"voltworks:item/wrench\" } }");
		itemDef("wrench", "voltworks:item/wrench");
		shaped("wrench", 1, new String[]{"I I", " T ", " I "}, "I", "minecraft:iron_ingot", "T", "voltworks:tin_ingot");

		// Guide: blue book with a copper lightning bolt on the cover
		png("item/guide", (x, y, r) -> {
			if (x < 3 || x > 13 || y < 1 || y > 14) return 0;
			if (x == 3 || x == 13 || y == 1 || y == 14) return rgb(24, 28, 44);
			if (x == 12) return y % 2 == 0 ? rgb(235, 228, 205) : rgb(205, 198, 175);       // page edges
			if (x <= 5) return rgb(40, 52, 96);                                             // spine
			boolean bolt = (y >= 3 && y <= 7 && (x == 10 - (y - 3) / 2 || x == 11 - (y - 3) / 2))
					|| (y == 8 && x >= 7 && x <= 10) || (y >= 9 && y <= 12 && (x == 9 - (y - 9) / 2 || x == 10 - (y - 9) / 2));
			if (bolt) return rgb(240, 150, 70);
			return shade(rgb(58, 76, 140), r.nextInt(9) - 4);
		});
		write("assets/voltworks/models/item/guide.json",
				"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/guide\" } }");
		itemDef("guide", "voltworks:item/guide");
		write("data/voltworks/recipe/guide.json", """
			{ "type": "minecraft:crafting_shapeless",
			  "ingredients": [ "minecraft:book", "minecraft:copper_ingot" ],
			  "result": { "id": "voltworks:guide", "count": 1 } }
			""");

		// Translations for the logistics items, merged into the language file written by models()
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("block.voltworks.item_pipe", "Item Pipe");
		lang.put("block.voltworks.fast_item_pipe", "Fast Item Pipe");
		lang.put("block.voltworks.express_item_pipe", "Express Item Pipe");
		lang.put("block.voltworks.filter_pipe", "Filter Pipe");
		lang.put("tooltip.voltworks.pipe.machines", "Machines empty into chests automatically");
		lang.put("tooltip.voltworks.pipe.chests", "To pull from a chest: sneak + right-click that pipe arm with a Wrench (Extract)");
		lang.put("jei.voltworks.crafter.missing", "Not in storage (highlighted)");
		lang.put("jei.voltworks.crafter.still_set", "The pattern is set anyway");
		lang.put("gui.voltworks.filter.allow", "Allow");
		lang.put("gui.voltworks.filter.deny", "Deny");
		lang.put("gui.voltworks.filter.allow.hint", "Only items in the filter pass. An empty filter lets everything through. Click to switch to Deny.");
		lang.put("gui.voltworks.filter.deny.hint", "Items in the filter are blocked; everything else passes. Click to switch to Allow.");
		lang.put("message.voltworks.pipe.extract_needs_inventory", "Only a pipe arm touching an inventory can extract");
		lang.put("item.voltworks.wrench", "Wrench");
		lang.put("item.voltworks.guide", "Voltworks Guide");
		lang.put("message.voltworks.wrench.rotate", "Wrench: Rotate mode");
		lang.put("message.voltworks.wrench.configure", "Wrench: Configure mode");
		lang.put("message.voltworks.wrench.cant_pick_up", "Only Voltworks blocks can be picked up with the wrench");
		lang.put("message.voltworks.wrench.cant_rotate", "This block can't be rotated");
		lang.put("message.voltworks.wrench.not_configurable", "This block has no configurable sides");
		lang.put("message.voltworks.wrench.side", "%s side: %s");
		lang.put("message.voltworks.pipe.nothing", "Nothing to connect to on that side");
		lang.put("message.voltworks.pipe.connected", "Pipe side: connected (not extracting)");
		lang.put("message.voltworks.pipe.extract", "Pipe side: extracting");
		lang.put("message.voltworks.pipe.disabled", "Pipe side: disconnected");
		lang.put("message.voltworks.pipe.none", "Pipe side: not connected");
		lang.put("tooltip.voltworks.wrench.rotate", "Mode: Rotate");
		lang.put("tooltip.voltworks.wrench.configure", "Mode: Configure sides");
		lang.put("tooltip.voltworks.wrench.hint1", "Right-click the air: switch Rotate / Configure mode");
		lang.put("tooltip.voltworks.wrench.hint2", "Right-click a pipe arm: connect / disconnect");
		lang.put("tooltip.voltworks.wrench.hint3", "Sneak + right-click: toggle Extract on a pipe arm, or pick up a Voltworks block");
		lang.put("tooltip.voltworks.chainsaw", "Fells the whole tree (sneak to cut one log)");
		for (String m : List.of("none", "input", "output", "both")) lang.put("side_mode.voltworks." + m, Character.toUpperCase(m.charAt(0)) + m.substring(1));
		for (String sd : List.of("front", "back", "left", "right", "top", "bottom")) lang.put("side.voltworks." + sd, Character.toUpperCase(sd.charAt(0)) + sd.substring(1));

		// Guide pages: tools/guide.txt, pages separated by a line containing only "---"
		Path guide = res.toAbsolutePath().getParent().getParent().getParent().resolve("tools/guide.txt");
		String[] pages = Files.readString(guide).replace("\r\n", "\n").strip().split("\n---\n");
		for (int i = 0; i < pages.length; i++) lang.put("guide.voltworks.page." + (i + 1), pages[i].strip());

		Path langFile = res.resolve("assets/voltworks/lang/en_us.json");
		String json = Files.readString(langFile).stripTrailing();
		StringBuilder sb = new StringBuilder(json.substring(0, json.lastIndexOf('}')).stripTrailing());
		for (var e : lang.entrySet()) sb.append(",\n  ").append(jsonString(e.getKey())).append(": ").append(jsonString(e.getValue()));
		sb.append("\n}\n");
		Files.writeString(langFile, sb.toString());
	}

	static String jsonString(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	/** Paints a sprite from a mask and colours, adding a dark 1px outline around it. */
	static void outlined(String path, boolean[][] mask, int[][] col) throws Exception {
		png(path, (x, y, r) -> {
			if (mask[y][x]) return col[y][x];
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + d[0], ny = y + d[1];
				if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && mask[ny][nx]) return rgb(28, 28, 32);
			}
			return 0;
		});
	}

	// ---------------- item pipes ----------------
	static int argb(int a, int r, int g, int b) { return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }

	/**
	 * All item pipes: clear glass tubes (so the items travelling inside show) with an opaque rim in the tier's colour.
	 * Extracting arms get a hazard-striped collar. The filter pipe has a mesh on its faces.
	 */
	static void pipes() throws Exception {
		png("block/item_pipe_extract", (x, y, r) -> ((x + y) / 2 % 2 == 0) ? rgb(235, 190, 40) : rgb(30, 30, 34));
		pipe("item_pipe", rgb(176, 182, 192), false);
		pipe("fast_item_pipe", rgb(196, 56, 44), false);
		pipe("express_item_pipe", rgb(240, 192, 40), false);
		pipe("filter_pipe", rgb(150, 74, 200), true);

		shaped("item_pipe", 8, new String[]{"TGT"}, "T", "voltworks:tin_ingot", "G", "minecraft:glass");
		shaped("fast_item_pipe", 8, new String[]{"PPP", "PSP", "PPP"}, "P", "voltworks:item_pipe", "S", "voltworks:steel_ingot");
		shaped("express_item_pipe", 6, new String[]{"PPP", "GCG", "PPP"}, "P", "voltworks:fast_item_pipe", "G", "minecraft:gold_ingot",
				"C", "voltworks:circuit");
		write("data/voltworks/recipe/filter_pipe.json", """
			{ "type": "minecraft:crafting_shapeless",
			  "ingredients": [ "voltworks:item_pipe", "minecraft:comparator" ],
			  "result": { "id": "voltworks:filter_pipe", "count": 1 } }
			""");
	}

	static void pipe(String name, int rim, boolean mesh) throws Exception {
		// Texture: faces use the 6x6 window at 5..10 (core, arm ends) or strips 0..4 x 5..10 (arm sides).
		png("block/" + name, (x, y, r) -> {
			if (x == 5 || x == 10 || y == 5 || y == 10) return shade(rim, (x + y) % 3 == 0 ? 18 : 0);   // rim
			if (mesh && x >= 6 && x <= 9 && y >= 6 && y <= 9 && (x + y) % 2 == 0) return shade(rim, -50);
			if (y == 6 && x != 5 && x != 10) return argb(80, 255, 255, 255);                         // glint
			return argb(28, 210, 235, 245);                                                            // clear glass
		});
		String tex = "\"textures\": { \"pipe\": { \"sprite\": \"voltworks:block/" + name + "\", \"force_translucent\": true },"
				+ " \"collar\": \"voltworks:block/item_pipe_extract\", \"particle\": \"voltworks:block/" + name + "\" }";
		write("assets/voltworks/models/block/" + name + "_core.json", """
			{ "ambientocclusion": false, %s,
			  "elements": [ { "from": [5,5,5], "to": [11,11,11], "faces": {
			    "north": {"uv":[5,5,11,11],"texture":"#pipe"}, "south": {"uv":[5,5,11,11],"texture":"#pipe"},
			    "east": {"uv":[5,5,11,11],"texture":"#pipe"}, "west": {"uv":[5,5,11,11],"texture":"#pipe"},
			    "up": {"uv":[5,5,11,11],"texture":"#pipe"}, "down": {"uv":[5,5,11,11],"texture":"#pipe"} } } ] }
			""".formatted(tex));
		write("assets/voltworks/models/block/" + name + "_side.json", """
			{ "ambientocclusion": false, %s,
			  "elements": [ { "from": [5,5,0], "to": [11,11,5], "faces": {
			    "north": {"uv":[5,5,11,11],"texture":"#pipe"},
			    "east": {"uv":[0,5,5,11],"texture":"#pipe"}, "west": {"uv":[0,5,5,11],"texture":"#pipe"},
			    "up": {"uv":[5,0,11,5],"texture":"#pipe", "rotation": 90}, "down": {"uv":[5,0,11,5],"texture":"#pipe", "rotation": 90} } } ] }
			""".formatted(tex));
		write("assets/voltworks/models/block/" + name + "_extract.json", """
			{ "ambientocclusion": false, %s,
			  "elements": [ { "from": [4,4,0], "to": [12,12,2], "faces": {
			    "north": {"uv":[4,4,12,12],"texture":"#collar"}, "south": {"uv":[4,4,12,12],"texture":"#collar"},
			    "east": {"uv":[0,4,2,12],"texture":"#collar"}, "west": {"uv":[0,4,2,12],"texture":"#collar"},
			    "up": {"uv":[4,0,12,2],"texture":"#collar"}, "down": {"uv":[4,0,12,2],"texture":"#collar"} } } ] }
			""".formatted(tex));
		write("assets/voltworks/models/block/" + name + "_inventory.json", """
			{ "parent": "minecraft:block/block", %s,
			  "elements": [
			    { "from": [0,5,5], "to": [16,11,11], "faces": {
			      "north": {"uv":[0,5,16,11],"texture":"#pipe"}, "south": {"uv":[0,5,16,11],"texture":"#pipe"},
			      "up": {"uv":[0,5,16,11],"texture":"#pipe"}, "down": {"uv":[0,5,16,11],"texture":"#pipe"},
			      "east": {"uv":[5,5,11,11],"texture":"#pipe"}, "west": {"uv":[5,5,11,11],"texture":"#pipe"} } },
			    { "from": [0,4,4], "to": [2,12,12], "faces": {
			      "north": {"uv":[0,4,2,12],"texture":"#collar"}, "south": {"uv":[0,4,2,12],"texture":"#collar"},
			      "up": {"uv":[0,4,2,12],"texture":"#collar"}, "down": {"uv":[0,4,2,12],"texture":"#collar"},
			      "east": {"uv":[4,4,12,12],"texture":"#collar"}, "west": {"uv":[4,4,12,12],"texture":"#collar"} } } ] }
			""".formatted(tex));
		String[][] dirs = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"},
				{"up", ", \"x\": 270"}, {"down", ", \"x\": 90"}};
		List<String> parts = new ArrayList<>();
		parts.add("{ \"apply\": { \"model\": \"voltworks:block/" + name + "_core\" } }");
		for (String[] d : dirs) {
			parts.add("{ \"when\": { " + q(d[0]) + ": \"connected|extract\" }, \"apply\": { \"model\": \"voltworks:block/" + name + "_side\"" + d[1] + " } }");
			parts.add("{ \"when\": { " + q(d[0]) + ": \"extract\" }, \"apply\": { \"model\": \"voltworks:block/" + name + "_extract\"" + d[1] + " } }");
		}
		write("assets/voltworks/blockstates/" + name + ".json", "{ \"multipart\": [\n  " + String.join(",\n  ", parts) + "\n] }");
		itemDef(name, "voltworks:block/" + name + "_inventory");
	}

	// ---------------- uranium refining ----------------
	/** Refined Uranium (a glowing fuel pellet) and the Uranium Refiner: two centrifuge tanks behind glass. */
	static void refining() throws Exception {
		png("item/refined_uranium", (x, y, r) -> {
			// A stubby pellet seen at an angle: rounded body, lighter top face, dark outline
			double dx = (x - 7.5) / 4.6, dyTop = (y - 5.0) / 2.0, dyBot = (y - 11.0) / 2.0;
			boolean body = Math.abs(dx) <= 1 && y >= 5 && y <= 11;
			boolean top = dx * dx + dyTop * dyTop <= 1;
			boolean bottom = dx * dx + dyBot * dyBot <= 1;
			if (!body && !top && !bottom) {
				// soft glow around the pellet
				double d = Math.hypot((x - 7.5) / 6.5, (y - 8.0) / 6.5);
				return d < 1 ? argb((int) (70 * (1 - d)), 140, 255, 90) : 0;
			}
			boolean edge = Math.abs(dx) > 0.82 || (bottom && dx * dx + dyBot * dyBot > 0.6 && y > 11);
			if (top && y <= 5) return y == 3 || Math.abs(dx) > 0.8 ? rgb(60, 120, 40) : rgb(200, 255, 150);
			if (edge) return rgb(50, 110, 35);
			return shade(rgb(110, 225, 70), (x - y) % 3 == 0 ? 25 : r.nextInt(9) - 4);
		});
		write("assets/voltworks/models/item/refined_uranium.json",
				"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/refined_uranium\" } }");
		itemDef("refined_uranium", "voltworks:item/refined_uranium");

		for (boolean on : new boolean[]{false, true}) {
			png("block/uranium_refiner_front" + (on ? "_on" : ""), (x, y, r) -> {
				for (int cx : new int[]{4, 11}) {
					if (Math.abs(x - cx) <= 2 && y >= 2 && y <= 11) {
						if (Math.abs(x - cx) == 2 || y == 2 || y == 11) return rgb(150, 156, 168);         // tank frame
						int level = on ? 4 + (int) ((x + cx) % 2) : 7;                                     // churning when on
						if (y >= level) return on ? rgb(110 + r.nextInt(50), 240, 80) : rgb(60, 110, 45);  // uranium solution
						return rgb(30, 40, 36);
					}
				}
				if (y >= 13 && y <= 14 && x >= 1 && x <= 14) return ((x + y) / 2 % 2 == 0) ? rgb(240, 200, 30) : rgb(25, 25, 25);
				if (x == 7 || x == 8) return y >= 4 && y <= 9 ? rgb(90, 96, 108) : casing2(x, y, r);         // pipe between tanks
				return casing2(x, y, r);
			});
		}
		machine("uranium_refiner", true);
		// Glass tanks, a water bucket for leaching, circuits and steel on an advanced frame
		shaped("uranium_refiner", 1, new String[]{"SCS", "GAG", "SWS"}, "S", "voltworks:steel_ingot", "C", "voltworks:circuit",
				"G", "minecraft:glass", "A", "voltworks:advanced_machine_frame", "W", "minecraft:water_bucket");
	}

	// ---------------- computer storage ----------------
	static final Map<String, Integer> DRIVE_COLORS = new LinkedHashMap<>();
	static {
		DRIVE_COLORS.put("small_hard_drive", rgb(70, 190, 80));
		DRIVE_COLORS.put("medium_hard_drive", rgb(60, 130, 230));
		DRIVE_COLORS.put("large_hard_drive", rgb(170, 80, 220));
		DRIVE_COLORS.put("huge_hard_drive", rgb(240, 190, 40));
	}

	/** Silicon, chips, hard drives, data cable, Drive Rack, Storage Terminal and Computer Crafter. */
	static void computers() throws Exception {
		// Silicon: a dark, glassy blue-grey crystal lump
		png("item/silicon", (x, y, r) -> {
			double d = Math.hypot((x - 7.5) / 5.5, (y - 8.5) / 4.8);
			if (d > 1) return 0;
			if (d > 0.82) return rgb(40, 46, 60);
			boolean facet = (x + 2 * y) % 7 < 2;
			return facet ? rgb(150, 165, 195) : shade(rgb(85, 96, 122), r.nextInt(15) - 7);
		});
		// Silicon chip: black package with silver pins and a bright die mark
		png("item/silicon_chip", (x, y, r) -> {
			if (inRect(x, y, 4, 4, 11, 11)) {
				if (inRect(x, y, 6, 6, 7, 7)) return rgb(120, 200, 255);
				return x == 4 || y == 4 ? rgb(70, 70, 78) : rgb(32, 32, 38);
			}
			boolean pinRow = (x == 2 || x == 3 || x == 12 || x == 13) && y >= 5 && y <= 10 && y % 2 == 1;
			boolean pinCol = (y == 2 || y == 3 || y == 12 || y == 13) && x >= 5 && x <= 10 && x % 2 == 1;
			return pinRow || pinCol ? rgb(200, 205, 215) : 0;
		});
		// Hard drives: a steel casing with a coloured label and an activity LED
		for (var d : DRIVE_COLORS.entrySet()) {
			int color = d.getValue();
			png("item/" + d.getKey(), (x, y, r) -> {
				if (!inRect(x, y, 2, 3, 13, 13)) return 0;
				if (x == 2 || x == 13 || y == 3 || y == 13) return rgb(40, 42, 48);
				if (inRect(x, y, 4, 5, 11, 8)) return y == 5 ? shade(color, 40) : color;    // label
				if (x == 11 && y == 11) return rgb(120, 255, 120);                           // LED
				if (inRect(x, y, 4, 10, 8, 11)) return rgb(70, 74, 82);                      // connector
				return shade(rgb(150, 156, 166), (x + y) % 4 == 0 ? 12 : 0);
			});
		}
		for (String item : List.of("silicon", "silicon_chip", "small_hard_drive", "medium_hard_drive", "large_hard_drive", "huge_hard_drive")) {
			write("assets/voltworks/models/item/" + item + ".json",
					"{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"voltworks:item/" + item + "\" } }");
			itemDef(item, "voltworks:item/" + item);
		}

		// Data cable: same shape as the copper cable (its models are copied with the texture swapped)
		png("block/data_cable", (x, y, r) -> {
			if (y == 7 || y == 8) return (x % 4 < 2) ? rgb(90, 220, 255) : rgb(40, 140, 200);   // light stripe
			return shade(rgb(30, 44, 80), y % 4 == 0 ? 15 : r.nextInt(7) - 3);
		});
		for (String part : List.of("models/block/copper_cable_core", "models/block/copper_cable_side", "models/block/copper_cable_inventory", "blockstates/copper_cable")) {
			String json = Files.readString(res.resolve("assets/voltworks/" + part + ".json"));
			write("assets/voltworks/" + part.replace("copper_cable", "data_cable") + ".json", json.replace("copper_cable", "data_cable"));
		}
		itemDef("data_cable", "voltworks:block/data_cable_inventory");

		// Drive Rack: eight drive bays, LEDs green while powered
		for (boolean on : new boolean[]{false, true}) {
			png("block/drive_rack_front" + (on ? "_on" : ""), (x, y, r) -> {
				int col = (x - 2) / 3, row = (y - 2) / 6;
				if (x >= 2 && x <= 13 && y >= 2 && y <= 13 && col < 4 && row < 2) {
					int bx = (x - 2) % 3, by = (y - 2) % 6;
					if (bx == 2 || by == 5) return rgb(30, 32, 38);                                  // gaps
					if (by == 0 && bx == 0) return on ? rgb(90, 255, 110) : rgb(40, 70, 45);        // LED
					return by % 2 == 0 ? rgb(120, 126, 138) : rgb(96, 102, 114);                      // drive face
				}
				return casing(x, y, r);
			});
		}
		machine("drive_rack", true);

		// Storage Terminal: a screen showing a list; Computer Crafter: a screen showing a 3x3 grid
		png("block/storage_terminal_front", (x, y, r) -> {
			if (!inRect(x, y, 2, 2, 13, 11)) return y >= 13 && y <= 13 && x >= 4 && x <= 11 ? rgb(60, 64, 72) : casing(x, y, r);
			if (x == 2 || x == 13 || y == 2 || y == 11) return rgb(30, 32, 38);
			if (y % 2 == 0 && x >= 4 && x <= 4 + (x * 7 + y * 3) % 8) return rgb(110, 230, 255);
			return rgb(16, 40, 56);
		});
		png("block/computer_crafter_front", (x, y, r) -> {
			if (!inRect(x, y, 2, 2, 13, 13)) return casing(x, y, r);
			if (x == 2 || x == 13 || y == 2 || y == 13) return rgb(30, 32, 38);
			int gx = (x - 3) % 3, gy = (y - 3) % 3;
			if (x >= 3 && x <= 11 && y >= 3 && y <= 11) return gx == 2 || gy == 2 ? rgb(16, 40, 56) : rgb(120, 200, 140);
			return rgb(16, 40, 56);
		});
		machine("storage_terminal", false);
		machine("computer_crafter", false);

		// Recipes
		shaped("silicon_chip", 3, new String[]{"SSS", "RCR"}, "S", "voltworks:silicon", "R", "minecraft:redstone", "C", "minecraft:copper_ingot");
		shaped("data_cable", 8, new String[]{"CCC", "CSC", "CCC"}, "C", "voltworks:copper_cable", "S", "voltworks:silicon_chip");
		shaped("drive_rack", 1, new String[]{"ICI", "CMC", "ICI"}, "I", "minecraft:iron_ingot", "C", "voltworks:silicon_chip",
				"M", "voltworks:machine_frame");
		shaped("storage_terminal", 1, new String[]{"GGG", "CMC", "IRI"}, "G", "minecraft:glass", "C", "voltworks:silicon_chip",
				"M", "voltworks:machine_frame", "I", "minecraft:iron_ingot", "R", "minecraft:redstone");
		write("data/voltworks/recipe/computer_crafter.json", """
			{ "type": "minecraft:crafting_shapeless",
			  "ingredients": [ "voltworks:storage_terminal", "minecraft:crafting_table", "voltworks:silicon_chip", "voltworks:silicon_chip" ],
			  "result": { "id": "voltworks:computer_crafter", "count": 1 } }
			""");
		shaped("small_hard_drive", 1, new String[]{"ICI", "ICI", "IRI"}, "I", "minecraft:iron_ingot", "C", "voltworks:silicon_chip",
				"R", "minecraft:redstone");
		shaped("medium_hard_drive", 1, new String[]{"ICI", "CGC", "ICI"}, "I", "minecraft:iron_ingot", "C", "voltworks:silicon_chip",
				"G", "minecraft:gold_ingot");
		shaped("large_hard_drive", 1, new String[]{"SCS", "CDC", "SCS"}, "S", "voltworks:steel_ingot", "C", "voltworks:silicon_chip",
				"D", "minecraft:diamond");
		shaped("huge_hard_drive", 1, new String[]{"SCS", "CNC", "SCS"}, "S", "voltworks:steel_ingot", "C", "voltworks:silicon_chip",
				"N", "minecraft:netherite_scrap");
	}
}
