package dev.voltworks.block.entity;

import net.minecraft.network.chat.Component;

/** Blocks without a GUI report their status on right-click. */
public interface StatusReporter {
	Component getStatus();
}
