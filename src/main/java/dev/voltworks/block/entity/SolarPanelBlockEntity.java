package dev.voltworks.block.entity;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.energy.EnergyNetwork;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Makes 1 J/tick in daylight with open sky above. No internal storage. */
public class SolarPanelBlockEntity extends BlockEntity implements VoltTicking, StatusReporter {
	public static final int OUTPUT_PER_TICK = 1;
	private List<EnergyReceiver> receivers = List.of();
	private int refresh;
	private boolean producing;

	public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLAR_PANEL, pos, state);
	}

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (--refresh <= 0) {
			receivers = EnergyNetwork.findReceivers(level, pos, this);
			producing = level.isBrightOutside() && !level.isRaining() && level.canSeeSky(pos.above());
			refresh = 20;
			if (state.getValue(MachineBlock.LIT) != producing) {
				level.setBlock(pos, state.setValue(MachineBlock.LIT, producing), 3);
			}
		}
		if (producing) {
			EnergyNetwork.distribute(receivers, OUTPUT_PER_TICK, true);
		}
	}

	@Override
	public Component getStatus() {
		return Component.translatable(producing ? "message.voltworks.solar.on" : "message.voltworks.solar.off");
	}
}
