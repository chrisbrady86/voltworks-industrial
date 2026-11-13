package dev.voltworks;

import dev.voltworks.recipe.AlloyRecipes;
import dev.voltworks.recipe.CrusherRecipes;
import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModComponents;
import dev.voltworks.registry.ModItems;
import dev.voltworks.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.GenerationStep;

public class Voltworks implements ModInitializer {
	public static final String MOD_ID = "voltworks";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		CrusherRecipes.init();
		AlloyRecipes.init();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.voltworks"))
				.icon(() -> new ItemStack(ModBlocks.GENERATOR))
				.displayItems((params, out) -> {
					out.accept(ModItems.GUIDE);
					out.accept(ModBlocks.GENERATOR);
					out.accept(ModBlocks.SOLAR_PANEL);
					out.accept(ModBlocks.BATTERY_BOX);
					out.accept(ModBlocks.COPPER_CABLE);
					out.accept(ModBlocks.ITEM_PIPE);
					out.accept(ModItems.WRENCH);
					out.accept(ModBlocks.CRUSHER);
					out.accept(ModBlocks.ELECTRIC_FURNACE);
					out.accept(ModBlocks.MACHINE_FRAME);
					out.accept(ModItems.IRON_DUST);
					out.accept(ModItems.GOLD_DUST);
					out.accept(ModItems.COPPER_DUST);
					out.accept(ModBlocks.ALLOY_SMELTER);
					out.accept(ModBlocks.THERMAL_GENERATOR);
					out.accept(ModBlocks.INDUSTRIAL_CRUSHER);
					out.accept(ModBlocks.INDUCTION_FURNACE);
					out.accept(ModBlocks.ADVANCED_MACHINE_FRAME);
					out.accept(ModItems.STEEL_INGOT);
					out.accept(ModItems.CIRCUIT);
					out.accept(ModBlocks.TIN_ORE);
					out.accept(ModBlocks.DEEPSLATE_TIN_ORE);
					out.accept(ModItems.RAW_TIN);
					out.accept(ModItems.TIN_DUST);
					out.accept(ModItems.TIN_INGOT);
					out.accept(ModBlocks.TIN_BLOCK);
					out.accept(ModBlocks.LEAD_ORE);
					out.accept(ModBlocks.DEEPSLATE_LEAD_ORE);
					out.accept(ModItems.RAW_LEAD);
					out.accept(ModItems.LEAD_DUST);
					out.accept(ModItems.LEAD_INGOT);
					out.accept(ModBlocks.LEAD_BLOCK);
					out.accept(ModBlocks.STEEL_BLOCK);
					out.accept(ModBlocks.CHARGER);
					out.accept(ModItems.POWER_CELL);
					out.accept(ModItems.ELECTRIC_DRILL);
					out.accept(ModItems.CHAINSAW);
					out.accept(ModItems.ADVANCED_DRILL);
					out.accept(ModItems.SPEED_UPGRADE);
					out.accept(ModItems.CAPACITY_UPGRADE);
					out.accept(ModItems.EJECTOR_UPGRADE);
					out.accept(ModBlocks.URANIUM_ORE);
					out.accept(ModBlocks.DEEPSLATE_URANIUM_ORE);
					out.accept(ModItems.RAW_URANIUM);
					out.accept(ModItems.URANIUM_DUST);
					out.accept(ModItems.URANIUM_INGOT);
					out.accept(ModBlocks.URANIUM_BLOCK);
					out.accept(ModBlocks.REACTOR_CHAMBER);
					out.accept(ModItems.FUEL_ROD);
					out.accept(ModItems.HEAT_VENT);
					out.accept(ModItems.DEPLETED_FUEL_ROD);
					// Fully charged copies for creative mode
					for (var tool : new net.minecraft.world.item.Item[]{ModItems.ELECTRIC_DRILL, ModItems.CHAINSAW, ModItems.ADVANCED_DRILL}) {
						ItemStack charged = new ItemStack(tool);
						((dev.voltworks.item.ChargeableItem) tool).setEnergy(charged, Integer.MAX_VALUE);
						out.accept(charged);
					}
				})
				.build());

		// Ore generation: features are defined in data/voltworks/worldgen; this adds them to overworld biomes.
		for (String ore : new String[]{"ore_tin", "ore_lead", "ore_uranium"}) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
					ResourceKey.create(Registries.PLACED_FEATURE, id(ore)));
		}
	}
}
