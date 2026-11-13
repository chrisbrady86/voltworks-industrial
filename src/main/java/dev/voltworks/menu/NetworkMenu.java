package dev.voltworks.menu;

import dev.voltworks.network.StorageViewPayload;
import dev.voltworks.storage.StorageNetwork;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Base for GUIs that show a storage network (Storage Terminal, Computer Crafter). The server sends the network's
 * contents to the client every {@value #SYNC_INTERVAL} ticks when they change; clicks in the list come back as
 * {@link dev.voltworks.network.StorageActionPayload}s handled by {@link #handleAction}.
 */
public abstract class NetworkMenu extends AbstractContainerMenu {
	public static final int ACTION_TAKE = 0;          // left click: a stack (or top up the held one)
	public static final int ACTION_TAKE_HALF = 1;     // right click: half a stack
	public static final int ACTION_TAKE_TO_INV = 2;   // shift click: a stack straight into the inventory
	public static final int ACTION_INSERT_HELD = 3;   // left click holding something: store all of it
	public static final int ACTION_INSERT_ONE = 4;    // right click holding something: store one
	public static final int ACTION_SET_PATTERN = 5;   // crafter: put a ghost copy in pattern slot {@code slot}
	private static final int SYNC_INTERVAL = 10;

	protected final ContainerLevelAccess access;
	private final Block block;
	protected final Player player;
	private int syncTimer = SYNC_INTERVAL;
	private int lastHash;

	// Client-side view, filled by StorageViewPayload
	public List<StoredItem> view = List.of();
	public boolean online;
	public long used, capacity;

	protected NetworkMenu(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access, Block block) {
		super(type, containerId);
		this.access = access;
		this.block = block;
		this.player = inventory.player;
	}

	protected void addPlayerInventory(Inventory inventory, int x, int y) {
		addStandardInventorySlots(inventory, x, y);
	}

	/** Index of the first player inventory slot (everything before it belongs to the menu). */
	protected abstract int playerSlotsStart();

	public Optional<StorageNetwork> network() {
		return access.evaluate(StorageNetwork::find);
	}

	public void forceSync() {
		syncTimer = SYNC_INTERVAL;
		lastHash = 0;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (!(player instanceof ServerPlayer sp) || ++syncTimer < SYNC_INTERVAL) return;
		syncTimer = 0;
		StorageNetwork net = network().orElse(null);
		List<StoredItem> items = net == null ? List.of() : net.contents();
		boolean on = net != null && net.isOnline();
		long u = net == null ? 0 : net.used(), cap = net == null ? 0 : net.capacity();
		int hash = (int) (31 * u + cap) + (on ? 1 : 0);
		for (StoredItem s : items) hash = hash * 31 + ItemStack.hashItemAndComponents(s.item()) + Long.hashCode(s.count());
		if (hash == lastHash) return;
		lastHash = hash;
		ServerPlayNetworking.send(sp, new StorageViewPayload(containerId, on, u, cap, items));
	}

	/** Server: the player clicked {@code item} (or an empty cell) in the storage list. */
	public void handleAction(ServerPlayer sp, ItemStack item, int action, int slot) {
		StorageNetwork net = network().orElse(null);
		if (net == null || !net.isOnline()) return;
		ItemStack held = getCarried();
		switch (action) {
			case ACTION_TAKE, ACTION_TAKE_HALF -> {
				if (item.isEmpty()) return;
				int max = item.getMaxStackSize();
				if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, item)) return;
				int want = action == ACTION_TAKE_HALF ? Math.max(1, (int) Math.min(max, net.count(item)) / 2) : max;
				want = Math.min(want, max - held.getCount());
				ItemStack got = net.extract(item, want, false);
				if (got.isEmpty()) return;
				if (held.isEmpty()) setCarried(got);
				else held.grow(got.getCount());
			}
			case ACTION_TAKE_TO_INV -> {
				if (item.isEmpty()) return;
				ItemStack got = net.extract(item, item.getMaxStackSize(), false);
				if (got.isEmpty()) return;
				sp.getInventory().add(got);
				if (!got.isEmpty()) net.insert(got); // inventory full: put the rest back
			}
			case ACTION_INSERT_HELD -> {
				if (held.isEmpty()) return;
				setCarried(net.insert(held));
			}
			case ACTION_INSERT_ONE -> {
				if (held.isEmpty()) return;
				if (net.insert(held.copyWithCount(1), false) == 1) held.shrink(1);
			}
			default -> {
				return;
			}
		}
		forceSync();
		broadcastChanges();
	}

	/** Shift-clicking an item in your inventory stores it in the network. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < playerSlotsStart()) return ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		StorageNetwork net = network().orElse(null);
		if (net == null || !net.isOnline()) return ItemStack.EMPTY;
		ItemStack rest = net.insert(slot.getItem());
		slot.set(rest);
		forceSync();
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, block);
	}
}
