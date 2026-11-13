package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.UpgradeItem;
import dev.voltworks.menu.MachineMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A powered machine with one or two independent "lanes". Each lane turns the item in its
 * input slot (2*lane) into a result in its output slot (2*lane + 1). Each working lane draws power.
 * After the lane slots come three upgrade slots.
 */
public abstract class ProcessingMachineBlockEntity extends PoweredContainerBlockEntity implements EnergyReceiver {
	public static final int INPUT = 0;
	public static final int OUTPUT = 1;
	private static final int[] ONE_LANE_IN = {0};
	private static final int[] ONE_LANE_OUT = {1};
	private static final int[] TWO_LANE_IN = {0, 2};
	private static final int[] TWO_LANE_OUT = {1, 3};

	private final int lanes;
	private final int energyPerTick;
	private final int ticksPerOperation;

	protected ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyPerTick, int ticksPerOperation) {
		this(type, pos, state, 1, 1_000, energyPerTick, ticksPerOperation);
	}

	protected ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int lanes, int capacity,
			int energyPerTick, int ticksPerOperation) {
		super(type, pos, state, lanes * 2, capacity, true);
		this.lanes = lanes;
		this.energyPerTick = energyPerTick;
		this.ticksPerOperation = ticksPerOperation;
		this.progressTotal = ticksPerOperation;
	}

	/** What one input item becomes, or empty if this machine can't process it. */
	protected abstract Optional<ItemStack> getResult(ServerLevel level, ItemStack input);

	/** Whether this item may go into an input slot. */
	protected boolean accepts(ItemStack stack) {
		return true;
	}

	private int getLaneProgress(int lane) {
		return lane == 0 ? progress : progress2;
	}

	private void setLaneProgress(int lane, int value) {
		if (lane == 0) progress = value;
		else progress2 = value;
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		int ticks = upgradedTicks(ticksPerOperation);
		int cost = upgradedEnergyPerTick(energyPerTick);
		progressTotal = ticks;
		boolean working = false;
		for (int lane = 0; lane < lanes; lane++) {
			int in = lane * 2, out = in + 1;
			ItemStack input = items.get(in);
			Optional<ItemStack> result = input.isEmpty() ? Optional.empty() : getResult(level, input);
			int laneProgress = getLaneProgress(lane);

			if (result.isPresent() && canOutput(out, result.get())) {
				if (energy >= cost) {
					energy -= cost;
					laneProgress++;
					working = true;
					if (laneProgress >= ticks) {
						laneProgress = 0;
						addOutput(out, result.get());
						input.shrink(1);
					}
					setChanged();
				}
			} else if (laneProgress != 0) {
				laneProgress = 0;
				setChanged();
			}
			setLaneProgress(lane, laneProgress);
		}
		setActive(level, pos, state, working);
		upgradeTick(level, pos, lanes == 1 ? ONE_LANE_OUT : TWO_LANE_OUT);
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
		return slot % 2 == 0 && !(stack.getItem() instanceof UpgradeItem) && accepts(stack);
	}

	@Override
	protected int[] inputSlots() {
		return lanes == 1 ? ONE_LANE_IN : TWO_LANE_IN;
	}

	@Override
	protected int[] outputSlots() {
		return lanes == 1 ? ONE_LANE_OUT : TWO_LANE_OUT;
	}

	@Override
	protected boolean allowInsert(int slot, ItemStack stack) {
		// Hoppers and pipes: spread items across lanes instead of always filling lane 0 first.
		return !(lanes == 2 && slot == 0 && !items.get(0).isEmpty() && items.get(2).isEmpty());
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(lanes == 1 ? MachineMenu.Kind.PROCESSING : MachineMenu.Kind.DUAL_PROCESSING, containerId, inventory, this, data);
	}
}
