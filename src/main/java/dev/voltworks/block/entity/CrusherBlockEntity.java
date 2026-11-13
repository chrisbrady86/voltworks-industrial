package dev.voltworks.block.entity;

import dev.voltworks.recipe.CrusherRecipes;
import dev.voltworks.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Crushes ores into dust: 2 J/tick, 200 ticks per item (400 J). */
public class CrusherBlockEntity extends ProcessingMachineBlockEntity {
	public CrusherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRUSHER, pos, state, 2, 200);
	}

	@Override
	protected Optional<ItemStack> getResult(ServerLevel level, ItemStack input) {
		return CrusherRecipes.get(input, false);
	}

	@Override
	protected boolean accepts(ItemStack stack) {
		return CrusherRecipes.has(stack);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.crusher");
	}
}
