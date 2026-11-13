package dev.voltworks.test;

import dev.voltworks.block.ChunkLoaderBlock;
import dev.voltworks.block.MachineBlock;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chunk Loader: two identical Generator + Crusher setups about 50 chunks from the player, far outside view distance.
 * A has an active Chunk Loader; B has one switched off by a redstone block. After the main run only A should have
 * crushed anything. Then A's loader is broken and its chunk must stop ticking.
 */
final class ChunkLoaderTest {
	private ChunkLoaderTest() {
	}

	/** Setup A; B is 3 chunks further east. Both in the middle of a chunk (x/z = 8). */
	static BlockPos loadedPos(BlockPos o) {
		ChunkPos c = ChunkPos.containing(o.offset(800, 0, 0));
		return new BlockPos(c.getMinBlockX() + 8, o.getY(), c.getMinBlockZ() + 8);
	}

	static BlockPos unloadedPos(BlockPos o) {
		return loadedPos(o).offset(48, 0, 0);
	}

	static void build(ServerLevel level, BlockPos o) {
		buildSetup(level, loadedPos(o));
		buildSetup(level, unloadedPos(o));
		level.setBlockAndUpdate(unloadedPos(o).offset(2, 0, 1), Blocks.REDSTONE_BLOCK.defaultBlockState()); // switches B's loader off
	}

	/** Generator (coal) - cable - Crusher (raw iron) on a stone floor, Chunk Loader behind the generator. */
	private static void buildSetup(ServerLevel level, BlockPos p) {
		for (int x = -1; x <= 3; x++) for (int z = -1; z <= 2; z++) level.setBlockAndUpdate(p.offset(x, -1, z), Blocks.STONE.defaultBlockState());
		machine(level, p, ModBlocks.GENERATOR);
		level.setBlockAndUpdate(p.offset(1, 0, 0), ModBlocks.COPPER_CABLE.defaultBlockState());
		machine(level, p.offset(2, 0, 0), ModBlocks.CRUSHER);
		((Container) level.getBlockEntity(p)).setItem(0, new ItemStack(Items.COAL, 16));
		((Container) level.getBlockEntity(p.offset(2, 0, 0))).setItem(0, new ItemStack(Items.RAW_IRON, 16));
		level.setBlockAndUpdate(p.offset(1, 0, 1), ModBlocks.CHUNK_LOADER.defaultBlockState());
	}

	private static void machine(ServerLevel level, BlockPos pos, Block block) {
		level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
	}

	private static int dust(ServerLevel level, BlockPos crusher) {
		Container c = (Container) level.getBlockEntity(crusher);
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(ModItems.IRON_DUST)) n += c.getItem(i).getCount();
		return n;
	}

	private static boolean ticking(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().isPositionTicking(ChunkPos.containing(pos).pack());
	}

	/** After the main run: A kept working far from the player, B (loader switched off) did not. Then breaks A's loader. */
	static List<String> checkLoaded(ServerLevel level, BlockPos o) {
		List<String> fails = new ArrayList<>();
		BlockPos a = loadedPos(o), b = unloadedPos(o);
		boolean aTicking = ticking(level, a), bTicking = ticking(level, b);
		// Reading B's crusher loads its chunk for a moment, so check ticking first, then the items.
		int dustA = dust(level, a.offset(2, 0, 0)), dustB = dust(level, b.offset(2, 0, 0));
		BlockState loaderB = level.getBlockState(b.offset(1, 0, 1));
		System.out.println("[voltworks-test] Chunk Loader " + Math.round(Math.sqrt(a.distSqr(o))) + " blocks away: ticking=" + aTicking
				+ ", crushed " + dustA + " iron dust | switched off: ticking=" + bTicking + ", crushed " + dustB
				+ ", loader active=" + loaderB.getValue(ChunkLoaderBlock.ACTIVE));
		if (!aTicking) fails.add("A Chunk Loader's chunk should keep ticking with no player nearby");
		if (dustA < 4) fails.add("Machines next to a Chunk Loader should keep working far from the player (" + dustA + " dust)");
		if (loaderB.getValue(ChunkLoaderBlock.ACTIVE)) fails.add("A redstone signal should switch the Chunk Loader off");
		if (bTicking || dustB > 0) fails.add("Without an active Chunk Loader the far-away chunk should not run (ticking=" + bTicking + ", " + dustB + " dust)");
		level.removeBlock(a.offset(1, 0, 1), false); // break A's loader: its ticket must go
		return fails;
	}

	/** Some time after breaking A's loader. */
	static List<String> checkReleased(ServerLevel level, BlockPos o) {
		boolean ticking = ticking(level, loadedPos(o));
		System.out.println("[voltworks-test] Chunk Loader broken: chunk still ticking=" + ticking);
		// Put it back for the restart check
		level.setBlockAndUpdate(loadedPos(o).offset(1, 0, 1), ModBlocks.CHUNK_LOADER.defaultBlockState());
		return ticking ? List.of("Breaking a Chunk Loader should release its chunks") : List.of();
	}

	static int loadedDust(ServerLevel level, BlockPos o) {
		return dust(level, loadedPos(o).offset(2, 0, 0));
	}

	/** After saving, closing and reopening the world: A's chunk is ticking again and its crusher kept working. */
	static List<String> checkAfterRestart(ServerLevel level, BlockPos o, int dustBefore) {
		List<String> fails = new ArrayList<>();
		boolean ticking = ticking(level, loadedPos(o));
		int dust = loadedDust(level, o);
		System.out.println("[voltworks-test] Chunk Loader after a restart: ticking=" + ticking + ", iron dust " + dustBefore + " -> " + dust);
		if (!ticking) fails.add("A Chunk Loader should keep its chunks loaded after the world is reopened");
		if (dust <= dustBefore) fails.add("Machines by a Chunk Loader should keep working after a restart (" + dustBefore + " -> " + dust + " dust)");
		return fails;
	}
}
