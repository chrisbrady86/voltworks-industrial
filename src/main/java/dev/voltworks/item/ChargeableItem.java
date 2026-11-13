package dev.voltworks.item;

import dev.voltworks.registry.ModComponents;
import net.minecraft.world.item.ItemStack;

/** An item that stores energy (J) in its {@code voltworks:energy} component and can be charged in a Charger. */
public interface ChargeableItem {
	int getMaxEnergy();

	default int getEnergy(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENERGY, 0);
	}

	default void setEnergy(ItemStack stack, int energy) {
		stack.set(ModComponents.ENERGY, Math.max(0, Math.min(getMaxEnergy(), energy)));
	}

	/** @return how much was accepted. */
	default int charge(ItemStack stack, int amount) {
		int accepted = Math.max(0, Math.min(amount, getMaxEnergy() - getEnergy(stack)));
		if (accepted > 0) {
			setEnergy(stack, getEnergy(stack) + accepted);
		}
		return accepted;
	}

	default boolean isFull(ItemStack stack) {
		return getEnergy(stack) >= getMaxEnergy();
	}
}
