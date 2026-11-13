package dev.voltworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Data cable: links Drive Racks, Storage Terminals and Computer Crafters into one storage network. Carries no power. */
public class DataCableBlock extends PipeBlock implements DataConnectable {
	public DataCableBlock(Properties properties) {
		super(4.0F, properties);
		BlockState state = this.stateDefinition.any();
		for (var prop : PROPERTY_BY_DIRECTION.values()) state = state.setValue(prop, false);
		this.registerDefaultState(state);
	}

	private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction dir : Direction.values()) {
			state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), level.getBlockState(pos.relative(dir)).getBlock() instanceof DataConnectable);
		}
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(PROPERTY_BY_DIRECTION.get(direction), neighbourState.getBlock() instanceof DataConnectable);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}
}
