package dev.voltworks.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public interface VoltTicking {
	void serverTick(ServerLevel level, BlockPos pos, BlockState state);
}
