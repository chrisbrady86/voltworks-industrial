package dev.voltworks.client;

import dev.voltworks.item.GuideBookItem;
import dev.voltworks.menu.MachineMenu;
import dev.voltworks.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public class VoltworksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		for (MachineMenu.Kind kind : MachineMenu.Kind.values()) {
			MenuScreens.register(ModMenus.get(kind), MachineScreen::new);
		}
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
