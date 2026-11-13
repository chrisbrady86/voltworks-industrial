package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.UpgradeItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.recipe.AlloyRecipes;
import dev.voltworks.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Combines two inputs into an alloy (e.g. iron + 2 coal = steel). 4 J/tick, 100 ticks per alloy. */
public class AlloySmelterBlockEntity extends PoweredContainerBlockEntity implements EnergyReceiver {
	public static final int OUTPUT = 2;
	public static final int ENERGY_PER_TICK = 4;
	public static final int TICKS = 100;
	private static final int[] INPUTS = {0, 1};
	private static final int[] OUTPUTS = {OUTPUT};

	public AlloySmelterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ALLOY_SMELTER, pos, state, 3, 2_000, true);
		this.progressTotal = TICKS;
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		int ticks = upgradedTicks(TICKS);
		int cost = upgradedEnergyPerTick(ENERGY_PER_TICK);
		progressTotal = ticks;
		Optional<AlloyRecipes.Match> match = AlloyRecipes.find(items.get(0), items.get(1));
		boolean working = false;
		if (match.isPresent() && canOutput(OUTPUT, match.get().recipe().result())) {
			if (energy >= cost) {
				energy -= cost;
				working = true;
				if (++progress >= ticks) {
					progress = 0;
					addOutput(OUTPUT, match.get().recipe().result());
					items.get(0).shrink(match.get().countForSlot(0));
					items.get(1).shrink(match.get().countForSlot(1));
				}
				setChanged();
			}
		} else if (progress != 0) {
			progress = 0;
			setChanged();
		}
		setActive(level, pos, state, working);
		upgradeTick(level, pos, OUTPUT);
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		return acceptEnergy(maxAmount, simulate);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (isUpgradeSlot(slot)) {
			return stack.getItem() instanceof UpgradeItem;
		}
		return slot != OUTPUT && AlloyRecipes.isIngredient(stack);
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return direction == Direction.DOWN ? OUTPUTS : INPUTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		if (!canPlaceItem(slot, stack)) {
			return false;
		}
		// Hoppers: keep the two ingredients in separate slots.
		ItemStack other = items.get(1 - slot);
		ItemStack current = items.get(slot);
		if (current.isEmpty()) {
			return other.isEmpty() || !ItemStack.isSameItemSameComponents(other, stack);
		}
		return ItemStack.isSameItemSameComponents(current, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot == OUTPUT;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.alloy_smelter");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(MachineMenu.Kind.ALLOY, containerId, inventory, this, data);
	}
}
