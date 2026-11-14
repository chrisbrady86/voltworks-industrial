package dev.voltworks.block;

import dev.voltworks.Voltworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Chunk Loader: keeps the 3x3 chunks around it loaded and running while no player is nearby, like vanilla
 * {@code /forceload}. A redstone signal switches it off.
 *
 * <p>It holds a chunk ticket of its own type, saved with the world, so the area stays loaded across restarts
 * without the block having to run any code. Loaders in different chunks each hold their own ticket, loaders in the
 * same chunk share one (returned when the last of them goes), and admin
 * {@code /forceload} areas are separate, so removing one never unloads another's chunks.
 */
public class ChunkLoaderBlock extends Block {
	public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
	/** Ticket radius 2 = the same strength as /forceload: its own chunk fully active, the 8 around it running machines. */
	public static final int RADIUS = 2;
	/** Persistent, loads and simulates chunks, keeps the dimension active, never times out (like the FORCED ticket). */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Voltworks.id("chunk_loader"),
			new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION
					| TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	public ChunkLoaderBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(ACTIVE, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	/** Takes a ticket when an active loader appears (placed, or switched on). */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		boolean wasActive = oldState.is(this) && oldState.getValue(ACTIVE);
		if (level instanceof ServerLevel server && state.getValue(ACTIVE) && !wasActive) setTicket(server, pos, true);
	}

	/** Returns the ticket when an active loader is removed (broken, exploded, picked up with the wrench). */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (state.getValue(ACTIVE) && !level.getBlockState(pos).is(this)) setTicket(level, pos, false);
	}

	/** Redstone switches it off; the ticket is returned when it turns off and taken again when it turns back on. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
		if (!(level instanceof ServerLevel server)) return;
		boolean active = !level.hasNeighborSignal(pos);
		if (active == state.getValue(ACTIVE)) return;
		level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_CLIENTS);
		if (!active) setTicket(server, pos, false); // switching on is handled by onPlace
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			ChunkPos c = ChunkPos.containing(pos);
			player.sendOverlayMessage(state.getValue(ACTIVE)
					? Component.translatable("message.voltworks.chunk_loader.on", c.x() - 1, c.z() - 1, c.x() + 1, c.z() + 1)
					: Component.translatable("message.voltworks.chunk_loader.off"));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Takes or returns the ticket. Minecraft keeps only one ticket of a kind per chunk, so loaders standing in the same
	 * chunk share it: it is only returned when no other active loader is left in that chunk.
	 */
	private static void setTicket(ServerLevel level, BlockPos pos, boolean add) {
		ChunkPos chunk = ChunkPos.containing(pos);
		if (add) {
			level.getChunkSource().addTicketWithRadius(TICKET, chunk, RADIUS);
		} else if (!hasOtherActiveLoader(level, pos)) {
			level.getChunkSource().removeTicketWithRadius(TICKET, chunk, RADIUS);
		}
	}

	/** Another switched-on Chunk Loader in the chunk of {@code pos} (not the one at {@code pos} itself). */
	private static boolean hasOtherActiveLoader(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof ChunkLoaderBlock && s.getValue(ACTIVE))) continue;
			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
				BlockState s = section.getBlockState(x, y, z);
				if (!(s.getBlock() instanceof ChunkLoaderBlock) || !s.getValue(ACTIVE)) continue;
				if ((pos.getX() & 15) != x || pos.getY() != baseY + y || (pos.getZ() & 15) != z) return true;
			}
		}
		return false;
	}


	/** Loads the class so the ticket type is registered during mod init. */
	public static void init() {
	}
}
