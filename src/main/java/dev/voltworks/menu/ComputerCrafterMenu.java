package dev.voltworks.menu;

import dev.voltworks.block.entity.ComputerCrafterBlockEntity;
import dev.voltworks.registry.ModBlocks;
import dev.voltworks.registry.ModMenus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Computer Crafter: a 3x3 crafting pattern of ghost items, the result it makes, Craft x1 / x8 / x64 buttons and the
 * network's searchable item list. Crafting takes the ingredients from storage and puts the result back in it.
 * Set the pattern by clicking items in the list (they go into the selected pattern slot) or clicking a pattern
 * slot while holding an item; click a pattern slot empty-handed to clear it.
 */
public class ComputerCrafterMenu extends NetworkMenu {
	public static final int PATTERN_X = 26, PATTERN_Y = 18, RESULT_X = 102, RESULT_Y = 36;
	public static final int INVENTORY_Y = 162;
	public static final int[] CRAFT_AMOUNTS = {1, 8, 64};
	private static final int RESULT_SLOT = 9;

	private final Container pattern;
	private final SimpleContainer result = new SimpleContainer(1);

	public ComputerCrafterMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Container pattern) {
		super(ModMenus.COMPUTER_CRAFTER, containerId, inventory, access, ModBlocks.COMPUTER_CRAFTER);
		this.pattern = pattern;
		checkContainerSize(pattern, 9);
		for (int i = 0; i < 9; i++) {
			addSlot(new FilterMenu.GhostSlot(pattern, i, PATTERN_X + (i % 3) * 18, PATTERN_Y + (i / 3) * 18));
		}
		addSlot(new FilterMenu.GhostSlot(result, 0, RESULT_X, RESULT_Y));
		addPlayerInventory(inventory, 8, INVENTORY_Y);
		updateResult();
	}

	public static ComputerCrafterMenu client(int containerId, Inventory inventory) {
		return new ComputerCrafterMenu(containerId, inventory, ContainerLevelAccess.NULL, new SimpleContainer(9));
	}

	@Override
	protected int playerSlotsStart() {
		return RESULT_SLOT + 1;
	}

	private void updateResult() {
		access.execute((level, pos) -> result.setItem(0, ComputerCrafterBlockEntity.preview((ServerLevel) level, pattern)));
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId >= 0 && slotId < 9) {
			ItemStack held = getCarried();
			pattern.setItem(slotId, held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1));
			updateResult();
			broadcastChanges();
			return;
		}
		if (slotId == RESULT_SLOT) return;
		super.clicked(slotId, button, input, player);
	}

	@Override
	public void handleAction(ServerPlayer sp, ItemStack item, int action, int slot) {
		if (action == ACTION_SET_PATTERN) {
			if (slot >= 0 && slot < 9) {
				pattern.setItem(slot, item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1));
				updateResult();
				broadcastChanges();
			}
			return;
		}
		super.handleAction(sp, item, action, slot);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id < 0 || id >= CRAFT_AMOUNTS.length) return false;
		access.execute((level, pos) -> ComputerCrafterBlockEntity.craft((ServerLevel) level, pos, pattern, CRAFT_AMOUNTS[id], player));
		forceSync();
		return true;
	}

	public Slot resultSlot() {
		return slots.get(RESULT_SLOT);
	}
}
