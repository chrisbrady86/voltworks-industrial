package dev.voltworks.block.entity;

import dev.voltworks.energy.EnergyNetwork;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Stores up to 100,000 J. Charges from generators, discharges into machines. */
public class BatteryBlockEntity extends BlockEntity implements EnergyReceiver, VoltTicking, StatusReporter {
	public static final int CAPACITY = 100_000;
	public static final int MAX_IO = 64;
	private int energy;
	private List<EnergyReceiver> receivers = List.of();
	private int refresh;

	public BatteryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BATTERY, pos, state);
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (--refresh <= 0) {
			receivers = EnergyNetwork.findReceivers(level, pos, this);
			refresh = 20;
		}
		if (energy > 0) {
			int sent = EnergyNetwork.distribute(receivers, Math.min(MAX_IO, energy), false);
			if (sent > 0) {
				energy -= sent;
				setChanged();
			}
		}
	}

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		int accepted = Math.min(Math.min(maxAmount, MAX_IO), CAPACITY - energy);
		if (!simulate && accepted > 0) {
			energy += accepted;
			setChanged();
		}
		return accepted;
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
		return Component.translatable("message.voltworks.battery", energy, CAPACITY);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		energy = Math.min(CAPACITY, input.getIntOr("energy", 0));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("energy", energy);
	}
}
