package dev.voltworks.block;

import dev.voltworks.block.entity.FilterPipeBlockEntity;
import dev.voltworks.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Basic-speed pipe that only lets filtered items through. Right-click to set the filter (the wrench still works on it). */
public class FilterPipeBlock extends ItemPipeBlock {
	public FilterPipeBlock(Properties properties) {
		super(properties, Tier.BASIC);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FilterPipeBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof WrenchItem) {
			return InteractionResult.PASS; // let the wrench connect / disconnect / extract
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FilterPipeBlockEntity filter) {
			player.openMenu(filter);
		}
		return InteractionResult.SUCCESS;
	}
}
