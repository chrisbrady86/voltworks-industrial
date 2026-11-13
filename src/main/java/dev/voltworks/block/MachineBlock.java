package dev.voltworks.block;

import dev.voltworks.block.entity.EnergyGauge;
import dev.voltworks.block.entity.ReactorBlockEntity;
import dev.voltworks.block.entity.StatusReporter;
import dev.voltworks.block.entity.VoltTicking;
import dev.voltworks.energy.EnergyConnectable;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Shared block for every Voltworks machine: faces the player and has an "active" (LIT) state. */
public class MachineBlock extends BaseEntityBlock implements EnergyConnectable {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** How full the machine's energy buffer is, shown by the gauge on its front (0 empty .. 6 full). */
	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, EnergyGauge.MAX_LEVEL);
	/** Only re-check the gauge every few ticks, so a buffer hovering at a boundary doesn't flicker. */
	private static final int GAUGE_INTERVAL = 10;

	private final Supplier<BlockEntityType<?>> type;

	public MachineBlock(Supplier<BlockEntityType<?>> type, Properties properties) {
		super(properties);
		this.type = type;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(CHARGE, 0));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return type.get().create(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actual) {
		if (level.isClientSide() || actual != type.get()) {
			return null;
		}
		BlockEntityTicker<BlockEntity> ticker = (lvl, pos, st, be) -> {
			if (be instanceof VoltTicking ticking) {
				ticking.serverTick((ServerLevel) lvl, pos, st);
			}
			if (be instanceof EnergyGauge gauge && !be.isRemoved() && lvl.getGameTime() % GAUGE_INTERVAL == 0) {
				BlockState current = lvl.getBlockState(pos);
				int gaugeLevel = gauge.gaugeLevel();
				if (current.getBlock() == this && current.getValue(CHARGE) != gaugeLevel) {
					lvl.setBlock(pos, current.setValue(CHARGE, gaugeLevel), Block.UPDATE_CLIENTS);
				}
			}
		};
		return (BlockEntityTicker<T>) ticker;
	}

	/** Holding a wrench: skip opening the GUI so the wrench can rotate, configure or pick up the machine. */
	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof dev.voltworks.item.WrenchItem) {
			return InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof MenuProvider menu) {
				player.openMenu(menu);
			} else if (be instanceof StatusReporter reporter) {
				player.sendOverlayMessage(reporter.getStatus());
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	/** Comparators read a reactor's heat, or how full a machine's inventory is. */
	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof ReactorBlockEntity reactor) {
			return reactor.getComparatorSignal();
		}
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(be);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, CHARGE);
	}
}
