package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.block.entity.AlloySmelterBlockEntity;
import dev.voltworks.block.entity.BatteryBlockEntity;
import dev.voltworks.block.entity.ChargerBlockEntity;
import dev.voltworks.block.entity.CrusherBlockEntity;
import dev.voltworks.block.entity.ElectricFurnaceBlockEntity;
import dev.voltworks.block.entity.GeneratorBlockEntity;
import dev.voltworks.block.entity.InductionFurnaceBlockEntity;
import dev.voltworks.block.entity.ComputerCrafterBlockEntity;
import dev.voltworks.block.entity.DriveRackBlockEntity;
import dev.voltworks.block.entity.FilterPipeBlockEntity;
import dev.voltworks.block.entity.StorageInterfaceBlockEntity;
import dev.voltworks.block.entity.StorageTerminalBlockEntity;
import dev.voltworks.block.entity.ItemPipeBlockEntity;
import dev.voltworks.block.entity.IndustrialCrusherBlockEntity;
import dev.voltworks.block.entity.ReactorBlockEntity;
import dev.voltworks.block.entity.SolarPanelBlockEntity;
import dev.voltworks.block.entity.ThermalGeneratorBlockEntity;
import dev.voltworks.block.entity.UraniumRefinerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE = register("item_pipe", ItemPipeBlockEntity::new,
			ModBlocks.ITEM_PIPE, ModBlocks.FAST_ITEM_PIPE, ModBlocks.EXPRESS_ITEM_PIPE);
	public static final BlockEntityType<FilterPipeBlockEntity> FILTER_PIPE = register("filter_pipe", FilterPipeBlockEntity::new, ModBlocks.FILTER_PIPE);
	public static final BlockEntityType<DriveRackBlockEntity> DRIVE_RACK = register("drive_rack", DriveRackBlockEntity::new, ModBlocks.DRIVE_RACK);
	public static final BlockEntityType<StorageTerminalBlockEntity> STORAGE_TERMINAL =
			register("storage_terminal", StorageTerminalBlockEntity::new, ModBlocks.STORAGE_TERMINAL);
	public static final BlockEntityType<ComputerCrafterBlockEntity> COMPUTER_CRAFTER =
			register("computer_crafter", ComputerCrafterBlockEntity::new, ModBlocks.COMPUTER_CRAFTER);
	public static final BlockEntityType<StorageInterfaceBlockEntity> STORAGE_INTERFACE =
			register("storage_interface", StorageInterfaceBlockEntity::new, ModBlocks.STORAGE_INTERFACE);
	public static final BlockEntityType<GeneratorBlockEntity> GENERATOR = register("generator", GeneratorBlockEntity::new, ModBlocks.GENERATOR);
	public static final BlockEntityType<CrusherBlockEntity> CRUSHER = register("crusher", CrusherBlockEntity::new, ModBlocks.CRUSHER);
	public static final BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE =
			register("electric_furnace", ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRIC_FURNACE);
	public static final BlockEntityType<BatteryBlockEntity> BATTERY = register("battery_box", BatteryBlockEntity::new,
			ModBlocks.SMALL_BATTERY, ModBlocks.BATTERY_BOX, ModBlocks.LARGE_BATTERY, ModBlocks.HUGE_BATTERY);
	public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL = register("solar_panel", SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL);
	public static final BlockEntityType<AlloySmelterBlockEntity> ALLOY_SMELTER = register("alloy_smelter", AlloySmelterBlockEntity::new, ModBlocks.ALLOY_SMELTER);
	public static final BlockEntityType<IndustrialCrusherBlockEntity> INDUSTRIAL_CRUSHER =
			register("industrial_crusher", IndustrialCrusherBlockEntity::new, ModBlocks.INDUSTRIAL_CRUSHER);
	public static final BlockEntityType<UraniumRefinerBlockEntity> URANIUM_REFINER =
			register("uranium_refiner", UraniumRefinerBlockEntity::new, ModBlocks.URANIUM_REFINER);
	public static final BlockEntityType<InductionFurnaceBlockEntity> INDUCTION_FURNACE =
			register("induction_furnace", InductionFurnaceBlockEntity::new, ModBlocks.INDUCTION_FURNACE);
	public static final BlockEntityType<ThermalGeneratorBlockEntity> THERMAL_GENERATOR =
			register("thermal_generator", ThermalGeneratorBlockEntity::new, ModBlocks.THERMAL_GENERATOR);
	public static final BlockEntityType<ChargerBlockEntity> CHARGER = register("charger", ChargerBlockEntity::new, ModBlocks.CHARGER);
	public static final BlockEntityType<ReactorBlockEntity> REACTOR = register("reactor_chamber", ReactorBlockEntity::new, ModBlocks.REACTOR_CHAMBER);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, FabricBlockEntityTypeBuilder.Factory<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Voltworks.id(name),
				FabricBlockEntityTypeBuilder.<T>create(factory, blocks).build());
	}

	public static void init() {
	}
}
