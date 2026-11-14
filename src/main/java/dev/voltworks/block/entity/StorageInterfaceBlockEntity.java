package dev.voltworks.block.entity;

import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.storage.StorageNetwork;
import dev.voltworks.storage.StorageNetwork.StoredItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Storage Interface: lets item pipes (and hoppers) reach the storage network it is connected to.
 * <ul>
 *   <li>A pipe arm set to Extract pulls items out of the drives. Put a Filter Pipe there to choose which,
 *       e.g. send only Raw Tin to a crusher.</li>
 *   <li>Any plain pipe arm delivers into storage, and machines empty into it automatically like into a chest,
 *       so a second pipe from the crusher brings the dust back.</li>
 * </ul>
 * To pipes it looks like an inventory: one slot per kind of stored item (showing up to a stack) plus one slot
 * that takes deliveries into storage.
 * <p>
 * It behaves like a real inventory for everything that treats it as one (vanilla hoppers, other mods' pipes through
 * Fabric's transfer API), which read a slot's stack, change its count and/or write a stack back:
 * <ul>
 *   <li>Each item slot hands out one "live" stack. Whatever is done to it (count changed in place, or a different
 *       stack written with {@link #setItem}) is applied to the drives as the difference from what was shown,
 *       so items are never duplicated or lost.</li>
 *   <li>The delivery slot is a real one-stack buffer that is moved into the drives once a tick, so an insert that is
 *       taken back (a simulated or aborted transfer) really is taken back.</li>
 * </ul>
 */
public class StorageInterfaceBlockEntity extends BlockEntity implements WorldlyContainer, StatusReporter, VoltTicking {
	/** One entry per item slot: the kind of item, the stack handed out for it, and the count that stack stood for. */
	private final List<ItemStack> keys = new ArrayList<>();
	private final List<ItemStack> live = new ArrayList<>();
	private final List<Integer> expected = new ArrayList<>();
	/** Slots are rebuilt at most once a tick, so slot numbers stay stable while something works through them. */
	private long viewTime = Long.MIN_VALUE;
	/** {@link StorageNetwork#changes()} when the shown counts were last read. */
	private long stamp;
	/** The delivery slot: waits here until the next tick, then goes into the drives. */
	private ItemStack buffer = ItemStack.EMPTY;

	public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.STORAGE_INTERFACE, pos, state);
	}

	public @Nullable StorageNetwork network() {
		return level == null ? null : StorageNetwork.find(level, worldPosition);
	}

	// ---------------- keeping the slots and the drives in step ----------------

	/** Applies any change made to a handed-out stack, then makes sure the slots show the drives as they are now. */
	private void sync() {
		if (level == null || level.isClientSide()) return;
		reconcile();
		long time = level.getGameTime();
		if (time != viewTime) {
			StorageNetwork net = network();
			keys.clear();
			live.clear();
			expected.clear();
			if (net != null && net.isOnline()) {
				for (StoredItem s : net.contents()) {
					ItemStack key = s.item().copyWithCount(1);
					int count = (int) Math.min(s.count(), key.getMaxStackSize());
					keys.add(key);
					live.add(key.copyWithCount(count));
					expected.add(count);
				}
			}
			viewTime = time;
			stamp = StorageNetwork.changes();
		} else if (stamp != StorageNetwork.changes()) {
			// Something was stored or taken since (here or elsewhere): same slots, fresh counts
			StorageNetwork net = network();
			boolean online = net != null && net.isOnline();
			for (int i = 0; i < keys.size(); i++) {
				ItemStack key = keys.get(i);
				int count = online ? (int) Math.min(net.count(key), key.getMaxStackSize()) : 0;
				live.set(i, key.copyWithCount(count)); // a new stack: someone may still hold the old one
				expected.set(i, count);
			}
			stamp = StorageNetwork.changes();
		}
	}

	/** A handed-out stack whose count no longer matches: take the difference out of, or put it into, the drives. */
	private void reconcile() {
		StorageNetwork net = null;
		for (int i = 0; i < live.size(); i++) {
			ItemStack shown = live.get(i);
			int now = shown.isEmpty() ? 0 : shown.getCount();
			if (now == expected.get(i)) continue;
			if (net == null) net = network();
			if (net == null) return;
			apply(net, keys.get(i), now - expected.get(i));
			expected.set(i, now);
		}
	}

	/** {@code delta} more (positive) or fewer (negative) of {@code key} in the drives; what cannot be stored drops. */
	private void apply(StorageNetwork net, ItemStack key, int delta) {
		if (delta < 0) {
			int need = -delta;
			while (need > 0) {
				ItemStack got = net.extract(key, need, false);
				if (got.isEmpty()) break;
				need -= got.getCount();
			}
		} else if (delta > 0) {
			drop(net.insert(key.copyWithCount(delta)));
		}
	}

	private void drop(ItemStack stack) {
		if (!stack.isEmpty() && level != null) {
			Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, stack);
		}
	}

	/** Once a tick: settle handed-out stacks and move the delivery slot into the drives. */
	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		reconcile();
		if (!buffer.isEmpty()) {
			StorageNetwork net = network();
			if (net != null && net.isOnline()) {
				ItemStack rest = net.insert(buffer);
				if (rest.getCount() != buffer.getCount()) {
					buffer = rest;
					setChanged();
				}
			}
		}
	}

	// ---------------- used by Voltworks pipes directly ----------------

	/** How many of {@code stack} storage could take right now. */
	public int room(ItemStack stack) {
		StorageNetwork net = network();
		return net == null || !net.isOnline() ? 0 : net.insert(stack.copyWithCount(Math.max(stack.getMaxStackSize(), 4096)), true);
	}

	/** Store as much as fits; returns the rest. */
	public ItemStack insert(ItemStack stack) {
		StorageNetwork net = network();
		if (net == null || !net.isOnline()) return stack;
		return net.insert(stack);
	}

	// ---------------- the inventory other things see ----------------

	private int insertSlot() {
		return keys.size();
	}

	@Override
	public int getContainerSize() {
		sync();
		return keys.size() + 1;
	}

	@Override
	public boolean isEmpty() {
		sync();
		return keys.isEmpty() && buffer.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		sync();
		if (slot == insertSlot()) return buffer;
		return slot < 0 || slot >= live.size() ? ItemStack.EMPTY : live.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		sync();
		if (slot < 0 || slot >= keys.size() || amount <= 0) return ItemStack.EMPTY;
		StorageNetwork net = network();
		if (net == null) return ItemStack.EMPTY;
		ItemStack taken = net.extract(keys.get(slot), Math.min(amount, expected.get(slot)), false);
		int left = expected.get(slot) - taken.getCount();
		live.get(slot).setCount(left);
		expected.set(slot, left);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		sync();
		return slot < 0 || slot >= keys.size() ? ItemStack.EMPTY : removeItem(slot, expected.get(slot));
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		sync(); // a live stack changed in place and then written back is settled here
		if (slot == insertSlot()) {
			buffer = stack;
			setChanged();
			return;
		}
		if (slot < 0 || slot >= keys.size() || stack == live.get(slot)) return;
		StorageNetwork net = network();
		if (net == null) return;
		ItemStack key = keys.get(slot);
		boolean same = ItemStack.isSameItemSameComponents(stack, key);
		int target = same ? stack.getCount() : 0;
		apply(net, key, target - expected.get(slot));
		live.set(slot, key.copyWithCount(target)); // never touch the old stack: the caller may be holding it
		expected.set(slot, target);
		if (!same && !stack.isEmpty()) drop(net.insert(stack.copy())); // some other item written over this slot
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		sync();
		if (slot != insertSlot()) return false;
		if (!buffer.isEmpty() && !ItemStack.isSameItemSameComponents(buffer, stack)) return false;
		return room(stack) >= buffer.getCount() + stack.getCount();
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

	/** Breaking it: settle what was handed out and drop a delivery that has not reached the drives yet. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		reconcile();
		drop(buffer);
		buffer = ItemStack.EMPTY;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!buffer.isEmpty()) output.store("buffer", ItemStack.CODEC, buffer);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		buffer = input.read("buffer", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	public Component getStatus() {
		StorageNetwork net = network();
		if (net == null || !net.isOnline()) return Component.translatable("message.voltworks.interface.offline");
		return Component.translatable("message.voltworks.interface.online", String.format("%,d", net.used()), String.format("%,d", net.capacity()));
	}
}
