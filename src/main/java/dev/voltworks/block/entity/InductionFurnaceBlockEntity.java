package dev.voltworks.block.entity;

import dev.voltworks.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;

/** Tier 2 furnace: two independent lanes, each 4x furnace speed. 6 J/tick per busy lane, 50 ticks per item. */
public class InductionFurnaceBlockEntity extends ProcessingMachineBlockEntity {
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> check = RecipeManager.createCheck(RecipeType.SMELTING);

	public InductionFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INDUCTION_FURNACE, pos, state, 2, 4_000, 6, 50);
	}

	@Override
	protected Optional<ItemStack> getResult(ServerLevel level, ItemStack input) {
		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		return check.getRecipeFor(recipeInput, level).map(holder -> holder.value().assemble(recipeInput));
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.induction_furnace");
	}
}
