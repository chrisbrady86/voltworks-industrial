package dev.voltworks.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Goes in a machine's upgrade slots (right side of the GUI). Effects stack per item. */
public class UpgradeItem extends Item {
	public enum Type {
		/** Each: 30% faster, 60% more power per tick (about 12% more power per item). Max 8 count. */
		SPEED,
		/** Each: +4,000 J of internal storage (machine total capped at 32,000 J). */
		CAPACITY,
		/** Pushes finished items into any adjacent inventory or machine. One is enough. */
		EJECTOR
	}

	public final Type type;

	public UpgradeItem(Properties properties, Type type) {
		super(properties.stacksTo(16));
		this.type = type;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String key = "tooltip.voltworks.upgrade." + type.name().toLowerCase(java.util.Locale.ROOT);
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.voltworks.upgrade.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
