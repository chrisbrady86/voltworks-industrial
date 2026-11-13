package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyNetwork;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A battery: charges from generators, discharges into machines. Capacity and throughput depend on its {@link Tier}. */
public class BatteryBlockEntity extends BlockEntity implements EnergyReceiver, VoltTicking, StatusReporter, EnergyGauge {
	/** Battery sizes. The Medium Battery is the original Battery Box (its block id is still "battery_box"). */
	public enum Tier {
		SMALL(40_000, 128), MEDIUM(100_000, 512), LARGE(1_000_000, 2_048), HUGE(10_000_000, 8_192);

		public final int capacity, maxIo;

		Tier(int capacity, int maxIo) {
			this.capacity = capacity;
			this.maxIo = maxIo;
		}

		public static Tier of(Block block) {
			if (block == ModBlocks.SMALL_BATTERY) return SMALL;
			if (block == ModBlocks.LARGE_BATTERY) return LARGE;
			if (block == ModBlocks.HUGE_BATTERY) return HUGE;
			return MEDIUM;
		}
	}

	private int energy;
	private List<EnergyReceiver> receivers = List.of();
	private int refresh;

	public BatteryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BATTERY, pos, state);
	}

	public Tier tier() {
		return Tier.of(getBlockState().getBlock());
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (energy > tier().capacity) energy = tier().capacity;
		if (--refresh <= 0) {
			receivers = EnergyNetwork.findReceivers(level, pos, this);
			refresh = 20;
		}
		if (energy > 0) {
			int sent = EnergyNetwork.distribute(receivers, Math.min(tier().maxIo, energy), false);
			if (sent > 0) {
				energy -= sent;
				setChanged();
			}
		}
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		int accepted = Math.max(0, Math.min(Math.min(maxAmount, tier().maxIo), tier().capacity - energy));
		if (!simulate && accepted > 0) {
			energy += accepted;
			setChanged();
		}
		return accepted;
	}

	@Override
	public int getStoredEnergy() {
		return energy;
	}

	@Override
	public int getMaxEnergy() {
		return tier().capacity;
	}

	public int getEnergy() {
		return energy;
	}

	@Override
	public boolean isStorage() {
		return true;
	}

	@Override
	public Component getStatus() {
		return Component.translatable("message.voltworks.battery", energy, tier().capacity);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		energy = Math.max(0, input.getIntOr("energy", 0)); // clamped to the tier's capacity on the next tick
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("energy", energy);
	}
}
