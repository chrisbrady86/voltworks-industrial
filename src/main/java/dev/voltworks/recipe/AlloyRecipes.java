package dev.voltworks.recipe;

import dev.voltworks.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Alloy Smelter recipes: two inputs (in either slot order) -> one output. */
public final class AlloyRecipes {
	/** One input: what matches, how many are used, and example items to show (e.g. in JEI). */
	public record Input(Predicate<ItemStack> test, int count, List<Item> display) {
		/** The example items as stacks of {@code count}. */
		public List<ItemStack> displayStacks() {
			return display.stream().map(i -> new ItemStack(i, count)).toList();
		}

		boolean matches(ItemStack stack) {
			return !stack.isEmpty() && test.test(stack) && stack.getCount() >= count;
		}
	}

	public record Recipe(Input a, Input b, Item output, int outputCount) {
		public ItemStack result() {
			return new ItemStack(output, outputCount);
		}
	}

	/** A recipe matched against the machine's slots; {@code swapped} means slot 0 holds input b. */
	public record Match(Recipe recipe, boolean swapped) {
		public int countForSlot(int slot) {
			boolean isA = (slot == 0) != swapped;
			return isA ? recipe.a().count() : recipe.b().count();
		}
	}

	private static final List<Recipe> RECIPES = new ArrayList<>();

	private AlloyRecipes() {
	}

	private static Input of(ItemLike item, int count) {
		Item i = item.asItem();
		return new Input(s -> s.is(i), count, List.of(i));
	}

	private static Input coal(int count) {
		return new Input(s -> s.is(ItemTags.COALS), count, List.of(Items.COAL, Items.CHARCOAL)); // coal or charcoal
	}

	public static void init() {
		RECIPES.add(new Recipe(of(Items.IRON_INGOT, 1), coal(2), ModItems.STEEL_INGOT, 1));
		RECIPES.add(new Recipe(of(ModItems.IRON_DUST, 1), coal(2), ModItems.STEEL_INGOT, 1));
		RECIPES.add(new Recipe(of(Items.GOLD_INGOT, 4), of(Items.NETHERITE_SCRAP, 4), Items.NETHERITE_INGOT, 1));
		// Silicon for computer chips: reduce sand (or nether quartz) with coal
		RECIPES.add(new Recipe(of(Items.SAND, 2), coal(1), ModItems.SILICON, 1));
		RECIPES.add(new Recipe(of(Items.QUARTZ, 1), coal(1), ModItems.SILICON, 2));
	}

	/** Every alloy recipe (for JEI). */
	public static List<Recipe> all() {
		return List.copyOf(RECIPES);
	}

	public static Optional<Match> find(ItemStack slot0, ItemStack slot1) {
		for (Recipe r : RECIPES) {
			if (r.a().matches(slot0) && r.b().matches(slot1)) return Optional.of(new Match(r, false));
			if (r.b().matches(slot0) && r.a().matches(slot1)) return Optional.of(new Match(r, true));
		}
		return Optional.empty();
	}

	/** Is this item used by any recipe? (for slot filtering) */
	public static boolean isIngredient(ItemStack stack) {
		for (Recipe r : RECIPES) {
			if (r.a().test().test(stack) || r.b().test().test(stack)) return true;
		}
		return false;
	}
}
