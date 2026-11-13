package dev.voltworks.client.jei;

import dev.voltworks.Voltworks;
import dev.voltworks.block.entity.AlloySmelterBlockEntity;
import dev.voltworks.block.entity.UraniumRefinerBlockEntity;
import dev.voltworks.recipe.AlloyRecipes;
import dev.voltworks.recipe.CrusherRecipes;
import dev.voltworks.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** JEI pages for the Voltworks machines that use their own recipes: Alloy Smelter, Crushers and Uranium Refiner. */
final class MachineCategories {
	static final IRecipeType<AlloyRecipes.Recipe> ALLOY = IRecipeType.create(Voltworks.MOD_ID, "alloy_smelting", AlloyRecipes.Recipe.class);
	static final IRecipeType<CrusherRecipes.Entry> CRUSHING = IRecipeType.create(Voltworks.MOD_ID, "crushing", CrusherRecipes.Entry.class);
	static final IRecipeType<Refining> REFINING = IRecipeType.create(Voltworks.MOD_ID, "refining", Refining.class);

	/** One Uranium Refiner recipe. */
	record Refining(ItemStack input, ItemStack output) {
	}

	private static final int TEXT_COLOR = 0xFF606060;

	private MachineCategories() {
	}

	/** "4,800 J · 15 s": what one item costs at the machine's base speed. */
	private static Component cost(int energyPerTick, int ticks) {
		return Component.translatable("jei.voltworks.cost", String.format("%,d", energyPerTick * ticks), String.format("%.1f", ticks / 20.0));
	}

	/** Common parts: a title, a machine icon and a fixed size. */
	private abstract static class Base<T> implements IRecipeCategory<T> {
		private final IRecipeType<T> type;
		private final Component title;
		private final IDrawable icon;

		Base(IGuiHelper gui, IRecipeType<T> type, ItemLike machine) {
			this.type = type;
			this.title = Component.translatable(machine.asItem().getDescriptionId());
			this.icon = gui.createDrawableItemLike(machine);
		}

		@Override
		public IRecipeType<T> getRecipeType() {
			return type;
		}

		@Override
		public Component getTitle() {
			return title;
		}

		@Override
		public IDrawable getIcon() {
			return icon;
		}

		@Override
		public int getWidth() {
			return 120;
		}

		@Override
		public int getHeight() {
			return 34;
		}
	}

	/** Two inputs (with amounts) -> one output. Steel, silicon, netherite. */
	static class Alloy extends Base<AlloyRecipes.Recipe> {
		Alloy(IGuiHelper gui) {
			super(gui, ALLOY, ModBlocks.ALLOY_SMELTER);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, AlloyRecipes.Recipe recipe, IFocusGroup focuses) {
			builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(recipe.a().displayStacks());
			builder.addInputSlot(19, 1).setStandardSlotBackground().addItemStacks(recipe.b().displayStacks());
			builder.addOutputSlot(71, 1).setOutputSlotBackground().addItemStacks(java.util.List.of(recipe.result()));
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder builder, AlloyRecipes.Recipe recipe, IFocusGroup focuses) {
			builder.addRecipeArrowWidget().setPosition(42, 1);
			builder.addText(cost(AlloySmelterBlockEntity.ENERGY_PER_TICK, AlloySmelterBlockEntity.TICKS), 120, 10).setPosition(0, 23).setColor(TEXT_COLOR);
		}
	}

	/** Input -> Crusher output, and the Industrial Crusher's (bigger, for ores) output. */
	static class Crushing extends Base<CrusherRecipes.Entry> {
		Crushing(IGuiHelper gui) {
			super(gui, CRUSHING, ModBlocks.CRUSHER);
		}

		@Override
		public int getWidth() {
			return 138;
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, CrusherRecipes.Entry recipe, IFocusGroup focuses) {
			builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(java.util.List.of(recipe.input()));
			builder.addOutputSlot(53, 1).setOutputSlotBackground().addItemStacks(java.util.List.of(recipe.crusher()));
			if (recipe.industrial().getCount() != recipe.crusher().getCount()) {
				builder.addOutputSlot(101, 1).setOutputSlotBackground().addItemStacks(java.util.List.of(recipe.industrial()));
			}
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder builder, CrusherRecipes.Entry recipe, IFocusGroup focuses) {
			builder.addRecipeArrowWidget().setPosition(23, 1);
			if (recipe.industrial().getCount() != recipe.crusher().getCount()) {
				// A label under each output
				builder.addText(Component.translatable("jei.voltworks.crusher.crusher"), 48, 10).setPosition(38, 23).setColor(TEXT_COLOR)
						.setTextAlignment(HorizontalAlignment.CENTER);
				builder.addText(Component.translatable("jei.voltworks.crusher.industrial"), 54, 10).setPosition(83, 23).setColor(TEXT_COLOR)
						.setTextAlignment(HorizontalAlignment.CENTER);
			} else {
				builder.addText(Component.translatable("jei.voltworks.crusher.same"), 120, 10).setPosition(0, 23).setColor(TEXT_COLOR);
			}
		}
	}

	/** Raw uranium / ore / spent fuel rod -> Refined Uranium. */
	static class Refiner extends Base<Refining> {
		Refiner(IGuiHelper gui) {
			super(gui, REFINING, ModBlocks.URANIUM_REFINER);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Refining recipe, IFocusGroup focuses) {
			builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(java.util.List.of(recipe.input()));
			builder.addOutputSlot(53, 1).setOutputSlotBackground().addItemStacks(java.util.List.of(recipe.output()));
		}

		@Override
		public void createRecipeExtras(IRecipeExtrasBuilder builder, Refining recipe, IFocusGroup focuses) {
			builder.addRecipeArrowWidget().setPosition(23, 1);
			builder.addText(cost(UraniumRefinerBlockEntity.ENERGY_PER_TICK, UraniumRefinerBlockEntity.TICKS), 120, 10).setPosition(0, 23).setColor(TEXT_COLOR);
		}
	}
}
