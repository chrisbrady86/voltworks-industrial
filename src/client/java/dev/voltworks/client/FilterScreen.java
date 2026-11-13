package dev.voltworks.client;

import dev.voltworks.menu.FilterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Filter pipe GUI, drawn from rectangles like the machine screens: 9 ghost slots and an Allow / Deny button. */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
	private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, DARK = 0xFF373737;
	private static final int BTN_X = 8, BTN_Y = 50, BTN_W = 70, BTN_H = 16;

	public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
		g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
		g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
		g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);

		for (Slot slot : menu.slots) {
			int sx = x + slot.x, sy = y + slot.y;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
			g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
			// Filter slots are tinted by mode: green = Allow, red = Deny
			int bg = slot instanceof FilterMenu.GhostSlot ? (menu.isDeny() ? 0xFFA07070 : 0xFF70A070) : 0xFF8B8B8B;
			g.fill(sx, sy, sx + 16, sy + 16, bg);
		}

		// Allow / Deny button
		boolean hover = inButton(mouseX, mouseY);
		int bx = x + BTN_X, by = y + BTN_Y;
		g.fill(bx, by, bx + BTN_W, by + BTN_H, DARK);
		g.fill(bx + 1, by + 1, bx + BTN_W - 1, by + BTN_H - 1, hover ? 0xFF9A9A9A : 0xFF7A7A7A);
		Component label = Component.translatable(menu.isDeny() ? "gui.voltworks.filter.deny" : "gui.voltworks.filter.allow");
		int color = menu.isDeny() ? 0xFFFF8080 : 0xFF80FF80;
		g.text(font, label, bx + (BTN_W - font.width(label)) / 2, by + 4, color, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (inButton(mouseX, mouseY)) {
			g.setTooltipForNextFrame(Component.translatable(menu.isDeny() ? "gui.voltworks.filter.deny.hint" : "gui.voltworks.filter.allow.hint"),
					mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (inButton(event.x(), event.y())) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.BUTTON_TOGGLE_MODE);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean inButton(double mx, double my) {
		return mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W && my >= topPos + BTN_Y && my < topPos + BTN_Y + BTN_H;
	}
}
