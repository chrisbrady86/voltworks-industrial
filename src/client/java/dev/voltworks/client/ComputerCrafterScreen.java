package dev.voltworks.client;

import dev.voltworks.menu.ComputerCrafterMenu;
import dev.voltworks.menu.NetworkMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Computer Crafter GUI. Top: the 3x3 pattern (the highlighted slot is where list clicks go), its result and the
 * Craft x1 / x8 / x64 buttons. Below: the searchable storage list. In this list a left click puts the item into the
 * pattern; right-click takes a stack and shift-click sends one to your inventory.
 */
public class ComputerCrafterScreen extends StorageScreen<ComputerCrafterMenu> {
	private static final int BTN_X = 128, BTN_Y = 16, BTN_W = 40, BTN_H = 13, BTN_GAP = 16;
	private int selected;

	public ComputerCrafterScreen(ComputerCrafterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ComputerCrafterMenu.INVENTORY_Y + 82, 7, 79, 3);
		inventoryLabelY = ComputerCrafterMenu.INVENTORY_Y - 11;
	}

	@Override
	protected int[] searchBox() {
		return new int[]{90, 64, 78};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;
		// Pattern slots: the selected one is outlined in yellow
		for (int i = 0; i < 9; i++) {
			Slot s = menu.slots.get(i);
			drawSlot(g, x + s.x, y + s.y, i == selected ? 0xFFB8A860 : 0xFF7A8AA0);
		}
		Slot result = menu.resultSlot();
		// Arrow to the result
		g.fill(x + 84, y + 40, x + 98, y + 44, DARK);
		g.fill(x + 96, y + 37, x + 98, y + 47, DARK);
		drawSlot(g, x + result.x, y + result.y, result.getItem().isEmpty() ? 0xFF8B6B6B : 0xFF6B8B6B);
		for (int i = 0; i < ComputerCrafterMenu.CRAFT_AMOUNTS.length; i++) {
			int bx = x + BTN_X, by = y + BTN_Y + i * BTN_GAP;
			boolean hover = inButton(i, mouseX, mouseY);
			boolean enabled = !result.getItem().isEmpty() && menu.online;
			g.fill(bx, by, bx + BTN_W, by + BTN_H, DARK);
			g.fill(bx + 1, by + 1, bx + BTN_W - 1, by + BTN_H - 1, !enabled ? 0xFF6A6A6A : hover ? 0xFF9AB09A : 0xFF7A907A);
			Component label = Component.translatable("gui.voltworks.crafter.craft", ComputerCrafterMenu.CRAFT_AMOUNTS[i]);
			g.text(font, label, bx + (BTN_W - font.width(label)) / 2, by + 3, enabled ? 0xFFFFFFFF : 0xFFA0A0A0, true);
		}
	}

	private boolean inButton(int i, double mx, double my) {
		int bx = leftPos + BTN_X, by = topPos + BTN_Y + i * BTN_GAP;
		return mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (int i = 0; i < ComputerCrafterMenu.CRAFT_AMOUNTS.length; i++) {
			if (inButton(i, event.x(), event.y())) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, i);
				return true;
			}
		}
		// Clicking a pattern slot also selects it for list clicks
		for (int i = 0; i < 9; i++) {
			Slot s = menu.slots.get(i);
			if (event.x() >= leftPos + s.x && event.x() < leftPos + s.x + 16 && event.y() >= topPos + s.y && event.y() < topPos + s.y + 16) {
				selected = i;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void onGridClick(ItemStack item, int button, boolean shift) {
		if (menu.getCarried().isEmpty() && !item.isEmpty() && !shift) {
			if (button == 0) {
				send(item, NetworkMenu.ACTION_SET_PATTERN, selected);
				selected = (selected + 1) % 9;
			} else {
				send(item, NetworkMenu.ACTION_TAKE, 0);
			}
			return;
		}
		super.onGridClick(item, button, shift);
	}
}
