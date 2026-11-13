package dev.voltworks.energy;

import dev.voltworks.block.CableBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Finds every energy receiver reachable from a source, either directly adjacent
 * or through a chain of cables, and splits energy between them.
 */
public final class EnergyNetwork {
	private static final int MAX_CABLES = 1024;

	private EnergyNetwork() {
	}

	public static List<EnergyReceiver> findReceivers(Level level, BlockPos origin, BlockEntity self) {
		List<EnergyReceiver> machines = new ArrayList<>();
		List<EnergyReceiver> storage = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);
		int cables = 0;

		while (!queue.isEmpty() && cables < MAX_CABLES) {
			BlockPos current = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos next = current.relative(dir);
				if (!visited.add(next) || !level.isLoaded(next)) {
					continue;
				}
				if (level.getBlockState(next).getBlock() instanceof CableBlock) {
					queue.add(next);
					cables++;
				} else if (level.getBlockEntity(next) instanceof EnergyReceiver receiver && receiver != self) {
					(receiver.isStorage() ? storage : machines).add(receiver);
				}
			}
		}

		machines.addAll(storage); // machines get served first
		return machines;
	}

	/**
	 * Hands out up to {@code amount} energy, machines first, splitting evenly within each pass.
	 * @return the amount that was accepted.
	 */
	public static int distribute(List<EnergyReceiver> receivers, int amount, boolean includeStorage) {
		int remaining = amount;
		List<EnergyReceiver> targets = new ArrayList<>();
		for (EnergyReceiver r : receivers) {
			if ((includeStorage || !r.isStorage()) && !((BlockEntity) r).isRemoved()) {
				targets.add(r);
			}
		}
		// Two passes: an even split, then let anyone with room soak up the rest.
		for (int pass = 0; pass < 2 && remaining > 0; pass++) {
			for (int i = 0; i < targets.size() && remaining > 0; i++) {
				int share = pass == 0 ? Math.max(1, remaining / (targets.size() - i)) : remaining;
				remaining -= targets.get(i).receiveEnergy(Math.min(share, remaining), false);
			}
		}
		return amount - remaining;
	}
}
