package dev.voltworks.item;

import dev.voltworks.registry.ModComponents;
import dev.voltworks.storage.DriveContents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A hard drive for the Drive Rack. Holds up to {@code capacity} items of up to {@value #MAX_TYPES} different kinds.
 * The contents live on the item, so a drive keeps them when taken out of a rack (or carried to another base).
 */
public class HardDriveItem extends Item {
	public static final int MAX_TYPES = 63;

	public enum Size {
		SMALL(1_024), MEDIUM(4_096), LARGE(16_384), HUGE(65_536);

		public final int capacity;

		Size(int capacity) {
			this.capacity = capacity;
		}
	}

	public final Size size;

	public HardDriveItem(Properties properties, Size size) {
		super(properties.stacksTo(1));
		this.size = size;
	}

	public static DriveContents contents(ItemStack drive) {
		return drive.getOrDefault(ModComponents.DRIVE_CONTENTS, DriveContents.EMPTY);
	}

	public static void setContents(ItemStack drive, DriveContents contents) {
		if (contents.entries().isEmpty()) drive.remove(ModComponents.DRIVE_CONTENTS);
		else drive.set(ModComponents.DRIVE_CONTENTS, contents);
	}

	/** How many of {@code stack} this drive could still take. */
	public int room(ItemStack drive, ItemStack stack) {
		DriveContents c = contents(drive);
		if (c.countOf(stack) == 0 && c.types() >= MAX_TYPES) return 0;
		return Math.max(0, size.capacity - c.total());
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return contents(stack).total() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * contents(stack).total() / size.capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		float fill = (float) contents(stack).total() / size.capacity;
		return fill < 0.75F ? 0x40C0FF : fill < 0.95F ? 0xFFB020 : 0xFF4040;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		DriveContents c = contents(stack);
		builder.accept(Component.translatable("tooltip.voltworks.drive.usage", c.total(), size.capacity).withStyle(ChatFormatting.AQUA));
		builder.accept(Component.translatable("tooltip.voltworks.drive.types", c.types(), MAX_TYPES).withStyle(ChatFormatting.GRAY));
	}
}
