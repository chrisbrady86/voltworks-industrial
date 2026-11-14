package dev.voltworks.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a hard drive holds: a list of item types (a one-item "prototype" stack carrying the item and its components)
 * with a count each. Immutable: changes produce a new value, which is set back on the drive item as a data component.
 */
public record DriveContents(List<Entry> entries) {
	public static final DriveContents EMPTY = new DriveContents(List.of());

	public record Entry(ItemStack item, int count) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
				Codec.INT.fieldOf("count").forGetter(Entry::count)
		).apply(i, Entry::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_INT, Entry::count, Entry::new);

		/** By value (ItemStack itself compares by identity), as data components must be. */
		@Override
		public boolean equals(Object o) {
			return o instanceof Entry e && count == e.count && ItemStack.matches(item, e.item);
		}

		@Override
		public int hashCode() {
			return 31 * ItemStack.hashItemAndComponents(item) + count;
		}
	}

	public static final Codec<DriveContents> CODEC = Entry.CODEC.listOf().xmap(DriveContents::new, DriveContents::entries);
	public static final StreamCodec<RegistryFriendlyByteBuf, DriveContents> STREAM_CODEC =
			Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DriveContents::new, DriveContents::entries);

	public int total() {
		int n = 0;
		for (Entry e : entries) n += e.count();
		return n;
	}

	public int types() {
		return entries.size();
	}

	public int countOf(ItemStack stack) {
		for (Entry e : entries) if (ItemStack.isSameItemSameComponents(e.item(), stack)) return e.count();
		return 0;
	}

	/** A copy with {@code delta} more (or fewer) of {@code stack}'s item; entries that reach zero are removed. */
	public DriveContents add(ItemStack stack, int delta) {
		List<Entry> list = new ArrayList<>(entries);
		for (int i = 0; i < list.size(); i++) {
			Entry e = list.get(i);
			if (ItemStack.isSameItemSameComponents(e.item(), stack)) {
				int n = e.count() + delta;
				if (n <= 0) list.remove(i);
				else list.set(i, new Entry(e.item(), n));
				return new DriveContents(List.copyOf(list));
			}
		}
		if (delta > 0) list.add(new Entry(stack.copyWithCount(1), delta));
		return new DriveContents(List.copyOf(list));
	}
}
