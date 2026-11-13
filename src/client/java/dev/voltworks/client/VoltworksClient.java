package dev.voltworks.client;

import dev.voltworks.block.ItemPipeBlock;
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
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public class VoltworksClient implements ClientModInitializer {
	/** Opens JEI's recipe page for an item; set by the JEI plugin when JEI is installed (used by the in-game test). */
	public static java.util.function.Consumer<net.minecraft.world.item.ItemStack> showRecipes = stack -> {
	};
	/** Opens one of the Voltworks machine pages in JEI ("alloy_smelting", "crushing", "refining"); set by the JEI plugin. */
	public static java.util.function.Consumer<String> showMachinePage = id -> {
	};

	@Override
	public void onInitializeClient() {
		for (MachineMenu.Kind kind : MachineMenu.Kind.values()) {
			MenuScreens.register(ModMenus.get(kind), MachineScreen::new);
		}
		MenuScreens.register(ModMenus.FILTER, FilterScreen::new);
		// Pipe items explain the one thing that isn't automatic: pulling items out of a chest
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ItemPipeBlock) {
				lines.add(Component.translatable("tooltip.voltworks.pipe.machines").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("tooltip.voltworks.pipe.chests").withStyle(ChatFormatting.YELLOW));
			}
		});
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
