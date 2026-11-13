package dev.voltworks.menu;

import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.item.UpgradeItem;
import dev.voltworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One menu class for all Voltworks GUIs: an energy bar, progress values and a layout of machine slots. */
public class MachineMenu extends AbstractContainerMenu {
	/** A GUI layout. Slot specs are {x, y, type} with type 0 = input, 1 = output, 2 = upgrade. */
	public enum Kind {
		GENERATOR(false, new int[][]{{80, 44, 0}}),
		PROCESSING(true, new int[][]{{56, 35, 0}, {116, 35, 1}}),
		DUAL_PROCESSING(true, new int[][]{{56, 21, 0}, {116, 21, 1}, {56, 49, 0}, {116, 49, 1}}),
		ALLOY(true, new int[][]{{38, 35, 0}, {56, 35, 0}, {116, 35, 1}}),
		CHARGER(false, new int[][]{{80, 30, 0}}),
		REACTOR(false, new int[][]{{62, 17, 0}, {80, 17, 0}, {98, 17, 0}, {62, 35, 0}, {80, 35, 0}, {98, 35, 0}, {62, 53, 0}, {80, 53, 0}, {98, 53, 0}});

		final int[][] slots;

		Kind(boolean upgradable, int[][] machineSlots) {
			if (!upgradable) {
				this.slots = machineSlots;
				return;
			}
			// Upgrade slots run down the right edge, after the machine's own slots.
			int[][] all = java.util.Arrays.copyOf(machineSlots, machineSlots.length + PoweredContainerBlockEntity.UPGRADE_SLOTS);
			for (int i = 0; i < PoweredContainerBlockEntity.UPGRADE_SLOTS; i++) {
				all[machineSlots.length + i] = new int[]{152, 17 + i * 18, 2};
			}
			this.slots = all;
		}

		public int slotCount() {
			return slots.length;
		}
	}

	public final Kind kind;
	private final Container container;
	private final ContainerData data;

	public MachineMenu(Kind kind, int containerId, Inventory inventory, Container container, ContainerData data) {
		super(ModMenus.get(kind), containerId);
		this.kind = kind;
		this.container = container;
		this.data = data;
		checkContainerSize(container, kind.slotCount());
		checkContainerDataCount(data, PoweredContainerBlockEntity.DATA_COUNT);
		for (int i = 0; i < kind.slots.length; i++) {
			int[] s = kind.slots[i];
			addSlot(switch (s[2]) {
				case 1 -> new OutputSlot(container, i, s[0], s[1]);
				case 2 -> new UpgradeSlot(container, i, s[0], s[1]);
				default -> new FilteredSlot(container, i, s[0], s[1]);
			});
		}
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	/** Client-side constructor: real values arrive via slot/data sync. */
	public static MachineMenu client(Kind kind, int containerId, Inventory inventory) {
		return new MachineMenu(kind, containerId, inventory, new SimpleContainer(kind.slotCount()),
				new SimpleContainerData(PoweredContainerBlockEntity.DATA_COUNT));
	}

	public int getEnergy() {
		return data.get(0);
	}

	public int getCapacity() {
		return Math.max(1, data.get(1));
	}

	public float getEnergyFraction() {
		return Math.min(1f, (float) getEnergy() / getCapacity());
	}

	/** Raw synced value: 0 energy, 1 capacity, 2 progress/heat, 3 total/max heat, 4 lane 2 progress/reactor output. */
	public int getValue(int index) {
		return data.get(index);
	}

	/** Processing: progress of a lane 0..1. Generator: remaining burn time 0..1 (lane 0). */
	public float getProgressFraction(int lane) {
		int total = data.get(3);
		return total <= 0 ? 0f : Math.min(1f, (float) data.get(lane == 0 ? 2 : 4) / total);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int machineSlots = kind.slotCount();
		int hotbarStart = machineSlots + 27;
		int end = hotbarStart + 9;

		if (index < machineSlots) {
			if (!moveItemStackTo(stack, machineSlots, end, true)) return ItemStack.EMPTY;
		} else {
			// Try each machine input that accepts this item, preferring empty ones (spreads across lanes).
			boolean moved = false;
			for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
				for (int i = 0; i < machineSlots && !stack.isEmpty(); i++) {
					Slot target = slots.get(i);
					if (target.mayPlace(stack) && (pass == 1 || !target.hasItem())) {
						moved |= moveItemStackTo(stack, i, i + 1, false);
					}
				}
			}
			if (!moved) {
				if (index < hotbarStart) {
					if (!moveItemStackTo(stack, hotbarStart, end, false)) return ItemStack.EMPTY;
				} else if (!moveItemStackTo(stack, machineSlots, hotbarStart, false)) {
					return ItemStack.EMPTY;
				}
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	private static class FilteredSlot extends Slot {
		FilteredSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return container.canPlaceItem(getContainerSlot(), stack);
		}
	}

	/** Holds one upgrade item (upgrades don't stack). */
	public static class UpgradeSlot extends Slot {
		public static final int MAX_PER_SLOT = 1;

		UpgradeSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof UpgradeItem;
		}

		@Override
		public int getMaxStackSize() {
			return MAX_PER_SLOT;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return MAX_PER_SLOT;
		}
	}

	private static class OutputSlot extends Slot {
		OutputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
