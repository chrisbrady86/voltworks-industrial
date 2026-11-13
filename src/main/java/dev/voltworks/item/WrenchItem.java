package dev.voltworks.item;

import dev.voltworks.Voltworks;
import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.ItemPipeBlock.Side;
import dev.voltworks.block.MachineBlock;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.block.entity.SideConfig;
import dev.voltworks.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wrench.
 * <ul>
 *   <li>Right-click in the air: switch between Rotate and Configure mode.</li>
 *   <li>Rotate mode, right-click a block: turn it (machines face the side you clicked).</li>
 *   <li>Configure mode, right-click a machine side: cycle that side through none / input / output / both.</li>
 *   <li>Right-click a pipe (either mode): cycle the connection you clicked (connected / extract / disabled).</li>
 *   <li>Sneak + right-click a Voltworks block: pick it up into your inventory.</li>
 * </ul>
 */
public class WrenchItem extends Item {
	public WrenchItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static boolean isConfigureMode(ItemStack stack) {
		return stack.getOrDefault(ModComponents.CONFIG_MODE, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean configure = !isConfigureMode(stack);
		stack.set(ModComponents.CONFIG_MODE, configure);
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable(configure ? "message.voltworks.wrench.configure" : "message.voltworks.wrench.rotate"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (level.isClientSide() || player == null) {
			return InteractionResult.SUCCESS;
		}
		BlockState state = level.getBlockState(pos);
		Component message;
		if (player.isSecondaryUseActive()) {
			message = dismantle(level, pos, player) ? null : Component.translatable("message.voltworks.wrench.cant_pick_up");
		} else if (state.getBlock() instanceof ItemPipeBlock) {
			message = cyclePipe(level, pos, pipeSideAt(context.getClickLocation(), pos, context.getClickedFace()));
		} else if (isConfigureMode(context.getItemInHand())) {
			message = configure(level, pos, context.getClickedFace());
		} else {
			message = rotate(level, pos, context.getClickedFace());
		}
		if (message != null) {
			player.sendOverlayMessage(message);
		}
		return InteractionResult.SUCCESS;
	}

	/** Which pipe arm was clicked: the arm the hit point sits on, or the core face that was clicked. */
	public static Direction pipeSideAt(Vec3 hit, BlockPos pos, Direction clickedFace) {
		Vec3 local = hit.subtract(Vec3.atCenterOf(pos));
		Direction best = Direction.getApproximateNearest(local.x, local.y, local.z);
		double reach = Math.abs(local.x * best.getStepX() + local.y * best.getStepY() + local.z * best.getStepZ());
		return reach > 0.2 ? best : clickedFace;
	}

	/** Pick up a Voltworks block (contents spill out as normal). */
	public static boolean dismantle(Level level, BlockPos pos, Player player) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Voltworks.MOD_ID) || block.asItem() == net.minecraft.world.item.Items.AIR) {
			return false;
		}
		ItemStack drop = new ItemStack(block.asItem());
		level.destroyBlock(pos, false, player, 512);
		if (!player.getInventory().add(drop)) {
			Block.popResource(level, pos, drop); // inventory full: drop it where the block was
		}
		return true;
	}

	/** Machines turn to face the clicked side (or a quarter turn if it already does); other blocks turn clockwise. */
	public static @Nullable Component rotate(Level level, BlockPos pos, Direction clickedFace) {
		BlockState state = level.getBlockState(pos);
		BlockState rotated;
		if (state.getBlock() instanceof MachineBlock) {
			Direction facing = state.getValue(MachineBlock.FACING);
			Direction target = clickedFace.getAxis().isHorizontal() && clickedFace != facing ? clickedFace : facing.getClockWise();
			rotated = state.setValue(MachineBlock.FACING, target);
		} else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
				|| (state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE)) {
			return Component.translatable("message.voltworks.wrench.cant_rotate");
		} else {
			rotated = state.rotate(Rotation.CLOCKWISE_90);
		}
		if (rotated == state) {
			return Component.translatable("message.voltworks.wrench.cant_rotate");
		}
		level.setBlock(pos, rotated, Block.UPDATE_ALL);
		return null;
	}

	/** Cycle one side of a machine: none, input, output, both. */
	public static Component configure(Level level, BlockPos pos, Direction face) {
		if (!(level.getBlockEntity(pos) instanceof PoweredContainerBlockEntity machine)) {
			return Component.translatable("message.voltworks.wrench.not_configurable");
		}
		SideConfig.Mode mode = machine.cycleSideMode(face);
		level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
		return Component.translatable("message.voltworks.wrench.side", machine.sideOf(face).label(), mode.label());
	}

	/** Cycle a pipe connection: to a pipe, connected/disabled; to an inventory, connected/extract/disabled. */
	public static Component cyclePipe(Level level, BlockPos pos, Direction dir) {
		BlockState state = level.getBlockState(pos);
		BlockPos npos = pos.relative(dir);
		BlockState nstate = level.getBlockState(npos);
		if (!ItemPipeBlock.canConnect(level, npos, nstate)) {
			return Component.translatable("message.voltworks.pipe.nothing");
		}
		Side current = ItemPipeBlock.get(state, dir);
		boolean toPipe = nstate.getBlock() instanceof ItemPipeBlock;
		Side next = switch (current) {
			case NONE, DISABLED -> Side.CONNECTED;
			case CONNECTED -> toPipe ? Side.DISABLED : Side.EXTRACT;
			case EXTRACT -> Side.DISABLED;
		};
		setPipeSide(level, pos, dir, next);
		if (toPipe) {
			// Keep both pipes in agreement so a cut-off join stays cut off
			setPipeSide(level, npos, dir.getOpposite(), next == Side.DISABLED ? Side.DISABLED : Side.CONNECTED);
		}
		return Component.translatable("message.voltworks.pipe." + next.getSerializedName());
	}

	public static void setPipeSide(Level level, BlockPos pos, Direction dir, Side side) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ItemPipeBlock) {
			level.setBlock(pos, state.setValue(ItemPipeBlock.SIDES.get(dir), side), Block.UPDATE_ALL);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable(isConfigureMode(stack) ? "tooltip.voltworks.wrench.configure" : "tooltip.voltworks.wrench.rotate")
				.withStyle(ChatFormatting.YELLOW));
		builder.accept(Component.translatable("tooltip.voltworks.wrench.hint1").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.voltworks.wrench.hint2").withStyle(ChatFormatting.GRAY));
	}
}
