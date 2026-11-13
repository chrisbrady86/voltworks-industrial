package dev.voltworks.menu;

import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Storage Terminal: a searchable view of everything in the network, plus the player's inventory. */
public class StorageTerminalMenu extends NetworkMenu {
	public static final int INVENTORY_Y = 150;

	public StorageTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.STORAGE_TERMINAL, containerId, inventory, access, ModBlocks.STORAGE_TERMINAL);
		addPlayerInventory(inventory, 8, INVENTORY_Y);
	}

	public static StorageTerminalMenu client(int containerId, Inventory inventory) {
		return new StorageTerminalMenu(containerId, inventory, ContainerLevelAccess.NULL);
	}

	@Override
	protected int playerSlotsStart() {
		return 0;
	}
}
