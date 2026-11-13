package dev.voltworks.client;

import dev.voltworks.menu.StorageTerminalMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Storage Terminal GUI: search box, 9x6 item grid, the player's inventory. */
public class StorageTerminalScreen extends StorageScreen<StorageTerminalMenu> {
	public StorageTerminalScreen(StorageTerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, StorageTerminalMenu.INVENTORY_Y + 82, 7, 17, 6);
		inventoryLabelY = StorageTerminalMenu.INVENTORY_Y - 11;
	}

	@Override
	protected int[] searchBox() {
		return new int[]{98, 4, 70};
	}
}
