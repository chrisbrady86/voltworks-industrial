package dev.voltworks.block.entity;

import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.storage.StorageNetwork;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Storage Interface: lets item pipes (and hoppers) reach the storage network it's connected to.
 * <ul>
 *   <li>A pipe arm set to Extract pulls items out of the drives. Put a Filter Pipe there to choose which,
 *       e.g. send only Raw Tin to a crusher.</li>
 *   <li>Any plain pipe arm delivers into storage, and machines empty into it automatically like into a chest,
 *       so a second pipe from the crusher brings the dust back.</li>
 * </ul>
 * To pipes it looks like an inventory: one slot per kind of stored item (showing up to a stack) plus one empty
 * slot that takes deliveries into storage.
 */
public class StorageInterfaceBlockEntity extends BlockEntity implements WorldlyContainer, StatusReporter {
	private List<StoredItem> view = List.of();
	private long viewTime = Long.MIN_VALUE;

	public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STORAGE_INTERFACE, pos, state);
	}

	public @Nullable StorageNetwork network() {
		return level == null ? null : StorageNetwork.find(level, worldPosition);
	}

	/** What's stored, refreshed at most once a tick (and after every change) so slot numbers stay stable. */
	private List<StoredItem> view() {
		if (level != null && level.getGameTime() != viewTime) {
			StorageNetwork net = network();
			view = net == null || !net.isOnline() ? List.of() : net.contents();
			viewTime = level.getGameTime();
		}
		return view;
	}

	private void invalidate() {
		viewTime = Long.MIN_VALUE;
	}

	/** How many of {@code stack} storage could take right now. */
	public int room(ItemStack stack) {
		StorageNetwork net = network();
		return net == null || !net.isOnline() ? 0 : net.insert(stack.copyWithCount(Math.max(stack.getMaxStackSize(), 4096)), true);
	}

	/** Store as much as fits; returns the rest. */
	public ItemStack insert(ItemStack stack) {
		StorageNetwork net = network();
		if (net == null || !net.isOnline()) return stack;
		ItemStack rest = net.insert(stack);
		invalidate();
		return rest;
	}

	private int insertSlot() {
		return view().size();
	}

	@Override
	public int getContainerSize() {
		return view().size() + 1;
	}

	@Override
	public boolean isEmpty() {
		return view().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		List<StoredItem> v = view();
		if (slot < 0 || slot >= v.size()) return ItemStack.EMPTY;
		StoredItem s = v.get(slot);
		return s.item().copyWithCount((int) Math.min(s.count(), s.item().getMaxStackSize()));
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		List<StoredItem> v = view();
		StorageNetwork net = network();
		if (net == null || slot < 0 || slot >= v.size() || amount <= 0) return ItemStack.EMPTY;
		ItemStack taken = net.extract(v.get(slot).item(), amount, false);
		invalidate();
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return removeItem(slot, getItem(slot).getCount());
	}

	/** Only the insert slot is ever filled from outside; whatever doesn't fit (it should) is dropped, never lost. */
	@Override
	public void setItem(int slot, ItemStack stack) {
		if (stack.isEmpty() || slot != insertSlot()) return;
		ItemStack rest = insert(stack);
		if (!rest.isEmpty() && level != null) {
			Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, rest);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == insertSlot() && room(stack) >= stack.getCount();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		int[] slots = new int[getContainerSize()];
		for (int i = 0; i < slots.length; i++) slots[i] = i;
		return slots;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot < insertSlot();
	}

	@Override
	public boolean stillValid(Player player) {
		return false; // no GUI
	}

	@Override
	public void clearContent() {
	}

	@Override
	public Component getStatus() {
		StorageNetwork net = network();
		if (net == null || !net.isOnline()) return Component.translatable("message.voltworks.interface.offline");
		return Component.translatable("message.voltworks.interface.online", String.format("%,d", net.used()), String.format("%,d", net.capacity()));
	}
}
