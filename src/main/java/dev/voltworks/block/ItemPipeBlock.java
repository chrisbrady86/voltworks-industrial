package dev.voltworks.block;

import dev.voltworks.Voltworks;
import dev.voltworks.block.entity.ItemPipeBlockEntity;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.block.entity.SideConfig;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
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
	 * Speed tier. {@code batch}: items moved per {@link ItemPipeBlockEntity#INTERVAL} ticks; a route goes at its slowest pipe's rate.
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

	/** What you bump into: the glass tube itself. */
	private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
	private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(6, 0, 5));
	/** What you click: a bit fatter than the tube, plus a stub where an arm was cut off, so it can be clicked to reconnect. */
	private static final VoxelShape HIT_CORE = Block.box(4, 4, 4, 12, 12, 12);
	private static final Map<Direction, VoxelShape> HIT_ARMS = Shapes.rotateAll(Block.boxZ(8, 0, 4));
	private static final Map<Direction, VoxelShape> HIT_STUBS = Shapes.rotateAll(Block.boxZ(8, 2, 4));
	/** Longest line {@link #lineTouchesInventory} follows. */
	private static final int MAX_SCAN = 512;
	private final Map<BlockState, VoxelShape> shapes = new HashMap<>();
	private final Map<BlockState, VoxelShape> hitShapes = new HashMap<>();

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

	/**
	 * How a new connection starts:
	 * <ul>
	 *   <li>a machine side that only outputs: EXTRACT, so outputs flow automatically;</li>
	 *   <li>plain storage (a chest, barrel...) that is the first inventory on its line: EXTRACT, so laying pipes away
	 *       from a chest just works. Once the line reaches another inventory, later chests are destinations;</li>
	 *   <li>anything else: CONNECTED (items get delivered into it).</li>
	 * </ul>
	 * Sneak + Wrench on the arm switches Extract on or off afterwards.
	 */
	private static Side defaultFor(BlockGetter level, BlockPos pipePos, Direction dir) {
		BlockPos neighbourPos = pipePos.relative(dir);
		BlockState neighbourState = level.getBlockState(neighbourPos);
		if (!canConnect(level, neighbourPos, neighbourState)) return Side.NONE;
		if (level.getBlockEntity(neighbourPos) instanceof PoweredContainerBlockEntity machine) {
			SideConfig.Mode mode = machine.getSideMode(dir.getOpposite());
			if (mode == SideConfig.Mode.OUTPUT) return Side.EXTRACT;
		}
		if (isPlainStorage(level, neighbourPos, neighbourState) && !lineTouchesInventory(level, pipePos, dir)) return Side.EXTRACT;
		return Side.CONNECTED;
	}

	/**
	 * Storage a new pipe may start pulling from: chests, barrels, hoppers, shulker boxes (other mods' too). Not Voltworks
	 * blocks (machines handle their own outputs; a Storage Interface would empty the whole network) and not blocks with
	 * per-side slots like furnaces.
	 */
	public static boolean isPlainStorage(BlockGetter level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof ItemPipeBlock) return false;
		if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().startsWith(Voltworks.MOD_ID)) return false;
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof Container)) return false;
		return !(be instanceof WorldlyContainer) || be instanceof ShulkerBoxBlockEntity;
	}

	/**
	 * Whether the pipe line at {@code pipePos} already reaches an inventory, not counting the side {@code except}.
	 * The pipe itself may not be placed yet: its neighbours are read from the world.
	 */
	static boolean lineTouchesInventory(BlockGetter level, BlockPos pipePos, Direction except) {
		BlockState self = level.getBlockState(pipePos);
		boolean placed = self.getBlock() instanceof ItemPipeBlock;
		Set<BlockPos> seen = new HashSet<>();
		seen.add(pipePos);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (Direction d : Direction.values()) {
			if (d == except || (placed && get(self, d) == Side.DISABLED)) continue;
			BlockPos n = pipePos.relative(d);
			BlockState ns = level.getBlockState(n);
			if (ns.getBlock() instanceof ItemPipeBlock) {
				if (get(ns, d.getOpposite()) != Side.DISABLED && seen.add(n)) queue.add(n);
			} else if (canConnect(level, n, ns)) {
				return true;
			}
		}
		while (!queue.isEmpty() && seen.size() < MAX_SCAN) {
			BlockPos p = queue.poll();
			BlockState ps = level.getBlockState(p);
			for (Direction d : Direction.values()) {
				if (!get(ps, d).isOpen()) continue;
				BlockPos n = p.relative(d);
				if (n.equals(pipePos)) continue;
				BlockState ns = level.getBlockState(n);
				if (ns.getBlock() instanceof ItemPipeBlock) {
					if (get(ns, d.getOpposite()).isOpen() && seen.add(n)) queue.add(n);
				} else if (canConnect(level, n, ns)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction d : Direction.values()) {
			BlockState ns = level.getBlockState(pos.relative(d));
			Side side = defaultFor(level, pos, d);
			// Don't connect to a pipe that has deliberately cut itself off from this side
			if (ns.getBlock() instanceof ItemPipeBlock && get(ns, d.getOpposite()) == Side.DISABLED) side = Side.DISABLED;
			state = state.setValue(SIDES.get(d), side);
		}
		return state;
	}

	/** Tell the player when a new pipe started pulling from a chest by itself, and how to undo it. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (level.isClientSide() || !(by instanceof Player player)) return;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			BlockState ns = level.getBlockState(n);
			if (get(state, d) == Side.EXTRACT && isPlainStorage(level, n, ns)) {
				player.sendOverlayMessage(Component.translatable("message.voltworks.pipe.auto_extract", ns.getBlock().getName()));
				return;
			}
		}
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
			next = defaultFor(level, pos, direction);
		} else {
			next = current; // keep CONNECTED / EXTRACT / DISABLED as configured
		}
		return state.setValue(SIDES.get(direction), next);
	}

	/** The click target: fatter than the tube, with stubs on cut-off sides. */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return hitShapes.computeIfAbsent(state, s -> {
			VoxelShape shape = HIT_CORE;
			for (Direction d : Direction.values()) {
				Side side = get(s, d);
				if (side.isOpen()) shape = Shapes.or(shape, HIT_ARMS.get(d));
				else if (side == Side.DISABLED) shape = Shapes.or(shape, HIT_STUBS.get(d));
			}
			return shape;
		});
	}

	/** What entities bump into: just the tube. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
