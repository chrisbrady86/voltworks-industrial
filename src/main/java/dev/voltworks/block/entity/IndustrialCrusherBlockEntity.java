package dev.voltworks.block.entity;

import dev.voltworks.recipe.CrusherRecipes;
import dev.voltworks.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Tier 2 crusher: 4x faster than the Crusher and 3 dusts per ore. 8 J/tick, 50 ticks per item. */
public class IndustrialCrusherBlockEntity extends ProcessingMachineBlockEntity {
	public IndustrialCrusherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INDUSTRIAL_CRUSHER, pos, state, 1, 4_000, 8, 50);
	}

	@Override
	protected Optional<ItemStack> getResult(ServerLevel level, ItemStack input) {
		return CrusherRecipes.get(input, true);
	}

	@Override
	protected boolean accepts(ItemStack stack) {
		return CrusherRecipes.has(stack);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.industrial_crusher");
	}
}
