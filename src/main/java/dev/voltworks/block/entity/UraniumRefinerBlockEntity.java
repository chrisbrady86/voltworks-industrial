package dev.voltworks.block.entity;

import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModItems;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uranium Refiner (tier 2): uranium can't be smelted or crushed into fuel. The refiner leaches and enriches raw
 * uranium (or uranium ore) into Refined Uranium, the only thing Fuel Rods are made from. It also reprocesses spent
 * fuel: a Depleted Fuel Rod gives back one Refined Uranium (its lead casing is lost; crush it instead to keep the lead).
 * Enrichment is power-hungry: 16 J/tick for 300 ticks (4,800 J) per item.
 */
public class UraniumRefinerBlockEntity extends ProcessingMachineBlockEntity {
	public static final int ENERGY_PER_TICK = 16;
	public static final int TICKS = 300;

	public UraniumRefinerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.URANIUM_REFINER, pos, state, 1, 8_000, ENERGY_PER_TICK, TICKS);
	}

	/** Input item -> how much Refined Uranium it gives. */
	public static Map<Item, Integer> recipes() {
		return Map.of(
				ModItems.RAW_URANIUM, 1,
				ModBlocks.URANIUM_ORE.asItem(), 1,
				ModBlocks.DEEPSLATE_URANIUM_ORE.asItem(), 1,
				ModItems.DEPLETED_FUEL_ROD, 1);
	}

	/** What one input item refines into. */
	public static Optional<ItemStack> refine(ItemStack input) {
		Integer count = recipes().get(input.getItem());
		return count == null ? Optional.empty() : Optional.of(new ItemStack(ModItems.REFINED_URANIUM, count));
	}

	@Override
	protected Optional<ItemStack> getResult(ServerLevel level, ItemStack input) {
		return refine(input);
	}

	@Override
	protected boolean accepts(ItemStack stack) {
		return refine(stack).isPresent();
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.uranium_refiner");
	}
}
