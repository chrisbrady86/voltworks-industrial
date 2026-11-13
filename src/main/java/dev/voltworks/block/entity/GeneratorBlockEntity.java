package dev.voltworks.block.entity;

import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import org.jspecify.annotations.Nullable;

/** Burns furnace fuel to make power. 10 J/tick while burning; coal = 16,000 J. */
public class GeneratorBlockEntity extends PoweredContainerBlockEntity {
	public static final int OUTPUT_PER_TICK = 10;
	/** Fuel burns this many times faster than in a furnace: coal (1,600 ticks) gives 400 ticks = 4,000 J. */
	public static final int BURN_SPEED = 4;
	public static final int MAX_PUSH_PER_TICK = 64;
	private static final int[] SLOTS = {0};

	public GeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GENERATOR, pos, state, 1, 10_000);
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		boolean changed = false;
		// "progress" counts down the remaining burn time for the flame gauge.
		if (progress <= 0 && energy + OUTPUT_PER_TICK <= capacity) {
			ItemStack fuel = items.get(0);
			int burn = fuel.isEmpty() ? 0
					: ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, this.getLootContext(level), 0);
			if (burn > 0) {
				progress = progressTotal = Math.max(1, burn / BURN_SPEED);
				ItemStackTemplate remainder = fuel.getItem().getCraftingRemainder();
				fuel.shrink(1);
				if (fuel.isEmpty() && remainder != null) {
					items.set(0, remainder.create()); // lava bucket -> empty bucket
				}
				changed = true;
			}
		}

		boolean burning = progress > 0;
		if (burning) {
			progress--;
			energy = Math.min(capacity, energy + OUTPUT_PER_TICK);
			changed = true;
		}

		int before = energy;
		pushEnergy(level, pos, MAX_PUSH_PER_TICK, true);
		changed |= before != energy;

		setActive(level, pos, state, burning);
		if (changed) {
			setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	@Override
	protected SideConfig.Mode defaultSideMode(SideConfig.Side side) {
		return SideConfig.Mode.BOTH; // fuel in from anywhere, empty buckets out from anywhere
	}

	@Override
	protected int[] inputSlots() {
		return SLOTS;
	}

	@Override
	protected int[] outputSlots() {
		return SLOTS;
	}

	@Override
	protected boolean allowExtract(int slot, ItemStack stack) {
		return !stack.has(DataComponents.COOKING_FUEL); // only pull out empty buckets
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.generator");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(MachineMenu.Kind.GENERATOR, containerId, inventory, this, data);
	}
}
