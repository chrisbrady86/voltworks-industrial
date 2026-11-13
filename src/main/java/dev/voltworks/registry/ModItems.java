package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.item.AdvancedDrillItem;
import dev.voltworks.item.FuelRodItem;
import dev.voltworks.item.TooltipItem;
import dev.voltworks.item.PowerToolItem;
import dev.voltworks.item.UpgradeItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item IRON_DUST = register("iron_dust");
	public static final Item GOLD_DUST = register("gold_dust");
	public static final Item COPPER_DUST = register("copper_dust");
	public static final Item STEEL_INGOT = register("steel_ingot");
	public static final Item CIRCUIT = register("circuit");
	public static final Item RAW_TIN = register("raw_tin");
	public static final Item TIN_INGOT = register("tin_ingot");
	public static final Item TIN_DUST = register("tin_dust");
	public static final Item RAW_LEAD = register("raw_lead");
	public static final Item LEAD_INGOT = register("lead_ingot");
	public static final Item LEAD_DUST = register("lead_dust");

	// Nuclear
	public static final Item RAW_URANIUM = register("raw_uranium");
	public static final Item URANIUM_INGOT = register("uranium_ingot");
	public static final Item URANIUM_DUST = register("uranium_dust");
	public static final Item FUEL_ROD = register("fuel_rod", FuelRodItem::new);
	public static final Item DEPLETED_FUEL_ROD = register("depleted_fuel_rod", p -> new TooltipItem(p.stacksTo(1), "tooltip.voltworks.depleted_fuel_rod"));
	public static final Item HEAT_VENT = register("heat_vent", p -> new TooltipItem(p.stacksTo(1), "tooltip.voltworks.heat_vent"));

	// Power tools
	public static final Item POWER_CELL = register("power_cell");

	// Machine upgrades
	public static final Item SPEED_UPGRADE = register("speed_upgrade", p -> new UpgradeItem(p, UpgradeItem.Type.SPEED));
	public static final Item CAPACITY_UPGRADE = register("capacity_upgrade", p -> new UpgradeItem(p, UpgradeItem.Type.CAPACITY));
	public static final Item EJECTOR_UPGRADE = register("ejector_upgrade", p -> new UpgradeItem(p, UpgradeItem.Type.EJECTOR));
	/** Iron-tier pickaxe + shovel. 30,000 J, 50 J per block (600 blocks per charge). */
	public static final Item ELECTRIC_DRILL = register("electric_drill", p -> new PowerToolItem(
			PowerToolItem.toolProperties(p, BlockTags.INCORRECT_FOR_IRON_TOOL, 8.0F, BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL),
			30_000, 50));
	/** Iron-tier axe that also shreds leaves. 30,000 J, 50 J per block. */
	public static final Item CHAINSAW = register("chainsaw", p -> new PowerToolItem(
			PowerToolItem.toolProperties(p, BlockTags.INCORRECT_FOR_IRON_TOOL, 8.0F, BlockTags.MINEABLE_WITH_AXE, BlockTags.LEAVES),
			30_000, 50));
	/** Diamond-tier pickaxe + shovel with a 3x3 mode. 100,000 J, 80 J per block. */
	public static final Item ADVANCED_DRILL = register("advanced_drill", p -> new AdvancedDrillItem(
			PowerToolItem.toolProperties(p, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 12.0F, BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_SHOVEL),
			100_000, 80));

	private ModItems() {
	}

	private static Item register(String name) {
		return register(name, Item::new);
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Voltworks.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static void init() {
	}
}
