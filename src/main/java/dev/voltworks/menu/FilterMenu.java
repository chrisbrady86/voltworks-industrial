package dev.voltworks.menu;

import dev.voltworks.block.entity.FilterPipeBlockEntity;
import dev.voltworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Filter pipe GUI: 9 ghost slots (click with an item to set a copy, click empty-handed to clear, shift-click an
 * inventory item to add it) and an Allow / Deny button (menu button 0).
 */
public class FilterMenu extends AbstractContainerMenu {
	public static final int SLOT_X = 8, SLOT_Y = 26;
	public static final int BUTTON_TOGGLE_MODE = 0;

	private final Container filter;
	private final ContainerData data;

	public FilterMenu(int containerId, Inventory inventory, Container filter, ContainerData data) {
		super(ModMenus.FILTER, containerId);
		this.filter = filter;
		this.data = data;
		checkContainerSize(filter, FilterPipeBlockEntity.SLOTS);
		checkContainerDataCount(data, 1);
		for (int i = 0; i < FilterPipeBlockEntity.SLOTS; i++) {
			addSlot(new GhostSlot(filter, i, SLOT_X + i * 18, SLOT_Y));
		}
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	public static FilterMenu client(int containerId, Inventory inventory) {
		return new FilterMenu(containerId, inventory, new SimpleContainer(FilterPipeBlockEntity.SLOTS), new SimpleContainerData(1));
	}

	public boolean isDeny() {
		return data.get(0) != 0;
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId >= 0 && slotId < FilterPipeBlockEntity.SLOTS) {
			ItemStack carried = getCarried();
			filter.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
			broadcastChanges();
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == BUTTON_TOGGLE_MODE) {
			data.set(0, isDeny() ? 0 : 1);
			return true;
		}
		return false;
	}

	/** Shift-clicking an inventory item copies it into the first free filter slot (the item stays put). */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < FilterPipeBlockEntity.SLOTS) {
			filter.setItem(index, ItemStack.EMPTY);
			return ItemStack.EMPTY;
		}
		ItemStack stack = slots.get(index).getItem();
		if (stack.isEmpty()) return ItemStack.EMPTY;
		for (int i = 0; i < FilterPipeBlockEntity.SLOTS; i++) {
			if (ItemStack.isSameItem(filter.getItem(i), stack)) return ItemStack.EMPTY;
		}
		for (int i = 0; i < FilterPipeBlockEntity.SLOTS; i++) {
			if (filter.getItem(i).isEmpty()) {
				filter.setItem(i, stack.copyWithCount(1));
				break;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return filter.stillValid(player);
	}

	/** Shows a filter entry; it can't be taken out or filled the normal way (see {@link #clicked}). */
	public static class GhostSlot extends Slot {
		GhostSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}
}
