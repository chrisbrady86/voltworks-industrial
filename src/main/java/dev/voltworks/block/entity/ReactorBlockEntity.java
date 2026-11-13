package dev.voltworks.block.entity;

import dev.voltworks.item.FuelRodItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.registry.ModGameRules;
import dev.voltworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Reactor Chamber: a 3x3 grid of fuel rods and heat vents.
 *
 * <ul>
 *   <li>Runs only while it receives a redstone signal, and pauses (no power, heat or fuel use) while its
 *       internal buffer is full, so an idle reactor doesn't waste fuel.</li>
 *   <li>Each fuel rod "pulses" once, plus once more for every rod directly beside it in the grid.
 *       Each pulse makes {@value #POWER_PER_PULSE} J and {@value #HEAT_PER_PULSE} heat per tick.</li>
 *   <li>Each heat vent removes {@value #VENT_BASE} heat per tick, plus {@value #VENT_PER_ROD} for every rod beside it.</li>
 *   <li>Every water source block touching the reactor removes {@value #WATER_COOLING} heat per tick.</li>
 *   <li>At {@value #MAX_HEAT} heat it melts down and explodes. The {@code voltworks:reactor_meltdown} gamerule can make
 *       the blast spare blocks ({@code contained}) or replace it with an emergency shutdown ({@code off}).</li>
 * </ul>
 * A comparator reads the heat level (0-15), so you can build an automatic shutdown.
 */
public class ReactorBlockEntity extends PoweredContainerBlockEntity implements StatusReporter {
	public static final int SIZE = 3;
	public static final int MAX_HEAT = 10_000;
	public static final int POWER_PER_PULSE = 25;
	public static final int HEAT_PER_PULSE = 2;
	public static final int VENT_BASE = 2;
	public static final int VENT_PER_ROD = 2;
	public static final int WATER_COOLING = 2;
	public static final int PASSIVE_COOLING = 1;
	public static final float MELTDOWN_POWER = 12.0F; // TNT is 4, a charged creeper 6
	public static final int MAX_PUSH = 512;
	/** With meltdowns off, a shut-down reactor restarts once it has cooled below this heat. */
	public static final int SHUTDOWN_RESTART = MAX_HEAT * 9 / 10;
	private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	/** Not saved: a reloaded reactor at 100% heat simply shuts down again on its first tick. */
	private boolean shutDown;

	public ReactorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.REACTOR, pos, state, SIZE * SIZE, 30_000);
		this.progressTotal = MAX_HEAT; // progress = heat, progress2 = output J/t (shown in the GUI)
	}

	public int getHeat() {
		return progress;
	}

	public int getOutputPerTick() {
		return progress2;
	}

	public void setHeat(int heat) {
		progress = Math.max(0, heat);
		setChanged();
	}

	private boolean isLiveRod(int slot) {
		ItemStack s = items.get(slot);
		return s.is(ModItems.FUEL_ROD) && FuelRodItem.getFuel(s) > 0;
	}

	/** Number of live fuel rods directly beside this grid slot (up/down/left/right). */
	private int adjacentRods(int slot) {
		int x = slot % SIZE, y = slot / SIZE, n = 0;
		if (x > 0 && isLiveRod(slot - 1)) n++;
		if (x < SIZE - 1 && isLiveRod(slot + 1)) n++;
		if (y > 0 && isLiveRod(slot - SIZE)) n++;
		if (y < SIZE - 1 && isLiveRod(slot + SIZE)) n++;
		return n;
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		ModGameRules.Meltdown mode = level.getGameRules().get(ModGameRules.REACTOR_MELTDOWN);
		// With meltdowns off, a reactor that hits 100% heat shuts down until it has cooled to 90%.
		if (mode != ModGameRules.Meltdown.off || progress < SHUTDOWN_RESTART) {
			shutDown = false;
		}
		boolean enabled = !shutDown && level.hasNeighborSignal(pos) && energy < getCapacity();
		int output = 0, heatIn = 0, cooling = PASSIVE_COOLING;

		// Work out this tick's numbers before changing any rods.
		boolean[] live = new boolean[items.size()];
		for (int i = 0; i < items.size(); i++) live[i] = isLiveRod(i);
		for (int i = 0; i < items.size(); i++) {
			int adj = adjacentRods(i);
			if (live[i] && enabled) {
				int pulses = 1 + adj;
				output += pulses * POWER_PER_PULSE;
				heatIn += pulses * HEAT_PER_PULSE;
			} else if (items.get(i).is(ModItems.HEAT_VENT)) {
				cooling += VENT_BASE + VENT_PER_ROD * (enabled ? adj : 0);
			}
		}
		for (Direction dir : Direction.values()) {
			FluidState fluid = level.getFluidState(pos.relative(dir));
			if (fluid.is(FluidTags.WATER) && fluid.isSource()) cooling += WATER_COOLING;
		}

		// Burn fuel
		if (enabled && output > 0) {
			for (int i = 0; i < items.size(); i++) {
				if (!live[i]) continue;
				ItemStack rod = items.get(i);
				int fuel = FuelRodItem.getFuel(rod) - 1;
				if (fuel <= 0) {
					items.set(i, new ItemStack(ModItems.DEPLETED_FUEL_ROD));
				} else {
					FuelRodItem.setFuel(rod, fuel);
				}
			}
			energy = Math.min(getCapacity(), energy + output);
		}

		int heat = Math.max(0, progress + heatIn - cooling);
		progress = heat;
		progress2 = enabled ? output : 0;
		if (enabled && output > 0 || heat > 0) setChanged();

		pushEnergy(level, pos, MAX_PUSH, true);
		setActive(level, pos, state, enabled && output > 0);

		if (heat >= MAX_HEAT && mode == ModGameRules.Meltdown.off) {
			progress = MAX_HEAT;
			shutDown = true;
		} else if (heat >= MAX_HEAT) {
			meltdown(level, pos, mode == ModGameRules.Meltdown.destructive);
			return;
		}
		if (heat > MAX_HEAT * 7 / 10 && level.getGameTime() % 10 == 0) {
			// Warning signs: smoke pours out when it's running hot.
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.02);
		}
	}

	/** {@code destructive}: blocks break and fires start. Otherwise the blast only hurts entities (gamerule {@code contained}). */
	private void meltdown(ServerLevel level, BlockPos pos, boolean destructive) {
		items.clear(); // the fuel is consumed in the blast, nothing drops
		level.removeBlock(pos, false);
		level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, MELTDOWN_POWER, destructive,
				destructive ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
	}

	/** True while a reactor is shut down after reaching 100% heat (only with meltdowns off). */
	public boolean isShutDown() {
		return shutDown;
	}

	/** Comparator output: 0 when cold, 15 at meltdown. */
	public int getComparatorSignal() {
		return Math.min(15, (int) Math.ceil(15.0 * progress / MAX_HEAT));
	}

	@Override
	public Component getStatus() {
		if (shutDown) return Component.translatable("message.voltworks.reactor_shutdown", Math.round(100.0F * progress / MAX_HEAT));
		return Component.translatable("message.voltworks.reactor", progress2, Math.round(100.0F * progress / MAX_HEAT));
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.is(ModItems.FUEL_ROD) || stack.is(ModItems.HEAT_VENT);
	}

	@Override
	protected SideConfig.Mode defaultSideMode(SideConfig.Side side) {
		return SideConfig.Mode.BOTH;
	}

	@Override
	protected int[] inputSlots() {
		return ALL_SLOTS;
	}

	@Override
	protected int[] outputSlots() {
		return ALL_SLOTS;
	}

	@Override
	protected boolean allowInsert(int slot, ItemStack stack) {
		return stack.is(ModItems.FUEL_ROD); // hoppers and pipes refuel; vents are placed by hand
	}

	@Override
	protected boolean allowExtract(int slot, ItemStack stack) {
		return stack.is(ModItems.DEPLETED_FUEL_ROD); // hoppers and pipes pull out spent rods
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks.reactor_chamber");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(MachineMenu.Kind.REACTOR, containerId, inventory, this, data);
	}
}
