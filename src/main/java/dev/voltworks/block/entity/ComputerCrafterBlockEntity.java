package dev.voltworks.block.entity;

import dev.voltworks.menu.ComputerCrafterMenu;
import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.storage.StorageNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Computer Crafter: remembers a 3x3 crafting pattern and crafts it on demand straight from the storage network.
 * Each craft takes one of each pattern item from storage, then stores the result and any leftovers (empty buckets...).
 */
public class ComputerCrafterBlockEntity extends BlockEntity implements MenuProvider {
	private final SimpleContainer pattern = new SimpleContainer(9) {
		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			ComputerCrafterBlockEntity.this.setChanged();
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(ComputerCrafterBlockEntity.this, player);
		}
	};

	public ComputerCrafterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.COMPUTER_CRAFTER, pos, state);
	}

	public Container getPattern() {
		return pattern;
	}

	private static Optional<RecipeHolder<CraftingRecipe>> recipe(ServerLevel level, CraftingInput input) {
		return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
	}

	private static CraftingInput input(Container pattern) {
		List<ItemStack> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) grid.add(pattern.getItem(i).copyWithCount(pattern.getItem(i).isEmpty() ? 0 : 1));
		return CraftingInput.of(3, 3, grid);
	}

	/** What the pattern makes (empty if it isn't a recipe). */
	public static ItemStack preview(ServerLevel level, Container pattern) {
		CraftingInput input = input(pattern);
		return recipe(level, input).map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	/**
	 * Craft the pattern up to {@code times} times from the network at {@code pos}. Stops early when an ingredient runs
	 * out. Results go into storage; anything that doesn't fit goes to the player, or drops. Returns crafts made.
	 */
	public static int craft(ServerLevel level, BlockPos pos, Container pattern, int times, @Nullable Player player) {
		StorageNetwork net = StorageNetwork.find(level, pos);
		if (!net.isOnline()) return 0;
		CraftingInput input = input(pattern);
		Optional<RecipeHolder<CraftingRecipe>> recipe = recipe(level, input);
		if (recipe.isEmpty()) return 0;

		// How many of each item one craft needs
		List<ItemStack> needs = new ArrayList<>();
		outer:
		for (int i = 0; i < 9; i++) {
			ItemStack s = pattern.getItem(i);
			if (s.isEmpty()) continue;
			for (ItemStack n : needs) {
				if (ItemStack.isSameItemSameComponents(n, s)) {
					n.grow(1);
					continue outer;
				}
			}
			needs.add(s.copyWithCount(1));
		}

		int made = 0;
		for (; made < times; made++) {
			boolean enough = true;
			for (ItemStack n : needs) if (net.count(n) < n.getCount()) enough = false;
			if (!enough) break;
			for (ItemStack n : needs) {
				int left = n.getCount();
				while (left > 0) {
					ItemStack got = net.extract(n, left, false);
					if (got.isEmpty()) break;
					left -= got.getCount();
				}
			}
			give(level, pos, net, recipe.get().value().assemble(input), player);
			for (ItemStack rest : recipe.get().value().getRemainingItems(input)) {
				if (!rest.isEmpty()) give(level, pos, net, rest.copy(), player);
			}
		}
		return made;
	}

	private static void give(ServerLevel level, BlockPos pos, StorageNetwork net, ItemStack stack, @Nullable Player player) {
		ItemStack rest = net.insert(stack);
		if (!rest.isEmpty() && player != null) player.getInventory().add(rest);
		if (!rest.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, rest);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.voltworks.computer_crafter");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new ComputerCrafterMenu(containerId, inventory, ContainerLevelAccess.create(level, worldPosition), pattern);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.TypedOutputList<ItemStack> list = output.list("pattern", ItemStack.OPTIONAL_CODEC);
		for (int i = 0; i < 9; i++) list.add(pattern.getItem(i));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		int i = 0;
		for (ItemStack s : input.listOrEmpty("pattern", ItemStack.OPTIONAL_CODEC)) if (i < 9) pattern.setItem(i++, s);
		while (i < 9) pattern.setItem(i++, ItemStack.EMPTY);
	}
}
