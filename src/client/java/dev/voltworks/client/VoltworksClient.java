package dev.voltworks.client;

import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class VoltworksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (MachineMenu.Kind kind : MachineMenu.Kind.values()) {
			MenuScreens.register(ModMenus.get(kind), MachineScreen::new);
		}
	}
}
