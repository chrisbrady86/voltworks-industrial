package dev.voltworks.registry;

import dev.voltworks.Voltworks;
import dev.voltworks.block.CableBlock;
import dev.voltworks.block.ComputerBlock;
import dev.voltworks.block.DataCableBlock;
import dev.voltworks.block.FilterPipeBlock;
import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.MachineBlock;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block MACHINE_FRAME = register("machine_frame", Block::new, metal());
	public static final Block ITEM_PIPE = register("item_pipe", ItemPipeBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.COPPER).noOcclusion());
	public static final Block FAST_ITEM_PIPE = register("fast_item_pipe", p -> new ItemPipeBlock(p, ItemPipeBlock.Tier.FAST), pipe());
	public static final Block EXPRESS_ITEM_PIPE = register("express_item_pipe", p -> new ItemPipeBlock(p, ItemPipeBlock.Tier.EXPRESS), pipe());
	public static final Block FILTER_PIPE = register("filter_pipe", FilterPipeBlock::new, pipe());

	// Computer storage
	public static final Block DATA_CABLE = register("data_cable", DataCableBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.4F).sound(SoundType.COPPER).noOcclusion());
	public static final Block DRIVE_RACK = computer("drive_rack", () -> ModBlockEntities.DRIVE_RACK);
	public static final Block STORAGE_TERMINAL = computer("storage_terminal", () -> ModBlockEntities.STORAGE_TERMINAL);
	public static final Block COMPUTER_CRAFTER = computer("computer_crafter", () -> ModBlockEntities.COMPUTER_CRAFTER);
	public static final Block STORAGE_INTERFACE = computer("storage_interface", () -> ModBlockEntities.STORAGE_INTERFACE);
	public static final Block COPPER_CABLE = register("copper_cable", CableBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.4F).sound(SoundType.COPPER).noOcclusion());
	public static final Block GENERATOR = machine("generator", () -> ModBlockEntities.GENERATOR,
			metal().lightLevel(state -> state.getValue(MachineBlock.LIT) ? 13 : 0));
	public static final Block CRUSHER = machine("crusher", () -> ModBlockEntities.CRUSHER, metal());
	public static final Block ELECTRIC_FURNACE = machine("electric_furnace", () -> ModBlockEntities.ELECTRIC_FURNACE,
			metal().lightLevel(state -> state.getValue(MachineBlock.LIT) ? 10 : 0));
	public static final Block SMALL_BATTERY = machine("small_battery", () -> ModBlockEntities.BATTERY, metal());
	public static final Block BATTERY_BOX = machine("battery_box", () -> ModBlockEntities.BATTERY, metal());
	public static final Block LARGE_BATTERY = machine("large_battery", () -> ModBlockEntities.BATTERY, metal().strength(5.0F, 8.0F));
	public static final Block HUGE_BATTERY = machine("huge_battery", () -> ModBlockEntities.BATTERY, metal().strength(5.0F, 8.0F));
	public static final Block SOLAR_PANEL = machine("solar_panel", () -> ModBlockEntities.SOLAR_PANEL, metal());

	// Tier 2
	public static final Block ADVANCED_MACHINE_FRAME = register("advanced_machine_frame", Block::new, metal().strength(5.0F, 8.0F));
	public static final Block ALLOY_SMELTER = machine("alloy_smelter", () -> ModBlockEntities.ALLOY_SMELTER,
			metal().lightLevel(state -> state.getValue(MachineBlock.LIT) ? 10 : 0));
	public static final Block INDUSTRIAL_CRUSHER = machine("industrial_crusher", () -> ModBlockEntities.INDUSTRIAL_CRUSHER, metal().strength(5.0F, 8.0F));
	public static final Block URANIUM_REFINER = machine("uranium_refiner", () -> ModBlockEntities.URANIUM_REFINER,
			metal().strength(5.0F, 8.0F).lightLevel(state -> state.getValue(MachineBlock.LIT) ? 7 : 0));
	public static final Block INDUCTION_FURNACE = machine("induction_furnace", () -> ModBlockEntities.INDUCTION_FURNACE,
			metal().strength(5.0F, 8.0F).lightLevel(state -> state.getValue(MachineBlock.LIT) ? 12 : 0));
	public static final Block THERMAL_GENERATOR = machine("thermal_generator", () -> ModBlockEntities.THERMAL_GENERATOR,
			metal().strength(5.0F, 8.0F).lightLevel(state -> state.getValue(MachineBlock.LIT) ? 8 : 0));

	// Ores and storage blocks
	public static final Block TIN_ORE = register("tin_ore", Block::new, stoneOre());
	public static final Block DEEPSLATE_TIN_ORE = register("deepslate_tin_ore", Block::new, deepslateOre());
	public static final Block LEAD_ORE = register("lead_ore", Block::new, stoneOre());
	public static final Block DEEPSLATE_LEAD_ORE = register("deepslate_lead_ore", Block::new, deepslateOre());
	public static final Block TIN_BLOCK = register("tin_block", Block::new, metal().strength(5.0F, 6.0F));
	public static final Block LEAD_BLOCK = register("lead_block", Block::new, metal().strength(5.0F, 6.0F));
	public static final Block STEEL_BLOCK = register("steel_block", Block::new, metal().strength(6.0F, 8.0F));
	public static final Block URANIUM_ORE = register("uranium_ore", Block::new, stoneOre().lightLevel(s -> 3));
	public static final Block DEEPSLATE_URANIUM_ORE = register("deepslate_uranium_ore", Block::new, deepslateOre().lightLevel(s -> 3));
	public static final Block URANIUM_BLOCK = register("uranium_block", Block::new, metal().strength(5.0F, 6.0F).lightLevel(s -> 6));
	public static final Block REACTOR_CHAMBER = machine("reactor_chamber", () -> ModBlockEntities.REACTOR,
			metal().strength(8.0F, 20.0F).lightLevel(state -> state.getValue(MachineBlock.LIT) ? 9 : 0));
	public static final Block CHARGER = machine("charger", () -> ModBlockEntities.CHARGER, metal().lightLevel(state -> state.getValue(MachineBlock.LIT) ? 7 : 0));

	private ModBlocks() {
	}

	private static Block computer(String name, Supplier<BlockEntityType<?>> type) {
		return register(name, p -> new ComputerBlock(type, p), metal());
	}

	private static BlockBehaviour.Properties pipe() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.COPPER).noOcclusion();
	}

	private static BlockBehaviour.Properties metal() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties stoneOre() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties deepslateOre() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops();
	}

	private static Block machine(String name, Supplier<BlockEntityType<?>> type, BlockBehaviour.Properties props) {
		return register(name, p -> new MachineBlock(type, p), props);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Voltworks.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Voltworks.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
	}
}
