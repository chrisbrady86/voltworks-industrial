package dev.voltworks.block.entity;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.energy.EnergyNetwork;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Tier 2 passive power: 5 J/tick for every lava source block touching it (up to 25 J/tick). */
public class ThermalGeneratorBlockEntity extends BlockEntity implements VoltTicking, StatusReporter {
	public static final int PER_LAVA_SOURCE = 5;
	private List<EnergyReceiver> receivers = List.of();
	private int refresh;
	private int lavaSources;

	public ThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.THERMAL_GENERATOR, pos, state);
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (--refresh <= 0) {
			refresh = 20;
			receivers = EnergyNetwork.findReceivers(level, pos, this);
			lavaSources = 0;
			for (Direction dir : Direction.values()) {
				FluidState fluid = level.getFluidState(pos.relative(dir));
				if (fluid.is(FluidTags.LAVA) && fluid.isSource()) {
					lavaSources++;
				}
			}
			lavaSources = Math.min(lavaSources, 5); // at least one side is needed for a cable
			boolean on = lavaSources > 0;
			if (state.getValue(MachineBlock.LIT) != on) {
				level.setBlock(pos, state.setValue(MachineBlock.LIT, on), 3);
			}
		}
		if (lavaSources > 0) {
			EnergyNetwork.distribute(receivers, lavaSources * PER_LAVA_SOURCE, true);
		}
	}

	public int getOutputPerTick() {
		return lavaSources * PER_LAVA_SOURCE;
	}

	@Override
	public Component getStatus() {
		return lavaSources > 0
				? Component.translatable("message.voltworks.thermal.on", getOutputPerTick(), lavaSources)
				: Component.translatable("message.voltworks.thermal.off");
	}
}
