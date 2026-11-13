package dev.voltworks.client.jei;

import dev.voltworks.Voltworks;
import dev.voltworks.menu.ComputerCrafterMenu;
import dev.voltworks.menu.NetworkMenu;
import dev.voltworks.network.StorageActionPayload;
import dev.voltworks.registry.ModMenus;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * JEI support: the "+" button on any crafting recipe fills the Computer Crafter's 3x3 pattern. For each slot it picks
 * the ingredient variant you have most of in storage (any plank, say), falling back to what JEI shows. Slots whose
 * ingredient isn't in storage are highlighted red, but the pattern is set anyway, ready for when you have them.
 * Only loaded when JEI is installed (fabric.mod.json "jei_mod_plugin" entrypoint).
 */
@JeiPlugin
public class VoltworksJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Voltworks.id("jei");
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addRecipeTransferHandler(new CrafterTransfer(), RecipeTypes.CRAFTING);
		org.slf4j.LoggerFactory.getLogger("voltworks").info("JEI: registered the Computer Crafter recipe transfer");
	}

	static class CrafterTransfer implements IRecipeTransferHandler<ComputerCrafterMenu, RecipeHolder<CraftingRecipe>> {
		@Override
		public Class<? extends ComputerCrafterMenu> getContainerClass() {
			return ComputerCrafterMenu.class;
		}

		@Override
		public Optional<MenuType<ComputerCrafterMenu>> getMenuType() {
			return Optional.of(ModMenus.COMPUTER_CRAFTER);
		}

		@Override
		public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
			return RecipeTypes.CRAFTING;
		}

		// Still abstract in JEI 31.14 (the context-based default forwards here), so it must be implemented.
		@Override
		@SuppressWarnings("removal")
		public @Nullable IRecipeTransferError transferRecipe(ComputerCrafterMenu menu, RecipeHolder<CraftingRecipe> recipe,
				IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
			List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
			List<IRecipeSlotView> missing = new ArrayList<>();
			ItemStack[] pattern = new ItemStack[9];
			for (int i = 0; i < 9; i++) {
				pattern[i] = ItemStack.EMPTY;
				if (i >= inputs.size() || inputs.get(i).isEmpty()) continue;
				IRecipeSlotView slot = inputs.get(i);
				ItemStack best = bestInStorage(menu, slot);
				if (best == null) {
					missing.add(slot);
					best = slot.getDisplayedItemStack().orElse(slot.getItemStacks().findFirst().orElse(ItemStack.EMPTY));
				}
				pattern[i] = best;
			}
			if (doTransfer) {
				for (int i = 0; i < 9; i++) {
					ClientPlayNetworking.send(new StorageActionPayload(menu.containerId, pattern[i], NetworkMenu.ACTION_SET_PATTERN, i));
				}
			}
			return missing.isEmpty() ? null : new MissingInStorage(missing);
		}

		/** The slot's ingredient variant that storage has the most of, or null if it has none of them. */
		private static @Nullable ItemStack bestInStorage(NetworkMenu menu, IRecipeSlotView slot) {
			ItemStack best = null;
			long bestCount = 0;
			for (ItemStack option : slot.getItemStacks().toList()) {
				for (StoredItem stored : menu.view) {
					if (ItemStack.isSameItemSameComponents(stored.item(), option) && stored.count() > bestCount) {
						best = stored.item();
						bestCount = stored.count();
					}
				}
			}
			return best;
		}
	}

	/** Shown on the "+" button: these ingredients aren't in storage. The transfer still goes ahead. */
	record MissingInStorage(List<IRecipeSlotView> slots) implements IRecipeTransferError {
		@Override
		public Type getType() {
			return Type.COSMETIC;
		}

		@Override
		public int getButtonHighlightColor() {
			return 0x80FFA500;
		}

		@Override
		public void showError(GuiGraphicsExtractor g, int mouseX, int mouseY, IRecipeSlotsView recipeSlots, int recipeX, int recipeY) {
			g.pose().pushMatrix();
			g.pose().translate(recipeX, recipeY);
			for (IRecipeSlotView slot : slots) slot.drawHighlight(g, 0x66FF0000);
			g.pose().popMatrix();
		}

		@Override
		public void getTooltip(ITooltipBuilder tooltip) {
			tooltip.add(Component.translatable("jei.voltworks.crafter.missing").withStyle(ChatFormatting.RED));
			tooltip.add(Component.translatable("jei.voltworks.crafter.still_set").withStyle(ChatFormatting.GRAY));
		}
	}
}
