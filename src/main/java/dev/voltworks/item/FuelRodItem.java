package dev.voltworks.item;

import dev.voltworks.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Uranium fuel rod for the Reactor Chamber. Burns for {@link #LIFETIME} ticks of reactor operation. */
public class FuelRodItem extends Item {
	public static final int LIFETIME = 20_000; // ~16.7 minutes of running

	public FuelRodItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static int getFuel(ItemStack stack) {
		return stack.getOrDefault(ModComponents.FUEL, LIFETIME);
	}

	public static void setFuel(ItemStack stack, int fuel) {
		stack.set(ModComponents.FUEL, Math.max(0, fuel));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return getFuel(stack) < LIFETIME;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * getFuel(stack) / LIFETIME);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x7CFF4A;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.voltworks.fuel", Math.round(100.0F * getFuel(stack) / LIFETIME)).withStyle(ChatFormatting.GREEN));
		builder.accept(Component.translatable("tooltip.voltworks.fuel_rod").withStyle(ChatFormatting.GRAY));
	}
}
