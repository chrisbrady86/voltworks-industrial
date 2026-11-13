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

/** Runs any vanilla smelting recipe on power: 3 J/tick, 100 ticks per item (2x furnace speed). */
public class ElectricFurnaceBlockEntity extends ProcessingMachineBlockEntity {
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> check = RecipeManager.createCheck(RecipeType.SMELTING);

	public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ELECTRIC_FURNACE, pos, state, 3, 100);
	}

	@Override
	protected Optional<ItemStack> getResult(ServerLevel level, ItemStack input) {
		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		return check.getRecipeFor(recipeInput, level).map(holder -> holder.value().assemble(recipeInput));
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.electric_furnace");
	}
}
