package dev.voltworks.recipe;

import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Crusher recipes. Ores and raw ores become two dusts (doubling your ingots);
 * the Industrial Crusher gives 50% more on ore recipes (three dusts).
 */
public final class CrusherRecipes {
	/** Stored as item+count: ItemStacks cannot be created during mod init. */
	private record Output(Item item, int count, boolean ore) {
	}

	private static final Map<Item, Output> RECIPES = new HashMap<>();

	private CrusherRecipes() {
	}

	private static void ore(ItemLike input, ItemLike output, int count) {
		RECIPES.put(input.asItem(), new Output(output.asItem(), count, true));
	}

	private static void add(ItemLike input, ItemLike output, int count) {
		RECIPES.put(input.asItem(), new Output(output.asItem(), count, false));
	}

	public static void init() {
		ore(Items.IRON_ORE, ModItems.IRON_DUST, 2);
		ore(Items.DEEPSLATE_IRON_ORE, ModItems.IRON_DUST, 2);
		ore(Items.RAW_IRON, ModItems.IRON_DUST, 2);
		ore(Items.RAW_IRON_BLOCK, ModItems.IRON_DUST, 18);
		add(Items.IRON_INGOT, ModItems.IRON_DUST, 1);

		ore(Items.GOLD_ORE, ModItems.GOLD_DUST, 2);
		ore(Items.DEEPSLATE_GOLD_ORE, ModItems.GOLD_DUST, 2);
		ore(Items.RAW_GOLD, ModItems.GOLD_DUST, 2);
		ore(Items.RAW_GOLD_BLOCK, ModItems.GOLD_DUST, 18);
		add(Items.GOLD_INGOT, ModItems.GOLD_DUST, 1);

		ore(Items.COPPER_ORE, ModItems.COPPER_DUST, 2);
		ore(Items.DEEPSLATE_COPPER_ORE, ModItems.COPPER_DUST, 2);
		ore(Items.RAW_COPPER, ModItems.COPPER_DUST, 2);
		ore(Items.RAW_COPPER_BLOCK, ModItems.COPPER_DUST, 18);
		add(Items.COPPER_INGOT, ModItems.COPPER_DUST, 1);

		ore(ModBlocks.TIN_ORE, ModItems.TIN_DUST, 2);
		ore(ModBlocks.DEEPSLATE_TIN_ORE, ModItems.TIN_DUST, 2);
		ore(ModItems.RAW_TIN, ModItems.TIN_DUST, 2);
		add(ModItems.TIN_INGOT, ModItems.TIN_DUST, 1);

		ore(ModBlocks.LEAD_ORE, ModItems.LEAD_DUST, 2);
		ore(ModBlocks.DEEPSLATE_LEAD_ORE, ModItems.LEAD_DUST, 2);
		ore(ModItems.RAW_LEAD, ModItems.LEAD_DUST, 2);
		add(ModItems.LEAD_INGOT, ModItems.LEAD_DUST, 1);

		add(ModItems.DEPLETED_FUEL_ROD, ModItems.LEAD_DUST, 4); // recycle the casing

		ore(Items.COAL_ORE, Items.COAL, 2);
		ore(Items.DEEPSLATE_COAL_ORE, Items.COAL, 2);
		add(Items.COBBLESTONE, Items.GRAVEL, 1);
		add(Items.STONE, Items.COBBLESTONE, 1);
		add(Items.GRAVEL, Items.SAND, 1);
		add(Items.GLOWSTONE, Items.GLOWSTONE_DUST, 4);
		add(Items.BLAZE_ROD, Items.BLAZE_POWDER, 5);
		add(Items.BONE, Items.BONE_MEAL, 6);
	}

	public static Optional<ItemStack> get(ItemStack input, boolean industrial) {
		Output o = RECIPES.get(input.getItem());
		if (o == null) {
			return Optional.empty();
		}
		int count = industrial && o.ore() ? o.count() * 3 / 2 : o.count();
		return Optional.of(new ItemStack(o.item(), count));
	}

	/** One crusher recipe: the input, what the Crusher makes, and what the Industrial Crusher makes (for JEI). */
	public record Entry(ItemStack input, ItemStack crusher, ItemStack industrial) {
	}

	public static java.util.List<Entry> all() {
		java.util.List<Entry> list = new java.util.ArrayList<>();
		for (var e : RECIPES.entrySet()) {
			ItemStack in = new ItemStack(e.getKey());
			list.add(new Entry(in, get(in, false).orElseThrow(), get(in, true).orElseThrow()));
		}
		list.sort(java.util.Comparator.comparing(en -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(en.input().getItem()).toString()));
		return list;
	}

	public static boolean has(ItemStack input) {
		return RECIPES.containsKey(input.getItem());
	}
}
