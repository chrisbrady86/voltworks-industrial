package dev.voltworks.block.entity;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.energy.EnergyNetwork;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.UpgradeItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Base for machines with an inventory, an energy buffer and progress values shown in a GUI. */
public abstract class PoweredContainerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, VoltTicking, EnergyGauge {
	public static final int DATA_COUNT = 5;

	protected NonNullList<ItemStack> items;
	protected final int capacity;
	protected int energy;
	/** Lane 0 progress (generators: remaining burn time). */
	protected int progress;
	/** Lane 1 progress, for two-lane machines. */
	protected int progress2;
	protected int progressTotal;
	private List<EnergyReceiver> cachedReceivers = List.of();
	private int receiverRefresh;

	/** Synced to the open GUI: energy, capacity, progress, progressTotal, progress2. */
	protected final ContainerData data = new ContainerData() {
		@Override
		public int get(int id) {
			return switch (id) {
				case 0 -> energy;
				case 1 -> getCapacity();
				case 2 -> progress;
				case 3 -> progressTotal;
				case 4 -> progress2;
				default -> 0;
			};
		}

		@Override
		public void set(int id, int value) {
			if (id == 0) energy = value;
			else if (id == 2) progress = value;
			else if (id == 3) progressTotal = value;
			else if (id == 4) progress2 = value;
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	protected PoweredContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, int capacity) {
		this(type, pos, state, slots, capacity, false);
	}

	/** @param upgradable adds {@link #UPGRADE_SLOTS} upgrade slots after the machine's own slots. */
	protected PoweredContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, int capacity, boolean upgradable) {
		super(type, pos, state);
		this.upgradeSlots = upgradable ? UPGRADE_SLOTS : 0;
		this.items = NonNullList.withSize(slots + upgradeSlots, ItemStack.EMPTY);
		this.capacity = capacity; // keep <= 32767: GUI sync uses shorts
	}

	@Override
	public int getStoredEnergy() {
		return energy;
	}

	@Override
	public int getMaxEnergy() {
		return getCapacity();
	}

	public int getEnergy() {
		return energy;
	}

	// ---------------- side configuration ----------------
	/** Item input/output per side; set with the wrench. Default: every side both accepts and gives items (as before). */
	protected final SideConfig sides = new SideConfig(this::defaultSideMode);

	protected SideConfig.Mode defaultSideMode(SideConfig.Side side) {
		return SideConfig.Mode.BOTH;
	}

	/** Slots that hoppers and pipes may insert into (when the side allows input). */
	protected abstract int[] inputSlots();

	/** Slots that hoppers and pipes may take from (when the side allows output). */
	protected abstract int[] outputSlots();

	/** Extra per-machine insertion rule on top of the side config, e.g. spreading items across lanes. */
	protected boolean allowInsert(int slot, ItemStack stack) {
		return true;
	}

	/** Extra per-machine extraction rule, e.g. only take fully charged tools. */
	protected boolean allowExtract(int slot, ItemStack stack) {
		return true;
	}

	public SideConfig.Side sideOf(Direction dir) {
		BlockState state = getBlockState();
		Direction facing = state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
		return SideConfig.Side.of(facing, dir);
	}

	public SideConfig.Mode getSideMode(Direction dir) {
		return sides.get(sideOf(dir));
	}

	public void setSideMode(Direction dir, SideConfig.Mode mode) {
		sides.set(sideOf(dir), mode);
		setChanged();
	}

	/** The side setup as it is saved ("side_config"), or -1 while it is still this machine's default. */
	public int customSideConfig() {
		int packed = sides.pack();
		return packed == new SideConfig(this::defaultSideMode).pack() ? -1 : packed;
	}

	/** Wrench: advance this side to the next mode (none, input, output, both). */
	public SideConfig.Mode cycleSideMode(Direction dir) {
		SideConfig.Mode next = getSideMode(dir).next();
		setSideMode(dir, next);
		return next;
	}

	@Override
	public int[] getSlotsForFace(Direction dir) {
		SideConfig.Mode mode = getSideMode(dir);
		int[] in = mode.input ? inputSlots() : new int[0];
		int[] out = mode.output ? outputSlots() : new int[0];
		return java.util.stream.IntStream.concat(java.util.Arrays.stream(in), java.util.Arrays.stream(out)).distinct().toArray();
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @org.jspecify.annotations.Nullable Direction dir) {
		if (dir != null && !getSideMode(dir).input) return false;
		return contains(inputSlots(), slot) && canPlaceItem(slot, stack) && allowInsert(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return getSideMode(dir).output && contains(outputSlots(), slot) && allowExtract(slot, stack);
	}

	private static boolean contains(int[] slots, int slot) {
		for (int s : slots) if (s == slot) return true;
		return false;
	}

	// ---------------- upgrades ----------------
	public static final int UPGRADE_SLOTS = 3;
	public static final int MAX_SPEED_UPGRADES = 8;
	public static final int CAPACITY_PER_UPGRADE = 4_000;
	public static final int MAX_CAPACITY = 32_000;
	private static final int EJECT_INTERVAL = 10;
	private final int upgradeSlots;
	private int ejectTimer;

	public boolean isUpgradeSlot(int slot) {
		return slot >= items.size() - upgradeSlots;
	}

	public int countUpgrades(UpgradeItem.Type type) {
		int count = 0;
		for (int i = items.size() - upgradeSlots; i < items.size(); i++) {
			if (items.get(i).getItem() instanceof UpgradeItem u && u.type == type) {
				count += items.get(i).getCount();
			}
		}
		return count;
	}

	/** Base capacity plus Capacity Upgrades. */
	public int getCapacity() {
		return Math.min(MAX_CAPACITY, capacity + countUpgrades(UpgradeItem.Type.CAPACITY) * CAPACITY_PER_UPGRADE);
	}

	/** Speed Upgrades: each makes an operation 30% shorter... */
	protected int upgradedTicks(int baseTicks) {
		int n = Math.min(MAX_SPEED_UPGRADES, countUpgrades(UpgradeItem.Type.SPEED));
		return Math.max(1, (int) Math.round(baseTicks * Math.pow(0.7, n)));
	}

	/** ...and draw 60% more power per tick. */
	protected int upgradedEnergyPerTick(int baseEnergy) {
		int n = Math.min(MAX_SPEED_UPGRADES, countUpgrades(UpgradeItem.Type.SPEED));
		return (int) Math.ceil(baseEnergy * Math.pow(1.6, n));
	}

	/** Call at the end of serverTick: clamps energy after a Capacity Upgrade is removed, and runs the Ejector. */
	protected void upgradeTick(ServerLevel level, BlockPos pos, int... outputSlots) {
		if (energy > getCapacity()) {
			energy = getCapacity();
		}
		if (++ejectTimer < EJECT_INTERVAL || countUpgrades(UpgradeItem.Type.EJECTOR) == 0) {
			return;
		}
		ejectTimer = 0;
		for (Direction dir : Direction.values()) {
			if (!getSideMode(dir).output) {
				continue; // the Ejector only pushes out of sides configured for output
			}
			Container target = HopperBlockEntity.getContainerAt(level, pos.relative(dir));
			if (target == null || target == this) {
				continue;
			}
			for (int slot : outputSlots) {
				ItemStack stack = items.get(slot);
				if (stack.isEmpty()) {
					continue;
				}
				ItemStack rest = HopperBlockEntity.addItem(this, target, stack.copy(), dir.getOpposite());
				if (rest.getCount() != stack.getCount()) {
					items.set(slot, rest);
					setChanged();
				}
			}
		}
	}

	/** Push up to {@code max} stored energy into the cable network. */
	protected void pushEnergy(ServerLevel level, BlockPos pos, int max, boolean includeStorage) {
		if (--receiverRefresh <= 0) {
			cachedReceivers = EnergyNetwork.findReceivers(level, pos, this);
			receiverRefresh = 20;
		}
		if (energy > 0 && !cachedReceivers.isEmpty()) {
			energy -= EnergyNetwork.distribute(cachedReceivers, Math.min(max, energy), includeStorage);
		}
	}

	/** Shared implementation of {@link EnergyReceiver#receiveEnergy} for consumers. */
	protected int acceptEnergy(int maxAmount, boolean simulate) {
		int accepted = Math.max(0, Math.min(maxAmount, getCapacity() - energy));
		if (!simulate && accepted > 0) {
			energy += accepted;
			setChanged();
		}
		return accepted;
	}

	/** Can {@code result} be added to the given output slot? */
	protected boolean canOutput(int slot, ItemStack result) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(out, result)
				&& out.getCount() + result.getCount() <= Math.min(getMaxStackSize(), out.getMaxStackSize());
	}

	protected void addOutput(int slot, ItemStack result) {
		ItemStack out = items.get(slot);
		if (out.isEmpty()) {
			items.set(slot, result.copy());
		} else {
			out.grow(result.getCount());
		}
	}

	protected static void setActive(ServerLevel level, BlockPos pos, BlockState state, boolean active) {
		if (state.getValue(MachineBlock.LIT) != active) {
			level.setBlock(pos, state.setValue(MachineBlock.LIT, active), 3);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.energy = Math.min(getCapacity(), input.getIntOr("energy", 0));
		this.progress = input.getIntOr("progress", 0);
		this.progress2 = input.getIntOr("progress2", 0);
		this.progressTotal = input.getIntOr("progress_total", this.progressTotal);
		int packed = input.getIntOr("side_config", -1);
		if (packed >= 0) {
			sides.unpack(packed);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("energy", energy);
		output.putInt("progress", progress);
		output.putInt("progress2", progress2);
		output.putInt("progress_total", progressTotal);
		output.putInt("side_config", sides.pack());
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}
}
