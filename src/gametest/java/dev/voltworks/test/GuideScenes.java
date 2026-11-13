package dev.voltworks.test;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.block.entity.BatteryBlockEntity;
import dev.voltworks.block.entity.FilterPipeBlockEntity;
import dev.voltworks.client.GuideScreen;
import dev.voltworks.item.WrenchItem;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small example builds photographed for the guide's pictures. The screenshots are saved as "guide-NAME"; copy them
 * into the mod with {@code java tools/GuideImages.java build/run/clientGameTest/screenshots src/main/resources}.
 * Each scene sits on its own grass platform north-west of the test layout, seen from the north. The camera faces south,
 * so scenes are built towards -x (offsets are negated) to read left to right in the picture.
 */
final class GuideScenes {
	/** A scene: its name (= the picture's name), and where its middle is relative to the scene origin. */
	private record Scene(String name, double centerX) {
	}

	private static final List<Scene> SCENES = List.of(
			new Scene("power", 3.5), new Scene("machines", 3.5), new Scene("pipes", 3.5), new Scene("reactor", 3.5), new Scene("storage", 4.0));
	private static final int SPACING = 16;

	private GuideScenes() {
	}

	private static BlockPos sceneOrigin(BlockPos o, int index) {
		return o.offset(-40 + index * SPACING, 0, -45);
	}

	static void build(ServerLevel level, BlockPos o) {
		for (int i = 0; i < SCENES.size(); i++) {
			BlockPos s = sceneOrigin(o, i);
			for (int x = -12; x <= 4; x++) for (int z = -8; z <= 9; z++) { // the platforms join into one lawn
				level.setBlockAndUpdate(s.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(s.offset(x, -2, z), Blocks.DIRT.defaultBlockState());
			}
		}
		buildPower(level, sceneOrigin(o, 0));
		buildMachines(level, sceneOrigin(o, 1));
		buildPipes(level, sceneOrigin(o, 2));
		buildReactor(level, sceneOrigin(o, 3));
		buildStorage(level, sceneOrigin(o, 4));
	}

	/** Photographs every scene, then opens the guide for a few screenshots and returns its layout problems. */
	static List<String> capture(ClientGameTestContext context, TestSingleplayerContext world) {
		// Spectator: no hand or block outline in the pictures. The mode is put back afterwards.
		GameType before = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).gameMode());
		world.getServer().runOnServer(server -> command(server, "gamemode spectator @a"));
		// No chat (game mode and advancement messages) in the pictures
		ChatVisiblity chat = context.computeOnClient(client -> client.options.chatVisibility().get());
		context.runOnClient(client -> client.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		context.runOnClient(client -> setHudHidden(client, true)); // like F1: no crosshair
		for (int i = 0; i < SCENES.size(); i++) {
			Scene scene = SCENES.get(i);
			int index = i;
			world.getServer().runOnServer(server -> {
				BlockPos s = sceneOrigin(origin(server), index);
				command(server, String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f 0 38", s.getX() + 1 - scene.centerX(), s.getY() + 1.4, s.getZ() - 2.6)); // the eye is 1.6 above this
			});
			context.waitTicks(i == 0 ? 60 : 25);
			context.takeScreenshot("guide-" + scene.name());
		}
		context.runOnClient(client -> client.options.chatVisibility().set(chat));
		context.runOnClient(client -> setHudHidden(client, false));
		List<String> problems = context.computeOnClient(client -> GuideScreen.problems());
		List<String> titles = context.computeOnClient(client -> GuideScreen.loadPages().stream().map(GuideScreen.Page::title).toList());
		for (int page : new int[]{-1, titles.indexOf("Power"), titles.indexOf("Reactor")}) {
			context.runOnClient(client -> client.gui.setScreen(new GuideScreen(page)));
			context.waitTicks(10);
			context.takeScreenshot("guide-ui-" + (page == -1 ? "contents" : "page-" + (page + 1)));
		}
		context.runOnClient(client -> client.gui.setScreen(null));
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).setGameMode(before));
		return problems;
	}

	// Generator, Solar Panel and Thermal Generator on one cable line feeding an Electric Furnace, a Crusher and a battery.
	private static void buildPower(ServerLevel level, BlockPos s) {
		Block[] row = {ModBlocks.GENERATOR, ModBlocks.COPPER_CABLE, ModBlocks.ELECTRIC_FURNACE, ModBlocks.COPPER_CABLE, ModBlocks.CRUSHER,
				ModBlocks.COPPER_CABLE, ModBlocks.BATTERY_BOX};
		for (int x = 0; x < row.length; x++) place(level, s.offset(-x, 0, 0), row[x]);
		place(level, s.offset(-1, 0, 1), ModBlocks.SOLAR_PANEL);
		place(level, s.offset(-5, 0, 1), ModBlocks.THERMAL_GENERATOR);
		level.setBlockAndUpdate(s.offset(-5, -1, 1), Blocks.LAVA.defaultBlockState()); // hidden under it
		put(level, s, 0, Items.COAL, 64);
		put(level, s.offset(-2, 0, 0), 0, Items.RAW_IRON, 64);
		put(level, s.offset(-4, 0, 0), 0, Items.RAW_COPPER, 64);
	}

	// The six machines in a row, a cable line behind them and a full battery.
	private static void buildMachines(ServerLevel level, BlockPos s) {
		Block[] row = {ModBlocks.CRUSHER, ModBlocks.ELECTRIC_FURNACE, ModBlocks.ALLOY_SMELTER, ModBlocks.INDUSTRIAL_CRUSHER,
				ModBlocks.INDUCTION_FURNACE, ModBlocks.URANIUM_REFINER};
		for (int x = 0; x < row.length; x++) {
			place(level, s.offset(-x, 0, 0), row[x]);
			place(level, s.offset(-x, 0, 1), ModBlocks.COPPER_CABLE);
		}
		place(level, s.offset(-6, 0, 1), ModBlocks.LARGE_BATTERY);
		fillBattery(level, s.offset(-6, 0, 1));
		put(level, s, 0, Items.RAW_IRON, 64);
		put(level, s.offset(-1, 0, 0), 0, Items.RAW_GOLD, 64);
		put(level, s.offset(-3, 0, 0), 0, Items.RAW_COPPER, 64);
		put(level, s.offset(-4, 0, 0), 0, Items.RAW_IRON, 64);
		put(level, s.offset(-5, 0, 0), 0, ModItems.RAW_URANIUM, 64);
	}

	// Front: chest -> extract pipe -> pipes -> Filter Pipe -> chest. Behind: a Crusher emptied by fast pipes into a chest.
	private static void buildPipes(ServerLevel level, BlockPos s) {
		level.setBlockAndUpdate(s, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(s.offset(-6, 0, 0), Blocks.CHEST.defaultBlockState());
		for (int x = 1; x <= 4; x++) pipe(level, s.offset(-x, 0, 0), ModBlocks.ITEM_PIPE);
		pipe(level, s.offset(-5, 0, 0), ModBlocks.FILTER_PIPE);
		((FilterPipeBlockEntity) level.getBlockEntity(s.offset(-5, 0, 0))).setFilter(0, new ItemStack(Items.RAW_IRON));
		for (int slot = 0; slot < 6; slot++) put(level, s, slot, Items.RAW_IRON, 64);
		WrenchItem.toggleExtract(level, s.offset(-1, 0, 0), Direction.EAST);

		place(level, s.offset(0, 0, 3), ModBlocks.GENERATOR);
		place(level, s.offset(-1, 0, 3), ModBlocks.COPPER_CABLE);
		place(level, s.offset(-2, 0, 3), ModBlocks.CRUSHER);
		put(level, s.offset(0, 0, 3), 0, Items.COAL, 64);
		put(level, s.offset(-2, 0, 3), 0, Items.RAW_GOLD, 64);
		for (int x = 3; x <= 5; x++) pipe(level, s.offset(-x, 0, 3), ModBlocks.FAST_ITEM_PIPE);
		level.setBlockAndUpdate(s.offset(-6, 0, 3), Blocks.CHEST.defaultBlockState());
	}

	// A running reactor (2 rods, 3 vents) in a cut-away shell of two lead layers, charging a battery.
	private static void buildReactor(ServerLevel level, BlockPos s) {
		BlockPos r = s.offset(-3, 0, 1);
		for (int dx = -3; dx <= 3; dx++) for (int dz = 0; dz <= 3; dz++) for (int dy = 0; dy <= 1; dy++) {
			int d = Math.max(Math.abs(dx), Math.abs(dz));
			if (d >= 2) level.setBlockAndUpdate(r.offset(dx, dy, dz), ModBlocks.LEAD_BLOCK.defaultBlockState());
		}
		place(level, r, ModBlocks.REACTOR_CHAMBER);
		level.setBlockAndUpdate(r.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		place(level, r.west(), ModBlocks.HUGE_BATTERY);
		put(level, r, 3, ModItems.FUEL_ROD, 1);
		put(level, r, 4, ModItems.FUEL_ROD, 1);
		put(level, r, 0, ModItems.HEAT_VENT, 1);
		put(level, r, 1, ModItems.HEAT_VENT, 1);
		put(level, r, 2, ModItems.HEAT_VENT, 1);
	}

	// Battery, Drive Rack with drives, Data Cable, Storage Terminal, Computer Crafter, Storage Interface piped to a chest.
	private static void buildStorage(ServerLevel level, BlockPos s) {
		place(level, s, ModBlocks.BATTERY_BOX);
		fillBattery(level, s);
		place(level, s.offset(-1, 0, 0), ModBlocks.DRIVE_RACK);
		Item[] drives = {ModItems.SMALL_HARD_DRIVE, ModItems.MEDIUM_HARD_DRIVE, ModItems.LARGE_HARD_DRIVE, ModItems.HUGE_HARD_DRIVE};
		for (int i = 0; i < drives.length; i++) put(level, s.offset(-1, 0, 0), i, drives[i], 1);
		pipe(level, s.offset(-2, 0, 0), ModBlocks.DATA_CABLE);
		place(level, s.offset(-3, 0, 0), ModBlocks.STORAGE_TERMINAL);
		place(level, s.offset(-4, 0, 0), ModBlocks.COMPUTER_CRAFTER);
		place(level, s.offset(-5, 0, 0), ModBlocks.STORAGE_INTERFACE);
		pipe(level, s.offset(-6, 0, 0), ModBlocks.ITEM_PIPE);
		level.setBlockAndUpdate(s.offset(-7, 0, 0), Blocks.CHEST.defaultBlockState());
		pipe(level, s.offset(-6, 0, 0), ModBlocks.ITEM_PIPE);
	}

	/** The HUD has no setter for F1's hidden flag, so set the field (dev names, test only). */
	private static void setHudHidden(Minecraft client, boolean hidden) {
		try {
			java.lang.reflect.Field field = client.gui.hud.getClass().getDeclaredField("isHidden");
			field.setAccessible(true);
			field.setBoolean(client.gui.hud, hidden);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Can't hide the HUD for the guide pictures", e);
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
		if (state.hasProperty(MachineBlock.FACING)) state = state.setValue(MachineBlock.FACING, Direction.NORTH);
		level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(state, level, pos));
	}

	private static void pipe(ServerLevel level, BlockPos pos, Block pipe) {
		level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(pipe.defaultBlockState(), level, pos));
	}

	private static void put(ServerLevel level, BlockPos pos, int slot, Item item, int count) {
		((Container) level.getBlockEntity(pos)).setItem(slot, new ItemStack(item, count));
	}

	private static void fillBattery(ServerLevel level, BlockPos pos) {
		BatteryBlockEntity battery = (BatteryBlockEntity) level.getBlockEntity(pos);
		while (battery.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
		}
	}
}
