package dev.voltworks.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chainsaw: cutting a log fells the whole tree. Every log of the same kind connected to it (diagonals included),
 * at or above the cut, is broken too, paying the usual energy per log and stopping when the battery runs out.
 * Only trees fell: the logs must touch natural (non-player-placed) leaves, so log builds lose one log at a time.
 * Sneak to cut a single log.
 */
public class ChainsawItem extends PowerToolItem {
	/** Most logs felled at once (a big jungle or dark oak tree has around 100-150). */
	public static final int MAX_LOGS = 256;
	/** How far sideways from the cut a tree may spread. */
	public static final int MAX_RADIUS = 12;

	/** Set while we fell the rest of the tree, so those breaks don't each start another felling. */
	private static boolean felling;

	public ChainsawItem(Properties properties, int maxEnergy, int energyPerBlock) {
		super(properties, maxEnergy, energyPerBlock);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean hadPower = hasPower(stack);
		super.mineBlock(stack, level, state, pos, owner);
		if (hadPower && !felling && !level.isClientSide() && state.is(BlockTags.LOGS) && owner instanceof ServerPlayer player
				&& !player.isShiftKeyDown()) {
			felling = true;
			try {
				fell(stack, level, pos, state.getBlock(), player);
			} finally {
				felling = false;
			}
		}
		return true;
	}

	private void fell(ItemStack stack, Level level, BlockPos origin, Block log, ServerPlayer player) {
		List<BlockPos> logs = findTree(level, origin, log);
		for (BlockPos target : logs) {
			if (!hasPower(stack)) return;
			player.gameMode.destroyBlock(target);
		}
	}

	/**
	 * The logs of {@code log}'s kind connected to {@code origin} (which is already cut), lowest and nearest first.
	 * Empty if none of them touch natural leaves, i.e. it isn't a tree.
	 */
	public static List<BlockPos> findTree(Level level, BlockPos origin, Block log) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);
		boolean leaves = false;
		while (!queue.isEmpty() && found.size() < MAX_LOGS) {
			BlockPos current = queue.poll();
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dy == 0 && dz == 0) continue;
				BlockPos next = current.offset(dx, dy, dz);
				if (next.getY() < origin.getY() || Math.abs(next.getX() - origin.getX()) > MAX_RADIUS
						|| Math.abs(next.getZ() - origin.getZ()) > MAX_RADIUS || !visited.add(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(log)) {
					found.add(next);
					queue.add(next);
				} else if (!leaves && state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
					leaves = true;
				}
			}
		}
		if (!leaves) return List.of();
		found.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(p -> p.distManhattan(origin)));
		return found;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(Component.translatable("tooltip.voltworks.chainsaw").withStyle(ChatFormatting.GRAY));
	}
}
