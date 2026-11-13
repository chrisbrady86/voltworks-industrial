package dev.voltworks.block.entity;

import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.ItemPipeBlock.Side;
import dev.voltworks.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moves items for a pipe's EXTRACT sides. Every {@value #INTERVAL} ticks each extracting side pulls up to
 * {@value #BATCH} items from the inventory it faces and delivers them to the nearest inventories on the
 * same pipe network that accept them (via their CONNECTED sides), taking turns between destinations.
 * Transfers are instant; inventories' own side rules (machine side config, hopper faces) are respected.
 */
public class ItemPipeBlockEntity extends BlockEntity {
	public static final int INTERVAL = 10;
	public static final int BATCH = 8;
	private static final int MAX_PIPES = 512;

	private int timer;
	private int roundRobin;

	public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ITEM_PIPE, pos, state);
	}

	/** An inventory reachable from the network, and the face of it that the pipe touches. */
	private record Target(BlockPos pos, Direction face) {
	}

	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (++timer < INTERVAL) return;
		timer = 0;
		List<Target> targets = null;
		for (Direction dir : Direction.values()) {
			if (ItemPipeBlock.get(state, dir) != Side.EXTRACT) continue;
			Container source = HopperBlockEntity.getContainerAt(level, pos.relative(dir));
			if (source == null) continue;
			if (targets == null) targets = findTargets(level, pos);
			BlockPos sourcePos = pos.relative(dir);
			List<Target> others = targets.stream().filter(t -> !t.pos().equals(sourcePos)).toList();
			if (!others.isEmpty()) {
				extract(level, source, dir.getOpposite(), others);
			}
		}
	}

	/** Breadth-first search through connected pipes, collecting every inventory on a CONNECTED side. */
	private List<Target> findTargets(ServerLevel level, BlockPos start) {
		List<Target> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && seen.size() < MAX_PIPES) {
			BlockPos p = queue.poll();
			BlockState ps = level.getBlockState(p);
			if (!(ps.getBlock() instanceof ItemPipeBlock)) continue;
			for (Direction d : Direction.values()) {
				Side side = ItemPipeBlock.get(ps, d);
				if (!side.isOpen()) continue;
				BlockPos n = p.relative(d);
				BlockState ns = level.getBlockState(n);
				if (ns.getBlock() instanceof ItemPipeBlock) {
					if (ItemPipeBlock.get(ns, d.getOpposite()).isOpen() && seen.add(n)) queue.add(n);
				} else if (side == Side.CONNECTED) {
					found.add(new Target(n, d.getOpposite()));
				}
			}
		}
		return found;
	}

	/** Pull one batch from the first slot that has something a destination will accept. */
	private void extract(ServerLevel level, Container source, Direction face, List<Target> targets) {
		int[] slots = source instanceof WorldlyContainer w ? w.getSlotsForFace(face) : allSlots(source);
		for (int slot : slots) {
			ItemStack stack = source.getItem(slot);
			if (stack.isEmpty()) continue;
			if (source instanceof WorldlyContainer w && !w.canTakeItemThroughFace(slot, stack, face)) continue;

			ItemStack moving = stack.copyWithCount(Math.min(BATCH, stack.getCount()));
			int start = roundRobin++ % targets.size();
			for (int i = 0; i < targets.size() && !moving.isEmpty(); i++) {
				Target t = targets.get((start + i) % targets.size());
				Container dest = HopperBlockEntity.getContainerAt(level, t.pos());
				if (dest != null && dest != source) {
					moving = HopperBlockEntity.addItem(source, dest, moving, t.face());
				}
			}
			int moved = Math.min(BATCH, stack.getCount()) - moving.getCount();
			if (moved > 0) {
				stack.shrink(moved);
				source.setChanged();
				return;
			}
		}
	}

	private static int[] allSlots(Container c) {
		int[] s = new int[c.getContainerSize()];
		for (int i = 0; i < s.length; i++) s[i] = i;
		return s;
	}
}
