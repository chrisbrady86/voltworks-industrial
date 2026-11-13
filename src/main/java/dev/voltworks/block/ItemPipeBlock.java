package dev.voltworks.block;

import dev.voltworks.block.entity.ItemPipeBlockEntity;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.block.entity.SideConfig;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Item pipe. Each side is NONE (nothing there), CONNECTED (to a pipe, or delivering into an inventory),
 * EXTRACT (pulling items out of the inventory on that side) or DISABLED (cut off with the wrench).
 */
public class ItemPipeBlock extends BaseEntityBlock {
	public enum Side implements StringRepresentable {
		NONE, CONNECTED, EXTRACT, DISABLED;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public boolean isOpen() {
			return this == CONNECTED || this == EXTRACT;
		}
	}

	/**
	 * Speed tier. {@code batch}: items an extracting side pulls every {@link ItemPipeBlockEntity#INTERVAL} ticks.
	 * {@code ticksPerBlock}: how long an item takes to cross one pipe of this tier.
	 */
	public enum Tier {
		BASIC(2, 6), FAST(8, 3), EXPRESS(32, 1);

		public final int batch, ticksPerBlock;

		Tier(int batch, int ticksPerBlock) {
			this.batch = batch;
			this.ticksPerBlock = ticksPerBlock;
		}
	}

	public static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Direction.class);

	static {
		for (Direction d : Direction.values()) {
			SIDES.put(d, EnumProperty.create(d.getSerializedName(), Side.class));
		}
	}

	private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
	private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(6, 0, 5));
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

	public final Tier tier;

	public ItemPipeBlock(Properties properties) {
		this(properties, Tier.BASIC);
	}

	public ItemPipeBlock(Properties properties, Tier tier) {
		super(properties);
		this.tier = tier;
		BlockState state = this.stateDefinition.any();
		for (EnumProperty<Side> p : SIDES.values()) state = state.setValue(p, Side.NONE);
		this.registerDefaultState(state);
	}

	public static Side get(BlockState state, Direction dir) {
		return state.getValue(SIDES.get(dir));
	}

	/** Pipes connect to other pipes and to anything with an inventory (chests, hoppers, machines...). */
	public static boolean canConnect(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof ItemPipeBlock) return true;
		return state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container;
	}

	/** New connections to a machine side that only outputs default to EXTRACT, so outputs flow automatically. */
	private static Side defaultFor(BlockGetter level, BlockPos neighbourPos, BlockState neighbourState, Direction dirToNeighbour) {
		if (!canConnect(level, neighbourPos, neighbourState)) return Side.NONE;
		if (level.getBlockEntity(neighbourPos) instanceof PoweredContainerBlockEntity machine) {
			SideConfig.Mode mode = machine.getSideMode(dirToNeighbour.getOpposite());
			if (mode == SideConfig.Mode.OUTPUT) return Side.EXTRACT;
		}
		return Side.CONNECTED;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			BlockState ns = level.getBlockState(n);
			Side side = defaultFor(level, n, ns, d);
			// Don't connect to a pipe that has deliberately cut itself off from this side
			if (ns.getBlock() instanceof ItemPipeBlock && get(ns, d.getOpposite()) == Side.DISABLED) side = Side.DISABLED;
			state = state.setValue(SIDES.get(d), side);
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		Side current = get(state, direction);
		boolean connectable = canConnect(level, neighbourPos, neighbourState);
		Side next;
		if (!connectable) {
			next = Side.NONE;
		} else if (current == Side.NONE) {
			next = defaultFor(level, neighbourPos, neighbourState, direction);
		} else {
			next = current; // keep CONNECTED / EXTRACT / DISABLED as configured
		}
		return state.setValue(SIDES.get(direction), next);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.computeIfAbsent(state, s -> {
			VoxelShape shape = CORE;
			for (Direction d : Direction.values()) {
				if (get(s, d).isOpen()) shape = Shapes.or(shape, ARMS.get(d));
			}
			return shape;
		});
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ItemPipeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide()) return null;
		return (lvl, pos, st, be) -> {
			if (be instanceof ItemPipeBlockEntity pipe) pipe.serverTick((ServerLevel) lvl, pos, st);
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		for (Direction d : Direction.values()) builder.add(SIDES.get(d));
	}
}
