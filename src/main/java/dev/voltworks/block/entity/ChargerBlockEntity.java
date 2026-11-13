package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.ChargeableItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Charges power tools from the network at up to 128 J/tick. */
public class ChargerBlockEntity extends PoweredContainerBlockEntity implements EnergyReceiver {
	public static final int RATE = 128;
	private static final int[] SLOTS = {0};

	public ChargerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHARGER, pos, state, 1, 4_000);
		this.progressTotal = 100;
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		ItemStack stack = items.get(0);
		boolean charging = false;
		int percent = 0;
		if (stack.getItem() instanceof ChargeableItem item) {
			if (energy > 0 && !item.isFull(stack)) {
				int moved = item.charge(stack, Math.min(RATE, energy));
				if (moved > 0) {
					energy -= moved;
					charging = true;
					setChanged();
				}
			}
			percent = (int) (100L * item.getEnergy(stack) / item.getMaxEnergy());
		}
		progress = percent; // shown as the charge bar in the GUI
		setActive(level, pos, state, charging);
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		return acceptEnergy(maxAmount, simulate);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof ChargeableItem;
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		// Hoppers only pull tools out once they're fully charged.
		return stack.getItem() instanceof ChargeableItem item && item.isFull(stack);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.charger");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(MachineMenu.Kind.CHARGER, containerId, inventory, this, data);
	}
}
