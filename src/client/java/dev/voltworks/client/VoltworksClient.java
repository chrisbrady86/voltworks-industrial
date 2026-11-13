package dev.voltworks.client;

import dev.voltworks.item.GuideBookItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.menu.NetworkMenu;
import dev.voltworks.network.StorageViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.voltworks.registry.ModBlockEntities;
import dev.voltworks.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public class VoltworksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (MachineMenu.Kind kind : MachineMenu.Kind.values()) {
			MenuScreens.register(ModMenus.get(kind), MachineScreen::new);
		}
		MenuScreens.register(ModMenus.FILTER, FilterScreen::new);
		MenuScreens.register(ModMenus.STORAGE_TERMINAL, StorageTerminalScreen::new);
		MenuScreens.register(ModMenus.COMPUTER_CRAFTER, ComputerCrafterScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(StorageViewPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof NetworkMenu menu && menu.containerId == payload.containerId()) {
				menu.view = payload.items();
				menu.online = payload.online();
				menu.used = payload.used();
				menu.capacity = payload.capacity();
			}
		});
		BlockEntityRenderers.register(ModBlockEntities.ITEM_PIPE, PipeRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.FILTER_PIPE, PipeRenderer::new);
		GuideBookItem.opener = () -> Minecraft.getInstance().gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(guidePages())));
	}

	/** Reads "guide.voltworks.page.1", "...2", ... from the current language until a page is missing. */
	public static List<Component> guidePages() {
		List<Component> pages = new ArrayList<>();
		Language lang = Language.getInstance();
		for (int i = 1; lang.has("guide.voltworks.page." + i); i++) {
			pages.add(Component.translatable("guide.voltworks.page." + i));
		}
		return pages;
	}
}
