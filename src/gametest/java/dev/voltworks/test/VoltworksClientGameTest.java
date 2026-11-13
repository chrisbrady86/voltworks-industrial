package dev.voltworks.test;

import dev.voltworks.Voltworks;
import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.MachineBlock;
import dev.voltworks.block.entity.BatteryBlockEntity;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.block.entity.ReactorBlockEntity;
import dev.voltworks.block.entity.SideConfig;
import dev.voltworks.item.FuelRodItem;
import dev.voltworks.item.ChargeableItem;
import dev.voltworks.item.WrenchItem;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModComponents;
import dev.voltworks.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * End-to-end test in a real singleplayer world: builds powered machine lines,
 * lets the game run, then checks every machine produced the right items.
 * Run with: ./gradlew runClientGameTest
 */
public class VoltworksClientGameTest implements FabricClientGameTest {
	private static final int RUN_TICKS = 700;

	@Override
	public void runTest(ClientGameTestContext context) {
		// The harness defaults to a superflat world (no ores); use normal terrain with its fixed seed instead.
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(s -> s.setWorldType(new WorldCreationUiState.WorldTypeEntry(
						s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL))))
				.create()) {
			world.getServer().runOnServer(VoltworksClientGameTest::build);
			context.waitTicks(RUN_TICKS);
			context.takeScreenshot("voltworks-machines");
			// Close-up of the front gauges on the Battery Box and the machines next to it
			world.getServer().runOnServer(server -> command(server, String.format("tp @a %d %d %d 0 20",
					origin(server).getX() + 10, origin(server).getY() + 1, origin(server).getZ() - 3)));
			context.waitTicks(10);
			context.takeScreenshot("voltworks-gauges");
			// Item pipes, extract collars and the wrench-cut join
			world.getServer().runOnServer(server -> command(server, String.format("tp @a %d %d %d 0 40",
					logisticsOrigin(origin(server)).getX() + 2, origin(server).getY() + 3, origin(server).getZ() - 3)));
			context.waitTicks(10);
			context.takeScreenshot("voltworks-pipes");
			// The meltdown crater, from above and to the north
			world.getServer().runOnServer(server -> {
				BlockPos hot = meltdownPos(origin(server));
				command(server, String.format("tp @a %d %d %d 0 50", hot.getX(), hot.getY() + 14, hot.getZ() - 16));
			});
			context.waitTicks(20);
			context.takeScreenshot("voltworks-meltdown");
			List<String> failures = world.getServer().computeOnServer(VoltworksClientGameTest::check);
			// Open the sped-up crusher's GUI (upgrade slots + output) and screenshot it
			world.getServer().runOnServer(server -> {
				BlockPos crusher = origin(server).offset(1, 0, 8);
				put(server.overworld(), crusher, 0, Items.RAW_GOLD, 16);
				command(server, String.format("tp @a %d %d %d 180 30", crusher.getX(), crusher.getY() + 1, crusher.getZ() - 2));
				server.getPlayerList().getPlayers().get(0).openMenu((MenuProvider) server.overworld().getBlockEntity(crusher));
			});
			context.waitTicks(30);
			context.takeScreenshot("voltworks-gui");
			world.getServer().runOnServer(server -> {
				BlockPos reactor = origin(server).offset(12, 0, 4);
				command(server, String.format("tp @a %d %d %d 0 30", reactor.getX(), reactor.getY() + 1, reactor.getZ() - 2));
				server.getPlayerList().getPlayers().get(0).openMenu((MenuProvider) server.overworld().getBlockEntity(reactor));
			});
			context.waitTicks(30);
			context.takeScreenshot("voltworks-reactor-gui");
			if (!failures.isEmpty()) {
				throw new AssertionError("Voltworks checks failed:\n - " + String.join("\n - ", failures));
			}
			System.out.println("[voltworks-test] ALL CHECKS PASSED");
		}
	}

	private static BlockPos origin(MinecraftServer server) {
		BlockPos spawn = server.getRespawnData().pos();
		return new BlockPos(spawn.getX(), 150, spawn.getZ());
	}

	private static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
	}

	private static void place(ServerLevel level, BlockPos pos, Block block) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(MachineBlock.FACING)) {
			state = state.setValue(MachineBlock.FACING, Direction.NORTH); // face the camera
		}
		level.setBlockAndUpdate(pos, state);
	}

	private static void put(ServerLevel level, BlockPos pos, int slot, Item item, int count) {
		((Container) level.getBlockEntity(pos)).setItem(slot, new ItemStack(item, count));
	}

	/*
	 * Layout (seen from above, north is up; the camera stands north looking south):
	 *   z=0:  GEN . CRU . EFU . ALY . ICR . GEN . BAT      (x = 0..12, machines on even x)
	 *   z=1:  cable line x = 0..12
	 *   z=4:  THERMAL(lava below) - cable - INDUCTION FURNACE
	 */
	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);
		command(server, "time set day");
		command(server, "weather clear");

		// Stone platform under everything
		for (int x = -3; x <= 15; x++) {
			for (int z = -8; z <= 9; z++) {
				level.setBlockAndUpdate(o.offset(x, -1, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(o.offset(x, -2, z), Blocks.STONE.defaultBlockState());
			}
		}

		Block[] row = {ModBlocks.GENERATOR, ModBlocks.CRUSHER, ModBlocks.ELECTRIC_FURNACE, ModBlocks.ALLOY_SMELTER,
				ModBlocks.INDUSTRIAL_CRUSHER, ModBlocks.GENERATOR, ModBlocks.BATTERY_BOX};
		for (int i = 0; i < row.length; i++) {
			place(level, o.offset(i * 2, 0, 0), row[i]);
		}
		for (int x = 0; x <= 12; x++) {
			place(level, o.offset(x, 0, 1), ModBlocks.COPPER_CABLE);
		}

		put(level, o.offset(0, 0, 0), 0, Items.COAL, 4);           // generator 1
		put(level, o.offset(10, 0, 0), 0, Items.COAL, 4);          // generator 2
		put(level, o.offset(2, 0, 0), 0, Items.RAW_IRON, 2);       // crusher -> 4 iron dust
		put(level, o.offset(4, 0, 0), 0, ModItems.IRON_DUST, 3);   // electric furnace -> 3 iron ingots
		put(level, o.offset(6, 0, 0), 0, Items.IRON_INGOT, 2);     // alloy smelter: iron + coal -> steel
		put(level, o.offset(6, 0, 0), 1, Items.COAL, 4);
		put(level, o.offset(8, 0, 0), 0, Items.RAW_GOLD, 4);       // industrial crusher -> 12 gold dust

		// Thermal generator with one contained lava source underneath
		BlockPos thermal = o.offset(0, 0, 4);
		level.setBlockAndUpdate(thermal.below(), Blocks.LAVA.defaultBlockState());
		place(level, thermal, ModBlocks.THERMAL_GENERATOR);
		place(level, o.offset(1, 0, 4), ModBlocks.COPPER_CABLE);
		place(level, o.offset(2, 0, 4), ModBlocks.INDUCTION_FURNACE);
		put(level, o.offset(2, 0, 4), 0, Items.RAW_IRON, 2);       // lane 1 -> 2 iron ingots
		put(level, o.offset(2, 0, 4), 2, Items.RAW_COPPER, 2);     // lane 2 -> 2 copper ingots

		// Separate network: Solar Panel -> cable -> Charger holding an empty Electric Drill
		place(level, o.offset(6, 0, 4), ModBlocks.SOLAR_PANEL);
		place(level, o.offset(7, 0, 4), ModBlocks.COPPER_CABLE);
		place(level, o.offset(8, 0, 4), ModBlocks.CHARGER);
		put(level, o.offset(8, 0, 4), 0, ModItems.ELECTRIC_DRILL, 1);

		// Upgrades line (z=6..7), powered by two generators:
		//   z=6:  GEN  cable  CRUSHER(ejector)
		//   z=7:  GEN  cable  E.FURNACE(2x capacity)  <- receives dust from the crusher next to it
		//   z=8:       CRUSHER(2x speed), fed by the z=7 cable
		place(level, o.offset(0, 0, 6), ModBlocks.GENERATOR);
		place(level, o.offset(0, 0, 7), ModBlocks.GENERATOR);
		place(level, o.offset(1, 0, 6), ModBlocks.COPPER_CABLE);
		place(level, o.offset(1, 0, 7), ModBlocks.COPPER_CABLE);
		place(level, o.offset(2, 0, 6), ModBlocks.CRUSHER);
		place(level, o.offset(2, 0, 7), ModBlocks.ELECTRIC_FURNACE);
		place(level, o.offset(1, 0, 8), ModBlocks.CRUSHER);
		put(level, o.offset(0, 0, 6), 0, Items.COAL, 4);
		put(level, o.offset(0, 0, 7), 0, Items.COAL, 4);
		put(level, o.offset(2, 0, 6), 0, Items.RAW_IRON, 2);          // -> 4 dust, ejected into the furnace -> 4 ingots
		put(level, o.offset(2, 0, 6), 2, ModItems.EJECTOR_UPGRADE, 1);
		put(level, o.offset(2, 0, 7), 2, ModItems.CAPACITY_UPGRADE, 1);
		put(level, o.offset(2, 0, 7), 3, ModItems.CAPACITY_UPGRADE, 1);
		put(level, o.offset(1, 0, 8), 0, Items.RAW_GOLD, 4);          // 2 speed upgrades: 98 ticks each -> 8 dust
		put(level, o.offset(1, 0, 8), 2, ModItems.SPEED_UPGRADE, 1);
		put(level, o.offset(1, 0, 8), 3, ModItems.SPEED_UPGRADE, 1);

		// Reactors. A (safe): 2 rods + 3 vents, redstone on, feeding a Battery Box.
		BlockPos safe = o.offset(12, 0, 4);
		place(level, safe, ModBlocks.REACTOR_CHAMBER);
		level.setBlockAndUpdate(safe.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		place(level, safe.west(), ModBlocks.BATTERY_BOX);
		put(level, safe, 0, ModItems.FUEL_ROD, 1);
		put(level, safe, 1, ModItems.FUEL_ROD, 1);
		put(level, safe, 2, ModItems.HEAT_VENT, 1);
		put(level, safe, 3, ModItems.HEAT_VENT, 1);
		put(level, safe, 4, ModItems.HEAT_VENT, 1);
		// B (off): a rod but no redstone signal.
		place(level, o.offset(12, 0, 7), ModBlocks.REACTOR_CHAMBER);
		put(level, o.offset(12, 0, 7), 4, ModItems.FUEL_ROD, 1);
		// C (meltdown): 9 rods, no vents, on its own 17x17 platform far enough away that the blast spares the rest.
		BlockPos hot = meltdownPos(o);
		for (int x = -MELT_PLATFORM; x <= MELT_PLATFORM; x++) for (int z = -MELT_PLATFORM; z <= MELT_PLATFORM; z++) level.setBlockAndUpdate(hot.offset(x, -1, z), Blocks.STONE.defaultBlockState());
		place(level, hot, ModBlocks.REACTOR_CHAMBER);
		for (int i = 0; i < 9; i++) put(level, hot, i, ModItems.FUEL_ROD, 1);
		level.setBlockAndUpdate(hot.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		// Something to power: a reactor with a full buffer pauses, so give it 200,000 J of batteries to fill.
		place(level, hot.west(), ModBlocks.BATTERY_BOX);
		place(level, hot.north(), ModBlocks.BATTERY_BOX);
		// D (idle): 2x2 rods + 5 vents, redstone on, nothing to power.
		BlockPos idle = o.offset(14, 0, 9);
		place(level, idle, ModBlocks.REACTOR_CHAMBER);
		level.setBlockAndUpdate(idle.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		for (int i : new int[]{0, 1, 3, 4}) put(level, idle, i, ModItems.FUEL_ROD, 1);
		for (int i : new int[]{2, 5, 6, 7, 8}) put(level, idle, i, ModItems.HEAT_VENT, 1);

		buildLogistics(level, o);

		// Camera: stand north of the rows, look south at them
		command(server, "gamemode spectator @a");
		command(server, String.format("tp @a %d %d %d 0 25", o.getX() + 6, o.getY() + 3, o.getZ() - 7));
	}

	private static List<String> check(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos o = origin(server);
		List<String> fails = new ArrayList<>();

		expect(fails, level, o.offset(2, 0, 0), 1, ModItems.IRON_DUST, 4, "Crusher: 2 raw iron -> 4 iron dust");
		expect(fails, level, o.offset(4, 0, 0), 1, Items.IRON_INGOT, 3, "Electric Furnace: 3 iron dust -> 3 iron ingots");
		expect(fails, level, o.offset(6, 0, 0), 2, ModItems.STEEL_INGOT, 2, "Alloy Smelter: 2 iron + 4 coal -> 2 steel");
		expect(fails, level, o.offset(8, 0, 0), 1, ModItems.GOLD_DUST, 12, "Industrial Crusher: 4 raw gold -> 12 gold dust");
		expect(fails, level, o.offset(2, 0, 4), 1, Items.IRON_INGOT, 2, "Induction Furnace lane 1: 2 raw iron -> 2 iron");
		expect(fails, level, o.offset(2, 0, 4), 3, Items.COPPER_INGOT, 2, "Induction Furnace lane 2: 2 raw copper -> 2 copper");

		if (!level.getBlockState(o.offset(0, 0, 4)).getValue(MachineBlock.LIT)) {
			fails.add("Thermal Generator should be active next to lava");
		}
		if (level.getBlockEntity(o.offset(12, 0, 0)) instanceof BatteryBlockEntity battery) {
			System.out.println("[voltworks-test] Battery charge: " + battery.getEnergy());
			if (battery.getEnergy() <= 0) fails.add("Battery Box should have stored surplus power");
			int bars = level.getBlockState(o.offset(12, 0, 0)).getValue(MachineBlock.CHARGE);
			System.out.println("[voltworks-test] Battery Box gauge: " + bars + "/6 bars");
			if (bars != battery.gaugeLevel() || bars < 1) fails.add("Battery Box gauge should show its charge (shows " + bars + ")");
		} else {
			fails.add("Battery Box block entity missing");
		}

		checkOres(fails, level, server);
		checkTools(fails, level, server, o);

		// Upgrades
		expect(fails, level, o.offset(2, 0, 7), 1, Items.IRON_INGOT, 4, "Ejector: crusher dust auto-fed into the furnace -> 4 iron ingots");
		ItemStack leftover = ((Container) level.getBlockEntity(o.offset(2, 0, 6))).getItem(1);
		if (!leftover.isEmpty()) fails.add("Ejector should have emptied the crusher's output (left " + leftover + ")");
		expect(fails, level, o.offset(1, 0, 8), 1, ModItems.GOLD_DUST, 8, "2 Speed Upgrades: 4 raw gold crushed in 700 ticks -> 8 gold dust");
		int cap = ((PoweredContainerBlockEntity) level.getBlockEntity(o.offset(2, 0, 7))).getCapacity();
		System.out.println("[voltworks-test] Electric Furnace capacity with 2 Capacity Upgrades: " + cap + " J");
		if (cap != 1_000 + 2 * 4_000) fails.add("2 Capacity Upgrades should give 9000 J (got " + cap + ")");
		checkReactors(fails, level, o);
		checkLogistics(fails, level, server, o);

		// Data-driven recipes must have parsed
		for (String id : List.of("machine_frame", "copper_cable", "generator", "crusher", "electric_furnace", "battery_box", "solar_panel",
				"alloy_smelter", "circuit", "advanced_machine_frame", "industrial_crusher", "induction_furnace", "thermal_generator",
				"iron_ingot_from_smelting_dust", "gold_ingot_from_blasting_dust",
				"tin_ingot_from_smelting_raw_tin", "lead_ingot_from_blasting_lead_dust", "tin_block", "lead_ingot_from_lead_block",
				"steel_block", "item_pipe", "wrench", "guide")) {
			if (server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Voltworks.id(id))).isEmpty()) {
				fails.add("Recipe failed to load: voltworks:" + id);
			}
		}
		return fails;
	}

	/** Charger + solar, tool tiers, empty-tool behaviour, and a real 3x3 dig by the test player. */
	private static void checkTools(List<String> fails, ServerLevel level, MinecraftServer server, BlockPos o) {
		// 1. Solar panel charged the drill through the Charger
		ItemStack charged = ((Container) level.getBlockEntity(o.offset(8, 0, 4))).getItem(0);
		int chargedEnergy = ((ChargeableItem) ModItems.ELECTRIC_DRILL).getEnergy(charged);
		System.out.println("[voltworks-test] Drill charged by Solar Panel + Charger: " + chargedEnergy + " J");
		if (chargedEnergy <= 0) fails.add("Charger should have charged the drill from the Solar Panel");

		// 2. Tool tiers and the no-power behaviour
		ItemStack drill = full(ModItems.ELECTRIC_DRILL);
		ItemStack adv = full(ModItems.ADVANCED_DRILL);
		ItemStack saw = full(ModItems.CHAINSAW);
		ItemStack empty = new ItemStack(ModItems.ELECTRIC_DRILL);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
		if (!drill.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) fails.add("Electric Drill should mine diamond ore");
		if (drill.isCorrectToolForDrops(obsidian)) fails.add("Electric Drill (iron tier) should not harvest obsidian");
		if (!adv.isCorrectToolForDrops(obsidian)) fails.add("Advanced Drill (diamond tier) should harvest obsidian");
		if (drill.getDestroySpeed(Blocks.DIRT.defaultBlockState()) <= 1) fails.add("Electric Drill should dig dirt fast (shovel)");
		if (saw.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) <= 1) fails.add("Chainsaw should cut logs fast");
		if (empty.getDestroySpeed(stone) != 1.0F || empty.isCorrectToolForDrops(stone)) fails.add("An empty drill should act like a bare hand");
		System.out.println("[voltworks-test] Mining speed on stone: drill=" + drill.getDestroySpeed(stone) + ", advanced=" + adv.getDestroySpeed(stone)
				+ ", empty=" + empty.getDestroySpeed(stone));

		// 3. 3x3 mode: the player (facing south) digs the centre of a 3x3 stone wall
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		command(server, "gamemode survival @a");
		command(server, String.format("tp @a %d %d %d 0 0", o.getX() + 6, o.getY() + 3, o.getZ() - 7));
		BlockPos center = o.offset(6, 3, -4);
		for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) level.setBlockAndUpdate(center.offset(a, b, 0), stone);
		level.setBlockAndUpdate(center.offset(0, 0, 1), stone); // behind the wall: must NOT be mined
		adv.set(ModComponents.WIDE_MODE, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, adv);
		int before = ((ChargeableItem) ModItems.ADVANCED_DRILL).getEnergy(adv);
		player.gameMode.destroyBlock(center);
		int mined = 0;
		for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (level.getBlockState(center.offset(a, b, 0)).isAir()) mined++;
		int used = before - ((ChargeableItem) ModItems.ADVANCED_DRILL).getEnergy(player.getMainHandItem());
		System.out.println("[voltworks-test] 3x3 drill: mined " + mined + "/9 blocks, used " + used + " J");
		if (mined != 9) fails.add("Advanced Drill 3x3 should mine all 9 blocks (mined " + mined + ")");
		if (used != 9 * 80) fails.add("3x3 should cost 9 x 80 J (used " + used + ")");
		if (level.getBlockState(center.offset(0, 0, 1)).isAir()) fails.add("3x3 must not dig the block behind the wall");
		command(server, "gamemode spectator @a");
	}

	/** Safe reactor stays cold and makes power; unpowered one idles; overloaded one melts down. */
	private static void checkReactors(List<String> fails, ServerLevel level, BlockPos o) {
		BlockPos safe = o.offset(12, 0, 4);
		if (level.getBlockEntity(safe) instanceof ReactorBlockEntity r) {
			int fuel = FuelRodItem.getFuel(r.getItem(0));
			BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(safe.west());
			System.out.println("[voltworks-test] Safe reactor: " + r.getOutputPerTick() + " J/t, heat " + r.getHeat()
					+ ", rod fuel " + fuel + "/" + FuelRodItem.LIFETIME + ", battery " + battery.getEnergy() + " J");
			if (r.getOutputPerTick() != 2 * 2 * ReactorBlockEntity.POWER_PER_PULSE) fails.add("Two adjacent rods should make 4 pulses of power (got " + r.getOutputPerTick() + " J/t)");
			if (r.getHeat() != 0) fails.add("Safe reactor (3 vents) should stay at 0 heat (got " + r.getHeat() + ")");
			if (fuel >= FuelRodItem.LIFETIME) fails.add("Safe reactor should have burned some fuel");
			if (battery.getEnergy() <= 0) fails.add("Reactor should have charged the adjacent Battery Box");
			int rbars = level.getBlockState(safe.west()).getValue(MachineBlock.CHARGE);
			System.out.println("[voltworks-test] Reactor-fed Battery Box gauge: " + rbars + "/6 bars (" + battery.getEnergy() + " J)");
			if (rbars != battery.gaugeLevel() || rbars < 3) fails.add("Reactor-fed battery should show its charge, at least 3 of 6 bars (shows " + rbars + ")");
			if (r.getComparatorSignal() != 0) fails.add("Cold reactor comparator signal should be 0");
		} else {
			fails.add("Safe reactor is missing!");
		}

		ReactorBlockEntity off = (ReactorBlockEntity) level.getBlockEntity(o.offset(12, 0, 7));
		if (off.getEnergy() != 0 || FuelRodItem.getFuel(off.getItem(4)) != FuelRodItem.LIFETIME) {
			fails.add("Reactor without a redstone signal must not run");
		}

		// D: 2x2 rods with nothing to power. Its 30,000 J buffer fills in 100 ticks, then it must pause and save fuel.
		ReactorBlockEntity idle = (ReactorBlockEntity) level.getBlockEntity(o.offset(14, 0, 9));
		int idleFuel = FuelRodItem.getFuel(idle.getItem(0));
		System.out.println("[voltworks-test] Idle reactor: buffer " + idle.getEnergy() + "/" + idle.getCapacity() + " J, rod fuel " + idleFuel
				+ ", output " + idle.getOutputPerTick() + " J/t");
		if (idle.getEnergy() != idle.getCapacity()) fails.add("Idle reactor should have filled its buffer (" + idle.getEnergy() + " J)");
		if (idleFuel < FuelRodItem.LIFETIME - 150) fails.add("Reactor with a full buffer must stop burning fuel (rod at " + idleFuel + ")");
		if (idle.getOutputPerTick() != 0) fails.add("Reactor with a full buffer should report 0 J/t");

		BlockPos hot = meltdownPos(o);
		int crater = 0;
		for (int x = -MELT_PLATFORM; x <= MELT_PLATFORM; x++) for (int z = -MELT_PLATFORM; z <= MELT_PLATFORM; z++) if (level.getBlockState(hot.offset(x, -1, z)).isAir()) crater++;
		boolean gone = !level.getBlockState(hot).is(ModBlocks.REACTOR_CHAMBER);
		System.out.println("[voltworks-test] Overloaded reactor: melted down=" + gone + ", platform blocks destroyed=" + crater + "/" + (2 * MELT_PLATFORM + 1) * (2 * MELT_PLATFORM + 1));
		if (!gone) fails.add("9-rod reactor with no cooling should have melted down");
		if (crater < 10) fails.add("Meltdown should blow a crater in the platform (only " + crater + " blocks)");
	}

	/*
	 * Logistics, on its own platform west of the main layout (L = origin - 12x), all rows run west -> east:
	 *   z=0:  CHEST(16 raw iron) pipe[extract west] pipe pipe CHEST           -> items cross the network
	 *   z=3:  GEN cable CRUSHER(east side = Output) pipe CHEST               -> pipe defaults to extract, dust flows
	 *   z=6:  CHEST(8 cobble) pipe[extract] pipe |cut| pipe CHEST             -> wrench-disabled join blocks the flow
	 *   z=9:  GEN (rotated by wrench)   MACHINE FRAME (picked up by wrench)
	 */
	/** Half-width of the meltdown reactor's stone platform. */
	private static final int MELT_PLATFORM = 8;

	/** The meltdown reactor: far enough east that a full-power blast can't reach the main layout. */
	private static BlockPos meltdownPos(BlockPos o) {
		return o.offset(50, 0, 0);
	}

	private static BlockPos logisticsOrigin(BlockPos o) {
		return o.offset(-12, 0, 0);
	}

	private static void placePipe(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(ModBlocks.ITEM_PIPE.defaultBlockState(), level, pos));
	}

	private static void buildLogistics(ServerLevel level, BlockPos o) {
		BlockPos l = logisticsOrigin(o);
		for (int x = -4; x <= 6; x++) for (int z = -2; z <= 10; z++) level.setBlockAndUpdate(l.offset(x, -1, z), Blocks.STONE.defaultBlockState());

		// Row 1: chest -> chest across three pipes, first arm switched to extract with the wrench
		level.setBlockAndUpdate(l, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(l.offset(4, 0, 0), Blocks.CHEST.defaultBlockState());
		for (int x = 1; x <= 3; x++) placePipe(level, l.offset(x, 0, 0));
		put(level, l, 0, Items.RAW_IRON, 16);
		WrenchItem.cyclePipe(level, l.offset(1, 0, 0), Direction.WEST); // connected -> extract

		// Row 2: a powered crusher whose east side is configured as Output; the pipe placed there starts extracting
		BlockPos crusher = l.offset(2, 0, 3);
		place(level, l.offset(0, 0, 3), ModBlocks.GENERATOR);
		place(level, l.offset(1, 0, 3), ModBlocks.COPPER_CABLE);
		place(level, crusher, ModBlocks.CRUSHER);
		put(level, l.offset(0, 0, 3), 0, Items.COAL, 4);
		put(level, crusher, 0, Items.RAW_IRON, 2);
		for (int i = 0; i < 4 && ((PoweredContainerBlockEntity) level.getBlockEntity(crusher)).getSideMode(Direction.EAST) != SideConfig.Mode.OUTPUT; i++) {
			WrenchItem.configure(level, crusher, Direction.EAST);
		}
		level.setBlockAndUpdate(l.offset(4, 0, 3), Blocks.CHEST.defaultBlockState());
		placePipe(level, l.offset(3, 0, 3));

		// Row 3: same as row 1, but the join between the 2nd and 3rd pipe is cut with the wrench
		BlockPos c = l.offset(0, 0, 6);
		level.setBlockAndUpdate(c, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(c.offset(4, 0, 0), Blocks.CHEST.defaultBlockState());
		for (int x = 1; x <= 3; x++) placePipe(level, c.offset(x, 0, 0));
		put(level, c, 0, Items.COBBLESTONE, 8);
		WrenchItem.cyclePipe(level, c.offset(1, 0, 0), Direction.WEST); // extract
		WrenchItem.cyclePipe(level, c.offset(2, 0, 0), Direction.EAST); // pipe-to-pipe: connected -> disabled

		// Row 4: wrench rotation and pick-up targets
		place(level, l.offset(0, 0, 9), ModBlocks.GENERATOR);
		place(level, l.offset(2, 0, 9), ModBlocks.MACHINE_FRAME);
	}

	private static void checkLogistics(List<String> fails, ServerLevel level, MinecraftServer server, BlockPos o) {
		BlockPos l = logisticsOrigin(o);

		// Row 1
		if (ItemPipeBlock.get(level.getBlockState(l.offset(1, 0, 0)), Direction.WEST) != ItemPipeBlock.Side.EXTRACT) {
			fails.add("Wrench should switch the pipe arm at the source chest to extract");
		}
		expect(fails, level, l.offset(4, 0, 0), 0, Items.RAW_IRON, 16, "Item Pipe: 16 raw iron moved chest -> chest");
		if (!((Container) level.getBlockEntity(l)).isEmpty()) fails.add("Item Pipe should have emptied the source chest");

		// Row 2
		BlockPos crusher = l.offset(2, 0, 3);
		SideConfig.Mode eastMode = ((PoweredContainerBlockEntity) level.getBlockEntity(crusher)).getSideMode(Direction.EAST);
		if (eastMode != SideConfig.Mode.OUTPUT) fails.add("Wrench should set the crusher's east side to Output (is " + eastMode + ")");
		ItemPipeBlock.Side pipeSide = ItemPipeBlock.get(level.getBlockState(l.offset(3, 0, 3)), Direction.WEST);
		if (pipeSide != ItemPipeBlock.Side.EXTRACT) fails.add("A pipe placed on a machine's Output side should start extracting (is " + pipeSide + ")");
		expect(fails, level, l.offset(4, 0, 3), 0, ModItems.IRON_DUST, 4, "Item Pipe: crusher output (4 iron dust) piped into a chest");
		ItemStack crusherOut = ((Container) level.getBlockEntity(crusher)).getItem(1);
		if (!crusherOut.isEmpty()) fails.add("Pipe should have emptied the crusher's output (left " + crusherOut + ")");

		// Row 3
		BlockPos c = l.offset(0, 0, 6);
		if (ItemPipeBlock.get(level.getBlockState(c.offset(3, 0, 0)), Direction.WEST) != ItemPipeBlock.Side.DISABLED) {
			fails.add("Disabling a pipe join should cut off both pipes");
		}
		if (!((Container) level.getBlockEntity(c.offset(4, 0, 0))).isEmpty()) fails.add("Items must not cross a disabled pipe join");
		expect(fails, level, c, 0, Items.COBBLESTONE, 8, "Disabled join: source chest keeps its 8 cobblestone");

		// Row 4: rotate and pick up
		BlockPos gen = l.offset(0, 0, 9);
		WrenchItem.rotate(level, gen, Direction.EAST);
		Direction facing = level.getBlockState(gen).getValue(MachineBlock.FACING);
		if (facing != Direction.EAST) fails.add("Wrench should turn the generator to face the clicked side (faces " + facing + ")");
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		BlockPos frame = l.offset(2, 0, 9);
		boolean picked = WrenchItem.dismantle(level, frame, player);
		if (!picked || !level.getBlockState(frame).isAir()) fails.add("Wrench should pick up the Machine Frame");
		if (!player.getInventory().contains(new ItemStack(ModBlocks.MACHINE_FRAME.asItem()))) fails.add("Picked-up Machine Frame should be in the player's inventory");
		if (WrenchItem.dismantle(level, l.offset(0, -1, 0), player)) fails.add("Wrench must not pick up vanilla blocks");
	}

	private static ItemStack full(Item item) {
		ItemStack stack = new ItemStack(item);
		((ChargeableItem) item).setEnergy(stack, Integer.MAX_VALUE);
		return stack;
	}

	/** Ores must have generated in natural terrain, drop raw ore, and need the right pickaxe tier. */
	private static void checkOres(List<String> fails, ServerLevel level, MinecraftServer server) {
		BlockPos spawn = server.getRespawnData().pos();
		int uranium = 0, iron = 0, tin = 0, lead = 0, minLeadY = Integer.MAX_VALUE, maxTinY = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -40; x <= 40; x++) {
			for (int z = -40; z <= 40; z++) {
				for (int y = -64; y <= 100; y++) {
					p.set(spawn.getX() + x, y, spawn.getZ() + z);
					Block b = level.getBlockState(p).getBlock();
					if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) iron++;
					if (b == ModBlocks.URANIUM_ORE || b == ModBlocks.DEEPSLATE_URANIUM_ORE) uranium++;
					if (b == ModBlocks.TIN_ORE || b == ModBlocks.DEEPSLATE_TIN_ORE) {
						tin++;
						maxTinY = Math.max(maxTinY, y);
					} else if (b == ModBlocks.LEAD_ORE || b == ModBlocks.DEEPSLATE_LEAD_ORE) {
						lead++;
						minLeadY = Math.min(minLeadY, y);
					}
				}
			}
		}
		System.out.println("[voltworks-test] Ore blocks within 40 blocks of spawn: vanilla iron=" + iron + ", tin=" + tin + " (highest y " + maxTinY
				+ "), lead=" + lead + " (lowest y " + minLeadY + ")");
		if (tin == 0) fails.add("No tin ore generated near spawn");
		if (lead == 0) fails.add("No lead ore generated near spawn");
		System.out.println("[voltworks-test] Uranium ore within 40 blocks of spawn: " + uranium);
		if (uranium == 0) fails.add("No uranium ore generated near spawn");
		if (lead > 0 && minLeadY > 40) fails.add("Lead ore should be deep");

		List<ItemStack> drops = Block.getDrops(ModBlocks.TIN_ORE.defaultBlockState(), level, spawn, null);
		System.out.println("[voltworks-test] Tin ore drops: " + drops);
		if (drops.isEmpty() || !drops.get(0).is(ModItems.RAW_TIN)) fails.add("Tin ore should drop raw tin, got " + drops);
		List<ItemStack> leadDrops = Block.getDrops(ModBlocks.DEEPSLATE_LEAD_ORE.defaultBlockState(), level, spawn, null);
		if (leadDrops.isEmpty() || !leadDrops.get(0).is(ModItems.RAW_LEAD)) fails.add("Deepslate lead ore should drop raw lead, got " + leadDrops);

		if (!ModBlocks.TIN_ORE.defaultBlockState().is(BlockTags.NEEDS_STONE_TOOL)) fails.add("Tin ore should need a stone pickaxe");
		if (!ModBlocks.LEAD_ORE.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL)) fails.add("Lead ore should need an iron pickaxe");
		if (!ModBlocks.LEAD_ORE.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)) fails.add("Lead ore should be mineable with a pickaxe");
	}

	private static void expect(List<String> fails, ServerLevel level, BlockPos pos, int slot, Item item, int count, String what) {
		ItemStack actual = ((Container) level.getBlockEntity(pos)).getItem(slot);
		String result = actual.getCount() + "x " + actual.getItem();
		System.out.println("[voltworks-test] " + what + " | got " + result);
		if (!actual.is(item) || actual.getCount() != count) {
			fails.add(what + " (got " + result + ")");
		}
	}
}
