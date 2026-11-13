package dev.voltworks.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.ItemPipeBlock.Side;
import dev.voltworks.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Moves items through a pipe network. Items really travel: every {@value #INTERVAL} ticks an extracting side pulls a
 * batch (set by this pipe's {@link ItemPipeBlock.Tier}) out of the inventory it faces and sends it along the shortest
 * route to an inventory that has room for it. The batch is carried in this pipe's {@link Transit} list until it
 * arrives (each pipe on the way adds its tier's travel time), then it is inserted. If the destination filled up in the
 * meantime the items go to another inventory on the network, else back to where they came from, else drop.
 * Transits are saved and synced to clients, which draw the items gliding through the glass.
 * <p>
 * Which sides pull:
 * <ul>
 *   <li>EXTRACT (set with the wrench, or automatic on a machine's Output side): delivers to any inventory.</li>
 *   <li>CONNECTED touching a Voltworks machine: pulls the machine's finished items (only what it lets out:
 *       products, empty buckets, charged tools, spent rods) but delivers them to non-machine inventories only,
 *       so outputs can't loop between machines (furnace ingots back into a crusher).</li>
 * </ul>
 * Filter pipes on a route only let matching items through ({@link FilterPipeBlockEntity}).
 */
public class ItemPipeBlockEntity extends BlockEntity {
	public static final int INTERVAL = 10;
	private static final int MAX_PIPES = 512;
	/** Most batches one pipe can have in flight; it waits when this many are travelling. */
	private static final int MAX_TRANSITS = 64;

	/**
	 * A batch in flight. {@code path} runs from this pipe to the pipe touching the destination; the items enter the
	 * first pipe from {@code from} (towards the source) and leave the last one through {@code to}. They spend
	 * {@code durations[i]} ticks crossing {@code path[i]}, starting at game time {@code depart}.
	 */
	public record Transit(ItemStack stack, List<BlockPos> path, Direction from, Direction to, long depart, List<Integer> durations) {
		public static final Codec<Transit> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("stack").forGetter(Transit::stack),
				BlockPos.CODEC.listOf().fieldOf("path").forGetter(Transit::path),
				Direction.CODEC.fieldOf("from").forGetter(Transit::from),
				Direction.CODEC.fieldOf("to").forGetter(Transit::to),
				Codec.LONG.fieldOf("depart").forGetter(Transit::depart),
				Codec.INT.listOf().fieldOf("durations").forGetter(Transit::durations)
		).apply(i, Transit::new));

		public long arrival() {
			long t = depart;
			for (int d : durations) t += d;
			return t;
		}

		public BlockPos destination() {
			return path.get(path.size() - 1).relative(to);
		}

		/** Where the items are (block coordinates, centre of a pipe = pos + 0.5) at game time {@code time}. */
		public double[] positionAt(double time) {
			double t = time - depart;
			for (int i = 0; i < path.size(); i++) {
				int d = durations.get(i);
				if (t <= d || i == path.size() - 1) {
					double f = d <= 0 ? 1 : Math.max(0, Math.min(1, t / d));
					BlockPos p = path.get(i);
					Direction in = i == 0 ? from : dirBetween(p, path.get(i - 1));
					Direction out = i == path.size() - 1 ? to : dirBetween(p, path.get(i + 1));
					// Entry face -> centre -> exit face
					Direction leg = f < 0.5 ? in : out;
					double k = f < 0.5 ? (0.5 - f) : (f - 0.5); // 0.5 at a face, 0 at the centre
					return new double[]{p.getX() + 0.5 + leg.getStepX() * k, p.getY() + 0.5 + leg.getStepY() * k, p.getZ() + 0.5 + leg.getStepZ() * k};
				}
				t -= d;
			}
			BlockPos p = path.get(0);
			return new double[]{p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5};
		}

		private static Direction dirBetween(BlockPos from, BlockPos to) {
			return Direction.getNearest(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ(), Direction.UP);
		}
	}

	/** An inventory reachable from this pipe, the face of it that the network touches, and the pipes leading there. */
	private record Route(BlockPos pos, Direction face, List<BlockPos> path) {
	}

	private final List<Transit> transits = new ArrayList<>();
	private int timer;
	private int roundRobin;

	public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlockEntities.ITEM_PIPE, pos, state);
	}

	protected ItemPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public List<Transit> getTransits() {
		return transits;
	}

	public ItemPipeBlock.Tier tier() {
		return getBlockState().getBlock() instanceof ItemPipeBlock pipe ? pipe.tier : ItemPipeBlock.Tier.BASIC;
	}

	/** Whether this pipe lets {@code stack} through. Plain pipes pass everything; filter pipes override this. */
	public boolean allows(ItemStack stack) {
		return true;
	}

	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		boolean changed = deliverArrivals(level, pos);
		if (++timer >= INTERVAL) {
			timer = 0;
			changed |= pull(level, pos, state);
		}
		if (changed) {
			setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	// ---------------- pulling ----------------

	private boolean pull(ServerLevel level, BlockPos pos, BlockState state) {
		boolean changed = false;
		for (Direction dir : Direction.values()) {
			if (transits.size() >= MAX_TRANSITS) break;
			Side side = ItemPipeBlock.get(state, dir);
			BlockPos sourcePos = pos.relative(dir);
			boolean autoPull = side == Side.CONNECTED && level.getBlockEntity(sourcePos) instanceof PoweredContainerBlockEntity;
			if (side != Side.EXTRACT && !autoPull) continue;
			Container source = HopperBlockEntity.getContainerAt(level, sourcePos);
			if (source != null) {
				changed |= extract(level, pos, source, sourcePos, dir, autoPull);
			}
		}
		return changed;
	}

	/** Send one batch from the first slot whose item has somewhere to go. */
	private boolean extract(ServerLevel level, BlockPos pos, Container source, BlockPos sourcePos, Direction dir, boolean autoPull) {
		Direction face = dir.getOpposite();
		int[] slots = source instanceof WorldlyContainer w ? w.getSlotsForFace(face) : allSlots(source);
		for (int slot : slots) {
			ItemStack stack = source.getItem(slot);
			if (stack.isEmpty() || !allows(stack)) continue;
			if (source instanceof WorldlyContainer w && !w.canTakeItemThroughFace(slot, stack, face)) continue;

			List<Route> routes = findRoutes(level, pos, stack).stream()
					.filter(r -> !r.pos().equals(sourcePos))
					.filter(r -> !autoPull || !(level.getBlockEntity(r.pos()) instanceof PoweredContainerBlockEntity))
					.toList();
			if (routes.isEmpty()) continue;
			int start = Math.floorMod(roundRobin++, routes.size());
			for (int i = 0; i < routes.size(); i++) {
				Route route = routes.get((start + i) % routes.size());
				Container dest = HopperBlockEntity.getContainerAt(level, route.pos());
				if (dest == null || dest == source) continue;
				int room = room(dest, route.face(), stack) - inFlightTo(route.pos(), stack);
				int amount = Math.min(Math.min(tier().batch, stack.getCount()), room);
				if (amount <= 0) continue;
				ItemStack moving = source.removeItem(slot, amount);
				if (moving.isEmpty()) return false;
				source.setChanged();
				transits.add(newTransit(level, moving, route, dir));
				return true;
			}
		}
		return false;
	}

	private Transit newTransit(Level level, ItemStack stack, Route route, Direction sourceDir) {
		List<Integer> durations = new ArrayList<>();
		for (BlockPos p : route.path()) {
			durations.add(level.getBlockState(p).getBlock() instanceof ItemPipeBlock pipe ? pipe.tier.ticksPerBlock : ItemPipeBlock.Tier.BASIC.ticksPerBlock);
		}
		BlockPos last = route.path().get(route.path().size() - 1);
		Direction to = Direction.getNearest(route.pos().getX() - last.getX(), route.pos().getY() - last.getY(), route.pos().getZ() - last.getZ(), Direction.UP);
		return new Transit(stack, List.copyOf(route.path()), sourceDir, to, level.getGameTime(), durations);
	}

	/** Items of this kind already travelling to {@code dest}, so we don't send more than it can hold. */
	private int inFlightTo(BlockPos dest, ItemStack stack) {
		int n = 0;
		for (Transit t : transits) {
			if (t.destination().equals(dest) && ItemStack.isSameItemSameComponents(t.stack(), stack)) n += t.stack().getCount();
		}
		return n;
	}

	/**
	 * Breadth-first search through connected pipes, nearest first. Filter pipes that don't allow {@code stack} are
	 * dead ends, so an inventory behind one only gets matching items. Each route's path starts with this pipe.
	 */
	private List<Route> findRoutes(Level level, BlockPos start, ItemStack stack) {
		List<Route> found = new ArrayList<>();
		Map<BlockPos, BlockPos> parent = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		parent.put(start, start);
		while (!queue.isEmpty() && parent.size() < MAX_PIPES) {
			BlockPos p = queue.poll();
			BlockState ps = level.getBlockState(p);
			if (!(ps.getBlock() instanceof ItemPipeBlock)) continue;
			for (Direction d : Direction.values()) {
				Side side = ItemPipeBlock.get(ps, d);
				if (!side.isOpen()) continue;
				BlockPos n = p.relative(d);
				BlockState ns = level.getBlockState(n);
				if (ns.getBlock() instanceof ItemPipeBlock) {
					if (!ItemPipeBlock.get(ns, d.getOpposite()).isOpen() || parent.containsKey(n)) continue;
					if (level.getBlockEntity(n) instanceof ItemPipeBlockEntity next && !next.allows(stack)) continue;
					parent.put(n, p);
					queue.add(n);
				} else if (side == Side.CONNECTED) {
					found.add(new Route(n, d.getOpposite(), pathTo(p, parent)));
				}
			}
		}
		return found;
	}

	private static List<BlockPos> pathTo(BlockPos end, Map<BlockPos, BlockPos> parent) {
		ArrayList<BlockPos> path = new ArrayList<>();
		for (BlockPos p = end; ; p = parent.get(p)) {
			path.add(p);
			if (parent.get(p).equals(p)) break;
		}
		java.util.Collections.reverse(path);
		return path;
	}

	// ---------------- delivering ----------------

	private boolean deliverArrivals(ServerLevel level, BlockPos pos) {
		if (transits.isEmpty()) return false;
		boolean changed = false;
		long now = level.getGameTime();
		for (Iterator<Transit> it = transits.iterator(); it.hasNext(); ) {
			Transit t = it.next();
			if (t.arrival() > now) continue;
			it.remove();
			changed = true;
			BlockPos broken = firstMissingPipe(level, t.path());
			if (broken != null) {
				Containers.dropItemStack(level, broken.getX() + 0.5, broken.getY() + 0.5, broken.getZ() + 0.5, t.stack());
				continue;
			}
			ItemStack rest = insert(level, t.destination(), t.to().getOpposite(), t.stack());
			if (!rest.isEmpty()) rest = reroute(level, pos, rest, t.destination());
			if (!rest.isEmpty()) rest = insert(level, pos.relative(t.from()), t.from().getOpposite(), rest); // back to the source
			if (!rest.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rest);
		}
		return changed;
	}

	/** Destination full on arrival: hand the items straight to any other inventory on the network with room. */
	private ItemStack reroute(Level level, BlockPos pos, ItemStack stack, BlockPos skip) {
		for (Route r : findRoutes(level, pos, stack)) {
			if (r.pos().equals(skip)) continue;
			stack = insert(level, r.pos(), r.face(), stack);
			if (stack.isEmpty()) break;
		}
		return stack;
	}

	private static ItemStack insert(Level level, BlockPos pos, Direction face, ItemStack stack) {
		Container dest = HopperBlockEntity.getContainerAt(level, pos);
		return dest == null ? stack : HopperBlockEntity.addItem(null, dest, stack, face);
	}

	private static BlockPos firstMissingPipe(Level level, List<BlockPos> path) {
		for (BlockPos p : path) {
			if (!(level.getBlockState(p).getBlock() instanceof ItemPipeBlock)) return p;
		}
		return null;
	}

	/** How many of {@code stack} the container could take through {@code face}, without changing it. */
	static int room(Container c, Direction face, ItemStack stack) {
		int[] slots = c instanceof WorldlyContainer w ? w.getSlotsForFace(face) : allSlots(c);
		int room = 0;
		for (int slot : slots) {
			if (!c.canPlaceItem(slot, stack)) continue;
			if (c instanceof WorldlyContainer w && !w.canPlaceItemThroughFace(slot, stack, face)) continue;
			ItemStack in = c.getItem(slot);
			int max = Math.min(c.getMaxStackSize(stack), stack.getMaxStackSize());
			if (in.isEmpty()) room += max;
			else if (ItemStack.isSameItemSameComponents(in, stack)) room += Math.max(0, max - in.getCount());
		}
		return room;
	}

	private static int[] allSlots(Container c) {
		int[] s = new int[c.getContainerSize()];
		for (int i = 0; i < s.length; i++) s[i] = i;
		return s;
	}

	/** Breaking the pipe drops whatever it still has in flight. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			for (Transit t : transits) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, t.stack());
			transits.clear();
		}
	}

	// ---------------- saving & client sync ----------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.TypedOutputList<Transit> list = output.list("transits", Transit.CODEC);
		for (Transit t : transits) list.add(t);
		output.putInt("timer", timer);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		transits.clear();
		input.listOrEmpty("transits", Transit.CODEC).forEach(transits::add);
		timer = input.getIntOr("timer", 0);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
