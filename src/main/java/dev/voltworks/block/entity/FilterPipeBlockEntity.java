package dev.voltworks.block.entity;

import dev.voltworks.menu.FilterMenu;
import dev.voltworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Filter pipe: a basic-speed pipe with 9 filter slots and an Allow / Deny mode. Items only pass through (or leave
 * through it, when it extracts) if they match: in Allow mode the item must be in the filter, in Deny mode it must not.
 * An empty filter lets everything through. Filter slots hold "ghost" copies: setting one doesn't use up the item.
 */
public class FilterPipeBlockEntity extends ItemPipeBlockEntity implements MenuProvider {
	public static final int SLOTS = 9;

	private final SimpleContainer filter = new SimpleContainer(SLOTS) {
		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public boolean stillValid(Player player) {
			return Container.stillValidBlockEntity(FilterPipeBlockEntity.this, player);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			FilterPipeBlockEntity.this.setChanged();
		}
	};
	private boolean deny;

	/** Synced to the open GUI: 0 = Allow, 1 = Deny. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return deny ? 1 : 0;
		}

		@Override
		public void set(int index, int value) {
			setDeny(value != 0);
		}

		@Override
		public int getCount() {
			return 1;
		}
	};

	public FilterPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FILTER_PIPE, pos, state);
	}

	public Container getFilter() {
		return filter;
	}

	public boolean isDeny() {
		return deny;
	}

	public void setDeny(boolean deny) {
		this.deny = deny;
		setChanged();
	}

	/** Put a ghost copy of {@code stack} in a filter slot (empty clears it). */
	public void setFilter(int slot, ItemStack stack) {
		filter.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
	}

	@Override
	public boolean allows(ItemStack stack) {
		boolean empty = true, listed = false;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack f = filter.getItem(i);
			if (f.isEmpty()) continue;
			empty = false;
			if (ItemStack.isSameItem(f, stack)) listed = true;
		}
		return empty || listed != deny;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.voltworks.filter_pipe");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new FilterMenu(containerId, inventory, filter, data);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ValueOutput.TypedOutputList<ItemStack> list = output.list("filter", ItemStack.OPTIONAL_CODEC);
		for (int i = 0; i < SLOTS; i++) list.add(filter.getItem(i));
		output.putBoolean("deny", deny);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		int i = 0;
		for (ItemStack s : input.listOrEmpty("filter", ItemStack.OPTIONAL_CODEC)) {
			if (i < SLOTS) filter.setItem(i++, s);
		}
		while (i < SLOTS) filter.setItem(i++, ItemStack.EMPTY);
		deny = input.getBooleanOr("deny", false);
	}
}
