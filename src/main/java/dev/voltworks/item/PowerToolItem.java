package dev.voltworks.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tool that runs on stored energy instead of durability. It never breaks: when it runs
 * out of energy it mines at bare-hand speed and won't harvest drops until recharged.
 */
public class PowerToolItem extends Item implements ChargeableItem {
	private static final int BAR_COLOR = 0x40C0FF;

	private final int maxEnergy;
	protected final int energyPerBlock;

	public PowerToolItem(Properties properties, int maxEnergy, int energyPerBlock) {
		super(properties.stacksTo(1));
		this.maxEnergy = maxEnergy;
		this.energyPerBlock = energyPerBlock;
	}

	/** Build a tool component: deny drops for blocks above {@code incorrectTag}'s tier, mine the given tags at {@code speed}. */
	@SafeVarargs
	public static Properties toolProperties(Properties props, TagKey<Block> incorrectTag, float speed, TagKey<Block>... mines) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		List<Tool.Rule> rules = new ArrayList<>();
		rules.add(Tool.Rule.deniesDrops(blocks.getOrThrow(incorrectTag)));
		for (TagKey<Block> tag : mines) {
			rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(tag), speed));
		}
		return props.component(DataComponents.TOOL, new Tool(rules, 1.0F, 0, true));
	}

	@Override
	public int getMaxEnergy() {
		return maxEnergy;
	}

	public boolean hasPower(ItemStack stack) {
		return getEnergy(stack) >= energyPerBlock;
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return hasPower(stack) ? super.getDestroySpeed(stack, state) : 1.0F;
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		return hasPower(stack) && super.isCorrectToolForDrops(stack, state);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F && hasPower(stack)) {
			setEnergy(stack, getEnergy(stack) - energyPerBlock);
		}
		return true;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * getEnergy(stack) / maxEnergy);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.voltworks.energy", getEnergy(stack), maxEnergy).withStyle(ChatFormatting.AQUA));
		builder.accept(Component.translatable("tooltip.voltworks.per_block", energyPerBlock).withStyle(ChatFormatting.GRAY));
	}
}
