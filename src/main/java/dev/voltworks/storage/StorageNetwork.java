package dev.voltworks.storage;

import dev.voltworks.block.DataConnectable;
import dev.voltworks.block.entity.DriveRackBlockEntity;
import dev.voltworks.item.HardDriveItem;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A computer storage network: every Data Cable and computer block (Drive Rack, Storage Terminal, Computer Crafter)
 * joined to the starting block. Its storage is the hard drives in its powered Drive Racks, seen as one big inventory.
 * Built fresh for each operation (it's cheap), so it always reflects the world as it is.
 */
public final class StorageNetwork {
	private static final int MAX_BLOCKS = 1024;
	/** Goes up every time any drive's contents change (server thread only). */
	private static long changes;

	/** A number that changes whenever something is stored in or taken out of any network; lets views notice they are stale. */
	public static long changes() {
		return changes;
	}

	/** One kind of item and how many the whole network holds. */
	public record StoredItem(ItemStack item, long count) {
		public static final StreamCodec<RegistryFriendlyByteBuf, StoredItem> STREAM_CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, StoredItem::item, ByteBufCodecs.VAR_LONG, StoredItem::count, StoredItem::new);
	}

	private record Drive(DriveRackBlockEntity rack, int slot) {
		ItemStack stack() {
			return rack.getItem(slot);
		}

		HardDriveItem item() {
			return (HardDriveItem) stack().getItem();
		}

		DriveContents contents() {
			return HardDriveItem.contents(stack());
		}

		void set(DriveContents contents) {
			HardDriveItem.setContents(stack(), contents);
			rack.setChanged();
			changes++;
		}
	}

	/** Stacks are the same key when they are the same item with the same components (counts ignored). */
	private static final Hash.Strategy<ItemStack> SAME_ITEM = new Hash.Strategy<>() {
		@Override
		public int hashCode(ItemStack stack) {
			return stack == null ? 0 : ItemStack.hashItemAndComponents(stack);
		}

		@Override
		public boolean equals(ItemStack a, ItemStack b) {
			return a == b || (a != null && b != null && ItemStack.isSameItemSameComponents(a, b));
		}
	};

	private final List<DriveRackBlockEntity> racks;
	private final List<Drive> drives = new ArrayList<>();

	private StorageNetwork(List<DriveRackBlockEntity> racks) {
		this.racks = racks;
		for (DriveRackBlockEntity rack : racks) {
			for (int i = 0; i < rack.getContainerSize(); i++) {
				if (rack.getItem(i).getItem() instanceof HardDriveItem) drives.add(new Drive(rack, i));
			}
		}
	}

	/** The network that the computer block (or data cable) at {@code start} belongs to. */
	public static StorageNetwork find(Level level, BlockPos start) {
		List<DriveRackBlockEntity> racks = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		int blocks = 0; // cables and computer blocks only: the blocks around them don't count towards the limit
		while (!queue.isEmpty() && blocks < MAX_BLOCKS) {
			BlockPos p = queue.poll();
			if (!(level.getBlockState(p).getBlock() instanceof DataConnectable)) continue;
			blocks++;
			if (level.getBlockEntity(p) instanceof DriveRackBlockEntity rack && rack.isOnline()) racks.add(rack);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (level.isLoaded(n) && seen.add(n)) queue.add(n);
			}
		}
		return new StorageNetwork(racks);
	}

	/** Online means at least one powered Drive Rack is connected. */
	public boolean isOnline() {
		return !racks.isEmpty();
	}

	public int driveCount() {
		return drives.size();
	}

	public long capacity() {
		long n = 0;
		for (Drive d : drives) n += d.item().size.capacity;
		return n;
	}

	public long used() {
		long n = 0;
		for (Drive d : drives) n += d.contents().total();
		return n;
	}

	/** Everything stored, merged across drives, most plentiful first. */
	public List<StoredItem> contents() {
		Object2LongLinkedOpenCustomHashMap<ItemStack> totals = new Object2LongLinkedOpenCustomHashMap<>(SAME_ITEM);
		for (Drive d : drives) {
			for (DriveContents.Entry e : d.contents().entries()) totals.addTo(e.item(), e.count());
		}
		List<StoredItem> list = new ArrayList<>(totals.size());
		totals.object2LongEntrySet().forEach(e -> list.add(new StoredItem(e.getKey(), e.getLongValue())));
		list.sort(Comparator.comparingLong(StoredItem::count).reversed()
				.thenComparing(s -> s.item().getHoverName().getString()));
		return list;
	}

	public long count(ItemStack item) {
		long n = 0;
		for (Drive d : drives) n += d.contents().countOf(item);
		return n;
	}

	/** Store as much of {@code stack} as fits (drives already holding it first). Returns how many were stored. */
	public int insert(ItemStack stack, boolean simulate) {
		// No drives inside drives, directly or packed in a shulker box or bundle: contents could nest without limit
		if (stack.isEmpty() || stack.getItem() instanceof HardDriveItem || HardDriveItem.holdsLoadedDrive(stack)) return 0;
		int left = stack.getCount();
		for (int pass = 0; pass < 2 && left > 0; pass++) {
			for (Drive d : drives) {
				boolean holds = d.contents().countOf(stack) > 0;
				if ((pass == 0) != holds) continue;
				int n = Math.min(left, d.item().room(d.stack(), stack));
				if (n <= 0) continue;
				if (!simulate) d.set(d.contents().add(stack, n));
				left -= n;
				if (left == 0) break;
			}
		}
		return stack.getCount() - left;
	}

	/** Store what fits and return the rest. */
	public ItemStack insert(ItemStack stack) {
		int stored = insert(stack, false);
		return stack.copyWithCount(stack.getCount() - stored);
	}

	/** Take up to {@code amount} of {@code item} out (its own max stack size at most). */
	public ItemStack extract(ItemStack item, int amount, boolean simulate) {
		amount = Math.min(amount, item.getMaxStackSize());
		int taken = 0;
		for (Drive d : drives) {
			if (taken >= amount) break;
			int n = Math.min(amount - taken, d.contents().countOf(item));
			if (n <= 0) continue;
			if (!simulate) d.set(d.contents().add(item, -n));
			taken += n;
		}
		return taken == 0 ? ItemStack.EMPTY : item.copyWithCount(taken);
	}
}
