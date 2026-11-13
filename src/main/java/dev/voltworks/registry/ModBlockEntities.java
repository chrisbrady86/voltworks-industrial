package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.block.entity.AlloySmelterBlockEntity;
import dev.voltworks.block.entity.BatteryBlockEntity;
import dev.voltworks.block.entity.ChargerBlockEntity;
import dev.voltworks.block.entity.CrusherBlockEntity;
import dev.voltworks.block.entity.ElectricFurnaceBlockEntity;
import dev.voltworks.block.entity.GeneratorBlockEntity;
import dev.voltworks.block.entity.InductionFurnaceBlockEntity;
import dev.voltworks.block.entity.ItemPipeBlockEntity;
import dev.voltworks.block.entity.IndustrialCrusherBlockEntity;
import dev.voltworks.block.entity.ReactorBlockEntity;
import dev.voltworks.block.entity.SolarPanelBlockEntity;
import dev.voltworks.block.entity.ThermalGeneratorBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE = register("item_pipe", ItemPipeBlockEntity::new, ModBlocks.ITEM_PIPE);
	public static final BlockEntityType<GeneratorBlockEntity> GENERATOR = register("generator", GeneratorBlockEntity::new, ModBlocks.GENERATOR);
	public static final BlockEntityType<CrusherBlockEntity> CRUSHER = register("crusher", CrusherBlockEntity::new, ModBlocks.CRUSHER);
	public static final BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE =
			register("electric_furnace", ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE);
	public static final BlockEntityType<BatteryBlockEntity> BATTERY = register("battery_box", BatteryBlockEntity::new, ModBlocks.BATTERY_BOX);
	public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL = register("solar_panel", SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL);
	public static final BlockEntityType<AlloySmelterBlockEntity> ALLOY_SMELTER = register("alloy_smelter", AlloySmelterBlockEntity::new, ModBlocks.ALLOY_SMELTER);
	public static final BlockEntityType<IndustrialCrusherBlockEntity> INDUSTRIAL_CRUSHER =
			register("industrial_crusher", IndustrialCrusherBlockEntity::new, ModBlocks.INDUSTRIAL_CRUSHER);
	public static final BlockEntityType<InductionFurnaceBlockEntity> INDUCTION_FURNACE =
			register("induction_furnace", InductionFurnaceBlockEntity::new, ModBlocks.INDUCTION_FURNACE);
	public static final BlockEntityType<ThermalGeneratorBlockEntity> THERMAL_GENERATOR =
			register("thermal_generator", ThermalGeneratorBlockEntity::new, ModBlocks.THERMAL_GENERATOR);
	public static final BlockEntityType<ChargerBlockEntity> CHARGER = register("charger", ChargerBlockEntity::new, ModBlocks.CHARGER);
	public static final BlockEntityType<ReactorBlockEntity> REACTOR = register("reactor_chamber", ReactorBlockEntity::new, ModBlocks.REACTOR_CHAMBER);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, FabricBlockEntityTypeBuilder.Factory<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Voltworks.id(name),
				FabricBlockEntityTypeBuilder.<T>create(factory, block).build());
	}

	public static void init() {
	}
}
