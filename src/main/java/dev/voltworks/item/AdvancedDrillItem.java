package dev.voltworks.item;

import dev.voltworks.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Tier 2 drill. Sneak + right-click toggles 3x3 mode, which mines the 8 blocks around the target too. */
public class AdvancedDrillItem extends PowerToolItem {
	/** Set while we break the surrounding blocks, so those breaks don't each trigger another 3x3. */
	private static boolean breakingArea;

	public AdvancedDrillItem(Properties properties, int maxEnergy, int energyPerBlock) {
		super(properties, maxEnergy, energyPerBlock);
	}

	public static boolean isWide(ItemStack stack) {
		return stack.getOrDefault(ModComponents.WIDE_MODE, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean wide = !isWide(stack);
		stack.set(ModComponents.WIDE_MODE, wide);
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable(wide ? "message.voltworks.drill.wide" : "message.voltworks.drill.single"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		boolean hadPower = hasPower(stack);
		super.mineBlock(stack, level, state, pos, owner);
		if (hadPower && isWide(stack) && !breakingArea && !level.isClientSide() && owner instanceof ServerPlayer player
				&& !player.isShiftKeyDown()) {
			breakingArea = true;
			try {
				breakArea(stack, level, pos, player);
			} finally {
				breakingArea = false;
			}
		}
		return true;
	}

	private void breakArea(ItemStack stack, Level level, BlockPos center, ServerPlayer player) {
		// Pick the plane facing the player: looking steeply up/down -> horizontal layer, else a vertical wall.
		Direction.Axis normal = Math.abs(player.getLookAngle().y) > 0.7 ? Direction.Axis.Y : player.getDirection().getAxis();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				BlockPos target = switch (normal) {
					case Y -> center.offset(a, 0, b);
					case Z -> center.offset(a, b, 0);
					case X -> center.offset(0, b, a);
				};
				BlockState targetState = level.getBlockState(target);
				if (!hasPower(stack)) return;
				// Only blocks this drill mines efficiently and can harvest (never bedrock, fluids, air...).
				if (targetState.isAir() || targetState.getDestroySpeed(level, target) < 0
						|| super.getDestroySpeed(stack, targetState) <= 1.0F || !isCorrectToolForDrops(stack, targetState)) {
					continue;
				}
				player.gameMode.destroyBlock(target);
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(Component.translatable(isWide(stack) ? "tooltip.voltworks.mode.wide" : "tooltip.voltworks.mode.single")
				.withStyle(ChatFormatting.YELLOW));
		builder.accept(Component.translatable("tooltip.voltworks.mode.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
