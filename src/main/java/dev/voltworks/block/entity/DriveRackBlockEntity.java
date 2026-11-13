package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.HardDriveItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drive Rack: holds {@value #SLOTS} hard drives and serves them to the storage network it's part of. Needs power
 * ({@value #BASE_COST} J/tick plus {@value #PER_DRIVE} per drive); without it, its drives go offline.
 */
public class DriveRackBlockEntity extends PoweredContainerBlockEntity implements EnergyReceiver {
	public static final int SLOTS = 8;
	public static final int BASE_COST = 1;
	public static final int PER_DRIVE = 1;
	private static final int[] ALL = {0, 1, 2, 3, 4, 5, 6, 7};

	private boolean online;

	public DriveRackBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DRIVE_RACK, pos, state, SLOTS, 2_000);
		this.progressTotal = 1; // progress 1/1 = online, shown in the GUI
	}

	public int driveCount() {
		int n = 0;
		for (ItemStack s : items) if (s.getItem() instanceof HardDriveItem) n++;
		return n;
	}

	/** Powered this tick, so its drives are part of the network. */
	public boolean isOnline() {
		return online;
	}

	public int costPerTick() {
		return BASE_COST + PER_DRIVE * driveCount();
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		int cost = costPerTick();
		boolean was = online;
		online = energy >= cost;
		if (online) {
			energy -= cost;
			setChanged();
		}
		progress = online ? 1 : 0; // shown in the GUI
		if (was != online) setChanged();
		setActive(level, pos, state, online);
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		return acceptEnergy(maxAmount, simulate);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof HardDriveItem;
	}

	/** Drives are only ever taken out by hand: pipes and hoppers must not empty a rack and cut off its storage. */
	@Override
	protected boolean allowExtract(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	protected int[] inputSlots() {
		return ALL;
	}

	@Override
	protected int[] outputSlots() {
		return ALL;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.drive_rack");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(MachineMenu.Kind.DRIVE_RACK, containerId, inventory, this, data);
	}
}
